package id.cyclocomp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Geometri rute navigasi: jarak kumulatif, posisi terdekat di rute, dan
 * belokan berikutnya.
 *
 * Sengaja murni Java (tanpa kelas Android) supaya bisa diuji di JVM. Untuk
 * satu perjalanan, proyeksi ekuirektangular di sekitar titik pertama sudah
 * jauh lebih akurat daripada GPS; sesudah itu semua hitungan hanya jarak
 * bidang datar.
 */
final class NavRoute {
    /** Arah belokan, dilihat dari pengendara. */
    static final int KIRI = -1, LURUS = 0, KANAN = 1;

    /** Arah dianggap belok kalau berubah minimal sekian derajat. */
    static final double TURN_DEG = 25.0;
    /**
     * Arah masuk/keluar diukur dari titik 18 m sebelum/sesudah sudut, bukan
     * dari segmen terdekat. Titik GPS mentah berjarak beberapa meter dan
     * arahnya bergoyang; jendela ini membuat tikungan 90 derajat tetap
     * terbaca 90 derajat, bukan 60 atau 120.
     */
    private static final double HEAD_WINDOW_M = 18.0;
    /** Beberapa belokan dalam jarak ini dianggap satu tikungan (jalan melingkar). */
    private static final double TURN_MIN_GAP_M = 40.0;
    /** Titik yang hampir menumpuk membuat arah segmen tidak terdefinisi. */
    private static final double MIN_SEG_M = 0.5;
    private static final double EARTH_M = 6371000.0;
    private static final double RAD = Math.PI / 180.0;

    /** Keadaan posisi pengendara pada rute. */
    static final class Snap {
        public double alongM;      // jarak dari awal rute
        public double offM;        // jarak tegak lurus dari garis rute
        public double remainM;     // sisa sampai akhir rute
        public double toTurnM;     // jarak ke belokan berikutnya
        public double turnAlongM;  // posisi belokan berikutnya di rute
        public double courseDeg;   // arah jalan di posisi ini (0 = utara)
        public int turnDir;        // KIRI / LURUS / KANAN
    }

    private final double lat0, lon0, cosLat;
    private final double[] east, north;   // meter dari titik pertama
    private final double[] cum;           // jarak kumulatif tiap titik
    private final double[] bear;          // arah segmen i -> i+1, derajat
    private final double[] turnAt;
    private final int[] turnDir;
    private final double lengthM;

    private NavRoute(double lat0, double lon0, double[] east, double[] north,
                     double[] cum, double[] bear) {
        this.lat0 = lat0;
        this.lon0 = lon0;
        this.cosLat = Math.cos(lat0 * RAD);
        this.east = east;
        this.north = north;
        this.cum = cum;
        this.bear = bear;
        this.lengthM = cum[cum.length - 1];

        List<Double> at = new ArrayList<Double>();
        List<Integer> dir = new ArrayList<Integer>();
        double lastDelta = 0;
        for (int i = 1; i + 1 < cum.length; i++) {
            double before = headingAt(cum[i] - HEAD_WINDOW_M);
            double after = headingAt(cum[i] + HEAD_WINDOW_M);
            double d = norm180(after - before);
            if (Math.abs(d) < TURN_DEG) continue;
            if (!at.isEmpty() && cum[i] - at.get(at.size() - 1) < TURN_MIN_GAP_M) {
                // Masih tikungan yang sama: simpan sudut yang paling tajam.
                if (Math.abs(d) > Math.abs(lastDelta)) {
                    int last = at.size() - 1;
                    at.set(last, cum[i]);
                    dir.set(last, d > 0 ? KANAN : KIRI);
                    lastDelta = d;
                }
                continue;
            }
            at.add(cum[i]);
            dir.add(d > 0 ? KANAN : KIRI);
            lastDelta = d;
        }
        turnAt = new double[at.size()];
        turnDir = new int[dir.size()];
        for (int i = 0; i < turnAt.length; i++) {
            turnAt[i] = at.get(i);
            turnDir[i] = dir.get(i);
        }
    }

