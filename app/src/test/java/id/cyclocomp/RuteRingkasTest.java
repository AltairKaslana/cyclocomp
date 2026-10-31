package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Uji peringkasan rute untuk thumbnail Riwayat. */
public class RuteRingkasTest {

    private static final double DEG_PER_M = 1.0 / 111000.0;

    private static List<GpxReader.P> garis(int n) {
        List<GpxReader.P> out = new ArrayList<GpxReader.P>();
        for (int i = 0; i < n; i++) {
            out.add(new GpxReader.P(i * 10 * DEG_PER_M, 0));
        }
        return out;
    }

    @Test public void rutePendekTidakDiubah() {
        List<GpxReader.P> asal = garis(5);
        List<GpxReader.P> hasil = RuteRingkas.sederhanakan(asal, 48);
        assertEquals(5, hasil.size());
        assertEquals(asal.get(0).lat, hasil.get(0).lat, 1e-12);
        assertEquals(asal.get(4).lat, hasil.get(4).lat, 1e-12);
    }

    @Test public void rutePanjangDibatasiDanUjungnyaTetap() {
        List<GpxReader.P> asal = garis(2000);
        List<GpxReader.P> hasil = RuteRingkas.sederhanakan(asal, 48);
        assertTrue("titik harus <= 48, dapat " + hasil.size(), hasil.size() <= 48);
        assertEquals(asal.get(0).lat, hasil.get(0).lat, 1e-12);
        assertEquals(asal.get(1999).lat, hasil.get(hasil.size() - 1).lat, 1e-12);
    }

    @Test public void titikTerpilihUrutMaju() {
        List<GpxReader.P> hasil = RuteRingkas.sederhanakan(garis(500), 20);
        for (int i = 1; i < hasil.size(); i++) {
            assertTrue("lat harus naik", hasil.get(i).lat > hasil.get(i - 1).lat);
        }
    }

    @Test public void titikMenumpukMenjadiSatu() {
        List<GpxReader.P> asal = new ArrayList<GpxReader.P>();
        for (int i = 0; i < 100; i++) asal.add(new GpxReader.P(-6.2, 106.8));
        List<GpxReader.P> hasil = RuteRingkas.sederhanakan(asal, 48);
        assertEquals(1, hasil.size());
    }

    @Test public void encodeDecodeBulat() {
        List<GpxReader.P> asal = garis(10);
        String teks = RuteRingkas.encode(asal);
        assertNotNull(teks);
        List<GpxReader.P> balik = RuteRingkas.decode(teks);
        assertEquals(asal.size(), balik.size());
        for (int i = 0; i < asal.size(); i++) {
            assertEquals(asal.get(i).lat, balik.get(i).lat, 2e-5);
            assertEquals(asal.get(i).lon, balik.get(i).lon, 2e-5);
        }
    }

    @Test public void decodeMengabaikanPotonganRusak() {
        List<GpxReader.P> hasil = RuteRingkas.decode("a,b;1,2;3;4,5");
        assertEquals(2, hasil.size());
        assertEquals(1.0, hasil.get(0).lat, 1e-9);
        assertEquals(4.0, hasil.get(1).lat, 1e-9);
    }

    @Test public void kosongAman() {
        assertNull(RuteRingkas.encode(new ArrayList<GpxReader.P>()));
        assertTrue(RuteRingkas.decode(null).isEmpty());
        assertTrue(RuteRingkas.decode("").isEmpty());
        assertTrue(RuteRingkas.sederhanakan(null).isEmpty());
        assertNull(RuteRingkas.dariGpx(null, 48));
    }
}
