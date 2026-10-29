package id.cyclocomp;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Pemilih tema aplikasi: gelap (bawaan) atau siang.
 *
 * KENAPA LEWAT PENANDA KONFIGURASI, BUKAN DUA TEMA
 * Seluruh layout dan drawable menunjuk token warna secara langsung -
 * {@code @color/accent}, {@code @color/card}, dan seterusnya. Memakai dua tema
 * induk saja TIDAK mengubah token itu: hanya atribut tema ({@code ?attr/...})
 * yang ikut berubah, sedangkan {@code @color/...} tetap menunjuk berkas yang
 * sama. Jadi satu-satunya cara membuat seluruh layar ikut berganti tanpa
 * menulis ulang setiap layout adalah menukar BERKAS warna yang dipakai, dan itu
 * hanya bisa dilakukan lewat penanda konfigurasi resource ({@code -night}).
 *
 * Konsekuensinya palet gelap pindah ke {@code res/values-night/colors.xml} dan
 * palet siang menjadi berkas bawaan di {@code res/values/colors.xml}.
 *
 * KENAPA DIPAKSA PER AKTIVITAS, BUKAN MENGIKUTI PERANGKAT
 * Kalau hanya mengandalkan {@code -night} apa adanya, tema aplikasi akan
 * berganti mengikuti mode gelap PERANGKAT - bukan mengikuti pilihan pengguna di
 * layar Pengaturan. Dua orang dengan pilihan yang sama bisa mendapat tampilan
 * yang berbeda. Jadi setiap aktivitas membungkus konteks dasarnya supaya
 * penanda malam-nya DIPAKSA, apa pun yang diatur perangkat.
 *
 * KALAU MEKANISME INI GAGAL
 * Semua dibungkus try/catch dan gagal dengan LEMBUT: aplikasi tetap terbuka
 * dengan palet bawaan perangkat. Tema yang tidak berubah jauh lebih baik
 * daripada aplikasi yang tidak mau terbuka sama sekali. Konsekuensinya kalau
 * mekanismenya tidak jalan di perangkat tertentu, sakelar "Tema terang" terasa
 * tidak melakukan apa-apa - dan itu memang yang terjadi, bukan aplikasi yang
 * rusak.
 */
final class Tema {
    private Tema() {}

    /** Bungkus konteks dasar sebuah aktivitas supaya warnanya sesuai pilihan. */
    static Context bungkus(Context base) {
        return paksa(base, Settings.theme(base));
    }

    /**
     * Konteks yang SELALU memakai palet gelap, apa pun pilihan pengguna.
     *
     * Dipakai {@link ShareCard}: kartu bagikan digambar ke Bitmap di luar
     * hierarki view, jadi warnanya diambil lewat {@code getColor()}. Kartu itu
     * adalah PRODUK yang dikirim ke orang lain, bukan layar aplikasi - kalau
     * ikut tema siang, orang yang menerima kartu akan melihat kartu berwarna
     * terang sementara aplikasinya gelap, dan kartu versi terang belum pernah
     * diperiksa kontrasnya.
     */
    static Context gelap(Context base) {
        return paksa(base, false);
    }

    private static Context paksa(Context base, boolean terang) {
        try {
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            int malam = terang ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | malam;
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) {
            // Lihat catatan kelas: gagal lembut, jangan sampai aplikasi mati.
            return base;
        }
    }
}
