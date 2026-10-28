# Channel Pesan Antar-Agent (async via git)

Cara WIN (Windows) dan HERMEST (Armbian) bertukar informasi tanpa server.

## Cara kerja

```
HERMEST tulis di outbox/  →  commit + push  →  WIN pull  →  muncul di inbox/ WIN
WIN tulis di outbox/      →  commit + push  →  HERMEST pull → muncul di inbox/ HERMEST
```

Setiap mesin hanya menulis di **`outbox/`** dan hanya membaca **`inbox/`**.

## Format nama file

`YYYY-MM-DD_<topik-singkat>.md` — contoh: `2026-10-28_cek-branch-share-variants.md`

## Format isi pesan

```markdown
dari: hermest
ke: win
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
2. Setelah topik `SELESAI`, pemilik inbox memindahkan pesannya ke `archive/`.
3. Tulis juga ringkasan di `../STATUS.md` kalau pesan mengubah status pekerjaan.
4. Satu pesan = satu topik. Jangan campur beberapa hal dalam satu file.
