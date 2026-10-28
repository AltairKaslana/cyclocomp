# STATUS — update di setiap akhir sesi

> Terakhir diupdate: 2026-10-28 oleh **WIN** (opencode)

## State Project

- Versi: **v0.9.32** (fix "setelah gowes tidak bisa kembali ke menu utama")
- Branch `main` up-to-date dengan origin. Ada branch remote: `feature/share-variants-polish`
- CI: `.github/workflows/build-apk.yml` — build APK **release** bertanda tangan keystore (dari GitHub Secrets)
- Build lokal: hanya mungkin di WIN (x86_64). HERMEST di Armbian **tidak bisa** build lokal.
- Unit test: 4 file (`RideStatsTest`, `TimeFixTest`, `JsonTest`, `StravaParamTest`) — jalan di CI

## Sedang Dikerjakan

| Agent | Task | Branch | Status |
|---|---|---|---|
| — | — | — | — |

## Gantung / Perlu Dilengkapi

1. Repo ini sudah di GitHub (`AltairKaslana/cyclocomp`) — remote `origin` sudah ter-set otomatis dari clone
2. Branch `feature/share-variants-polish` belum di-merge ke `main` — perlu direview
3. Roadmap sisa: 4b (kadensi/kecepatan CSC BLE `0x1816`), integrasi Strava API (lihat HANDOFF.md §12)

## Untuk Agent Berikutnya

- Baca `HANDOFF.md` dulu (sumber kebenaran, 435 baris — termasuk pelajaran per-versi §16), lalu `handoff/PROTOCOL.md`
- Roadmap & status fitur: HANDOFF.md bagian 12
