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
 * KENAPA PALET JUGA DIPERIKSA ULANG DI onStart
 * Menekan sakelar tema saja tidak cukup. Aktivitas yang masih hidup di tumpukan
 * belakang (mis. layar utama saat pengguna menekan kembali dari Pengaturan)
 * tidak ikut berganti, dan di sebagian perangkat recreate() tidak memasang
 * ulang konteks seperti yang diharapkan. Gejalanya di lapangan halus dan
 * membingungkan: sakelar tema terasa MACET - pilih siang bisa, balik ke gelap
 * tidak kelihatan. Karena itu pilihan pengguna selalu yang menang: kalau palet
 * yang terpasang berbeda dengan pilihan tersimpan, layar dibangun ulang. Lihat
 * {@link #perbaikiKalauPerlu}.
 *
 * KALAU MEKANISME INI GAGAL
 * Semua dibungkus try/catch dan gagal dengan LEMBUT: aplikasi tetap terbuka
 * dengan palet bawaan perangkat. Tema yang tidak berubah jauh lebih baik
 * daripada aplikasi yang tidak mau terbuka sama sekali.
 */
final class Tema {
    /**
     * Pilihan tema yang diubah di proses ini, atau null kalau belum ada yang
     * mengubah.
     *
     * Kenapa ikut disimpan di memori dan tidak cukup dari SharedPreferences:
     * penulisan preferensi bisa berjalan di thread lain, dan sebagian perangkat
     * masih mengembalikan nilai LAMA sesaat sesudahnya. Kalau layar baru
     * membaca pilihan tepat di attachBaseContext dan kebetulan mendapat nilai
     * lama, layar itu terbangun dengan palet yang salah. Penanda ini dibuat
     * seketika saat sakelar ditekan, jadi tidak mungkin tertinggal.
     */
    private static Boolean pilihanProses;

    private Tema(){}

    /**
     * Ganti pilihan tema: tandai seketika, lalu simpan.
     *
     * Urutan sengaja begini - penanda memori diperbarui LEBIH DULU supaya layar
     * apa pun yang dibangun sesudah ini (termasuk layar hasil recreate()) pasti
     * memakai palet baru, tidak peduli apa yang masih tertulis di disk.
     */
    static void terapkan(Context c, boolean terang) {
        pilihanProses = Boolean.valueOf(terang);
        Settings.setTheme(c, terang);
    }

    /** Palet yang BENAR-BENAR terpasang pada konteks ini - bukan pilihan tersimpan. */
    static boolean sedangTerang(Context c) {
        return terangDariPenanda(c.getResources().getConfiguration().uiMode);
    }

    /**
     * Penanda malam pada Configuration dikodekan di bit 4-5. Yang dipakai hanya
     * dua bit itu; bit lain (tipe layar, kerapatan) tidak boleh mengubah
     * hasilnya. Dipisah dari {@link #sedangTerang} supaya bisa diuji tanpa
     * perangkat.
     */
    static boolean terangDariPenanda(int uiMode) {
        return (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
    }

    /**
     * Keputusan murni: palet yang terpasang vs pilihan tersimpan. Berbeda =
     * layar harus dibangun ulang. Sengaja diekspresikan sebagai "tidak sama",
     * bukan "kalau gelap paksa gelap": yang diperbaiki adalah KETIDAKSESUAIAN,
     * jadi tidak ada arah yang diistimewakan - macet ke siang dan macet ke
     * gelap sama-sama tertangkap.
     */
    static boolean perluPasangUlang(boolean terpasangTerang, boolean pilihanTerang) {
        return terpasangTerang != pilihanTerang;
    }

    /**
     * Kalau palet yang terpasang pada layar ini tidak sama dengan pilihan
     * tersimpan, bangun ulang layar SEKALI.
     *
     * Dipakai dari onStart. Dua hal yang dijaga:
     *   1. Layar yang masih hidup di tumpukan belakang ikut palet baru.
     *   2. Kalau recreate() di sebagian perangkat tidak memasang ulang konteks,
     *      palet tetap akhirnya mengikuti pilihan pengguna.
     *
     * BATAS JUMLAH, BUKAN SEKALI PER LAYAR. Percobaan pertama memakai kunci
     * berupa objek aktivitas, dan itu tidak menahan apa pun: recreate()
     * menghasilkan aktivitas BARU, jadi kuncinya baru dan "sekali" tidak lagi
     * sekali. Kalau palet sama sekali tidak bisa dipasang, hasilnya bukan
     * perbaikan tapi layar yang berkedip tanpa berhenti. Karena itu
     * dibatasi per PROSES: paling banyak dua kali per palet yang berubah -
     * cukup untuk memperbaiki layar di tumpukan belakang, dan tidak mungkin
     * jadi pengulangan tanpa akhir.
     */
    static void perbaikiKalauPerlu(Activity a) {
        if (a == null || a.isFinishing()) return;
        if (!perluPasangUlang(sedangTerang(a), Settings.theme(a))) return;
        if (!bolehPasangUlag(pasangUlangProses)) return;
        pasangUlangProses++;
        a.recreate();
    }

    /**
     * Keputusan murni: bolehkah memasang ulang lagi, kalau sudah sebanyak ini.
     * Dipisah dari {@link #perbaikiKalauPerlu} supaya batasnya bisa diuji
     * tanpa perangkat.
     */
    static boolean bolehPasangUlag(int sudahBerapa) {
        return sudahBerapa < MAKS_PASANG_ULANG;
    }

    /** Batas keras pemasangan ulang otomatis per proses. Lihat di atas. */
    static final int MAKS_PASANG_ULANG = 2;

    private static int pasangUlangProses;

    /** Bungkus konteks dasar sebuah aktivitas supaya warnanya sesuai pilihan. */
    static Context bungkus(Context base) {
        if (base == null) return null;
        boolean terang = pilihanProses != null ? pilihanProses.booleanValue() : Settings.theme(base);
        return paksa(base, terang);
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

    /**
     * Bit malam yang benar untuk sebuah nilai uiMode. Dipisah supaya bisa
     * diuji tanpa perangkat: yang diuji di sini cuma aritmetika bit, dan itu
     * tepat yang menentukan palet mana yang dipakai.
     *
     * Hanya dua bit UI_MODE_NIGHT_* yang disentuh; bit lain (tipe layar,
     * kerapatan) dibiarkan apa adanya.
     */
    static int bitMalam(int uiMode, boolean terang) {
        return (uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (terang ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
    }

    /** Balikkan konteks yang resource-nya sudah terpaksa gelap/terang. */
    static Context paksa(Context base, boolean terang) {
        if (base == null) return null;
        try {
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.uiMode = bitMalam(cfg.uiMode, terang);
            Context c = base.createConfigurationContext(cfg);

            // JALUR CADANGAN - dan ini yang penting.
            //
            // createConfigurationContext() punya bug di framework: override
            // yang dipasang masuk ke konfigurasi KANONIK proses
            // (ActivityThread.mCurrentConfig), bukan ke konfigurasi dasar
            // aktivitas. Efek sampingnya, ResourcesImpl yang sudah dipakai
            // sebelumnya bisa ikut ditimpa konfigurasi proses - sehingga
            // meminta palet gelap SESUDAH sempat memakai palet terang
            // mengembalikan Resources yang tetap memakai berkas values/
            // (terang). Gejalanya di lapangan: sakelar tema "macet", hanya
            // satu arah yang tidak bereaksi.
            //
            // updateConfiguration() memaksa konfigurasi itu benar-benar
            // dipasang pada Resources yang AKAN dipakai layar ini, apa pun isi
            // cache-nya. Ini jalur yang sama yang dipakai AppCompat sebagai
            // cadangan (lihat AppCompatDelegateImpl.updateResourcesConfiguration).
            // Bersifat best-effort: kalau gagal, palet tetap apa adanya -
            // aplikasi tidak boleh mati karena urusan warna.
            android.content.res.Resources res = c.getResources();
            res.updateConfiguration(cfg, null);

            // LAPIS KEDUA: Resources milik PROSES.
            //
            // Di beberapa perangkat Resources yang dipakai layar BUKAN yang
            // dikembalikan konteks di atas, melainkan milik proses - sehingga
            // memaksa konteksnya saja tidak mengubah apa pun. Aplikasi ini
            // cuma punya SATU palet untuk seluruh layar, jadi memaksa konteks
            // proses bukan efek samping yang meruginkan: justru yang
            // diinginkan. Kegagalan di sini diabaikan diam-diam.
            try {
                Context app = base.getApplicationContext();
                if (app != null && app != base) {
                    app.getResources().updateConfiguration(cfg, null);
                }
            } catch (Throwable diabaikan) {
                // Sengaja kosong: lapis ini cadangan, bukan syarat.
            }
            return c;
        } catch (Throwable t) {
            // Lihat catatan kelas: gagal lembut, jangan sampai aplikasi mati.
            return base;
        }
    }

    /**
     * Kalimat ringkas berisi apa yang BENAR-BENAR terpasang pada sebuah layar -
     * bukan pilihan tersimpan.
     *
     * Dipakai untuk mencari sebab masalah lapangan. Kalau palet tidak berganti
     * padahal pilihan sudah benar, kalimat ini menunjukkan tepat di lapisan
     * mana putusnya:
     *   - pilih dan pasang BERBEDA = pilihan tersimpan tidak terbaca saat
     *     layar dibangun;
     *   - pasang dan bg BERBEDA = layar tidak memakai Resources yang dipaksa;
     *   - keduanya sama tapi layar tetap salah = warnanya bukan dari berkas
     *     warna, dan pencariannya pindah ke layout/drawable.
     */
    static String diagnostik(Context c) {
        if (c == null) return "layar=null";
        StringBuilder sb = new StringBuilder("TEMA pilih=");
        sb.append(Settings.theme(c) ? "SIANG" : "GELAP");
        sb.append(" pasang=").append(sedangTerang(c) ? "SIANG" : "GELAP");
        sb.append(" uiMode=0x").append(Integer.toHexString(c.getResources().getConfiguration().uiMode));
        try {
            sb.append(" bg=#").append(Integer.toHexString(c.getResources().getColor(R.color.bg) & 0xFFFFFF));
        } catch (Throwable diabaikan) {
            // Sengaja kosong: kalimat diagnostik tidak boleh bikin crash.
        }
        return sb.toString();
    }

}
