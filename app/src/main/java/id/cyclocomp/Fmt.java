package id.cyclocomp;

import java.util.Locale;

/** Helper format angka untuk tampilan. */
final class Fmt {
    private Fmt() {}

    static String speed(float ms) {
        return String.format(Locale.getDefault(), "%.1f", ms * 3.6f);
    }

    static String km(double meters) {
        return String.format(Locale.getDefault(), "%.2f", meters / 1000.0);
    }

    static String meters(double m) {
        return String.format(Locale.getDefault(), "%.0f", m);
    }

    /** Gradien persen, selalu positif (arahnya sudah ditunjukkan panah terpisah). */
    static String grade(double percent) {
        return String.format(Locale.getDefault(), "%.1f", percent);
    }

    static String duration(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /** Durasi ringkas untuk lap: m:ss di bawah satu jam, h:mm:ss di atasnya. */
    static String shortDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 3600) return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /**
     * Kalori bulat dengan pemisah ribuan.
     *
     * Tanpa pemisah, 1234 terbaca 1.234 di Indonesia (di mana titik = desimal) —
     * justru kebalikannya di tempat lain. Locale dipakai supaya benar di kedua
     * tempat.
     */
    static String kcal(int kcal) {
        return String.format(Locale.getDefault(), "%,d", Math.max(0, kcal));
    }
}
