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
    private static final String K_LAST_RIDE   = "last_ride";
    private static final String K_HR_MAC      = "hr_mac";        // alamat sensor detak jantung
    private static final String K_HR_NAME     = "hr_name";
    private static final String K_CSC_MAC     = "csc_mac";       // sensor kecepatan/kadensi
    private static final String K_CSC_NAME    = "csc_name";
    private static final String K_WHEEL_MM    = "wheel_mm";      // keliling roda, milimeter
    private static final String K_MAP         = "map";           // peta ubin di Ringkasan
    private static final String K_WEIGHT      = "weight";        // kg, dipakai hitung kalori
    private static final String K_THEME       = "theme_light";   // tema siang?
    private static final String K_ONBOARDING  = "onboarding_done"; // 3 langkah pertama sudah dilewati?
    private static final String K_FIELDS      = "fields_";        // + nomor halaman: susunan bidang

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

    /** Id rekaman terakhir yang disimpan (dipakai layar Ringkasan). */
    public static long lastRideId(Context c) { return sp(c).getLong(K_LAST_RIDE, -1); }
    public static void setLastRideId(Context c, long id) { sp(c).edit().putLong(K_LAST_RIDE, id).apply(); }

    // ---------- sensor detak jantung ----------

    /** MAC sensor BLE yang dipilih; "" = belum ada (aplikasi akan mencari sendiri). */
    public static String hrMac(Context c) { return sp(c).getString(K_HR_MAC, ""); }
    public static void setHrMac(Context c, String v) {
        sp(c).edit().putString(K_HR_MAC, v == null ? "" : v).apply();
    }

    /** Nama sensor, hanya untuk ditampilkan di Pengaturan. */
    public static String hrName(Context c) { return sp(c).getString(K_HR_NAME, ""); }
    public static void setHrName(Context c, String v) {
        sp(c).edit().putString(K_HR_NAME, v == null ? "" : v).apply();
    }

    // ---------- sensor kecepatan/kadensi + roda ----------

    /** MAC sensor CSC yang dipilih; "" = aplikasi mencari sendiri saat gowes. */
    public static String cscMac(Context c) { return sp(c).getString(K_CSC_MAC, ""); }
    public static void setCscMac(Context c, String v) {
        sp(c).edit().putString(K_CSC_MAC, v == null ? "" : v).apply();
    }

    public static String cscName(Context c) { return sp(c).getString(K_CSC_NAME, ""); }
    public static void setCscName(Context c, String v) {
        sp(c).edit().putString(K_CSC_NAME, v == null ? "" : v).apply();
    }

    /**
     * Keliling roda dalam milimeter. Sensor CSC hanya mengirim jumlah putaran,
     * jadi angka ini yang mengubahnya menjadi jarak dan kecepatan. Nilai di
     * luar batas wajar dijepit, bukan ditolak diam-diam.
     */
    public static int wheelMm(Context c) {
        return Roda.normalisasi(sp(c).getInt(K_WHEEL_MM, Roda.BAWAAN_MM));
    }

    public static void setWheelMm(Context c, int mm) {
        sp(c).edit().putInt(K_WHEEL_MM, Roda.normalisasi(mm)).apply();
    }

    // ---------- peta ----------

    /** Tampilkan peta ubin OpenStreetMap di layar Ringkasan. */
    public static boolean map(Context c) { return sp(c).getBoolean(K_MAP, true); }
    public static void setMap(Context c, boolean v) { sp(c).edit().putBoolean(K_MAP, v).apply(); }

    /** Bunyi bip saat peringatan & lap. */
    public static boolean beep(Context c) { return sp(c).getBoolean(K_BEEP, true); }
    public static void setBeep(Context c, boolean v) { sp(c).edit().putBoolean(K_BEEP, v).apply(); }

    // ---------- massa pengendara ----------

    /**
     * Massa pengendara (kg), dipakai memperkira kalori.
     *
     * Bawaan 70 kg: angka rata-rata orang dewasa Asia Tenggara, supaya aplikasi
     * tetap memberi perkiraan masuk akal tanpa pernah membuka Pengaturan.
     */
    public static float weight(Context c) { return sp(c).getFloat(K_WEIGHT, 70f); }
    public static void setWeight(Context c, float v) {
        float kg = v;
        if (Float.isNaN(kg) || kg < 30f) kg = 30f;
        if (kg > 150f) kg = 150f;
        sp(c).edit().putFloat(K_WEIGHT, kg).apply();
    }

    // ---------- tema ----------

    /**
     * Tema siang aktif? Bawaannya false = gelap, sama seperti sebelum sakelar
     * ini ada, supaya pengguna lama tidak mendapati tampilan berubah sendiri.
     *
     * Sakelarnya ada di layar Pengaturan. Lihat {@link Tema} untuk cara
     * paletnya benar-benar ditukar.
     */
    public static boolean theme(Context c) { return sp(c).getBoolean(K_THEME, false); }
    public static void setTheme(Context c, boolean v) {
        // commit(), bukan apply(): nilai harus sudah sampai disk SEBELUM layar
        // di-recreate. apply() menulis di thread lain, dan sebagian perangkat
        // masih mengembalikan nilai lama sesaat sesudahnya - layar baru lalu
        // terbangun dengan palet yang salah (pilihan tema terasa macet).
        // Satu boolean, murah: menunggu beberapa milidetik lebih baik daripada
        // tema yang tidak ikut berganti.
        //
        // commit() bisa mengembalikan false (disk sibuk, atau penulisan sedang
        // bersaing). Dulu nilai baliknya diabaikan, dan akibatnya pilihan yang
        // gagal ditulis terlihat sama dengan pilihan yang berhasil - pengguna
        // menekan "Gelap", layar dibangun ulang, dan temanya tetap siang tanpa
        // ada penjelasan. Sekarang ditulis ulang sekali, lalu dibaca balik:
        // yang menentukan bukan "semoga berhasil", tapi apa yang benar-benar
        // terbaca sesudahnya.
        for (int i = 0; i < 2; i++) {
            if (Tema.tulisBerhasil(sp(c).edit().putBoolean(K_THEME, v).commit(),
                    sp(c).getBoolean(K_THEME, !v) == v)) {
                return;
            }
        }
    }

    // ---------- onboarding ----------

    /**
     * Onboarding 3 langkah (lokasi, baterai, sensor) sudah pernah ditutup?
     * Ditandai selesai juga saat pengguna melewatinya - paksanya bukan gaya
     * aplikasi ini. Yang membuat onboarding TIDAK muncul lagi bukan hanya tanda
     * ini, tapi juga keadaan izin (lihat {@link Onboarding#layakDitampilkan}).
     */
    public static boolean onboardingDone(Context c) { return sp(c).getBoolean(K_ONBOARDING, false); }
    public static void setOnboardingDone(Context c, boolean v) {
        sp(c).edit().putBoolean(K_ONBOARDING, v).apply();
    }

    // ---------- bidang per halaman (Atur layar) ----------

    /**
     * Susunan bidang di satu halaman dashboard. Disimpan sebagai daftar angka
     * dipisah koma ("3,4,1"), bukan JSON: tanpa dependency, dan lebih mudah
     * dibaca saat mengeluarkan berkas pengaturan.
     *
     * Kalau isinya rusak atau dari versi yang belum mengenali bidang baru,
     * kembalikan susunan bawaan - dashboard tidak boleh pernah kosong.
     */
    public static int[] pageFields(Context c, int halaman) {
        String raw = sp(c).getString(K_FIELDS + halaman, "");
        if (raw == null || raw.isEmpty()) return Bidang.bawaan(halaman);
        String[] p = raw.split(",");
        int[] out = new int[p.length];
        try {
            for (int i = 0; i < p.length; i++) out[i] = Integer.parseInt(p[i].trim());
        } catch (NumberFormatException e) {
            return Bidang.bawaan(halaman);
        }
        return Bidang.sah(halaman, out) ? out : Bidang.bawaan(halaman);
    }

    public static void setPageFields(Context c, int halaman, int[] urutan) {
        if (!Bidang.sah(halaman, urutan)) return;   // jangan simpan yang tidak sah
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urutan.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(urutan[i]);
        }
        sp(c).edit().putString(K_FIELDS + halaman, sb.toString()).apply();
    }
}
