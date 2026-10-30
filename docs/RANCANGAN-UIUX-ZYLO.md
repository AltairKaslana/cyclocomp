# Rancangan UI/UX Zylo — modern, tetap ringan

Dokumen rancangan dan desain untuk tahap setelah perbaikan R1/R2. Dapat diserahkan ke developer atau AI agent.
Mockup interaktif: https://claude.ai/artifact/1BxzfyCU5VSEJNQu225h2P (baris atas = layar yang sudah diperbaiki,
baris bawah = layar rancangan baru v1.1 dan v1.2).

---

## 1. Arah desain

**Satu kalimat:** instrumen sepeda yang terbaca sekilas, tenang di mata, dan tidak membebani HP tua.

**Modern** di sini berarti:
- Hierarki tegas: satu angka dominan, sisanya mengecil mengikuti pentingnya.
- Satu warna aksen, warna lain hanya untuk status.
- Tipografi condensed besar untuk angka, label kecil huruf kapital berjarak lebar.
- Ruang kosong yang disengaja, sudut bulat konsisten, tanpa dekorasi.
- Umpan balik yang berguna (progres tahan, getar, status GPS), bukan animasi hias.

**Ringan** di sini berarti:
- Tanpa library eksternal (seperti sekarang). View klasik, `ListView`, Canvas, drawable vektor.
- Tanpa gambar raster baru, tanpa bayangan/blur, tanpa animasi berkelanjutan.
- Peta, thumbnail, dan data berat hanya dibuat **saat diminta**, bukan di muka.
- Layar utama tidak boleh membuat baterai terkuras lebih dari yang sudah dilakukan GPS dan layar menyala.

## 2. Anggaran ringan (target, uji tiap rilis)

| Aspek | Target |
|---|---|
| Ukuran APK | naik ≤ 100 KB per rilis fitur; tanpa dependency baru |
| Mulai dingin | < 1,5 dtk di HP RAM 2 GB |
| Kedalaman view | ≤ 7 tingkat per layar |
| Refresh dashboard | 1 Hz untuk angka; ≤ 30 fps hanya saat bar progres tahan-berhenti aktif |
| Animasi | hanya `alpha`/`translation`, ≤ 200 ms; tidak ada animasi saat merekam selain progres tahan |
| Memori | tanpa `Bitmap` besar di layar utama; kartu bagikan dirender sekali lalu dilepas |
| Jaringan | tidak ada pada layar utama; peta dan Strava hanya setelah aksi pengguna |
| Font | Barlow dan Barlow Condensed, masing-masing ≤ 2 bobot yang dipakai |
| Aset | ikon vektor/Canvas; tidak ada PNG baru kecuali ikon launcher |

## 3. Bahasa desain

**Warna** (sudah diterapkan di v0.9.38): latar `#0E1013`, kartu `#1E2227`, garis `#3A4250`, tepi kontrol `#5B6573`,
label `#9AA3AE`, aksen `#22D3EE` (teks di atas aksen `#0E1013`), merekam `#34D399`, peringatan `#F5B83D`, bahaya `#F87171`.
Aturan: aksen = aksi utama dan tab/pilihan aktif; hijau/kuning/merah = **status**, selalu disertai teks (bukan hanya warna).

**Tipografi:** angka Barlow Condensed 700 (kecepatan 176, nilai besar 32-36, odometer 72); label Barlow 500 12 dengan jarak huruf +14%;
satuan 14; isi 15-17. Semua dalam `sp`.

**Bentuk:** kartu radius 16, tombol pill (radius penuh), chip 44dp tinggi radius 22, pemisah 1dp. Tanpa bayangan (datar).

**Spasi:** kelipatan 4; padding layar 16; antar kartu 12; antar tombol 10.

**Target sentuh:** ≥ 48dp; tombol aksi bawah 64dp.

**Ikon:** satu set garis 2dp, gaya sama (riwayat, pengaturan, gembok, tambah, kembali, tutup, sensor, garasi). Digambar dengan vektor/Canvas.

## 4. Arsitektur informasi dan navigasi

```
Main (Sekilas / Detail / Lap, bisa diatur)
 ├─ saat IDLE: ikon Riwayat · Garasi · Pengaturan di bar atas
 └─ saat MEREKAM: semua terkunci (gembok)
Riwayat ── ketuk baris ──> Ringkasan ──> Bagikan
Garasi (sepeda, odometer, perawatan)
Pengaturan ──> Atur layar · Sensor · Peringatan · Lap · Peta · Strava (Lanjutan)
```

