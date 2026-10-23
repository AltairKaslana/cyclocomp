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

    static String duration(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
