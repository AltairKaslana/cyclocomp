package id.cyclocomp;

import android.location.Location;

/**
 * Hitungan statistik gowes.
 *
 * Inti logikanya ada di {@link #onFix(Fix)} yang TIDAK menyentuh kelas Android apa pun,
 * sehingga bisa diuji di JVM biasa tanpa Robolectric. {@link #onLocation(Location)}
 * hanya pembungkus yang mengubah Location menjadi Fix.
 *
 * Satuan internal selalu SI: meter, meter/detik, milidetik.
 */
public class RideStats {
    static final float MAX_ACCURACY_M = 30f;   // buang fix yang akurasinya jelek
    static final float MOVING_SPEED_MS = 1.0f; // < 3,6 km/jam dianggap berhenti
    static final float MIN_STEP_M = 2f;        // buang jitter GPS
    static final long MAX_GAP_MS = 10_000L;    // celah > 10 dtk = segmen baru
    static final double ELEV_STEP_M = 3.0;     // ambang noise ketinggian

    // Gradien dihitung dari jendela, bukan sesaat: mulus dan tidak melompat karena
    // noise altitude. 25 m horizontal cukup responsif untuk tanjakan nyata.
    private static final double GRADE_WINDOW_M = 25.0;
    private static final double GRADE_MAX = 30.0;   // batas wajar, di luar ini noise

    /** Satu titik masukan, bebas dari kelas Android. */
    public static final class Fix {
        public double lat, lon;
        public long timeMs;
        public float speedMps;              // < 0 = tidak tersedia
        public float accuracyM = -1f;       // < 0 = tidak diketahui
        public double altitudeM = Double.NaN;

        public Fix(double lat, double lon, long timeMs) {
            this.lat = lat;
            this.lon = lon;
            this.timeMs = timeMs;
        }
    }

    public double distanceM;
    public long movingMs;
    public float currentSpeedMs;
    public float maxSpeedMs;
    public double elevGainM;
    public double altitudeM = Double.NaN;

    /** Kemiringan jalan dalam persen; positif = naik. */
    public double gradePercent;

    // ---------- lap ----------
    public int lapIndex;          // lap sedang berjalan (1-based)
    public double lapDistanceM;
    public long lapMovingMs;

    private Fix last;
    private double altRef = Double.NaN;
    private double gradeRefDist, gradeRefAlt;

    /** Ketinggian dari barometer; dipakai menggantikan altitude GPS bila tersedia. */
    private double baroAlt = Double.NaN;

    public void resetLast() {
        last = null;
        currentSpeedMs = 0f;
    }

    /** Mulai lap baru: angka lap sebelumnya disimpan pemanggil, di sini mulai dari nol. */
    public void startLap() {
        lapIndex++;
        lapDistanceM = 0;
        lapMovingMs = 0;
    }

    public float avgSpeedMs() {
        return movingMs > 0 ? (float) (distanceM / (movingMs / 1000.0)) : 0f;
    }

    /**
     * Ketinggian barometer (sudah dikalibrasi ke altitude GPS oleh pemanggil).
     * Barometer jauh lebih halus daripada altitude GPS, jadi tanjakan dan kemiringan
     * ikut lebih akurat. Dipakai hanya bila tidak NaN.
     */
    public void setBaroAltitude(double alt) {
        baroAlt = alt;
    }

    public boolean hasBaro() { return !Double.isNaN(baroAlt); }

    /** Pembungkus untuk Location GPS. */
    public boolean onLocation(Location loc) {
        Fix f = new Fix(loc.getLatitude(), loc.getLongitude(), loc.getTime());
        if (loc.hasSpeed()) f.speedMps = loc.getSpeed();
        if (loc.hasAccuracy()) f.accuracyM = loc.getAccuracy();
        if (loc.hasAltitude()) f.altitudeM = loc.getAltitude();
        return onFix(f);
    }

    /** @return true bila titik layak ditulis ke GPX (sedang bergerak / titik awal). */
    public boolean onFix(Fix f) {
        if (f.accuracyM >= 0 && f.accuracyM > MAX_ACCURACY_M) return false;
        updateElevation(f);

        if (last == null) {
            last = f;
            gradeRefDist = 0;
            gradeRefAlt = altitudeM;
            return true;
        }
        long dt = f.timeMs - last.timeMs;
        if (dt <= 0) return false;
        if (dt > MAX_GAP_MS) {          // sinyal sempat hilang: jangan lompatkan jarak
            last = f;
            currentSpeedMs = 0f;
            return true;
        }

        double d = distanceM(last.lat, last.lon, f.lat, f.lon);
        float v = f.speedMps >= 0 ? f.speedMps : (float) (d / (dt / 1000.0));
        last = f;

        updateGrade();

        if (v < MOVING_SPEED_MS || d < MIN_STEP_M) {
            currentSpeedMs = 0f;
            return false;
        }
        currentSpeedMs = v;
        distanceM += d;
        movingMs += dt;
        lapDistanceM += d;
        lapMovingMs += dt;
        if (v > maxSpeedMs) maxSpeedMs = v;
        return true;
    }

    private void updateElevation(Fix f) {
        // Barometer diutamakan: lebih halus dan tidak melompat seperti altitude GPS
        double a;
        if (!Double.isNaN(baroAlt)) a = baroAlt;
        else if (!Double.isNaN(f.altitudeM)) a = f.altitudeM;
        else return;

        altitudeM = a;
        if (Double.isNaN(altRef)) {
            altRef = a;
            return;
        }
        double diff = a - altRef;
        if (diff >= ELEV_STEP_M) {
            elevGainM += diff;
            altRef = a;
        } else if (diff <= -ELEV_STEP_M) {
            altRef = a;
        }
    }

    /**
     * Kemiringan dari jendela jarak tetap. Menunggu sampai jendela terpenuhi supaya
     * nilainya tidak bergoyang karena ketinggian yang kasar.
     */
    private void updateGrade() {
        if (Double.isNaN(altitudeM)) return;
        double win = distanceM - gradeRefDist;
        if (win < GRADE_WINDOW_M) return;
        double rise = altitudeM - gradeRefAlt;
        double g = (rise / win) * 100.0;
        if (g > GRADE_MAX) g = GRADE_MAX;
        else if (g < -GRADE_MAX) g = -GRADE_MAX;
        gradePercent = g;
        gradeRefDist = distanceM;
        gradeRefAlt = altitudeM;
    }

    /** Jarak dua koordinat (haversine). Menggantikan Location.distanceTo agar bisa diuji. */
    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