Aturan: satu tingkat dalam dari Main (maksimal Main → layar → detail). Tombol kembali di kiri atas di semua layar selain Main.

## 5. Spesifikasi layar

### 5.1 Sekilas, Detail, Lap (sudah ada; acuan artboard 1-3)
Tidak diubah selain hasil R2 dan:
- **Pemilih halaman** tetap geser, titik halaman bisa diketuk (area 48dp).
- **Bidang data** dapat diganti per halaman (lihat 5.3).

### 5.2 Onboarding pertama kali (baru, 3 langkah, layar penuh)
1. **Lokasi:** penjelasan satu kalimat + tombol "Izinkan lokasi".
2. **Baterai:** "Agar rekaman tidak dihentikan sistem" + tombol ke pengaturan pengecualian baterai (sudah ada dialognya; pindahkan ke sini).
3. **Sensor (opsional):** "Punya sensor detak/kadensi? Hubungkan nanti di Pengaturan." tombol "Mulai gowes".
Tidak ada ilustrasi raster; ikon garis + teks. Lewati bisa dilakukan kapan saja.

### 5.3 Atur layar (artboard "v1.1 — Atur layar")
- Tab SEKILAS / DETAIL / LAP; grid yang sedang diatur tampil langsung seperti aslinya.
- Ketuk sel → sel ditandai garis aksen 2dp dan panel bawah menampilkan semua bidang sebagai chip. Pilih chip = ganti.
- Panel bawah: ATUR ULANG (sekunder) dan SIMPAN (utama).
- Bidang tersedia (15): Kecepatan, Rata-rata, Maks, Jarak, Waktu gerak, Waktu total, Kadensi, Detak jantung, Daya,
  Tanjakan, Kemiringan, Ketinggian, Kalori, Jam, Baterai HP. Bidang yang butuh sensor yang tidak terhubung tampil "-".
- Sekilas: batas 3 bidang di kartu bawah. Detail: grid 2xN (N 3-5). Lap: 4 bidang.

### 5.4 Sensor (artboard "v1.1 — Sensor & roda")
- Kartu per sensor: KECEPATAN & KADENSI, DETAK JANTUNG, DAYA. Status: titik + teks ("Terhubung", "Mencari sensor…", "Belum dipilih"), baterai sensor, tombol Lepas/Hubungkan.
- Mencari: daftar perangkat BLE terdekat dengan kekuatan sinyal; satu tombol HUBUNGKAN per baris.
- Kartu RODA: ukuran roda (nilai umum: 700×23C 2096 mm, 700×25C 2105, 700×28C 2136, 700×32C 2155; atau isi mm manual) dan sakelar
  "Kalibrasi dari GPS". Catatan: jika sensor kecepatan putus, kecepatan otomatis memakai GPS.

### 5.5 Riwayat (artboard "v1.1 — Riwayat (ringan, tanpa peta)")
- Kartu **7 hari terakhir**: total jarak besar, jumlah gowes dan waktu, 7 batang (hari kosong = batang abu pendek). Diberi `contentDescription` ringkasan.
- Baris gowes: thumbnail rute 64dp (polyline, **bukan** peta), tanggal, jarak besar, waktu dan rata-rata, chip (LAP / KOMUTER).
- Thumbnail dihitung **sekali saat menyimpan** (polyline disederhanakan ≤ 48 titik, disimpan di database) lalu digambar dengan Canvas. Tidak ada tile peta di daftar.
- Ketuk baris → Ringkasan. Tahan baris → menu kecil (Hapus).
- Keadaan kosong: ikon garis + "Belum ada gowes. Mulai satu hari ini."

### 5.6 Ringkasan v2
- Hero: jarak sangat besar + tanggal; di bawahnya polyline rute berukuran lebar layar (Canvas, aksen).
- **Peta OSM hanya muncul jika pengguna menekan "Tampilkan peta"** (hemat data dan memori); pilihan diingat.
- Statistik 2 tingkat: jarak/waktu gerak besar, rata-rata/maks/tanjakan sedang, kalori dan daya diberi label "perkiraan".
- Aksi: BAGIKAN GAMBAR (utama), BAGIKAN GPX, Hapus (teks bahaya). Penilaian usaha dan Komuter dipertahankan.
- Pengingat perawatan (bila ada yang SEGERA/LEWAT) muncul sebagai satu baris di atas aksi.

