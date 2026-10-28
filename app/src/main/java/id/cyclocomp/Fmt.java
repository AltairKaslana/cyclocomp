package id.cyclocomp;

import java.util.Locale;

/**
 * Helper format angka untuk tampilan.
 *
 * Semua angka menggunakan Locale.US (titik sebagai desimal) supaya konsisten
 * di semua perangkat. Label/satuan menggunakan bahasa sistem.
 */
final class Fmt {
    private Fmt() {}

    /** Kecepatan: m/s -> km/jam. */
    static String speed(float mps) {
        return String.format(Locale.US, "%.1f", mps * 3.6f);
    }

    /** Jarak: meter -> km. */
    static String km(double meters) {
        return String.format(Locale.US, "%.2f", meters / 1000.0);
    }

    /** Jarak: meter -> meter bulat. */
    static String meters(double m) {
        return String.format(Locale.US, "%.0f", m);
    }

    /** Gradien persen. */
    static String grade(double percent) {
        return String.format(Locale.US, "%.1f", percent);
    }

    /** Durasi: ms -> h:mm:ss. */
    static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /**
     * Ringkasan satu lap: "Lap n • distance km • duration h:mm:ss • speed km/jam".
     * Dipakai di baris daftar riwayat dan ringkasan.
     */
    static String lap(int n, double meters, long ms, float mps) {
        return String.format(Locale.US, "Lap %d • %s km • %s • %s km/jam",
                n, km(meters), duration(ms), speed(mps));
    }

    /**
     * Kalori bulat dengan pemisah ribuan (Locale.getDefault() agar sesuai region).
     */
    static String kcal(int kcal) {
        return String.format(Locale.getDefault(), "%,d", Math.max(0, kcal));
    }
}