    /**
     * Bangun rute dari pasangan {lat[], lon[]}. Titik ganda dan di luar
     * rentang koordinat dibuang; null kalau tersisa kurang dari dua titik.
     */
    static NavRoute build(double[][] pts) {
        if (pts == null || pts.length < 2 || pts[0] == null || pts[1] == null) return null;
        double[] la = pts[0], lo = pts[1];
        int n = Math.min(la.length, lo.length);
        if (n < 2) return null;

        double lat0 = Double.NaN, lon0 = 0;
        double[] east = new double[n];
        double[] north = new double[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            double a = la[i], o = lo[i];
            if (Double.isNaN(a) || Double.isNaN(o)
                    || a < -90 || a > 90 || o < -180 || o > 180) continue;
            if (m == 0) {
                lat0 = a;
                lon0 = o;
            }
            double cos = Math.cos(lat0 * RAD);
            double y = (a - lat0) * EARTH_M * RAD;
            double x = (o - lon0) * cos * EARTH_M * RAD;
            if (m > 0) {
                double dx = x - east[m - 1], dy = y - north[m - 1];
                if (dx * dx + dy * dy < MIN_SEG_M * MIN_SEG_M) continue;
            }
            east[m] = x;
            north[m] = y;
            m++;
        }
        if (m < 2) return null;

        double[] cum = new double[m];
        double[] bear = new double[m - 1];
        for (int i = 1; i < m; i++) {
            double dx = east[i] - east[i - 1];
            double dy = north[i] - north[i - 1];
            cum[i] = cum[i - 1] + Math.hypot(dx, dy);
            if (i - 1 < bear.length) {
                bear[i - 1] = norma360(Math.atan2(dx, dy) / RAD);
            }
        }
        return new NavRoute(lat0, lon0, Arrays.copyOf(east, m), Arrays.copyOf(north, m),
                cum, bear);
    }

    // ---------- akses untuk gambar pita ----------

    int size() { return east.length; }

    double lengthM() { return lengthM; }

    double alongM(int i) { return cum[i]; }

    double eastM(int i) { return east[i]; }

    double northM(int i) { return north[i]; }

    double latAt(int i) { return lat0 + north[i] / (EARTH_M * RAD); }

    double lonAt(int i) { return lon0 + east[i] / (cosLat * EARTH_M * RAD); }

    /** Arah jalan pada jarak tertentu dari awal. */
    double courseAt(double along) { return headingAt(along); }

    /** Indeks segmen yang memuat jarak tertentu (cum[i] <= along < cum[i+1]). */
    int indexNear(double along) {
        double a = clamp(along, 0, lengthM);
        int lo = 0, hi = cum.length - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (cum[mid] <= a) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    double eastAt(double along) {
        int i = indexNear(along);
        double len = cum[i + 1] - cum[i];
        double t = len <= 0 ? 0 : (clamp(along, 0, lengthM) - cum[i]) / len;
        return east[i] + t * (east[i + 1] - east[i]);
    }

    double northAt(double along) {
        int i = indexNear(along);
        double len = cum[i + 1] - cum[i];
        double t = len <= 0 ? 0 : (clamp(along, 0, lengthM) - cum[i]) / len;
        return north[i] + t * (north[i + 1] - north[i]);
    }

    // ---------- posisi ----------

    /** Posisi GPS dipetakan ke titik terdekat di rute. */
    Snap snap(double lat, double lon) {
        Snap s = new Snap();
        snap(s, lat, lon);
        return s;
    }

    void snap(Snap out, double lat, double lon) {
        double px = (lon - lon0) * cosLat * EARTH_M * RAD;
        double py = (lat - lat0) * EARTH_M * RAD;

        double best = Double.MAX_VALUE;
        int seg = 0;
        double bestT = 0;
        for (int i = 0; i + 1 < east.length; i++) {
            double ax = east[i], ay = north[i];
            double dx = east[i + 1] - ax, dy = north[i + 1] - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 <= 1e-12 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
            if (t < 0) t = 0;
            else if (t > 1) t = 1;
            double qx = ax + t * dx, qy = ay + t * dy;
            double d2 = (px - qx) * (px - qx) + (py - qy) * (py - qy);
            if (d2 < best) {
                best = d2;
                seg = i;
                bestT = t;
            }
        }
        out.alongM = cum[seg] + bestT * (cum[seg + 1] - cum[seg]);
        out.offM = Math.sqrt(Math.max(0, best));
        isiSisa(out, bear[seg]);
    }

    /** Posisi di rute tanpa GPS (mis. sebelum fix pertama): dianggap tepat di garis. */
    void snapAlong(Snap out, double along) {
        out.alongM = clamp(along, 0, lengthM);
        out.offM = 0;
        isiSisa(out, headingAt(out.alongM));
    }

    private void isiSisa(Snap out, double course) {
        out.remainM = Math.max(0, lengthM - out.alongM);
        out.courseDeg = course;
        out.turnDir = LURUS;
        out.turnAlongM = lengthM;
        for (int i = 0; i < turnAt.length; i++) {
            // Belokan yang baru saja dilewati tidak lagi diumumkan.
            if (turnAt[i] > out.alongM + 3.0) {
                out.turnDir = turnDir[i];
                out.turnAlongM = turnAt[i];
                break;
            }
        }
        out.toTurnM = Math.max(0, out.turnAlongM - out.alongM);
    }

    // ---------- bantu ----------

    private double headingAt(double along) {
        if (bear.length == 0) return 0;
        int i = indexNear(along);
        return bear[Math.min(i, bear.length - 1)];
    }

    private static double norm180(double d) {
        while (d > 180) d -= 360;
        while (d <= -180) d += 360;
        return d;
    }

    private static double norma360(double d) {
        while (d < 0) d += 360;
        while (d >= 360) d -= 360;
        return d;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
