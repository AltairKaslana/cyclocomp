package id.cyclocomp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import java.util.WeakHashMap;

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
    /**
     * Pilihan tema yang sedang berlaku di proses ini.
     *
     * Diisi saat layar memasang palet (lihat {@link #bungkus}), dan diganti
     * seketika saat pengguna menekan sakelar (lihat {@link #terapkan}).
     *
     * Kenapa ikut disimpan di memori dan tidak cukup dari SharedPreferences:
     * penulisan SharedPreferences dengan apply() berjalan di thread lain, dan
     * sebagian perangkat mengembalikan nilai LAMA sesaat sesudah itu. Kalau
     * layar baru membaca pilihan tepat di attachBaseContext dan kebetulan
     * mendapat nilai lama, layar itu terbangun dengan palet yang salah -
     * gejalanya persis sakelar tema yang "macet": pilih terang bisa, tapi balik
     * ke gelap tidak terlihat. Penanda ini selalu nilai terbaru.
     */
    private static boolean terangTerpasang;

    /**
     * Layar yang sudah pernah dipasang ulang karena paletnya tidak cocok.
     * WeakHashMap supaya tidak menahan aktivitas yang sudah ditutup.
     */
    private static final WeakHashMap<Activity, Boolean> sudahPasangUlang =
            new WeakHashMap<Activity, Boolean>();

    private Tema(){}

    /**
     * Ganti pilihan tema: tandai seketika, lalu simpan.
     *
     * Urutan sengaja begini - penanda memori diperbarui LEBIH DULU supaya layar
     * apa pun yang dibangun sesudah ini (termasuk layar hasil recreate()) pasti
     * memakai palet baru, tidak peduli apa yang masih tertulis di disk.
     */
    static void terapkan(Context c, boolean terang) {
        terangTerpasang = terang;
        Settings.setTheme(c, terang);
    }

    /**
     * Palet yang BENAR-BENAR terpasang pada konteks ini - bukan pilihan
     * tersimpan. Bedanya penting: kalau pemasangan ulang gagal, keduanya
     * berbeda, dan itu yang harus diperbaiki (lihat
     * {@link #perbaikiKalauPerlu}), bukan disembunyikan.
     */
    static boolean sedangTerang(Context c) {
        int malam = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return malam == Configuration.UI_MODE_NIGHT_NO;
    }

    /**
     * Kalau palet yang terpasang pada layar ini tidak sama dengan pilihan
     * tersimpan, bangun ulang layar SEKALI.
     *
     * Dipakai dari onStart layar utama. Dua hal yang dijaga:
     *   1. Layar yang masih hidup di tumpukan belakang (mis. layar utama saat
     *      pengguna menekan kembali dari Pengaturan) ikut palet baru.
     *   2. Kalau recreate() di sebagian perangkat tidak memasang ulang konteks,
     *      palet tetap akhirnya mengikuti pilihan pengguna.
     *
     * Sekali saja per layar (sudahPasangUlang): kalau pemasangan palet gagal
     * sama sekali, perbaikan tidak boleh jadi pengulangan tanpa akhir - satu
     * recreate per kunjungan sudah cukup, dan aplikasi tetap bisa dipakai.
     */
    static void perbaikiKalauPerlu(Activity a) {
        if (a == null || a.isFinishing()) return;
        if (sedangTerang(a) == Settings.theme(a)) return;
        if (Boolean.TRUE.equals(sudahPasangUlang.get(a))) return;
        sudahPasangUlang.put(a, Boolean.TRUE);
        a.recreate();
    }

    /** Bungkus konteks dasar sebuah aktivitas supaya warnanya sesuai pilihan. */
    static Context bungkus(Context base) {
        if (base == null) return null;
        terangTerpasang = Settings.theme(base);
        return paksa(base, terangTerpasang);
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
