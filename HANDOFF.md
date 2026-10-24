# HANDOFF — Cyclocomp (bike computer GPS untuk Android lawas)

Dokumen serah-terima untuk developer / sesi AI berikutnya. Baca ini dulu sebelum mengubah kode.
Versi kerangka: 0.1.0 · Bahasa UI: Indonesia · Bahasa kode/komentar: Java + komentar Indonesia.

---

## 1. Tujuan

Aplikasi cycling computer (cyclocomp) yang berjalan di HP Android lawas:
- **Target utama: Android 4.4 KitKat (API 19)** ke atas.
- Alternatif yang diinginkan pemilik proyek: hanya **Android 7+ (API 24)**. Keputusan belum final,
  jadi kode ditulis kompatibel KitKat dan bisa disederhanakan nanti (lihat bagian 13).
- Fungsi inti: rekam rute GPS, tampilkan kecepatan/jarak/waktu/rata-rata/maks/tanjakan secara
  langsung, simpan riwayat, ekspor GPX.

## 2. Status saat ini (JUJUR)

| Hal | Status |
|---|---|
| Kerangka proyek lengkap (Gradle, manifest, resource, 7 kelas Java) | Selesai |
| Dikompilasi | **SUDAH** — build pertama lolos 23 Okt 2026 (run CI `39959658581`) |
| APK hasil | `app-debug.apk` 29 KB, rilis GitHub `v0.1.0` |
| `minSdk` | **23 (Android 6.0+)** — diputuskan 23 Okt 2026, dulu 19 (KitKat) |
| Diuji di perangkat | **BELUM** — ini langkah berikutnya |
| Unit test | Belum ada |
| Sensor BLE, pengaturan, peta, ekspor/bagikan | Belum (lihat roadmap, bagian 12) |
| Ikon launcher | Memakai ikon bawaan sistem (`@android:drawable/ic_menu_compass`), belum ada ikon sendiri |
| Build APK | **GitHub Actions** (bukan lokal): host pengembangan adalah ARM/aarch64, dan `aapt2` build-tools resmi hanya tersedia untuk x86_64, jadi build lokal Android tidak mungkin. Workflow `.github/workflows/build-apk.yml`; repo `github.com/AltairKaslana/cyclocomp` |

Langkah pertama yang disarankan sekarang: **uji di perangkat** (bagian 10), mulai dari 1 HP KitKat dan
1 HP Android 7+. Setelah itu roadmap nomor 1–3.

### Satu-satunya perbaikan yang diperlukan agar lolos kompilasi

`RideService.onStatusChanged` kehilangan anotasi `@Override`. Method itu sudah usang dan
**dikeluarkan dari antarmuka `LocationListener` sejak API 30**, jadi dengan `compileSdk 34`
compiler menolaknya (`method does not override or implement a method from a supertype`).
Anotasi dibuang; badan method dibiarkan kosong dan tetap aman.

## 3. Struktur proyek

```
cyclocomp/
├─ HANDOFF.md                 dokumen ini
├─ README.md                  ringkasan singkat
├─ settings.gradle            repositori + modul :app
├─ build.gradle               plugin AGP 8.2.2 (apply false)
├─ gradle.properties          JVM args, AndroidX nonaktif (sengaja)
├─ gradle/wrapper/gradle-wrapper.properties   Gradle 8.2
└─ app/
   ├─ build.gradle            minSdk 19, target 33, compile 34, Java 8, TANPA dependency
   ├─ proguard-rules.pro      kosong
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/id/cyclocomp/
      │   ├─ MainActivity.java
      │   ├─ RideService.java
      │   ├─ RideStats.java
      │   ├─ GpxWriter.java
      │   ├─ RideDb.java
      │   ├─ HistoryActivity.java
      │   └─ Fmt.java
      └─ res/
          ├─ layout/activity_main.xml, activity_history.xml
          └─ values/strings.xml, colors.xml, styles.xml
```

### Peran tiap file

