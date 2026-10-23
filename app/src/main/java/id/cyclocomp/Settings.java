package id.cyclocomp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Pengaturan pengguna. Semua punya nilai bawaan yang masuk akal supaya aplikasi
 * tetap bisa dipakai tanpa pernah membuka layar Pengaturan.
 *
 * Kunci dipakai langsung sebagai nama; tidak pakai resource string agar
 * refaktor resource tidak pernah memutus data tersimpan.
 */
final class Settings {
    private Settings() {}

    private static final String FILE = "cyclocomp";

    // kunci
    private static final String K_AUTOPAUSE   = "autopause";
    private static final String K_AP_SPEED    = "ap_speed";     // km/j
    private static final String K_AP_DELAY    = "ap_delay";     // detik
    private static final String K_LAP_MODE    = "lap_mode";     // manual|distance|time
    private static final String K_LAP_DIST    = "lap_dist";     // km
    private static final String K_LAP_TIME    = "lap_time";     // menit
    private static final String K_ALERT_KM    = "alert_km";     // km, 0 = mati
    private static final String K_ALERT_MIN   = "alert_min";    // menit, 0 = mati
    private static final String K_BEEP        = "beep";

    // mode lap
    public static final int LAP_MANUAL = 0, LAP_DISTANCE = 1, LAP_TIME = 2;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------- auto-pause ----------

    public static boolean autoPause(Context c) { return sp(c).getBoolean(K_AUTOPAUSE, true); }
    public static void setAutoPause(Context c, boolean v) { sp(c).edit().putBoolean(K_AUTOPAUSE, v).apply(); }

    /** Ambang kecepatan di bawah ini dianggap berhenti (km/j). */
    public static float autoPauseSpeed(Context c) { return sp(c).getFloat(K_AP_SPEED, 3f); }
    public static void setAutoPauseSpeed(Context c, float v) { sp(c).edit().putFloat(K_AP_SPEED, v).apply(); }

    /** Ambang yang sama dalam m/s, satuan yang dipakai RideStats. */
    public static float autoPauseSpeedMs(Context c) { return autoPauseSpeed(c) / 3.6f; }

    /** Harus berhenti selama sekian detik sebelum benar-benar dijeda. */
    public static int autoPauseDelay(Context c) { return sp(c).getInt(K_AP_DELAY, 5); }
    public static void setAutoPauseDelay(Context c, int v) { sp(c).edit().putInt(K_AP_DELAY, v).apply(); }

    public static long autoPauseDelayMs(Context c) { return autoPauseDelay(c) * 1000L; }

    // ---------- lap ----------

    public static int lapMode(Context c) { return sp(c).getInt(K_LAP_MODE, LAP_MANUAL); }
    public static void setLapMode(Context c, int v) { sp(c).edit().putInt(K_LAP_MODE, v).apply(); }

    /** Jarak satu lap otomatis (km). */
    public static float lapDistance(Context c) { return sp(c).getFloat(K_LAP_DIST, 5f); }
    public static void setLapDistance(Context c, float v) { sp(c).edit().putFloat(K_LAP_DIST, v).apply(); }

    public static double lapDistanceM(Context c) { return lapDistance(c) * 1000.0; }

    /** Durasi satu lap otomatis (menit). */
    public static int lapTime(Context c) { return sp(c).getInt(K_LAP_TIME, 20); }
    public static void setLapTime(Context c, int v) { sp(c).edit().putInt(K_LAP_TIME, v).apply(); }

    public static long lapTimeMs(Context c) { return lapTime(c) * 60000L; }

    // ---------- peringatan ----------

    /** Beri tahu tiap sekian km; 0 = mati. */
    public static float alertKm(Context c) { return sp(c).getFloat(K_ALERT_KM, 0f); }
    public static void setAlertKm(Context c, float v) { sp(c).edit().putFloat(K_ALERT_KM, v).apply(); }

    /** Beri tahu tiap sekian menit; 0 = mati. */
    public static int alertMin(Context c) { return sp(c).getInt(K_ALERT_MIN, 0); }
    public static void setAlertMin(Context c, int v) { sp(c).edit().putInt(K_ALERT_MIN, v).apply(); }

    /** Bunyi bip saat peringatan & lap. */
    public static boolean beep(Context c) { return sp(c).getBoolean(K_BEEP, true); }
    public static void setBeep(Context c, boolean v) { sp(c).edit().putBoolean(K_BEEP, v).apply(); }
}
