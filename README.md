# Cyclocomp — kerangka awal

> Dokumentasi lengkap & serah-terima: lihat **HANDOFF.md** (status, arsitektur, keputusan desain, risiko, checklist uji, roadmap).

Bike computer GPS untuk Android lawas. minSdk 19 (KitKat), targetSdk 33.
Tanpa dependency eksternal (tanpa AndroidX, tanpa Google Play Services).

## Build
1. Buka folder ini di Android Studio (Giraffe/Hedgehog atau lebih baru, JDK 17).
2. Sync Gradle, colok HP (USB debugging), Run.
   Atau CLI: `gradle wrapper` sekali, lalu `./gradlew assembleDebug`.
3. APK: `app/build/outputs/apk/debug/app-debug.apk`

Ingin hanya Android 7+? Ubah `minSdk 19` jadi `24` di `app/build.gradle`.

## Arsitektur
- `RideService`  : foreground service, GPS (LocationManager), wakelock, state machine IDLE/RECORDING/PAUSED
- `RideStats`    : jarak, waktu gerak, rata-rata, maks, tanjakan (filter akurasi + jitter + gap)
- `GpxWriter`    : tulis GPX bertahap
- `RideDb`       : SQLite ringkasan rekaman
- `MainActivity` : dashboard (bind ke service, hanya menampilkan)
- `HistoryActivity` : daftar rekaman

GPX tersimpan di `Android/data/id.cyclocomp/files/rides/` (bisa diambil lewat file manager / USB).

## Catatan perangkat lawas
- Nyalakan GPS mode "akurasi tinggi" di pengaturan lokasi.
- Android 6+: izin lokasi runtime (sudah ditangani). Android 8+: notifikasi foreground wajib (sudah).
- Android 6+ (Doze): matikan optimasi baterai untuk app ini supaya rekaman tak dipotong.
- Layar dibuat tetap menyala selama merekam; pakai mode hemat daya OLED (tema hitam sudah).

## Roadmap
1. Sensor BLE (CSC kecepatan/kadensi UUID 0x1816, heart rate 0x180D). Butuh izin BLUETOOTH_* dan API 18+.
2. Pengaturan: satuan (km/mil), ukuran roda, auto-pause, field dashboard yang bisa diatur.
3. Layar lap/interval + notifikasi aksi (jeda/berhenti) dari notifikasi.
4. Ekspor/bagikan GPX (butuh FileProvider; di KitKat bisa file://, di Android 7+ wajib content://).
5. Peta offline (osmdroid) + tampilan rute riwayat.
6. Altimeter barometer (Sensor.TYPE_PRESSURE) bila HP punya, karena altitude GPS HP lawas kasar.
7. Unit test untuk RideStats.
