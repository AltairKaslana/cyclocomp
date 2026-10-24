package id.cyclocomp;

/**
 * Waktu yang aman ditulis ke berkas GPX.
 *
 * Kenapa ada: sebagian perangkat melaporkan jam GPS dengan detik/menit/jam benar tapi
 * tanggal ngawur - terlihat 2007 alih-alih 2026. Berkas GPX tetap terbuka, tapi gowesnya
 * mendarat di tahun yang salah saat diunggah ke Strava/Garmin.
 *
 * Jam sistem dipakai sebagai pembanding: kalau waktu GPS menjauh lebih dari sehari,
 * selisihnya dihitung sekali lalu dipakai untuk titik-titik berikutnya. Dengan begitu
 * jarak antar titik tetap akurat - bukan semua titik diberi waktu yang sama.
 *
 * Sengaja bebas kelas Android supaya bisa diuji di JVM tanpa Robolectric.
 */
final class TimeFix {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** Koreksi waktu; Long.MIN_VALUE berarti belum pernah dihitung. */
    private long offset = Long.MIN_VALUE;

    /**
     * @param gpsMs waktu dari {@code Location.getTime()} (epoch milidetik)
     * @return waktu yang layak ditulis ke GPX (epoch milidetik)
     */
    long sanitize(long gpsMs) {
        long now = System.currentTimeMillis();
        if (gpsMs >= now - DAY_MS && gpsMs <= now + DAY_MS) return gpsMs;   // masuk akal
        if (offset == Long.MIN_VALUE) offset = now - gpsMs;
        return gpsMs + offset;
    }

    /** true kalau koreksi sudah pernah dipakai (jam GPS memang bermasalah). */
    boolean correcting() { return offset != Long.MIN_VALUE; }
}
