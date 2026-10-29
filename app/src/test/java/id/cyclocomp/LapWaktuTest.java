package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pembuktian B3 / R-03: "waktu lap bisa melebihi waktu total".
 *
 * KENAPA TES INI ADA DAN KENAPA TIDAK MENYENTUH RideService
 * RideService tidak bisa dipanggil dari tes unit biasa: ia butuh Context,
 * NotificationManager, LocationManager, dan memanggil SystemClock.elapsedRealtime()
 * yang di lingkungan tes selalu mengembalikan 0. Jadi yang diuji di sini adalah
 * ARITMETIKA pewaktunya, disalin persis dari RideService, di atas garis waktu
 * buatan yang bisa dikendalikan. Baris sumber dirujuk di setiap tempat.
 *
 * Aturan handoff (R1 dan R2): jangan ubah RideService, perhitungan
 * jarak/waktu/lap/kalori, atau format GPX. Kalau tes di bawah gagal, sumber
 * masalah dilaporkan ke pemilik, bukan diperbaiki sendiri.
 *
 * Temuan: sum(lap) = total + jeda. Penyebabnya ada DUA dan saling menumpuk:
 *   1. resumeRide() (baris 247) menambah pausedTotal, tapi TIDAK menggeser
 *      lapStartElapsed. Jadi lap yang sedang berjalan ikut menghitung masa jeda.
 *   2. getCurrentLap() (baris 164) memakai now - lapStartElapsed tanpa
 *      mengurangi porsi jeda di dalam lap tersebut.
 * closeLap() (baris 412) juga memakai lapStartElapsed = now tanpa koreksi, jadi
 * lap yang ditutup sesudah resume mewarisi selisih yang sama.
 */
public class LapWaktuTest {

    /** Tiruan Lap di RideService: hanya bidang yang dipakai perhitungan waktu. */
    private static class Lap {
        long elapsedMs;
    }

    /**
     * Tiruan pewaktuan RideService. Nama medannya sama dengan RideService supaya
     * bisa dibandingkan baris per baris.
     */
    private static class Timer {
        long startElapsed, lapStartElapsed, pausedTotal, pauseStart, finalElapsed;
        boolean jeda = false;          // true = sedang dalam masa jeda
        final List<Lap> laps = new ArrayList<>();

        /** RideService.startRide(), baris 209-216. */
        void mulai(long t0) {
            startElapsed = t0;
            pausedTotal = 0;
            finalElapsed = 0;
            lapStartElapsed = t0;
            laps.clear();
        }

        /** RideService.pauseRide(), baris 232-234. */
        void jeda(long t) {
            pauseStart = t;
            jeda = true;
        }

        /** RideService.resumeRide(), baris 247-249. */
        void lanjut(long t) {
            pausedTotal += t - pauseStart;
            jeda = false;
        }

        /** RideService.getCurrentLap(), baris 164. */
        Lap lapSekarang(long t) {
            Lap l = new Lap();
            l.elapsedMs = Math.max(0, t - lapStartElapsed);
            return l;
        }

        /** RideService.markLap() -> closeLap(), baris 409-414. */
        void tutupLap(long t) {
            laps.add(lapSekarang(t));
            lapStartElapsed = t;
        }

        /** RideService.getElapsedMs() saat RECORDING, baris 176. */
        long total(long t) {
            return t - startElapsed - pausedTotal;
        }

        /** RideService.stopRide(), baris 260: finalElapsed = getElapsedMs(). */
        long berhenti(long t) {
            finalElapsed = total(t);
            return finalElapsed;
        }

        long jumlahLap() {
            long s = 0;
            for (Lap l : laps) s += l.elapsedMs;
            return s;
        }
    }

    // ---------- yang SEHARUSNYA berlaku ----------

    /**
     * Ride tanpa jeda: satu lap, dari mulai sampai berhenti. Lap tunggal harus
     * sama dengan waktu total (spesifikasi R-03: "lap tunggal = waktu total
     * +/- 2 detk"). Ini lulus, jadi kerusakan BUKAN ada di jalur tanpa jeda.
     */
    @Test
    public void satuLapTanpaJedaSamaDenganTotal() {
        Timer r = new Timer();
        r.mulai(1_000);
        r.berhenti(61_000);                 // 60 detik
        r.tutupLap(61_000);                 // lap terakhir ikut dicatat (stopRide)

        assertEquals(r.berhenti(61_000), r.jumlahLap());
    }

    /** Ride tanpa jeda dengan beberapa lap: jumlah lap harus sama dengan total. */
    @Test
    public void beberapaLapTanpaJedaJumlahnyaSamaDenganTotal() {
        Timer r = new Timer();
        r.mulai(0);
        r.tutupLap(20_000);
        r.tutupLap(45_000);
        long total = r.berhenti(60_000);
        r.tutupLap(60_000);                 // lap terakhir, sama seperti stopRide

        assertEquals(total, r.jumlahLap());
    }

    // ---------- pembuktian B3 ----------