**`MainActivity`** — dashboard. Hanya *menampilkan* dan *mengirim perintah*.
- `onStart` bind ke `RideService` (`BIND_AUTO_CREATE`), `onStop` unbind. Tidak memegang state rekaman.
- Perintah dikirim lewat `startService` / `startForegroundService` dengan Intent action
  (`ACTION_START/PAUSE/RESUME/STOP`). Satu jalur saja, bukan panggil method service langsung.
- Menangani izin runtime (API 23+), cek GPS aktif (kalau mati → buka pengaturan lokasi).
- Tombol BERHENTI harus ditekan lama (cegah kepencet di jalan jelek); klik biasa hanya menampilkan petunjuk.
- `refresh()` dipanggil dari callback `RideService.Listener.onRideUpdate()` (tiap ±1 dtk dan tiap fix GPS).
- Layar dijaga menyala (`FLAG_KEEP_SCREEN_ON`) selama state ≠ IDLE.

**`RideService`** — inti aplikasi. Foreground service, semua callback di **main thread** (tidak perlu lock).
- State machine: `IDLE → RECORDING ⇄ PAUSED → IDLE`.
- `startRide()` memanggil `startForeground` PERTAMA (wajib ≤5 dtk setelah `startForegroundService` di API 26+),
  lalu membuka file GPX, membuat `RideStats` baru, `requestLocationUpdates(GPS_PROVIDER, 1000 ms, 0 m)`.
- `pauseRide()` melepas update GPS dan me-reset titik terakhir; `resumeRide()` memasang lagi.
- `stopRide()` menutup GPX, menyimpan ke SQLite bila jarak ≥ 20 m (kalau lebih pendek, file GPX dihapus),
  melepas wakelock, `stopForeground(true)`, `stopSelf()`.
- Wakelock `PARTIAL_WAKE_LOCK` selama merekam.
- Ticker 1 detik: memicu update UI; tiap 5 detik memperbarui notifikasi.
- `getElapsedMs()` = waktu total (termasuk berhenti di lampu merah) dikurangi waktu jeda manual.
  Pakai `SystemClock.elapsedRealtime()` (tahan perubahan jam sistem).
- Setelah Stop, `stats` terakhir tetap ada selama service hidup (agar UI bisa menampilkan ringkasan);
  begitu Activity unbind dan service dihancurkan, tampilan kembali nol.

**`RideStats`** — logika hitungan murni (hanya bergantung pada `android.location.Location`), mudah di-unit-test.
Parameter (konstanta di atas kelas):

| Konstanta | Nilai | Arti |
|---|---|---|
| `MAX_ACCURACY_M` | 30 m | fix dengan akurasi lebih jelek dibuang |
| `MOVING_SPEED_MS` | 1,0 m/s (3,6 km/j) | di bawah ini dianggap berhenti: jarak & waktu gerak tidak bertambah |
| `MIN_STEP_M` | 2 m | langkah lebih kecil dianggap jitter GPS |
| `MAX_GAP_MS` | 10 dtk | selisih waktu antar-fix lebih besar = sinyal sempat hilang; mulai segmen baru, jarak tidak dilompatkan |
| `ELEV_STEP_M` | 3 m | histeresis tanjakan: naik dihitung hanya bila ≥3 m dari titik acuan (meredam noise) |

Kecepatan: pakai `Location.getSpeed()` bila ada, kalau tidak jarak/Δt.
`onLocation()` mengembalikan `true` bila titik layak ditulis ke GPX (sedang bergerak atau titik pertama).
Catatan: titik saat berhenti tidak ditulis ke GPX (sengaja, supaya file kecil).

**`GpxWriter`** — menulis GPX 1.1 bertahap (header saat dibuka, `<trkpt>` per titik, flush tiap 10 titik,
footer saat `close()`). Jika HP mati mendadak, file tidak punya footer; perbaiki dengan menambah
`</trkseg></trk></gpx>` di akhir. Waktu dalam UTC ISO-8601.

**`RideDb`** — `SQLiteOpenHelper`, DB `cyclocomp.db` versi 1, tabel `rides`
(`id, start_ms, duration_ms, moving_ms, distance_m, max_speed, elev_gain, gpx_path`).
Hanya ringkasan; titik detail ada di GPX. `onUpgrade` masih TODO (belum perlu di v1).

