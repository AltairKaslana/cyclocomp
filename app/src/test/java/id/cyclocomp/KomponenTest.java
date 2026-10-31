package id.cyclocomp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Batas status perawatan Garasi (rancangan 5.7). */
public class KomponenTest {

    private static Komponen komponen(double limitM, double startOdoM) {
        Komponen k = new Komponen();
        k.limitM = limitM;
        k.startOdoM = startOdoM;
        return k;
    }

    @Test public void batasNolSelaluBaik() {
        Komponen k = komponen(0, 0);
        assertEquals(Komponen.BAIK, k.status(999999));
        assertEquals(0, k.persen(999999));
    }

    @Test public void odometerDiBawahTitikMulaiTidakNegatif() {
        Komponen k = komponen(1000, 5000);
        assertEquals(0.0, k.terpakaiM(4000), 0.001);
        assertEquals(Komponen.BAIK, k.status(4000));
        assertEquals(1000.0, k.sisaM(4000), 0.001);
    }

    @Test public void diBawahDelapanPuluhPersenBaik() {
        Komponen k = komponen(1000, 0);
        assertEquals(Komponen.BAIK, k.status(0));
        assertEquals(Komponen.BAIK, k.status(799.9));
    }

    @Test public void delapanPuluhSampaiSeratusPersenSegera() {
        Komponen k = komponen(1000, 0);
        assertEquals(Komponen.SEGERA, k.status(800));
        assertEquals(Komponen.SEGERA, k.status(999.9));
        assertEquals(Komponen.SEGERA, k.status(1000));
    }

    @Test public void lebihDariSeratusPersenLewat() {
        Komponen k = komponen(1000, 0);
        assertEquals(Komponen.LEWAT, k.status(1000.1));
        assertEquals(120.0, k.lebihM(1120), 0.001);
        assertEquals(0.0, k.sisaM(1120), 0.001);
    }

    @Test public void titikMulaiIkutDihitung() {
        Komponen k = komponen(2000, 10000);
        assertEquals(Komponen.SEGERA, k.status(11600));   // 1600 / 2000
        assertEquals(Komponen.LEWAT, k.status(12001));    // 2001 / 2000
    }

    @Test public void persenDibulatkanKeBawah() {
        Komponen k = komponen(2000, 0);
        assertEquals(99, k.persen(1999));
        assertEquals(100, k.persen(2000));
        assertEquals(112, k.persen(2250));
    }
}
