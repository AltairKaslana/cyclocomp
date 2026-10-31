package id.cyclocomp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Pemulihan sesi: kalau proses aplikasi dibunuh sistem di tengah gowes (penghemat
 * baterai pabrikan, kehabisan RAM, HP restart), rekaman tidak boleh hilang begitu saja.
 *
 * RideService menulis penanda ringan tiap beberapa detik. Saat aplikasi dibuka lagi,
 * App memeriksa penanda itu: bila berkas GPX masih ada, berkas ditutup rapi (footer
 * ditambahkan) lalu dimasukkan ke Riwayat.
 *
 * Yang disimpan sengaja hanya yang diperlukan untuk menyelamatkan hasil; angka
 * terakhir dibaca ulang dari GPX bila perlu, jadi tidak ada data besar di sini.
 */
final class SessionRecovery {
    private SessionRecovery() {}

    private static final String FILE = "session";
    private static final String K_ACTIVE = "active";
    private static final String K_GPX = "gpx";
    private static final String K_START = "start";
    private static final String K_DIST = "dist";
    private static final String K_MOVING = "moving";
    private static final String K_MAX = "maxspeed";
    private static final String K_ELEV = "elev";
    private static final String K_DURATION = "duration";
    private static final String K_LAPS = "laps";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Dipanggil RideService berkala selama merekam. */
    static void mark(Context c, String gpxPath, long startMs, long durationMs,
                     double distanceM, long movingMs, double maxSpeedMs,
                     double elevGainM, String laps) {
        sp(c).edit()
                .putBoolean(K_ACTIVE, true)
                .putString(K_GPX, gpxPath)
                .putLong(K_START, startMs)
                .putLong(K_DURATION, durationMs)
                .putFloat(K_DIST, (float) distanceM)
                .putLong(K_MOVING, movingMs)
                .putFloat(K_MAX, (float) maxSpeedMs)
                .putFloat(K_ELEV, (float) elevGainM)
                .putString(K_LAPS, laps)
                .apply();
    }

    /** Dipanggil setelah rekaman selesai dengan benar. */
    static void clear(Context c) {
        sp(c).edit().clear().apply();
    }

    static boolean isActive(Context c) {
        return sp(c).getBoolean(K_ACTIVE, false);
    }

    /**
     * Selamatkan sesi yang tertinggal. Mengembalikan true bila ada yang dipulihkan.
     *
     * Penanda hanya dibersihkan setelah baris riwayat benar-benar TERSIMPAN.
     * Dulu penanda dihapus paling awal, sebelum DB disentuh: kalau insert gagal
     * (penyimpanan penuh, DB terkunci), GPX yang masih utuh di disk menjadi
     * yatim - tidak ada di Riwayat, dan tidak ada lagi penanda yang menuntun
     * pemulihan berikutnya. Jadi selama insert belum berhasil, penanda sengaja
     * dibiarkan; percobaan berikutnya masih punya kesempatan.
     *
     * Untuk sebab yang tidak akan berubah (tidak ada berkas, rekaman terlalu
     * pendek), penanda tetap dibersihkan: menyimpannya hanya akan mengulang
     * pekerjaan yang sama tiap aplikasi dibuka.
     */
    static boolean recover(Context c) {
        if (!isActive(c)) return false;
        String path = sp(c).getString(K_GPX, null);
        long startMs = sp(c).getLong(K_START, 0);
        long durationMs = sp(c).getLong(K_DURATION, 0);
        double dist = sp(c).getFloat(K_DIST, 0f);
        long moving = sp(c).getLong(K_MOVING, 0);
        double maxSp = sp(c).getFloat(K_MAX, 0f);
        double elev = sp(c).getFloat(K_ELEV, 0f);
        String laps = sp(c).getString(K_LAPS, null);

        if (path == null) {
            clear(c);
            return false;
        }

        // GPX yang terputus tidak punya footer; tambahkan supaya bisa dibaca aplikasi lain
        GpxWriter.closeTruncated(c, path);

        if (!GpxStore.exists(c, path)) {
            clear(c);
            return false;
        }

        // terlalu pendek = tidak layak masuk riwayat
        if (dist < 20) {
            GpxStore.delete(c, path);
            clear(c);
            return false;
        }

        RideDb db = new RideDb(c);
        try {
            RideDb.Row r = new RideDb.Row();
            r.startMs = startMs;
            r.durationMs = durationMs;
            r.movingMs = moving;
            r.distanceM = dist;
            r.maxSpeedMs = maxSp;
            r.elevGainM = elev;
            r.gpxPath = path;
            r.laps = laps;
            long id = db.insert(r);
            if (id <= 0) return false;
            Settings.setLastRideId(c, id);
            clear(c);
            return true;
        } finally {
            db.close();
        }
    }
}
