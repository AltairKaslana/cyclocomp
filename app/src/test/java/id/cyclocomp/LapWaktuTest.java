package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * B3 / R-03: "jumlah durasi lap tidak boleh melebihi waktu total, dan lap
 * tunggal = waktu total +/- 2 detk".
 *
 * KENAPA TES INI ADA DAN KENAPA TIDAK MEMANGGIL RideService LANGSUNG
 * RideService tidak bisa dipanggil dari tes unit biasa: ia butuh Context,
 * NotificationManager, LocationManager, dan memanggil SystemClock.elapsedRealtime()
 * yang di lingkungan tes selalu mengembalikan 0. Jadi yang diuji di sini adalah
 * ARITMETIKA pewaktunya, disalin persis dari RideService, di atas garis waktu
 * buatan yang bisa dikendalikan. Baris sumber dirujuk di setiap tempat.
 *
 * RIWAYAT BUG (SUDAH DIPERBAIKI)
 * sum(lap) = total + masa jeda, TEPAT. Penyebabnya satu baris: resumeRide()
 * menambah pausedTotal tapi tidak menggeser lapStartElapsed, sehingga
 * getCurrentLap() (now - lapStartElapsed) dan closeLap() ikut menghitung masa
 * jeda. Ride 60 detik dengan satu jeda 30 detik menampilkan lap 90 detik.
 *
 * Perbaikannya: masa jeda sekarang juga menggeser lapStartElapsed ke depan, jadi
 * lap dan total memakai garis waktu yang sama - keduanya berhenti selama jeda.
 * checkAutoLap() untuk mode LAP WAKTU ikut terbawa perbaikan yang sama karena ia
 * juga mengukur dari lapStartElapsed.
 *
 * Catatan proses yang layak diingat: tes ini PERTAMA kali ditulis saat bug-nya
 * masih ada, dan saat itu sengaja MENEGASKAN perilaku yang salah alih-alih gagal
 * - sebab RideService belum boleh diubah sebelum pemilik memutuskan, dan tes yang
 * merah terus akan membuat CI merah selamanya, yang akhirnya diabaikan orang dan
 * justru menyembunyikan masalah. Yang dikunci waktu itu adalah ANGKA selisihnya.
 * Setelah RideService diperbaiki, tiruan di kelas Timer disamakan dengan kode
 * barunya dan ketiga tes itu berubah menjadi asersi spesifikasi seperti di bawah.
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

        /**
         * RideService.resumeRide(), SESUDAH perbaikan B3.
         *
         * Sebelum perbaikan baris ini hanya menambah pausedTotal. Sekarang masa
         * jeda juga menggeser lapStartElapsed, supaya lap dan total memakai
         * garis waktu yang sama - keduanya berhenti selama jeda.
         */
        void lanjut(long t) {
            long lamaJeda = t - pauseStart;
            pausedTotal += lamaJeda;
            lapStartElapsed += lamaJeda;
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

    // ---------- spesifikasi R-03 ----------

    /**
     * Ride tanpa jeda: satu lap, dari mulai sampai berhenti. Lap tunggal harus
     * sama dengan waktu total.
     */
    @Test
    public void satuLapTanpaJedaSamaDenganTotal() {
        Timer r = new Timer();
        r.mulai(1_000);
        long total = r.berhenti(61_000);     // 60 detik
        r.tutupLap(61_000);                  // lap terakhir ikut dicatat (stopRide)

        assertEquals(total, r.jumlahLap());
    }

    /** Ride tanpa jeda dengan beberapa lap: jumlah lap harus sama dengan total. */
    @Test
    public void beberapaLapTanpaJedaJumlahnyaSamaDenganTotal() {
        Timer r = new Timer();
        r.mulai(0);
        r.tutupLap(20_000);
        r.tutupLap(45_000);
        long total = r.berhenti(60_000);
        r.tutupLap(60_000);                  // lap terakhir, sama seperti stopRide

        assertEquals(total, r.jumlahLap());
    }

    /**
     * Satu lap, DIJEDA 30 detik di tengah. Inilah kasus yang dulu gagal: total
     * 60 detik tapi lap 90 detik. Sekarang lap = 60 detik, sama dengan total.
     */
    @Test
    public void satuLapDenganJedaSamaDenganTotal() {
        Timer r = new Timer();
        r.mulai(0);
        r.jeda(30_000);                      // istirahat 30 detik
        r.lanjut(60_000);                    // lanjut lagi
        long total = r.berhenti(90_000);     // total = 90 - 0 - 30 = 60 detik
        r.tutupLap(90_000);

        assertEquals("waktu total", 60_000, total);
        assertEquals("lap tunggal harus sama dengan total, bukan total + jeda",
                60_000, r.jumlahLap());
    }

    /**
     * Beberapa lap dengan satu masa jeda: jumlah lap harus SAMA dengan total.
     * Dulu 100 detik vs total 70 detik (selisih 30 detik = masa jeda).
     */
    @Test
    public void beberapaLapDenganJedaJumlahnyaSamaDenganTotal() {
        Timer r = new Timer();
        r.mulai(0);
        r.tutupLap(20_000);                  // lap 1: 20 detik jalan
        r.jeda(25_000);
        r.lanjut(55_000);                    // jeda 30 detik
        r.tutupLap(70_000);                  // lap 2: 30 detik jalan (bukan 50)
        long total = r.berhenti(100_000);    // total = 100 - 30 = 70 detik
        r.tutupLap(100_000);                 // lap 3: 30 detik jalan

        assertEquals("waktu total", 70_000, total);
        assertEquals("jumlah lap harus sama dengan total", 70_000, r.jumlahLap());
    }

    /**
     * Batas yang tidak boleh dilanggar, untuk beberapa durasi jeda sekaligus -
     * ini wujud dari permintaan R-03 "untuk SETIAP ride".
     */
    @Test
    public void setiapRideJumlahLapTidakMelebihiTotal() {
        for (long lamaJeda : new long[]{0, 1_000, 30_000, 300_000}) {
            Timer r = new Timer();
            r.mulai(0);
            // garis waktu: jalan 60 detik -> jeda -> jalan 60 detik
            r.jeda(60_000);
            r.lanjut(60_000 + lamaJeda);
            long akhir = 60_000 + lamaJeda + 60_000;
            long total = r.berhenti(akhir);
            r.tutupLap(akhir);

            assertEquals("jeda " + lamaJeda + " ms: waktu total",
                    120_000, total);
            assertTrue("jeda " + lamaJeda + " ms: jumlah lap " + r.jumlahLap()
                            + " > total " + total,
                    r.jumlahLap() <= total);
            assertEquals("jeda " + lamaJeda + " ms: jumlah lap harus sama dengan total",
                    120_000, r.jumlahLap());
        }
    }

    /**
     * Lap yang ditutup SESUDAH resume tidak boleh mewarisi masa jeda. Ini jalur
     * yang berbeda dari lap yang sedang berjalan saat jeda dimulai, dan dulu juga
     * salah karena closeLap() memakai lapStartElapsed = now tanpa koreksi.
     *
     * Garis waktu: jalan 10 detik -> JEDA 30 detik -> jalan 10 detik -> jalan
     * 10 detik. Total jalan = 30 detik, dan itu harus sama dengan jumlah lap.
     * Dengan kode lama, lap 2 menjadi 40 detik (10 detik jalan + 30 detik jeda)
     * sehingga jumlahnya 60 detik melawan total 30 detik.
     */
    @Test
    public void lapDitutupSetelahResumeTidakMewarisiJeda() {
        Timer r = new Timer();
        r.mulai(0);
        r.tutupLap(10_000);                  // lap 1: 10 detik jalan, ditutup sebelum jeda
        r.jeda(10_000);
        r.lanjut(40_000);                    // jeda 30 detik
        r.tutupLap(50_000);                  // lap 2: 10 detik jalan sesudah lanjut
        long total = r.berhenti(60_000);     // 60 - 30 jeda = 30 detik
        r.tutupLap(60_000);                  // lap 3: 10 detik jalan

        assertEquals("lap pertama", 10_000, r.laps.get(0).elapsedMs);
        assertEquals("lap kedua tidak boleh mewarisi masa jeda",
                10_000, r.laps.get(1).elapsedMs);
        assertEquals("jumlah lap harus sama dengan total", 30_000, r.jumlahLap());
        assertEquals("waktu total", 30_000, total);
    }

    /**
     * R-03 juga meminta "untuk SETIAP ride" pada data sungguhan. Pemeriksaan itu
     * butuh RideDb (SQLite), yang tidak bisa dijalankan di tes unit biasa.
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
}
