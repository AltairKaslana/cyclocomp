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
        P(double lat, double lon) { this.lat = lat; this.lon = lon; }
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
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) out.add(new P(lat, lon));
            }
        } catch (Throwable ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
        return out;
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
