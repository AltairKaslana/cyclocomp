package id.cyclocomp;

/**
 * Aturan langkah onboarding - logika MURNI, tanpa android.* supaya bisa diuji
 * di JVM biasa (lihat OnboardingTest).
 *
 * Onboarding punya tiga langkah: lokasi, baterai, sensor. Yang boleh dilewati
 * kapan saja adalah SEMUA langkah (rancangan UI/UX 5.2: "Lewati bisa dilakukan
 * kapan saja") - tapi satu langkah punya syarat lebih kuat:
 *
 *  - Lokasi: tanpa izin ini aplikasi tidak bisa merekam sama sekali, jadi
 *    langkah ini tidak punya tombol "lewati" - hanya "Izinkan". Kalau pengguna
 *    menolak, onboarding tetap menutup (biar tidak mengunci aplikasi) dan
 *    MainActivity akan meminta izin lagi saat tombol MULAI ditekan.
 *  - Baterai: opsional, tapi kalau sudah di-whitelist, langkah ini selesai
 *    sendiri dan tombol utamanya berubah jadi "Lanjut" (bukan "Buka pengaturan").
 *  - Sensor: murni opsional - "Mulai gowes" langsung menutup onboarding.
 */
public final class Onboarding {

    public static final int LOKASI = 0;
    public static final int BATERAI = 1;
    public static final int SENSOR = 2;
    public static final int JUMLAH = 3;

    private Onboarding() {
    }

    /** Langkah berikutnya; kalau sudah di langkah terakhir, kembalikan -1. */
    public static int berikutnya(int langkah) {
        return langkah >= JUMLAH - 1 ? -1 : langkah + 1;
    }

    /** Langkah sebelumnya; kalau sudah di langkah pertama, kembalikan -1. */
    public static int sebelumnya(int langkah) {
        return langkah <= 0 ? -1 : langkah - 1;
    }

    /**
     * Tombol utama di tiap langkah.
     *
     * @param lokasiDiizinkan izin lokasi sudah diberikan
     * @param bateraiDikecualikan aplikasi sudah di luar optimasi baterai
     */
    public static int tombolUtama(int langkah, boolean lokasiDiizinkan, boolean bateraiDikecualikan) {
        switch (langkah) {
            case LOKASI:
                return lokasiDiizinkan ? R.string.onb_lanjut : R.string.onb_izinkan_lokasi;
            case BATERAI:
                return bateraiDikecualikan ? R.string.onb_lanjut : R.string.onb_buka_pengaturan;
            default:
                return R.string.onb_mulai;
        }
    }

    /**
     * Kalau izin lokasi sudah ada SEBELUM onboarding dibuka (pengguna menutup
     * onboarding lalu membukanya lagi, atau izin diberikan dari Pengaturan),
     * langkah lokasi dilewati otomatis: meminta izin yang sudah ada hanya
     * membuat pengguna bingung.
     */
    public static int langkahAwal(boolean lokasiDiizinkan) {
        return lokasiDiizinkan ? BATERAI : LOKASI;
    }

    /**
     * Langkah terakhir yang benar-benar perlu ditampilkan. Kalau lokasi sudah
     * diizinkan dan baterai sudah dikecualikan, onboarding hanya perlu satu
     * layar (sensor) - dan kalau itu pun sudah ditandai selesai, jangan
     * tampilkan apa pun.
     */
    public static boolean layakDitampilkan(boolean sudahSelesai, boolean lokasiDiizinkan,
                                           boolean bateraiDikecualikan) {
        if (sudahSelesai) return false;
        // selalu layak kalau ada setidaknya satu hal yang belum beres
        return !lokasiDiizinkan || !bateraiDikecualikan;
    }
}