### 5.7 Garasi (artboard "v1.2 — Garasi & perawatan")
- Pemilih sepeda (chip), kartu ODOMETER (angka 72sp), ringkasan waktu bergerak dan jumlah gowes.
- Daftar perawatan: tiap komponen punya batas km, bar progres, dan status **teks + warna**: BAIK (hijau), SEGERA (kuning, ≥ 80%),
  LEWAT n KM (merah, > 100%). Tombol "+ TAMBAH KOMPONEN".
- Setiap gowes dikaitkan ke satu sepeda (default = sepeda terakhir). Odometer = offset awal + jumlah jarak gowes sepeda itu.

### 5.8 Pengaturan
Bagian: PEREKAMAN, LAP, PERINGATAN, LAYAR (Atur layar, Tema), SENSOR, PETA, STRAVA (Lanjutan), LAINNYA.
Baris = judul + ringkasan, **nilai di kanan** sebelum panah. Switch bertema (thumb abu saat mati, aksen saat hidup).

### 5.9 Bagikan
Tampilkan 4 kartu utama (Kartu 4:5, Story, Struk, Stempel); sisanya di "Lainnya". Ingat pilihan terakhir. Pratinjau transparan di atas kotak-kotak.
Semua kartu dites dengan data ekstrem (199,9 km/jam; 99:59:59; 9.999 m tanjakan).

### 5.10 Keadaan khusus
- GPS hilang saat merekam: titik kuning + "MENCARI SINYAL GPS", angka kecepatan menjadi "-" setelah 5 dtk.
- Baterai HP < 15%: ikon baterai kecil di bar atas berubah kuning (bukan dialog). Opsi "Mode hemat" (kecerahan turun, refresh 0,5 Hz).
- Sensor putus: chip kecil di bar atas "SENSOR PUTUS" 5 dtk, lalu hilang; bidang terkait tampil "-".

## 6. Komponen (satu pustaka kecil)

| Komponen | Spesifikasi |
|---|---|
| Tombol utama | tinggi 64 (aksi bawah) atau 56, pill, isi aksen, teks `#0E1013` 20sp/700 condensed |
| Tombol sekunder | transparan, garis 1,5dp `#5B6573`, teks putih |
| Tombol bahaya | garis dan teks `#F87171`; BERHENTI punya bar progres 4dp dan petunjuk TAHAN |
| Chip | 44dp, radius 22; aktif = isi aksen + teks gelap; nonaktif = garis `#5B6573` |
| Kartu | `#1E2227`, radius 16, padding 16 |
| Sel metrik | label 12sp, nilai 32-36sp condensed, satuan 14sp di samping |
| Baris pengaturan | judul 17sp, ringkasan 14sp `label`, nilai kanan, panah; tinggi min 64dp |
| Status | titik 8dp + teks (hijau/kuning/merah) |
| Bar progres | tinggi 6dp, radius 3dp, trek `#0E1013`, isi sesuai status |
| Panel bawah | `#1E2227`, sudut atas 20dp, pegangan 36x4dp |

## 7. Gerak dan umpan balik

- Perpindahan layar: `alpha` 0→1, 120 ms. Tanpa transisi geser berat.
- Tahan BERHENTI: bar progres tumbuh sesuai durasi tahan; getar `LONG_PRESS` saat selesai; lepas = reset.
- Penyimpanan/berhasil: perubahan teks tombol 1,5 dtk, bukan toast panjang.
- Hormati pengaturan sistem "Hapus animasi" (durasi 0).

## 8. Aksesibilitas

- Semua ikon dan titik status punya `contentDescription`; status GPS dan sensor diumumkan sebagai teks.
- Kontras: teks utama ≥ 7:1, label ≥ 6:1 (sudah dihitung), garis kontrol ≥ 3:1.
- Skala font 1,3x tanpa teks terpotong (uji 360x640).
- Warna bukan satu-satunya penanda (selalu ada teks status).
- Tema siang berkontras tinggi (latar putih, teks hitam, aksen tua) sebagai opsi di Pengaturan > Layar.

## 9. Desain teknis ringkas per fitur

