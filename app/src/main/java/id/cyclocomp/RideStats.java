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

    // ---------- detak jantung ----------
    /** bpm terakhir dari sensor; <= 0 berarti belum ada data. */
    public int hr = -1;
    public int hrMax;
    private double hrSum;
    private int hrCount;

    /** Dipanggil tiap notifikasi sensor. Nilai di luar rentang wajar diabaikan. */
    public void setHr(int bpm) {
        if (bpm <= 0 || bpm > 250) return;
        hr = bpm;
        if (bpm > hrMax) hrMax = bpm;
        hrSum += bpm;
        hrCount++;
    }

    /** Rata-rata sederhana dari semua sampel; 0 kalau tidak ada data. */
    public int avgHr() {
        return hrCount == 0 ? 0 : (int) Math.round(hrSum / hrCount);
    }

    // ---------- sensor kecepatan/kadensi ----------

    /** Kadensi terakhir dalam rpm; < 0 berarti belum ada data. */
    public int cadence = -1;
    /** Kecepatan terakhir dari sensor roda; < 0 berarti belum ada data. */
    public float sensorSpeedMs = -1f;

    private long cadenceAtMs, sensorSpeedAtMs;

    /**
     * Data sensor dianggap basi setelah beberapa detik tanpa notifikasi.
     * Tanpa batas ini, sensor yang terputus akan menampilkan angka terakhir
     * selamanya - lebih buruk daripada kembali ke GPS, karena angkanya
     * terlihat normal padahal tidak lagi diperbarui.
     */
    private static final long CADENCE_FRESH_MS = 5000L;
    private static final long SPEED_FRESH_MS = 3000L;

    public void setCadence(int rpm, long nowMs) {
        if (rpm < 0 || rpm > 250) return;
        cadence = rpm;
        cadenceAtMs = nowMs;
    }

    public void setSensorSpeed(float mps, long nowMs) {
        if (Float.isNaN(mps) || mps < 0f || mps > 40f) return;
        sensorSpeedMs = mps;
        sensorSpeedAtMs = nowMs;
    }

    /** Buang nilai sensor saat sambungannya berakhir, supaya tidak tampil basi. */
    public void clearSensor() {
        cadence = -1;
        sensorSpeedMs = -1f;
        cadenceAtMs = 0;
        sensorSpeedAtMs = 0;
    }

    public boolean hasCadence(long nowMs) {
        return cadence >= 0 && nowMs - cadenceAtMs <= CADENCE_FRESH_MS;
    }

    public int cadenceNow(long nowMs) {
        return hasCadence(nowMs) ? cadence : -1;
    }

    /**
     * Kecepatan yang ditampilkan: sensor roda bila datanya masih segar,
     * GPS bila tidak. Jarak tetap dihitung dari GPS, jadi sensor hanya
     * memperhalus angka kecepatan - bukan mengubah rekaman rute.
     */
    public float speedMs(long nowMs) {
        if (sensorSpeedMs >= 0f && nowMs - sensorSpeedAtMs <= SPEED_FRESH_MS) {
            return sensorSpeedMs;
        }
        return currentSpeedMs;
    }

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
            gradeRefDist = distanceM;
            gradeRefAlt = altitudeM;
            return true;
        }

        double d = distanceM(last.lat, last.lon, f.lat, f.lon);
        float v = f.speedMps >= 0 ? f.speedMps : (float) (d / (dt / 1000.0));
        last = f;

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
        updateGrade();          // setelah jarak ditambah, supaya jendela 25 m terukur benar
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
