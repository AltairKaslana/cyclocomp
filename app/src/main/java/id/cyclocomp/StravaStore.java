package id.cyclocomp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Kredensial dan token Strava, disimpan di SharedPreferences privat aplikasi.
 *
 * Catatan jujur soal keamanan: client_secret yang dipakai di aplikasi Android selalu bisa
 * dikeluarkan dari berkas APK oleh siapa pun yang memegang perangkat itu. Untuk aplikasi
 * pribadi seperti ini itu dapat diterima, tapi kalau suatu saat dibagikan ke orang lain,
 * rahasia harus dipindah ke server. Token akses disimpan di tempat yang sama dan hanya
 * berlaku untuk satu akun Strava pada satu perangkat.
 */
final class StravaStore {

    private StravaStore() {}

    private static final String P = "strava";
    private static final String K_ID     = "client_id";
    private static final String K_SECRET = "client_secret";
    private static final String K_AT     = "access_token";
    private static final String K_RT     = "refresh_token";
    private static final String K_EXP    = "expires_at";
    private static final String K_NAME   = "athlete";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    static String clientId(Context c)     { return sp(c).getString(K_ID, ""); }
    static void clientId(Context c, String v) {
        sp(c).edit().putString(K_ID, v == null ? "" : v.trim()).apply();
    }

    static String clientSecret(Context c) { return sp(c).getString(K_SECRET, ""); }
    static void clientSecret(Context c, String v) {
        sp(c).edit().putString(K_SECRET, v == null ? "" : v.trim()).apply();
    }

    static String accessToken(Context c)  { return sp(c).getString(K_AT, ""); }
    static void accessToken(Context c, String v) {
        sp(c).edit().putString(K_AT, v == null ? "" : v).apply();
    }

    static String refreshToken(Context c) { return sp(c).getString(K_RT, ""); }
    static void refreshToken(Context c, String v) {
        sp(c).edit().putString(K_RT, v == null ? "" : v).apply();
    }

    /** Waktu kedaluwarsa token akses, epoch detik. 0 kalau belum ada. */
    static long expiresAt(Context c)     { return sp(c).getLong(K_EXP, 0L); }
    static void expiresAt(Context c, long v) {
        sp(c).edit().putLong(K_EXP, v).apply();
    }

    static String athlete(Context c)     { return sp(c).getString(K_NAME, ""); }
    static void athlete(Context c, String v) {
        sp(c).edit().putString(K_NAME, v == null ? "" : v).apply();
    }

    /** Sudah siap dipakai unggah: ada client_id, client_secret, dan token akses. */
    static boolean ready(Context c) {
        return !clientId(c).isEmpty() && !clientSecret(c).isEmpty() && !accessToken(c).isEmpty();
    }

    /** Sudah pernah diberi wewenang (token akses ada), walau kredensial aplikasi belum lengkap. */
    static boolean connected(Context c) {
        return !accessToken(c).isEmpty();
    }

    /** Hapus token saja: kredensial aplikasi tetap tersimpan supaya tidak perlu diketik ulang. */
    static void clearTokens(Context c) {
        sp(c).edit().remove(K_AT).remove(K_RT).remove(K_EXP).remove(K_NAME).apply();
    }

    /** Putuskan hubungan sepenuhnya, termasuk kredensial aplikasi. */
    static void clearAll(Context c) {
        sp(c).edit().clear().apply();
    }

    /** true kalau token akses sudah kedaluwarsa atau hampir (diberi margin 60 detik). */
    static boolean expired(Context c) {
        long exp = expiresAt(c);
        return exp > 0 && exp <= System.currentTimeMillis() / 1000L + 60L;
    }
}
