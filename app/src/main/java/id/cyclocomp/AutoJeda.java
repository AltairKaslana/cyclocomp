package id.cyclocomp;

/**
 * Keputusan murni untuk jeda otomatis: kapan ride harus BERHENTI sendiri, dan
 * kapan harus LANJUT sendiri.
 *
 * Dipisah dari RideService karena di situasi itulah pernah terjadi bug paling
 * mahal sepanjang sejarah aplikasi ini: auto-resume MATI total, tanpa gejala
 * yang terlihat dari luar. Penyebabnya berupa rantai -
 * pauseRide() mencabut GPS, checkAutoResume() hanya terpanggil dari
 * onLocationChanged(), dan tidak ada lagi yang mendaftarkan GPS kembali.
 * Bug itu tidak bisa ditemukan oleh tes apa pun yang menguji layar atau palet,
 * dan butuh enam rilis untuk ketemu di lapangan.
 *
 * Karena itu keputusannya sendiri ditaruh di sini: tanpa Context, tanpa
 * LocationManager, tanpa Service. Yang diuji cuma "dari angka-angka ini,
 * kesimpulannya apa" - dan itu bisa dijalankan di CI tanpa perangkat.
 */
final class AutoJeda {

    private AutoJeda() {}

    /**
     * Ride harus jeda sendiri?
     *
     * @param kecepatan kecepatan terakhir, m/s
     * @param ambang    kecepatan di bawah ini dianggap berhenti, m/s
     * @param sejak     kapan kecepatan mulai di bawah ambang (elapsedRealtime),
     *                  0 kalau belum pernah di bawah ambang
     * @param tundaMs   berapa lama harus berhenti dulu sebelum jeda, milidetik
     * @param sekarang  waktu sekarang (elapsedRealtime)
     */
    static boolean harusJeda(float kecepatan, float ambang, long sejak,
                             long tundaMs, long sekarang) {
        if (sejak == 0) return false;          // belum pernah berhenti
        return kecepatan < ambang && (sekarang - sejak) >= tundaMs;
    }

    /**
     * Ride harus lanjut sendiri?
     *
     * Catatan penting: kecepatan yang dibandingkan di sini HARUS kecepatan fix
     * JEDA itu sendiri, bukan stats.currentSpeedMs. Selama jeda stats sengaja
     * tidak diperbarui (fix tidak dicatat ke jarak) dan pauseRide()
     * mengosongkannya - jadi kalau memakainya, nilainya selalu 0 dan ride tidak
     * akan pernah lanjut sendiri. Itu bug yang sama, hanya pindah tempat.
     */
    static boolean harusLanjut(float kecepatanFix, float ambang) {
        return kecepatanFix >= ambang;
    }

    /**
     * Kecepatan dari satu fix GPS, m/s. Dipakai selama JEDA.
     *
     * Kalau perangkat tidak melaporkan kecepatan, dianggap diam: lebih baik
     * ride tetap jeda sesaat daripada lanjut sendiri karena salah baca.
     */
    static float kecepatanDari(boolean punyaKecepatan, float kecepatan) {
        if (!punyaKecepatan) return 0f;
        return kecepatan > 0f ? kecepatan : 0f;
    }
}
