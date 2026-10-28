package id.cyclocomp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Pembaca GPX minimal untuk menampilkan rute.
 *
 * Sengaja hanya mengambil lat/lon (dan waktu kalau ada): tidak perlu parser XML penuh
 * karena berkas ini kita sendiri yang menulis, dengan format yang tetap.
 */
final class GpxReader {
    private GpxReader() {}

    /** Satu titik rute. */
    static final class P {
        final double lat, lon;
        /** Detak jantung pada titik ini (bpm); 0 = tidak ada data. */
        final int hr;

        P(double lat, double lon) { this(lat, lon, 0); }

        P(double lat, double lon, int hr) {
            this.lat = lat;
            this.lon = lon;
            this.hr = hr > 0 && hr < 250 ? hr : 0;
        }
    }

    /**
     * Baca titik dari file GPX. Mengembalikan daftar kosong bila berkas tidak ada
     * atau rusak - pemanggil cukup menampilkan "rute tidak tersedia".
     */
    static List<P> read(File f) {
        List<P> out = new ArrayList<P>();
        if (f == null || !f.exists()) return out;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.indexOf("<trkpt");
                if (i < 0) continue;
                double lat = attr(line, "lat");
                double lon = attr(line, "lon");
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    out.add(new P(lat, lon, hr(line)));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
        return out;
    }

    /**
     * Detak jantung dari satu baris &lt;trkpt&gt;, atau 0 kalau tidak ada.
     *
     * Format ditulis sendiri oleh {@link GpxWriter}: ...&lt;gpxtpx:hr&gt;123&lt;/gpxtpx:hr&gt;...
     * Pencarian sengaja hanya menandai penanda pembuka, jadi nilai 0 atau baris tanpa
     * sensor tetap aman.
     */
    private static int hr(String line) {
        final String key = "gpxtpx:hr>";
        int i = line.indexOf(key);
        if (i < 0) return 0;
        int start = i + key.length();
        int end = line.indexOf('<', start);
        if (end < 0) return 0;
        try {
            return Integer.parseInt(line.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double attr(String line, String name) {
        String key = name + "=\"";
        int i = line.indexOf(key);
        if (i < 0) return Double.NaN;
        int start = i + key.length();
        int end = line.indexOf('"', start);
        if (end < 0) return Double.NaN;
        try {
            return Double.parseDouble(line.substring(start, end));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
