package id.cyclocomp;

/**
 * Registri bidang data - daftar tunggal semua angka yang bisa ditempatkan di
 * SEKILAS, DETAIL, dan LAP.
 *
 * Sebelum ini tiap halaman punya bidang yang ditulis tetap di layoutnya:
 * mengganti satu sel berarti mengedit XML dan kode MainActivity. Dengan registri
 * ini, satu halaman hanya punya "slot" - tempat kosong yang diisi bidang mana
 * saja sesuai pilihan pengguna (layar Atur layar).
 *
 * Kelas ini sengaja hanya berisi metadata dan aturan, TANPA android.* supaya
 * bisa diuji di JVM biasa (lihat BidangTest).
 */
public final class Bidang {

    // ---------- 15 bidang ----------
    public static final int KECEPATAN = 0;    // km/jam, dari GPS
    public static final int RATA_RATA = 1;    // km/jam
    public static final int MAKS = 2;         // km/jam
    public static final int JARAK = 3;        // km
    public static final int WAKTU_GERAK = 4;  // h:mm:ss
    public static final int WAKTU_TOTAL = 5;  // h:mm:ss (termasuk jeda)
    public static final int KADENSI = 6;      // rpm, butuh sensor CSC
    public static final int DETAK = 7;        // bpm, butuh sensor detak
    public static final int DAYA = 8;         // W, butuh sensor daya
    public static final int TANJAKAN = 9;     // m kumulatif
    public static final int KEMIRINGAN = 10;  // %
    public static final int KETINGGIAN = 11;  // m dari permukaan laut
    public static final int KALORI = 12;      // kcal, PERKIRAAN
    public static final int JAM = 13;         // jam dinding
    public static final int BATERAI = 14;     // % baterai HP
    public static final int JUMLAH = 15;

    // ---------- halaman ----------
    public static final int SEKILAS = 0;
    public static final int DETAIL = 1;
    public static final int LAP = 2;
    public static final int HALAMAN_JUMLAH = 3;

    // ---------- sensor yang dibutuhkan ----------
    public static final int SENSOR_TANPA = 0;
    public static final int SENSOR_HR = 1;    // detak jantung (sudah ada)
    public static final int SENSOR_CSC = 2;   // kecepatan/kadensi roda
    public static final int SENSOR_DAYA = 3;  // daya

    private Bidang() {
    }

    public static boolean dikenal(int f) {
        return f >= 0 && f < JUMLAH;
    }

    public static boolean halamanDikenal(int h) {
        return h >= 0 && h < HALAMAN_JUMLAH;
    }

    /** Label bidang. Semua huruf kapital dengan jarak lebar, sesuai bahasa desain. */
    public static int label(int f) {
        switch (f) {
            case KECEPATAN: return R.string.label_speed;
            case RATA_RATA: return R.string.label_avg;
            case MAKS: return R.string.label_max;
            case JARAK: return R.string.label_distance;
            case WAKTU_GERAK: return R.string.label_moving;
            case WAKTU_TOTAL: return R.string.label_total;
            case KADENSI: return R.string.label_cadence;
            case DETAK: return R.string.label_hr;
            case DAYA: return R.string.label_power;
            case TANJAKAN: return R.string.label_elev;
            case KEMIRINGAN: return R.string.label_grade;
            case KETINGGIAN: return R.string.label_alt;
            case KALORI: return R.string.label_kcal;
            case JAM: return R.string.label_clock;
            case BATERAI: return R.string.label_battery;
            default: return R.string.label_kcal;
        }
    }

    /** Satuan; 0 = tidak ada satuan terpisah (nilai sudah membawanya). */
    public static int unit(int f) {
        switch (f) {
            case KECEPATAN:
            case RATA_RATA:
            case MAKS: return R.string.unit_speed;
            case JARAK: return R.string.unit_km;
            case TANJAKAN:
            case KETINGGIAN: return R.string.unit_m;
            case KEMIRINGAN: return R.string.unit_grade;
            case KALORI: return R.string.unit_kcal;
            case DETAK: return R.string.unit_bpm;
            case KADENSI: return R.string.unit_rpm;
            case DAYA: return R.string.unit_w;
            default: return 0;   // waktu, jam, baterai: satuan ikut di nilai
        }
    }

    public static int sensor(int f) {
        switch (f) {
            case DETAK: return SENSOR_HR;
            case KADENSI: return SENSOR_CSC;
            case DAYA: return SENSOR_DAYA;
            default: return SENSOR_TANPA;
        }
    }

    /** Bidang yang angkanya PERKIRAAN, bukan ukuran. */
    public static boolean perkiraan(int f) {
        return f == KALORI || f == DAYA;
    }

    /**
     * Jumlah slot per halaman: SEKILAS 3 (kartu bawah), DETAIL 10 (kisi 2x5),
     * LAP 4.
     */
    public static int batas(int halaman) {
        switch (halaman) {
            case SEKILAS: return 3;
            case LAP: return 4;
            default: return 10;
        }
    }

    /**
     * Susunan bawaan = susunan yang sudah ada sebelum Atur layar dibuat, supaya
     * pengguna lama tidak mendapati dashboard berubah sendiri.
     */
    public static int[] bawaan(int halaman) {
        switch (halaman) {
            case SEKILAS:
                return new int[]{JARAK, WAKTU_GERAK, RATA_RATA};
            case LAP:
                return new int[]{JARAK, WAKTU_TOTAL, RATA_RATA, WAKTU_GERAK};
            default:
                // urutan ini sama dengan susunan kisi yang sudah ada, supaya
                // pengguna lama tidak mendapati dashboard bertukar tempat sendiri
                return new int[]{KECEPATAN, RATA_RATA, MAKS, JARAK, WAKTU_GERAK,
                        WAKTU_TOTAL, TANJAKAN, KETINGGIAN, KALORI, DETAK};
        }
    }

    /**
     * Susunan sah? Panjang harus pas sebanyak slot halaman itu dan tiap isinya
     * bidang yang dikenal. Dipakai sebelum menyimpan dan saat membaca dari
     * SharedPreferences, supaya nilai rusak (atau dari versi lain) tidak bisa
     * membuat dashboard kosong.
     */
    public static boolean sah(int halaman, int[] urutan) {
        if (urutan == null || urutan.length != batas(halaman)) return false;
        for (int f : urutan) {
            if (!dikenal(f)) return false;
        }
        return true;
    }
}