**Bidang data (registri):** daftar tetap `FieldId` (enum) dengan `{label, unit, formatter, sourceKey}`. Konfigurasi tiap halaman disimpan sebagai string
JSON kecil di `SharedPreferences` (urutan `FieldId`). Layar utama hanya membaca satu kali saat dibuat, lalu memperbarui `TextView` yang sama
(tanpa membangun ulang view). Bidang tanpa data menampilkan "-".

**Sensor kecepatan/kadensi BLE (CSC):** layanan `0x1816`, karakteristik `0x2A5B`. Flag bit0 = data roda (revolusi uint32, waktu event uint16 dalam 1/1024 dtk),
bit1 = data crank. Kecepatan = Δrevolusi x keliling / (Δwaktu/1024); kadensi = Δrevolusi_crank / (Δwaktu/1024) x 60; tangani wrap-around.
Kalibrasi roda: bandingkan jarak sensor dan GPS pada segmen ≥ 500 m dengan akurasi baik, koreksi keliling sebagai rata-rata bergerak.
Detak jantung sudah ada (`0x180D`/`0x2A37`); daya (`0x1818`/`0x2A63`) opsional.

**Riwayat:** kolom baru `preview` (teks angka ternormalisasi) dan `bike_id` di tabel `rides`; migrasi versi database + 1.
Grafik 7 hari: `SUM(distance_m)` dikelompokkan per hari lokal; digambar dengan `View` sederhana.

**Garasi:** tabel `bikes(id, name, type, odo_offset_m, created_ms)` dan `components(id, bike_id, name, limit_m, start_odo_m)`.
Terpakai = odometer sepeda - `start_odo_m`; status BAIK < 80%, SEGERA 80-100%, LEWAT > 100%.
Pengingat dihitung saat Ringkasan dibuka (tanpa layanan latar belakang).

**Peta sesuai permintaan:** `TileMapView` hanya dibuat setelah ketukan "Tampilkan peta"; cache tile dibatasi (mis. 20 MB) dan dibersihkan dari Pengaturan.

## 10. Rencana rilis dan tugas untuk agent

| Rilis | Isi | Tugas |
|---|---|---|
| **v1.0** | Stabil | Selesaikan R2 (Switch thumb, ikon hati merah, bukti B3/B1/B7, landscape lengkap, label perkiraan) |
| **v1.1** | Sensor dan layar kustom | N-01 onboarding; N-02 registri bidang data + layar Atur; N-03 sensor CSC + roda (+ UI Sensor); N-04 Riwayat v2 (7 hari, thumbnail polyline); N-05 Ringkasan v2 (hero + peta sesuai permintaan) |
| **v1.2** | Garasi | N-06 tabel bikes/components + migrasi; N-07 UI Garasi; N-08 pengingat di Ringkasan |
| **v1.3** | Navigasi ringan | N-09 impor GPX, N-10 garis rute di Sekilas (panah arah + jarak ke belokan berikutnya), tanpa tile peta |
| **v2.0** | Opsional | tema siang, ekspor/cadangan data, ekspor FIT, bahasa Inggris, satuan mil |

Aturan kerja agent tetap sama dengan R1/R2: UI dan fitur sesuai tugas, jangan ubah logika perekaman inti tanpa persetujuan;
satu tugas satu commit; screenshot sebelum/sesudah; tes unit untuk formatter dan hitungan baru; uji di 720x1600 dan 360x640 dengan font 1,3x.

**Kriteria terima per rilis:** anggaran ringan (bagian 2) terpenuhi, tidak ada teks terpotong, tidak ada dependency baru, semua layar baru cocok dengan artboard.

## 11. Metrik keberhasilan

- Pengguna bisa memulai gowes ≤ 2 ketukan dari layar utama.
- 0 ketukan tidak sengaja pada Riwayat/Pengaturan saat merekam.
- Mulai dingin dan ukuran APK sesuai anggaran.
- Tidak ada laporan crash baru dari layar baru pada 10 gowes uji.

## 12. Pertanyaan terbuka

1. Sensor mana yang akan diprioritaskan lebih dulu: kecepatan/kadensi (CSC) atau daya?
2. Satu sepeda atau banyak sepeda untuk v1.2?
3. Navigasi (v1.3) perlu belokan-demi-belokan atau cukup garis rute?
4. Apakah aksen cyan `#22D3EE` disetujui secara final?
5. Perlu bahasa Inggris dan satuan mil di v1.1 atau ditunda ke v2.0?
