# PROTOCOL Handoff Multi-Agent — Cyclocomp

Dua agent mengerjakan project ini di mesin berbeda. Wajib diikuti supaya tidak saling menimpa.

## Agent

| Agent | Mesin | Peran di project ini |
|---|---|---|
| **HERMEST** | Armbian (ARM/aarch64) | Dev host utama: tulis kode, commit, push. **Tidak bisa build Android lokal** (`aapt2` resmi hanya x86_64) — APK dibangun via GitHub Actions. |
| **WIN** | Windows (x86_64) | Build lokal (`gradlew assembleDebug`), **tes di HP fisik via USB debugging**, review kode. |

## Aturan Git

1. **Jangan push langsung ke `main`.** Pakai branch `<agent>/<topik>`, lalu PR/merge.
2. Sebelum mulai: `git pull origin main` + `git checkout -b <agent>/<topik>`.
3. Sebelum selesai/handoff: update `handoff/STATUS.md` + `handoff/TASKS.md`, lalu commit.
4. Versi: bump di commit message (`v0.9.33: ...`) mengikuti konvensi repo.
5. Jangan `push --force` ke branch agent lain. CI yang build release — commit ke `main` memicu workflow.

## Pembagian Kepemilikan Area

| Area | Owner | Catatan |
|---|---|---|
| Kode fitur baru (Java, layout, strings) | HERMEST utama | WIN review |
| Build/release, keystore, CI workflow | bersama | Keystore hanya di GitHub Secrets — JANGAN masuk repo |
| Tes di perangkat fisik | **WIN** (punya HP + USB) | HERMEST tidak bisa uji di device |
| Unit test JVM (`app/src/test/`) | bersama | CI menjalankan `testDebugUnitTest` |

## Prosedur Handoff (wajib tiap akhir sesi)

1. Commit + push branch pekerjaan.
2. Update `handoff/STATUS.md`: yang sudah dikerjakan, yang menggantung, file yang dipegang.
3. Update `handoff/TASKS.md`: tandai selesai / tambah task baru + owner.
4. Kalau perlu uji di HP: catat di STATUS.md sebagai "MENUNGGU TES DEVICE (WIN)" dengan langkah uji spesifik.

## Channel Pesan

Pertukaran informasi async antar-agent lewat `handoff/messages/` (outbox/inbox).
Aturan lengkap: `handoff/messages/README.md`. Cek `messages/inbox/` **setiap
awal sesi** sebelum mulai kerja.

## Aturan Kode (dari HANDOFF.md, jangan dilanggar)

- Java 8, tanpa lambda/stream, tanpa dependency (no AndroidX). Pakai `SDK_INT` guard.
- String UI wajib di `strings.xml` **dan** `values-en/strings.xml` — cek duplikat juga.
- Verifikasi resource cek: hilang + duplikat + referensi tak terpakai.
- Perubahan `RideStats` wajib ada/tambah unit test (CI menjalankannya).
