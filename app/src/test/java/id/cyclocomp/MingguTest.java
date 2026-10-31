package id.cyclocomp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/** Uji pengelompokan gowes per hari lokal untuk kartu tujuh hari. */
public class MingguTest {

    private static final TimeZone JKT = TimeZone.getTimeZone("Asia/Jakarta");

    private static long waktu(int hari, int jam) {
        Calendar c = Calendar.getInstance(JKT);
        c.clear();
        c.set(2026, Calendar.OCTOBER, 31, 12, 0, 0);
        c.add(Calendar.DAY_OF_MONTH, hari);
        c.set(Calendar.HOUR_OF_DAY, jam);
        return c.getTimeInMillis();
    }

    @Test public void batasTujuhHariLokal() {
        long[] b = Minggu.batas(waktu(0, 12), JKT);
        assertEquals(waktu(-6, 0), b[0]);
        assertEquals(waktu(0, 0), b[6]);
        assertEquals(waktu(1, 0), b[7]);
    }

    @Test public void jarakDikelompokkanPerHari() {
        // urut acak: hari ini, kemarin, enam hari lalu, dan satu di luar jendela
        long[] mulai = {waktu(0, 12), waktu(-1, 20), waktu(-6, 5), waktu(-7, 12)};
        double[] meter = {5000, 2000, 3000, 9999};
        double[] hasil = Minggu.jarak(waktu(0, 12), mulai, meter, JKT);

        assertEquals(3000.0, hasil[0], 0.01);
        assertEquals(0.0, hasil[1], 0.01);
        assertEquals(2000.0, hasil[5], 0.01);
        assertEquals(5000.0, hasil[6], 0.01);
    }

    @Test public void batasHariIkutDihitungSesuaiSisi() {
        long[] mulai = {waktu(-6, 0), waktu(1, 0)};
        double[] meter = {1234, 4321};
        double[] hasil = Minggu.jarak(waktu(0, 12), mulai, meter, JKT);

        assertEquals("tepat awal jendela masuk", 1234.0, hasil[0], 0.01);
        assertEquals("tepat awal hari besok keluar", 0.0, hasil[6], 0.01);
    }

    @Test public void tengahMalamLokal() {
        assertEquals(waktu(0, 0), Minggu.awalHari(waktu(0, 23), JKT));
        assertEquals(waktu(0, 0), Minggu.awalHari(waktu(0, 0), JKT));
    }

    @Test public void hariDaylightSavingTetapTujuhBatas() {
        // 8 Maret 2026: AS maju satu jam. Batas harus tetap tujuh hari kalender.
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        Calendar c = Calendar.getInstance(ny);
        c.clear();
        c.set(2026, Calendar.MARCH, 8, 12, 0, 0);
        long[] b = Minggu.batas(c.getTimeInMillis(), ny);
        assertEquals(8, b.length);
        assertEquals(7, Minggu.jarak(c.getTimeInMillis(),
                new long[]{c.getTimeInMillis()}, new double[]{1000}, ny).length);
        assertEquals(1000.0, Minggu.jarak(c.getTimeInMillis(),
                new long[]{c.getTimeInMillis()}, new double[]{1000}, ny)[6], 0.01);
    }
}
