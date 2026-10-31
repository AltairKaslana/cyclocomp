package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;

/** Uji impor GPX pihak ketiga dan penyaringan titik. */
public class GpxImportTest {

    private static final String CONTOH = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<gpx version=\"1.1\" creator=\"uji\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
            + "  <wpt lat=\"-6.100\" lon=\"106.700\"/>\n"
            + "  <trk><name>Pagi</name><trkseg>\n"
            + "    <trkpt\n      lat=\"-6.200\"\n      lon=\"106.800\">\n"
            + "      <ele>750</ele>\n    </trkpt>\n"
            + "    <trkpt lat=\"-6.201\" lon=\"106.801\"><ele>751</ele></trkpt>\n"
            + "  </trkseg></trk>\n"
            + "  <rte>\n    <rtept lat=\"-6.202\" lon=\"106.802\"/>\n  </rte>\n"
            + "</gpx>";

    private static double[][] baca(String xml) throws Exception {
        return GpxImport.read(new ByteArrayInputStream(xml.getBytes("UTF-8")));
    }

    @Test public void membacaTrkptDanRteptDariBarisBerbeda() throws Exception {
        double[][] p = baca(CONTOH);
        assertEquals(3, p[0].length);
        assertEquals(-6.200, p[0][0], 1e-9);
        assertEquals(106.800, p[1][0], 1e-9);
        assertEquals(-6.202, p[0][2], 1e-9);
    }

    @Test public void wptDanAtributRusakDiabaikan() throws Exception {
        String xml = "<gpx><wpt lat=\"-6.1\" lon=\"106.7\"/>"
                + "<trkpt lat=\"abc\" lon=\"1\"/>"
                + "<trkpt lon=\"2\"/>"
                + "<trkpt lat=\"-91\" lon=\"0\"/>"
                + "<trkpt lat=\"-6.2\" lon=\"106.8\"/></gpx>";
        assertEquals(1, baca(xml)[0].length);
    }

    @Test public void penyaringanMembuangTitikRapatDanMenjagaUjung() {
        int n = 101;   // ~1 m sekali, total ~100 m
        double[] lat = new double[n];
        double[] lon = new double[n];
        // 1,001 m: sedikit di atas ambang 5 m pada kelipatannya, supaya
        // pembulatan haversine tidak membuat uji ini gagal secara acak.
        for (int i = 0; i < n; i++) lat[i] = -6.2 + i * 1.001 / 111194.93;

        double[][] hasil = GpxImport.simplify(new double[][]{lat, lon});
        assertEquals(21, hasil[0].length);
        assertEquals(lat[n - 1], hasil[0][hasil[0].length - 1], 1e-9);
        for (int i = 1; i < hasil[0].length; i++) {
            double d = RideStats.distanceM(hasil[0][i - 1], hasil[1][i - 1],
                    hasil[0][i], hasil[1][i]);
            assertTrue("jarak antar titik harus >= 5 m, dapat " + d, d >= 4.9);
        }
    }

    @Test public void jumlahTitikDibatasiTetapiUjungTetap() {
        int n = GpxImport.MAX_POINTS * 3;
        double[] lat = new double[n];
        double[] lon = new double[n];
        for (int i = 0; i < n; i++) lat[i] = -6.2 + i * 10.0 / 111194.93;

        double[][] hasil = GpxImport.simplify(new double[][]{lat, lon});
        assertEquals(GpxImport.MAX_POINTS, hasil[0].length);
        assertEquals(lat[n - 1], hasil[0][hasil[0].length - 1], 1e-9);
    }

    @Test public void masukanKosongAman() throws Exception {
        assertEquals(0, GpxImport.read(null)[0].length);
        assertEquals(0, GpxImport.simplify(null)[0].length);
        assertEquals(0, GpxImport.simplify(new double[][]{{}, {}})[0].length);
    }
}