**`HistoryActivity`** — `ListView` dengan `ArrayAdapter<String>`; klik = dialog detail (+ path GPX);
tombol Hapus menghapus baris DB dan file GPX (tanpa konfirmasi kedua — pertimbangkan menambah).

**`Fmt`** — format angka. Memakai `Locale.getDefault()` untuk desimal (di locale Indonesia jadi koma).
Durasi memakai `Locale.US`.

**Resource** — tema `Theme.Holo.NoActionBar` (framework, bukan AppCompat) berlatar hitam;
style `Label` / `Value`; semua teks di `strings.xml` (Bahasa Indonesia).

## 4. Alur data

```
GPS chip ──► LocationManager ──► RideService.onLocationChanged
                                    │
                                    ├─► RideStats.onLocation()  (filter + hitung)
                                    │        └─ true? ─► GpxWriter.add()  ─► file .gpx
                                    └─► listener.onRideUpdate() ─► MainActivity.refresh() ─► TextView

Tombol UI ──Intent(action)──► RideService.onStartCommand ──► start/pause/resume/stop
Stop ──► RideDb.insert(ringkasan) ; GPX ditutup
HistoryActivity ──► RideDb.list()
```

State machine:

```
        START            PAUSE
 IDLE ─────────► RECORDING ─────► PAUSED
  ▲                 ▲  │  RESUME   │
  │      STOP       │  └───────────┘
  └─────────────────┴───────────────┘  (STOP dari RECORDING atau PAUSED)
```

## 5. Keputusan desain dan alasannya

1. **Tanpa dependency (no AndroidX, no Play Services).** KitKat lawas sering tanpa/dengan Play Services
   usang; AppCompat menambah ukuran dan risiko. Konsekuensi: UI memakai widget framework Holo.
2. **`LocationManager` + GPS_PROVIDER**, bukan Fused Location. Jalan di semua perangkat, tapi konsumsi
   baterai dan waktu cold-fix lebih buruk daripada Fused.
3. **Foreground service + Binder, perintah via Intent.** Rekaman tetap hidup saat layar mati / app di
   background; Activity bisa mati tanpa menghentikan rekaman.
4. **`START_NOT_STICKY`.** Jika service dibunuh sistem, rekaman tidak di-restart otomatis (state ada di
   memori). Pemulihan sesi (lihat roadmap) perlu persist state.
5. **GPX ditulis langsung per titik** (bukan di akhir) → tahan crash/baterai habis.
6. **targetSdk 33**, bukan 34: menghindari kewajiban tambahan Android 14 selama kerangka belum teruji.
   Izin `FOREGROUND_SERVICE_LOCATION` sudah dideklarasikan untuk saat targetSdk dinaikkan.
7. **Satu thread (main).** GPS callback, ticker, dan UI semua di main thread. Penulisan GPX ke disk
   dibuffer (flush tiap 10 titik); cukup untuk 1 Hz. Jika nanti ada sensor BLE 1–4 Hz, pertimbangkan
   `HandlerThread` untuk I/O.
8. **Moving time terpisah dari elapsed time.** Rata-rata = jarak / waktu gerak (standar bike computer).

## 6. Izin dan perbedaan per versi Android

| Android | Hal yang berlaku | Sudah ditangani? |
|---|---|---|
| 4.4 (API 19) | **Tidak lagi didukung** sejak 23 Okt 2026 (minSdk dinaikkan ke 23) | — |
| 6.0 (23) | Izin lokasi runtime; Doze menghentikan jaringan/alarm saat diam | Izin: ya. Doze: **whitelist baterai ditawarkan** sebelum mulai rekam (`askIgnoreBatteryOptimization`) |
| 7.0 (24) | `file://` URI antar-app dilarang (FileUriExposedException) | Belum relevan (belum ada share) — wajib FileProvider saat menambah share |
| 12 (31) | `PendingIntent` **wajib** mutable atau immutable | Ya: `FLAG_IMMUTABLE` dipakai tanpa guard (minSdk 23 sudah cukup) |
| 8.0 (26) | Wajib `startForegroundService` + notifikasi + channel | Ya |
| 10 (29) | Perlu `foregroundServiceType="location"` | Ya (manifest + `startForeground` 3 argumen) |
| 12 (31) | `exported` wajib di komponen dengan intent-filter; batasan start FGS dari background | Ya (MainActivity exported=true); start selalu dari Activity foreground |
| 13 (33) | Izin runtime `POST_NOTIFICATIONS` | Diminta bersama izin lokasi |
| 14 (34) | Izin `FOREGROUND_SERVICE_LOCATION` + tipe FGS wajib | Izin sudah ada; belum diuji (targetSdk 33) |

