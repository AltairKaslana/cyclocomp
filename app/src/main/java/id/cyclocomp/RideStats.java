package id.cyclocomp;

import android.location.Location;

/**
 * Hitungan statistik gowes dari titik GPS. Tanpa dependency Android selain Location,
 * jadi mudah di-unit-test.
 */
public class RideStats {
    static final float MAX_ACCURACY_M = 30f;   // buang fix yang akurasinya jelek
    static final float MOVING_SPEED_MS = 1.0f; // < 3,6 km/jam dianggap berhenti
    static final float MIN_STEP_M = 2f;        // buang jitter GPS
    static final long MAX_GAP_MS = 10_000L;    // celah > 10 dtk = segmen baru
    static final double ELEV_STEP_M = 3.0;     // ambang noise ketinggian

    public double distanceM;
    public long movingMs;
    public float currentSpeedMs;
    public float maxSpeedMs;
    public double elevGainM;
    public double altitudeM = Double.NaN;

    private Location last;
    private double altRef = Double.NaN;

    public void resetLast() {
        last = null;
        currentSpeedMs = 0f;
    }

    public float avgSpeedMs() {
        return movingMs > 0 ? (float) (distanceM / (movingMs / 1000.0)) : 0f;
    }

    /** @return true bila titik layak ditulis ke GPX (sedang bergerak / titik awal). */
    public boolean onLocation(Location loc) {
        if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ACCURACY_M) return false;
        updateElevation(loc);

        if (last == null) {
            last = loc;
            return true;
        }
        long dtMs = loc.getTime() - last.getTime();
        if (dtMs <= 0) return false;
        if (dtMs > MAX_GAP_MS) { // sinyal sempat hilang: jangan lompatkan jarak
            last = loc;
            currentSpeedMs = 0f;
            return true;
        }

        float d = last.distanceTo(loc);
        float v = loc.hasSpeed() ? loc.getSpeed() : d / (dtMs / 1000f);
        last = loc;

        if (v < MOVING_SPEED_MS || d < MIN_STEP_M) {
            currentSpeedMs = 0f;
            return false;
        }
        currentSpeedMs = v;
        distanceM += d;
        movingMs += dtMs;
        if (v > maxSpeedMs) maxSpeedMs = v;
        return true;
    }

    private void updateElevation(Location loc) {
        if (!loc.hasAltitude()) return;
        double a = loc.getAltitude();
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
}
