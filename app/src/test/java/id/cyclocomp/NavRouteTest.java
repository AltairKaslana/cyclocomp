package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Uji geometri rute navigasi di JVM: jarak, posisi terdekat, dan belokan.
 * Tidak butuh emulator maupun Robolectric karena NavRoute tidak menyentuh
 * kelas Android sama sekali.
 */
public class NavRouteTest {

    private static final double LAT0 = -6.2;
    private static final double LON0 = 106.8;
    /** Meter per derajat lintang pada R = 6371000. */
    private static final double M_PER_DEG = 111194.93;

    private static double latOf(double northM) {
        return LAT0 + northM / M_PER_DEG;
    }

    private static double lonOf(double eastM) {
        return LON0 + eastM / (M_PER_DEG * Math.cos(Math.toRadians(LAT0)));
    }

    private static double[][] rute(double[] east, double[] north) {
        double[] lat = new double[east.length];
        double[] lon = new double[east.length];
        for (int i = 0; i < east.length; i++) {
            lat[i] = latOf(north[i]);
            lon[i] = lonOf(east[i]);
        }
        return new double[][]{lat, lon};
    }

    private static NavRoute garisLurus() {
        double[] east = new double[11];
        double[] north = new double[11];
        for (int i = 0; i < 11; i++) north[i] = i * 100.0;
        return NavRoute.build(rute(east, north));
    }

    private static NavRoute siku(boolean keKanan) {
        double sign = keKanan ? 1 : -1;
        double[] east = new double[11];
        double[] north = new double[11];
        for (int i = 0; i < 6; i++) north[i] = i * 100.0;
        for (int i = 1; i <= 5; i++) {
            north[5 + i] = 500;
            east[5 + i] = sign * i * 100.0;
        }
        return NavRoute.build(rute(east, north));
    }

    @Test public void ruteTerlaluPendekDitolak() {
        assertNull(NavRoute.build(null));
        assertNull(NavRoute.build(new double[][]{{latOf(0)}, {lonOf(0)}}));
        assertNull(NavRoute.build(new double[][]{{Double.NaN, latOf(10)}, {lonOf(0), lonOf(0)}}));
        assertNotNull(garisLurus());
    }

    @Test public void panjangRuteSesuaiJarakTitik() {
        NavRoute r = garisLurus();
        assertEquals(1000.0, r.lengthM(), 1.0);
    }

    @Test public void posisiDiTengahTanpaBelokan() {
        NavRoute.Snap s = garisLurus().snap(latOf(500), lonOf(0));
        assertEquals(500.0, s.alongM, 1.0);
        assertEquals(0.0, s.offM, 0.5);
        assertEquals(500.0, s.remainM, 1.0);
        assertEquals(NavRoute.LURUS, s.turnDir);
        assertEquals(s.remainM, s.toTurnM, 1.0);
        assertEquals(0.0, s.courseDeg, 1.0);
    }

    @Test public void belokanKananDitemukanDenganJarakBenar() {
        NavRoute.Snap s = siku(true).snap(latOf(200), lonOf(0));
        assertEquals(200.0, s.alongM, 1.0);
        assertEquals(NavRoute.KANAN, s.turnDir);
        assertEquals(300.0, s.toTurnM, 1.5);
        assertEquals(90.0, siku(true).courseAt(700), 1.0);
    }

    @Test public void belokanKiriDitemukan() {
        NavRoute.Snap s = siku(false).snap(latOf(200), lonOf(0));
        assertEquals(NavRoute.KIRI, s.turnDir);
        assertEquals(300.0, s.toTurnM, 1.5);
    }

    @Test public void sesudahBelokanTidakAdaBelokanLagi() {
        NavRoute.Snap s = siku(true).snap(latOf(500), lonOf(200));
        assertEquals(700.0, s.alongM, 1.5);
        assertEquals(NavRoute.LURUS, s.turnDir);
        assertEquals(300.0, s.remainM, 1.5);
    }

    @Test public void jarakTegakLurusKeRute() {
        NavRoute.Snap s = garisLurus().snap(latOf(500), lonOf(30));
        assertEquals(30.0, s.offM, 0.5);
        assertEquals(500.0, s.alongM, 1.0);
    }

    @Test public void lewatUjungRuteSisaNol() {
        NavRoute.Snap s = garisLurus().snap(latOf(1200), lonOf(0));
        assertEquals(1000.0, s.alongM, 0.1);
        assertEquals(0.0, s.remainM, 0.1);
        assertEquals(NavRoute.LURUS, s.turnDir);
    }

    @Test public void tanpaGpsPosisiDiAwal() {
        NavRoute.Snap s = new NavRoute.Snap();
        garisLurus().snapAlong(s, 0);
        assertEquals(0.0, s.alongM, 0.01);
        assertEquals(1000.0, s.remainM, 0.01);
        assertEquals(0.0, s.offM, 0.01);
    }

    @Test public void titikMenumpukDibuang() {
        double[] east = {0, 0, 0, 0};
        double[] north = {0, 0.1, 100, 200};
        NavRoute r = NavRoute.build(rute(east, north));
        assertNotNull(r);
        assertTrue("titik 10 cm harus digabung, dapat " + r.size(), r.size() <= 3);
    }
}
