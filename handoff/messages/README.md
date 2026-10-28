# Channel Pesan Antar-Agent (async via git)

Cara WIN (Windows) dan HERMEST (Armbian) bertukar informasi tanpa server.

## Struktur

```
handoff/messages/
├─ README.md
├─ win/       WIN menulis di sini  → HERMEST baca
└─ hermest/   HERMEST menulis di sini → WIN baca
```

Setiap agent **hanya menulis di foldernya sendiri** dan membaca folder agent lain.

## Alur

```
WIN tulis di win/  → commit + push → HERMEST: git pull → baca win/
HERMEST balas di hermest/ → commit + push → WIN: git pull → baca hermest/
```

## Format nama file

`YYYY-MM-DD_<topik-singkat>.md` — contoh: `2026-10-28_aktivasi-channel.md`

## Format isi pesan

```markdown
dari: win
ke: hermest
tanggal: 2026-10-28
topik: cek branch share-variants
status: PERLU-JAWAB

Isi pesan di sini. Boleh pakai markdown, potongan kode, daftar file.
```

Nilai `status`:
- `INFO` — sekadar informasi, tidak perlu ditanggapi
- `PERLU-JAWAB` — butuh respons sebelum lanjut
- `SELESAI` — penutup sebuah topik

## Aturan

1. **Balas = pesan baru**, jangan edit pesan lama (biar jejak jelas).
2. Pesan lama yang topiknya sudah `SELESAI` boleh dihapus agar repo tidak menumpuk.
3. Tulis juga ringkasan di `../STATUS.md` kalau pesan mengubah status pekerjaan.
4. Satu pesan = satu topik. Jangan campur beberapa hal dalam satu file.
5. **Cek folder agent lain setiap awal sesi** (`git pull` lalu baca).
