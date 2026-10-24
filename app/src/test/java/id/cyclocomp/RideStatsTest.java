package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Uji inti hitungan RideStats. Berjalan di JVM biasa: tidak butuh Android, emulator,
 * maupun Robolectric, karena logikanya ada di onFix(Fix) yang bebas dari kelas Android.
 */
public class RideStatsTest {

    // 0,00001 derajat lintang kira-kira 1,11 m
    private static final double DEG_PER_M = 1.0 / 111000.0;

    /** Membangun titik yang bergerak ke utara sejauh meter tertentu dari titik awal. */
    private static RideStats.Fix at(double metersNorth, long timeMs, float speedMps) {
        RideStats.Fix f = new RideStats.Fix(metersNorth * DEG_PER_M, 0, timeMs);
        f.speedMps = speedMps;
        f.accuracyM = 5f;
        return f;
    }

    @Test public void jarakDuaKoordinatMasukAkal() {
        // 1 derajat lintang ~ 111 km
        double d = RideStats.distanceM(0, 0, 1, 0);
        assertTrue("1 derajat harus sekitar 111 km, dapat " + d,
                d > 110000 && d < 112500);
    }

    @Test public void titikPertamaSelaluDitulis() {
        RideStats s = new RideStats();
        assertTrue(s.onFix(at(0, 1000, 5f)));
        assertEquals(0.0, s.distanceM, 0.01);
    }

    @Test public void akurasiJelekDibuang() {
        RideStats s = new RideStats();
        RideStats.Fix f = at(0, 1000, 5f);
        f.accuracyM = 80f;                     // di atas MAX_ACCURACY_M (30 m)
        assertFalse(s.onFix(f));
        assertEquals(0.0, s.distanceM, 0.01);
    }

    @Test public void bergerakMenambahJarakDanWaktu() {
        RideStats s = new RideStats();
        s.onFix(at(0, 1000, 5f));
        s.onFix(at(100, 2000, 5f));            // 100 m dalam 1 detik
        assertEquals(100.0, s.distanceM, 1.0);
        assertEquals(1000L, s.movingMs);
        assertEquals(5f, s.currentSpeedMs, 0.01f);
    }

    @Test public void berhentiTidakMenambahGerak() {
        RideStats s = new RideStats();
        s.onFix(at(0, 1000, 0f));
        s.onFix(at(0.5, 2000, 0f));            // hampir tidak bergerak
        assertEquals(0.0, s.distanceM, 0.01);
        assertEquals(0L, s.movingMs);
        assertEquals(0f, s.currentSpeedMs, 0.001f);
    }

    @Test public void jitterKecilTidakDihitung() {
        RideStats s = new RideStats();
        s.onFix(at(0, 1000, 5f));
        s.onFix(at(1.0, 2000, 5f));            // 1 m, di bawah MIN_STEP_M (2 m)
        assertEquals(0.0, s.distanceM, 0.01);
    }

    @Test public void celahBesarTidakMelompatkanJarak() {
        RideStats s = new RideStats();
        s.onFix(at(0, 1000, 5f));
        s.onFix(at(2000, 1000 + 60_000L, 5f)); // 2 km tapi 60 detik: sinyal sempat hilang
        assertEquals(0.0, s.distanceM, 0.01);
        assertTrue("titik tetap ditulis sebagai awal segmen baru", s.onFix(at(2001, 1000 + 62_000L, 5f)));
    }

    @Test public void rataRataMemakaiWaktuGerak() {
        RideStats s = new RideStats();
        s.onFix(at(0, 1000, 5f));
        s.onFix(at(200, 11_000, 5f));          // 200 m dalam 10 detik -> 20 m/s
        assertEquals(20f, s.avgSpeedMs(), 0.5f);
    }

    @Test public void tanjakanMemakaiHisteresis() {
        RideStats s = new RideStats();
        RideStats.Fix a = at(0, 1000, 5f);
        a.altitudeM = 100.0;
        s.onFix(a);

        // naik 2 m (< ELEV_STEP_M) tidak dihitung
        RideStats.Fix b = at(50, 2000, 5f);
        b.altitudeM = 102.0;
        s.onFix(b);
        assertEquals(0.0, s.elevGainM, 0.01);

        // naik 5 m dari acuan terakhir dihitung penuh
        RideStats.Fix c = at(100, 3000, 5f);
        c.altitudeM = 105.0;
        s.onFix(c);
        assertEquals(5.0, s.elevGainM, 0.01);
    }

    @Test public void turunTidakMenambahTanjakan() {
        RideStats s = new RideStats();
        RideStats.Fix a = at(0, 1000, 5f);
        a.altitudeM = 200.0;
        s.onFix(a);
        RideStats.Fix b = at(100, 2000, 5f);
        b.altitudeM = 180.0;
        s.onFix(b);
        assertEquals(0.0, s.elevGainM, 0.01);
    }

    @Test public void gradienBaruMunculSetelahJendela25m() {
        RideStats s = new RideStats();
        RideStats.Fix a = at(0, 1000, 5f);
        a.altitudeM = 100.0;
        s.onFix(a);

        // baru 10 m: jendela belum terpenuhi, gradien tetap 0
        RideStats.Fix b = at(10, 2000, 5f);
        b.altitudeM = 101.0;
        s.onFix(b);
        assertEquals(0.0, s.gradePercent, 0.01);

        // 30 m total dengan kenaikan 3 m -> 10 %
        RideStats.Fix c = at(30, 3000, 5f);
        c.altitudeM = 103.0;
        s.onFix(c);
        assertEquals(10.0, s.gradePercent, 0.6);
    }

    @Test public void barometerMengalahkanAltitudeGps() {
        RideStats s = new RideStats();
        RideStats.Fix a = at(0, 1000, 5f);
        a.altitudeM = 100.0;
        s.setBaroAltitude(500.0);              // barometer bilang 500 m
        s.onFix(a);
        assertEquals(500.0, s.altitudeM, 0.01);
        assertTrue(s.hasBaro());
    }

    @Test public void lapDimulaiDariNol() {
        RideStats s = new RideStats();
        s.onFix(at(0, 1000, 5f));
        s.onFix(at(100, 2000, 5f));
        assertEquals(100.0, s.lapDistanceM, 1.0);

        s.startLap();
        assertEquals(1, s.lapIndex);
        assertEquals(0.0, s.lapDistanceM, 0.01);
        assertEquals(0L, s.lapMovingMs);
        assertEquals("jarak total tidak ikut direset", 100.0, s.distanceM, 1.0);
    }

    @Test public void waktuMundurDiabaikan() {
        RideStats s = new RideStats();
        s.onFix(at(0, 2000, 5f));
        assertFalse(s.onFix(at(100, 1000, 5f)));   // waktunya lebih lama dari sebelumnya
        assertEquals(0.0, s.distanceM, 0.01);
    }
}