Izin di manifest: `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`,
`POST_NOTIFICATIONS`, `WAKE_LOCK`. Tidak ada izin internet (app 100% offline saat ini).
Izin latar belakang (`ACCESS_BACKGROUND_LOCATION`) **tidak** diminta: rekaman selalu dimulai dari
Activity yang tampil, dan FGS bertipe location boleh jalan di background.

## 7. Format data

**GPX** — `Android/data/id.cyclocomp/files/rides/ride_YYYYMMDD_HHmmss.gpx` (waktu lokal pada nama file;
timestamp di dalam GPX UTC). Satu `<trk>` satu `<trkseg>`; tiap `<trkpt>` punya `lat`, `lon`, `<ele>`
(bila ada), `<time>`. Altitude dari `Location.getAltitude()` (umumnya terhadap elipsoid WGS84, bukan MSL,
sehingga bisa beda puluhan meter dari ketinggian sebenarnya).

**SQLite** — lihat `RideDb`. Satuan: meter, milidetik, m/s. Kolom waktu `start_ms` = epoch ms.

## 8. Build dan run

**Penting: host pengembangan adalah ARM/aarch64, dan build Android lokal tidak mungkin di sana**
(`aapt2` build-tools resmi hanya x86_64). Karena itu APK dibangun lewat GitHub Actions:

```
push ke main  →  .github/workflows/build-apk.yml  →  artefak cyclocomp-debug-apk
gh run watch <id> --exit-status --interval 20      # tunggu run selesai
gh run download <id> -n cyclocomp-debug-apk -D /tmp/apk
gh release create vX.Y.Z app-debug.apk             # tautan unduh stabil
```

Rantai versi yang sudah terbukti cocok untuk proyek ini: **AGP 8.2.2 → Gradle 8.2 → JDK 17**,
`platforms;android-34` + `build-tools;34.0.0`. Jangan pakai
`android-actions/setup-android@v3` (ia memanggil `sdkmanager tools`, paket itu sudah dihapus).

Di PC dengan Android Studio (x86_64), build lokal tetap seperti biasa: buka folder, Sync,
colok HP (USB debugging) → Run. CLI: `gradle wrapper` sekali, lalu `./gradlew assembleDebug`.

Rilis: `minifyEnabled true` sudah aktif; belum ada signing config dan belum diuji dengan R8 (kelas
proyek ini tidak memakai refleksi, seharusnya aman).

## 9. Hal yang perlu diverifikasi / risiko yang diketahui

1. **Belum pernah dikompilasi.** Periksa terutama: `RideService` (override `LocationListener` di compileSdk 34),
   `MainActivity` (`startForegroundService`, `checkSelfPermission` di balik cek `SDK_INT`).
2. Variabel `elapsed` di `MainActivity.refresh()` dihitung tapi tidak dipakai (tinggal tampilkan atau hapus).
3. Setelah Stop dan Activity ditutup, service di-`stopSelf` lalu hancur → ringkasan ride terakhir hilang
   dari dashboard (tetap ada di Riwayat).
4. Tidak ada pemulihan sesi: bila proses dibunuh di tengah rekaman, GPX parsial tersisa (tanpa footer) dan
   tidak masuk Riwayat.
5. Penghemat baterai agresif pabrikan (Xiaomi, Oppo, Vivo, Huawei, Samsung lama) bisa membunuh service
   walau foreground. Perlu panduan whitelist untuk pengguna.
