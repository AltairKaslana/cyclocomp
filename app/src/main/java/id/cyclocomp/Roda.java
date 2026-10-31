package id.cyclocomp;

/**
 * Ukuran roda untuk sensor kecepatan/kadensi.
 *
 * Sensor CSC hanya mengirim jumlah putaran roda, bukan kecepatan. Kecepatan
 * baru bisa dihitung kalau kita tahu keliling roda. Angka di sini adalah
 * keliling dalam milimeter, bukan diameter: sensor menghitung putaran penuh,
 * jadi yang dibutuhkan justru panjang satu putaran.
 *
 * Kelas ini bebas android.* supaya bisa diuji di JVM (lihat RodaTest).
 */
public final class Roda {

    private Roda() {
    }

    /** Ukuran yang paling umum dipakai; urutannya sama dengan chip di layar Sensor. */
    public static final String[] PRESET_LABEL = {
            "700×23C", "700×25C", "700×28C", "700×32C"
    };

    public static final int[] PRESET_MM = {2096, 2105, 2136, 2155};

    /**
     * Bawaan 700×25C (2105 mm): ukuran roda jalan raya yang paling banyak
     * dipakai, dan angka tengah dari daftar preset. Pengguna yang rodanya lain
     * bisa menggantinya tanpa menghalangi pemakaian pertama.
     */
    public static final int BAWAAN_MM = 2105;

    /** Batas wajar keliling roda sepeda; di luar ini hampir pasti salah ketik. */
    public static final int MIN_MM = 1000;
    public static final int MAX_MM = 3000;

    /** Jaga nilai dari salah ketik atau data lama yang rusak. */
    public static int normalisasi(int mm) {
        if (mm < MIN_MM) return MIN_MM;
        if (mm > MAX_MM) return MAX_MM;
        return mm;
    }

    /** Keliling dalam meter, satuan yang dipakai perhitungan kecepatan. */
    public static float meter(int mm) {
        return normalisasi(mm) / 1000f;
    }

    /** Nama preset untuk satu nilai, atau null kalau bukan salah satu preset. */
    public static String label(int mm) {
        for (int i = 0; i < PRESET_MM.length; i++) {
            if (PRESET_MM[i] == mm) return PRESET_LABEL[i];
        }
        return null;
    }
}