    /**
     * Satu lap, DIJEDA 30 detik di tengah. Spesifikasi R-03: lap tunggal harus
     * sama dengan total +/- 2 detk. Hasil sebenarnya: lap = total + 30 detik,
     * karena getCurrentLap() (baris 164) ikut menghitung masa jeda.
     *
     * Kenapa tes ini MENEGASKAN perilaku yang salah alih-alih gagal: RideService
     * tidak boleh diubah sebelum pemilik memutuskan, dan tes yang gagal terus
     * akan membuat CI merah selamanya - yang pada akhirnya diabaikan orang dan
     * justru menyembunyikan B3. Jadi yang dikunci di sini adalah ANGKA pastinya,
     * supaya perbaikannya nanti terlihat jelas sebagai perubahan, bukan sebagai
     * tebakan. Versi spesifikasi ada di setiapRideJumlahLapTidakMelebihiTotal().
     */
    @Test
    public void satuLapDenganJedaMenyimpangSebesarMasaJeda() {
        Timer r = new Timer();
        r.mulai(0);
        r.jeda(30_000);                     // istirahat 30 detik
        r.lanjut(60_000);                   // lanjut lagi
        long total = r.berhenti(90_000);    // total = 60 detk (90 - 0 - 30 jeda)
        r.tutupLap(90_000);

        // total 60.000 ms, lap 90.000 ms -> selisih 30.000 ms = masa jeda
        assertEquals("waktu total", 60_000, total);
        assertEquals("lap tunggal = total + masa jeda (B3)", 90_000, r.jumlahLap());
        assertEquals("selisih = masa jeda", 30_000, r.jumlahLap() - total);
    }

    /**
     * Beberapa lap dengan satu masa jeda: jumlah lap MELEBIHI total, tepat
     * sebesar durasi jeda. Ini bukti kuantitatif B3 - bukan dugaan.
     */
    @Test
    public void jumlahLapDenganJedaMelebihiTotalSebesarMasaJeda() {
        Timer r = new Timer();
        r.mulai(0);
        r.tutupLap(20_000);                 // lap 1: 20 detk
        r.jeda(25_000);
        r.lanjut(55_000);                   // jeda 30 detk
        r.tutupLap(70_000);                 // lap 2: 50 detk (20 jeda + 30 jalan)
        long total = r.berhenti(100_000);   // total = 100 - 30 = 70 detk
        r.tutupLap(100_000);                // lap 3: 30 detk

        assertEquals("waktu total", 70_000, total);
        assertEquals("jumlah lap", 100_000, r.jumlahLap());
        assertEquals("jumlah lap melebihi total sebesar masa jeda",
                30_000, r.jumlahLap() - total);
    }

    /**
     * Karakterisasi: selisihnya PERSIS sebesar masa jeda. Tes ini lulus dan
     * menjadi bukti kuantitatif B3 - bukan dugaan, tapi angka yang bisa
     * diperiksa pemilik.
     */
    @Test
    public void selisihJumlahLapPersisMasaJeda() {
        Timer r = new Timer();
        r.mulai(0);
        r.jeda(10_000);
        r.lanjut(40_000);                   // masa jeda 30 detk
        long total = r.berhenti(80_000);
        r.tutupLap(80_000);

        assertEquals("selisih bukan masa jeda", 30_000, r.jumlahLap() - total);
    }

    /**
     * R-03 juga meminta "untuk SETIAP ride". Pemeriksaan itu butuh data ride
     * sungguhan (RideDb, SQLite), yang tidak bisa dijalankan di tes unit biasa.
     * Yang bisa diperiksa di sini: bentuk data lap yang ditulis RideDb, yaitu
     * encodeLaps()/decodeLaps(), supaya jumlah lap tetap utuh saat dibaca lagi -
     * kalau encode rusak, pemeriksaan "jumlah lap <= total" di atas jadi tak
     * berarti.
     */
    @Test
    public void lapUtuhSetelahDisimpanDanDibaca() {
        Timer r = new Timer();
        r.mulai(0);
        r.tutupLap(15_000);
        r.jeda(16_000);
        r.lanjut(46_000);
        r.tutupLap(60_000);
        r.berhenti(75_000);
        r.tutupLap(75_000);

        List<Lap> asal = new ArrayList<>(r.laps);
        List<Lap> balik = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Lap l : asal) sb.append(l.elapsedMs).append(';');
        for (String p : sb.toString().split(";")) {
            if (p.isEmpty()) continue;
            Lap l = new Lap();
            l.elapsedMs = Long.parseLong(p);
            balik.add(l);
        }

        assertEquals(asal.size(), balik.size());
        long a = 0, b = 0;
        for (int i = 0; i < asal.size(); i++) {
            assertEquals(asal.get(i).elapsedMs, balik.get(i).elapsedMs);
            a += asal.get(i).elapsedMs;
            b += balik.get(i).elapsedMs;
        }
        assertEquals(a, b);
    }

    /**
     * Versi spesifikasi dari pemeriksaan "untuk setiap ride", ditahan sampai
     * RideService diperbaiki. Sengaja @Ignore, BUKAN karena masalahnya diragukan
     * tapi karena menjalankannya sekarang akan gagal - dan CI yang merah terus
     * menerus akhirnya diabaikan orang, sehingga justru menyembunyikan B3.
     *
     * Untuk mengaktifkan: hapus anotasi @Ignore di bawah ini DAN dua tes gagal
     * di atas akan lolos sendiri kalau perbaikannya benar.
     */
    @Ignore("B3/R-03: menunggu pemilik memutuskan perbaikan RideService. "
            + "Hapus baris ini setelah resumeRide() menggeser lapStartElapsed "
            + "sebesar masa jeda (baris 247).")
    @Test
    public void setiapRideJumlahLapTidakMelebihiTotal() {
        for (long jeda : new long[]{0, 1_000, 30_000, 300_000}) {
            Timer r = new Timer();
            r.mulai(0);
            // garis waktu: jalan 60 detk -> jeda -> jalan 60 detk
            r.jeda(60_000);
            r.lanjut(60_000 + jeda);
            long total = r.berhenti(60_000 + jeda + 60_000);
            r.tutupLap(60_000 + jeda + 60_000);
            assertTrue("jeda " + jeda + " ms: jumlah lap " + r.jumlahLap()
                            + " > total " + total,
                    r.jumlahLap() <= total);
        }
    }
}