6. GPS `GPS_PROVIDER` murni: cold start di HP tua bisa 1–5 menit; UI menampilkan "Mencari sinyal GPS…".
7. Perhitungan tanjakan dari altitude GPS kasar; tanpa barometer akurasinya terbatas.
8. Hapus riwayat tanpa konfirmasi kedua.
9. Teks hanya Bahasa Indonesia (belum ada `values-en`).
10. Layout belum dioptimalkan untuk layar sangat kecil (<4") atau landscape (activity dikunci portrait).

## 10. Checklist pengujian di perangkat

Jalankan di minimal: 1 HP KitKat (API 19) dan 1 HP Android 7+ (idealnya juga Android 10+ dan 13+).
- [ ] Instal, buka, izin lokasi diminta (API 23+); tolak izin → toast, tidak crash.
- [ ] GPS mati → diarahkan ke pengaturan lokasi.
- [ ] Mulai di luar ruangan: status "Mencari…" lalu "GPS OK (±N m)".
- [ ] Bergerak: kecepatan, jarak, waktu gerak bertambah; berhenti: kecepatan 0, waktu gerak tidak bertambah.
- [ ] Jeda/Lanjut: jarak tidak melompat setelah lanjut; waktu total tidak menghitung masa jeda.
- [ ] Layar mati / pindah app 10+ menit: rekaman tetap jalan, notifikasi tampil (Android 8+).
- [ ] Putar layar/matikan-nyalakan: UI tersambung kembali ke service yang masih merekam.
- [ ] Tahan BERHENTI: tersimpan di Riwayat; < 20 m: tidak tersimpan dan file GPX terhapus.
- [ ] Buka file GPX di aplikasi lain (mis. GPX viewer) → rute benar, waktu UTC wajar.
- [ ] Tutup paksa app saat merekam → perilaku sesuai bagian 9.
- [ ] Uji 1–2 jam: konsumsi baterai dan stabilitas.

## 11. Konvensi kode

- Java 8 source, tanpa lambda/stream (anonymous class dipakai agar sederhana dan aman di KitKat);
  pakai `SDK_INT` untuk semua API > 19.
- Satuan internal selalu SI (m, m/s, ms); konversi ke km/j hanya di `Fmt`.
- Tidak ada akses jaringan. Jika ditambah (peta online, upload), tambahkan izin `INTERNET` dan
  perhatikan TLS di KitKat (TLS 1.2 perlu diaktifkan manual / pakai library; banyak server menolak TLS lama).
- String UI di `strings.xml` (bawaan id) + `values-en/strings.xml`; satuan & tanggal ikut locale. Notifikasi memakai
  `R.string.notif_line` (satu pola, bukan tempelan literal).

## 12. Roadmap (urutan disarankan) dan petunjuk implementasi

**Status per v0.9.3 (24 Okt 2026): 1 ✅ · 2 ✅ · 3 ✅ · 4 ✅ (detak jantung) · 5 ✅ · 6 ✅ · 7 ✅ (peta ubin OSM) · 8 ✅ · 9 ✅ · 10 ✅ (lokalisasi) — sisa: 4b (kadensi/kecepatan CSC), Strava API.**

1. ✅ **Perbaikan dasar**: kompilasi, uji perangkat, hapus `elapsed` tak terpakai, konfirmasi hapus riwayat,
   ikon launcher sendiri.
2. ✅ **Pemulihan sesi** (`SessionRecovery`, penanda tiap 5 dtk): simpan state ringan (path GPX, start time, jarak) ke SharedPreferences tiap ±10 dtk;
   saat app dibuka dan ada sesi tanpa footer → tawarkan "pulihkan" (tambah footer GPX, masukkan ke DB).
3. ✅ **Pengaturan** (`SettingsActivity` + `SharedPreferences`, dikelompokkan per kartu sejak v0.6.0): satuan km/mil, auto-pause (ambang & jeda),
   ukuran roda (untuk sensor), interval GPS, field dashboard.
4. ✅ **Sensor BLE — detak jantung** (v0.7.0, `BleHr.java`): Heart Rate Service `0x180D` / `0x2A37`,
   notifikasi lewat CCCD `0x2902`, sambung ulang otomatis, `hr` + `hrMax` + `avgHr()` di `RideStats`,
   `gpxtpx:hr` per titik GPX, pil HR di halaman Sekilas, pemilih sensor di Pengaturan → SENSOR.
   **Belum**: kadensi/kecepatan CSC `0x1816` (butuh sensor khusus, bukan jam).
   Sisa catatan teknis untuk 4b:
   - Izin: API 18–30 `BLUETOOTH`, `BLUETOOTH_ADMIN` + izin lokasi untuk scan (23+); API 31+
     `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (`neverForLocation` bila tidak butuh lokasi dari scan).
   - Cycling Speed & Cadence: service `0x1816`, karakteristik Measurement `0x2A5B`. Byte flags: bit0 = data
     roda ada (uint32 revolusi kumulatif + uint16 waktu event, satuan 1/1024 detik), bit1 = data crank
     (uint16 revolusi + uint16 waktu). Kecepatan = Δrevolusi × keliling / (Δwaktu/1024), kadensi =
     Δrevolusi_crank / (Δwaktu/1024) × 60. Tangani wrap-around uint16/uint32.
   - Heart Rate: service `0x180D`, karakteristik `0x2A37`; flags bit0 = 0 → HR uint8, 1 → uint16.
   - Letakkan di kelas baru `BleSensors` dalam `RideService`; tambahkan field di `RideStats`
     (cadence, hr) dan `RideDb` (migrasi versi 2), plus ekstensi GPX (`gpxtpx:hr`, `gpxtpx:cad`).
   - **Pelajaran v0.9.2 (crash NPE, dari laporan pengguna)**: `removeAllViews()` pada kontainer yang
     **memuat view statis dari XML** menghancurkan view itu; `findViewById` berikutnya mengembalikan null.
     Di sini `lapWrap` memuat `tvLapCount`, lalu `bind()` memanggil `wrap.removeAllViews()` dan langsung
     memakai `tvLapCount` → NPE. **Aturan: kontainer yang dibersihkan harus kontainer kosong khusus**
     (`lapRows`), bukan kartu yang memuat header. Gejala hanya muncul di cabang tertentu (ada lap),
     jadi uji di satu perangkat bisa lolos sementara perangkat lain crash — selalu uji kedua cabang.
   - **Pelajaran v0.7.0**: jam/band hanya menyiarkan HR kalau ada mode "siarkan detak jantung";
     perangkat yang tidak punya service `0x180D` harus langsung dilepas (`wanted = false`),
     kalau tidak aplikasi berputar mencoba menyambung terus. Detak 0 = sensor belum kontak kulit,
     jangan ditampilkan sebagai angka nol.
5. ✅ **Ekspor/bagikan GPX** (`RideProvider`, authority `id.cyclocomp.files`): Android 7+ wajib `content://` via FileProvider (butuh `androidx.core` atau
   implementasi `ContentProvider` sendiri bila tetap tanpa AndroidX). Di KitKat `file://` masih bisa.
