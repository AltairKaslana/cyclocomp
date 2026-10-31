package id.cyclocomp;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Impor GPX dari aplikasi lain (Strava, Garmin, Komoot, ...).
 *
 * {@link GpxReader} hanya membaca format yang Zylo tulis sendiri - satu titik
 * per baris - jadi tidak cukup untuk berkas pihak ketiga. Di sini dipakai SAX
 * bawaan Android/JDK: atribut boleh berbaris berbeda, prefix namespace boleh
 * apa saja, dan hanya trkpt/rtept yang diambil.
 */
final class GpxImport {
    private GpxImport() {}

    /** Jarak minimum antar titik setelah disaring (meter). */
    static final double MIN_SPACING_M = 5.0;
    /**
     * Batas titik yang disimpan: rute 60 km masih memakai jarak 5 m, rute
     * 200 km memakai ~17 m. Tanpa batas, berkas 200 ribu titik membuat
     * penggambaran pita dan snap terasa berat di HP lama.
     */
    static final int MAX_POINTS = 12000;
    /** Batas titik mentah supaya berkas rusak/raksasa tidak menghabiskan memori. */
    private static final int MAX_RAW = 200000;

    /** @return {lat[], lon[]} titik mentah dari trkpt dan rtept. */
    static double[][] read(InputStream in) throws Exception {
        final List<double[]> pts = new ArrayList<double[]>();
        if (in == null) return new double[2][0];

        SAXParserFactory f = SAXParserFactory.newInstance();
        // Tanpa namespace: prefix tidak perlu terdaftar, dan nama yang dicari
        // tetap "trkpt"/"rtept" (prefix seperti gpx: ikut dicek di isTitik).
        f.setNamespaceAware(false);
        SAXParser parser = f.newSAXParser();
        parser.parse(in, new DefaultHandler() {
            @Override
            public void startElement(String uri, String local, String qName, Attributes a)
                    throws SAXException {
                if (!isTitik(qName)) return;
                double lat = angka(a, "lat");
                double lon = angka(a, "lon");
                if (Double.isNaN(lat) || Double.isNaN(lon)) return;
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return;
                if (pts.size() >= MAX_RAW) throw new SAXException("terlalu banyak titik");
                pts.add(new double[]{lat, lon});
            }
        });

        double[][] out = new double[2][pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            out[0][i] = pts.get(i)[0];
            out[1][i] = pts.get(i)[1];
        }
        return out;
    }

    /**
     * Buang titik yang terlalu rapat, lalu batasi jumlah titik.
     *
     * Titik terakhir selalu dipertahankan: tanpanya rute kehilangan ujungnya,
     * dan sisa jarak di Sekilas tidak pernah mencapai nol.
     */
    static double[][] simplify(double[][] raw) {
        if (raw == null || raw.length < 2 || raw[0] == null || raw[1] == null) {
            return new double[2][0];
        }
        double[] la = raw[0], lo = raw[1];
        int n = Math.min(la.length, lo.length);
        if (n == 0) return new double[2][0];

        double[] a = new double[n], b = new double[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (m == 0 || i == n - 1
                    || RideStats.distanceM(a[m - 1], b[m - 1], la[i], lo[i]) >= MIN_SPACING_M) {
                a[m] = la[i];
                b[m] = lo[i];
                m++;
            }
        }
        if (m <= MAX_POINTS) {
            return new double[][]{Arrays.copyOf(a, m), Arrays.copyOf(b, m)};
        }

        double[] a2 = new double[MAX_POINTS], b2 = new double[MAX_POINTS];
        int last = m - 1;
        for (int j = 0; j < MAX_POINTS; j++) {
            int idx = (int) Math.round((double) j * last / (MAX_POINTS - 1));
            a2[j] = a[idx];
            b2[j] = b[idx];
        }
        return new double[][]{a2, b2};
    }

    private static boolean isTitik(String nama) {
        if (nama == null) return false;
        return "trkpt".equals(nama) || "rtept".equals(nama)
                || nama.endsWith(":trkpt") || nama.endsWith(":rtept");
    }

    private static double angka(Attributes a, String nama) {
        String v = a.getValue(nama);
        if (v == null) return Double.NaN;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