6. ✅ **Lap/interval** dan aksi Jeda/Berhenti di notifikasi.
7. ✅ **Peta** (v0.8.0, `TileMapView` 316 baris — **tanpa osmdroid**, hanya ubin `tile.openstreetmap.org` + Web Mercator + LruCache/cache disk + pinch/geser).
   Konsekuensi desain: izin `INTERNET` + `ACCESS_NETWORK_STATE` **wajib** ditambah (dulu aplikasi sepenuhnya offline), jadi peta
   sekarang bergantung jaringan untuk ubin baru. Kalau gagal dimuat, `RouteView.drawBackground()` yang tampak (latar kisi),
   dan ubin yang sudah ter-cache tetap tampil offline. Bisa dimatikan di Pengaturan → PETA.
   Catatan: atribusi "© OpenStreetMap" digambar di sudut kanan bawah (syarat ketentuan OSM).
8. ✅ **Barometer** (`Baro.java`; `TYPE_PRESSURE` + `SensorManager.getAltitude`, dikalibrasi ke GPS, koreksi bila selisih > 25 m).
9. ✅ **Unit test `RideStats`** — dipilih **refactor**, bukan Robolectric: `onFix(Fix)` bebas kelas Android,
   jarak pakai haversine sendiri (`distanceM`). 14 tes di `app/src/test/.../RideStatsTest.java`, ikut dijalankan CI.
10. ✅ **Lokalisasi** (v0.9.0): semua teks layar di `res/values/strings.xml` (bawaan Indonesia) + `res/values-en/strings.xml`;
   tanggal memakai `DateFormat.getDateTimeInstance(..., Locale.getDefault())`; satuan ikut bahasa (`km/jam` vs `km/h`,
   `detik` vs `sec`); dialog, toast, dan notifikasi ikut. Catatan: `ContentProvider` **bukan** `Context` → `getString()`
   harus lewat `getContext()`; metadata GPX (`gpx_creator`) sengaja tidak diterjemahkan.

## 13. Keputusan minSdk: 23 (Android 6.0+) — sudah diambil

Diputuskan **23 Okt 2026**: `minSdk 23`, bukan 24 dan bukan 19. Alasan: pengguna ingin
menguji di rentang Android 6 → 13+ (Tes di Tecno Spark 30C, Android 13), dan KitKat
(Xperia E1) tidak lagi jadi target proyek ini.

Yang berubah di kode:

- `app/build.gradle`: `minSdk 19` → `minSdk 23`.
- `MainActivity.hasLocationPermission()` / `requestPermissionsCompat()`: guard
  `Build.VERSION.SDK_INT < 23` dibuang — izin runtime kini selalu berlaku.
- `RideService.buildNotification()`: `FLAG_IMMUTABLE` dipakai **tanpa** guard versi.
  Ini bukan sekadar bersih-bersih: di Android 12+ (API 31) `PendingIntent` tanpa flag
  mutable/immutable melempar `IllegalArgumentException`, jadi guard lama
  (`if (SDK_INT >= 23)`) sudah tidak menutup kasus itu.
- Ditambah whitelist baterai: `MainActivity.batteryOptimized()` +
  `askIgnoreBatteryOptimization()`, dipanggil sebelum `ACTION_START`. Di Android 6+
  (khususnya 10+) Doze dan pembatasan pabrikan adalah penyebab nomor satu rekaman
  berhenti sendiri; dialog ini menawarkan "Tidak dibatasi" tapi tetap memperbolehkan
  rekam kalau dilewati. Izin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` hanya untuk
  dialog itu, tidak dipakai di background.
- Guard yang **tetap diperlukan**: `startForegroundService` (>= 26), channel notifikasi
  (>= 26), `FOREGROUND_SERVICE_TYPE_LOCATION` (>= 29), `POST_NOTIFICATIONS` (>= 33).

Kalau suatu saat perlu menaikkan lagi ke 24: tidak ada yang perlu diubah selain angka
`minSdk` — semua guard 23 sudah dibuang, sisanya guard >= 26 yang tetap dibutuhkan.

## 14. Troubleshooting

| Gejala | Kemungkinan penyebab |
|---|---|
| Tidak dapat fix GPS | Di dalam ruangan; mode lokasi bukan "akurasi tinggi"; cold start lama |
| Rekaman berhenti sendiri saat layar mati | Penghemat baterai pabrikan; whitelist app (lihat dontkillmyapp.com) |
| Crash `ForegroundServiceDidNotStartInTimeException` (8+) | `startForeground` terlambat; jangan tambah kerja berat sebelum `startForegroundCompat` di `startRide()` |
| Crash `SecurityException` saat start | Izin lokasi belum diberikan; dicegat di `startRide` dan ditampilkan toast |
| Kecepatan selalu 0 | Ambang `MOVING_SPEED_MS` / akurasi > 30 m menyaring semua titik; cek log |
| Jarak membengkak saat diam | Turunkan `MAX_ACCURACY_M`, naikkan `MIN_STEP_M` |
| Build gagal "Unsupported class file / JDK" | Pakai JDK 17 untuk AGP 8.2 |
| Gagal sync, `namespace` | AGP < 7.3: hapus `namespace` dan isi `package` di manifest |

## 15. Ringkas untuk sesi berikutnya

Mulai dari: **build → perbaiki error → uji perangkat (bagian 10) → roadmap nomor 1–3**.
Jangan ubah arsitektur (service sebagai pemilik state, Activity hanya view) tanpa alasan kuat;
semua fitur berikutnya (sensor, lap, pemulihan) menempel di `RideService` dan `RideStats`.


## 16b. Pelajaran dari v0.9.2 → v0.9.3

- **GPX dengan tanggal ngawur**: Samsung SM-C710F (Android 7.1.1) melaporkan `Location.getTime()`
  dengan jam/menit/detik benar tapi **tanggal 19 tahun terlampaui** (2007 alih-alih 2026). Jam sistem
  HP sendiri benar. Berkas tetap terbuka, tapi kalau dibagikan ke Strava/Garmin gowesnya mendarat
  di tahun yang salah. **Aturan: jangan percaya `Location.getTime()` buta** — bandingkan dengan
  `System.currentTimeMillis()`, dan kalau menjauh > 1 hari hitung **satu** selisih koreksi lalu
  pakai untuk titik berikutnya (bukan memberi semua titik waktu yang sama, supaya jarak antar titik
  tetap akurat). Lihat `TimeFix.java` + `TimeFixTest.java`.
- **Cara menemukannya**: minta pengguna kirim berkas GPX-nya, lalu hitung sendiri (jumlah titik,
  selang antar titik, rentang lat/lon/ele, konsistensi tanggal). Kerusakan "tidak terlihat" di UI
  karena layar Ringkasan memakai `startMs` dari jam sistem, bukan waktu GPS.
- **Pelajaran v0.9.2 (NPE)**: `removeAllViews()` pada kontainer yang **memuat view statis dari XML**
  menghancurkan view itu; `findViewById` berikutnya mengembalikan null. Kontainer yang dibersihkan
  harus kontainer kosong khusus (`lapRows`), bukan kartu yang memakai header (`tvLapCount`).
  Gejala hanya muncul di cabang tertentu (ada lap), jadi uji di satu perangkat bisa lolos
  sementara perangkat lain crash — **selalu uji kedua cabang**.

## 16. Pelajaran dari v0.6.0 → v0.6.1

**Tes menemukan bug yang tidak terlihat di layar.** `updateGrade()` dipanggil *sebelum*
`distanceM += d`, jadi jendela 25 m selalu tertinggal satu titik dan gradien praktis
tidak pernah muncul (selalu 0%). Di HP ini tampak seperti "belum dapat tanjakan",
bukan seperti bug. Aturan: hitungan yang bergantung pada akumulasi harus dipanggil
**setelah** akumulasinya, dan urutan itu wajib dikunci tes.

**Sesudah celah sinyal, acuan gradien harus disegarkan.** Tanpa `gradeRefDist/gradeRefAlt`
di-reset, gradien pertama setelah sinyal kembali dihitung dari ketinggian lama dan melompat.

**Tes bisa salah, bukan kodenya.** Kasus `celahBesar`: titik uji hanya bergerak 1 m,
di bawah `MIN_STEP_M` (2 m), jadi `onFix` yang mengembalikan `false` itu benar.
Kalau tes gagal, periksa dulu apakah asumsinya masuk akal sebelum mengubah kode.

**Refactor lebih murah daripada Robolectric.** `RideStats.onFix(Fix)` + haversine sendiri
membuat 14 tes jalan di JVM biasa dalam hitungan detik, tanpa dependency uji tambahan
selain JUnit.

**CI wajib menjalankan `testDebugUnitTest`.** Sebelum v0.6.0 workflow hanya `assembleDebug`,
jadi tes yang tidak dikompilasi tidak pernah ketahuan rusak. Sekarang ada langkah
"Run unit tests" sebelum build APK.
