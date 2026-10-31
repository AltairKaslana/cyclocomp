package id.cyclocomp;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Penangkap crash. Perangkat pengujian tidak punya adb, jadi satu-satunya cara cepat
 * tahu penyebab aplikasi mati adalah aplikasi menulis sendiri penyebabnya.
 *
 * File: Android/data/id.cyclocomp/files/crash.txt (bisa diambil lewat file manager
 * atau kabel USB ke PC). Ditimpa tiap crash baru.
 */
public class App extends Application {
    private static Thread.UncaughtExceptionHandler sistem;

    /**
     * Konteks aplikasi, untuk pesan dari thread pekerja.
     *
     * Ini BUKAN kebocoran: konteks aplikasi hidup selama prosesnya, jadi
     * memegangnya tidak menahan layar apa pun.
     */
    private static Context aplikasi;

    /**
     * True bila sesi gowes sebelumnya berhasil diselamatkan.
     *
     * volatile: ditulis dari thread pekerja (kerjaLambat), dibaca dari thread
     * utama (MainActivity). Tanpa volatile, MainActivity bisa membaca nilai
     * lama - false - padahal sesi sudah diselamatkan, dan pemberitahuannya
     * tidak muncul.
     */
    public static volatile boolean recovered;

    @Override public void onCreate() {
        super.onCreate();
        aplikasi = this;

        // Penangkap crash dipasang lebih dulu: kalau ada yang gagal di bawah
        // ini, penyebabnya harus tetap tercatat.
        if (sistem == null) sistem = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                tulis(t, e);
                if (sistem != null) sistem.uncaughtException(t, e);
            }
        });

        // Pekerjaan berat JANGAN di sini.
        //
        // onCreate() aplikasi berjalan di thread utama, SEBELUM layar pertama
        // muncul - jadi apa pun yang dilakukan di sini langsung menambah cold
        // start. Dulu di sini ada SessionRecovery.recover() (buka DB, baca,
        // tulis) dan migrateGpx() (baca SEMUA baris riwayat, pindahkan berkas).
        // Untuk pengguna dengan riwayat panjang, keduanya bisa makan ratusan
        // milidetik - melewati anggaran cold start 1,5 detik.
        //
        // Sekarang keduanya jalan di thread terpisah. Aplikasi bisa langsung
        // tampil; kalau ada rekaman yang perlu diselamatkan, pemberitahuannya
        // muncul sedikit kemudian - dan itu jauh lebih baik daripada aplikasi
        // yang terasa berat setiap kali dibuka.
        new Thread(new Runnable() {
            @Override public void run() {
                kerjaLambat();
            }
        }, "zylo-startup").start();
    }

    /**
     * Pekerjaan sekali-jalan yang tidak boleh menghambat layar pertama.
     *
     * Urutannya sengaja: pulihkan sesi DULU, baru migrasi. Kalau migrasi
     * jalan lebih dulu dan aplikasi mati di tengah, penanda sesi yang belum
     * dipulihkan tetap ada dan tetap bisa diselamatkan nanti.
     */
    private void kerjaLambat() {
        try {
            recovered = SessionRecovery.recover(this);
            if (recovered) beritahuPemulihan();
        } catch (Throwable diabaikan) {
            // Perekam yang terputus tidak boleh membuat aplikasi gagal mulai.
        }
        try {
            migrateGpx();
        } catch (Throwable diabaikan) {
            // Lihat catatan migrateGpx(): kegagalan di sini tidak fatal.
        }
    }

    /**
     * Kabari pengguna bahwa rekaman gowes sebelumnya berhasil diselamatkan.
     * Dipanggil dari thread pekerja, jadi harus lewat handler thread utama -
     * Toast tidak bisa dibuat dari thread lain.
     */
    private void beritahuPemulihan() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                Context c = aplikasi;
                if (c == null) return;
                try {
                    Toast.makeText(c, R.string.recovered_msg, Toast.LENGTH_LONG).show();
                } catch (Throwable diabaikan) { /* aplikasi sedang ditutup */ }
            }
        });
    }

    /**
     * Pindahkan berkas GPX dari folder aplikasi (Android/data/id.cyclocomp/files/rides)
     * ke penyimpanan bersama, supaya rekaman tidak hilang kalau aplikasi
     * diuninstall. Dijalankan sekali, ditandai di SharedPreferences.
     *
     * Kegagalan di sini tidak fatal: kalau migrasi gagal, DB tetap menunjuk
     * lokasi lama dan rekaman tetap bisa dibaca selama berkasnya ada.
     */
    private void migrateGpx() {
        android.content.SharedPreferences sp =
                getSharedPreferences("gpxmig", MODE_PRIVATE);
        if (sp.getBoolean("done", false)) return;

        RideDb db = new RideDb(this);
        try {
            for (RideDb.Row r : db.list()) {
                if (r.gpxPath == null || r.gpxPath.startsWith("content://")) continue;
                String baru = GpxStore.migrate(this, r.gpxPath);
                if (!baru.equals(r.gpxPath)) db.updateGpxPath(r.id, baru);
            }
        } finally {
            db.close();
        }
        sp.edit().putBoolean("done", true).apply();
    }

    private void tulis(Thread t, Throwable e) {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            if (dir == null) return;

            StringBuilder sb = new StringBuilder();
            sb.append("waktu   : ").append(now()).append('\n');
            sb.append("versi   : ").append(versi()).append('\n');
            sb.append("perangkat: ").append(Build.MANUFACTURER).append(' ')
              .append(Build.MODEL).append("  Android ").append(Build.VERSION.RELEASE)
              .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("thread  : ").append(t.getName()).append('\n');
            // Keadaan tema ikut dilaporkan: tema pernah bermasalah, dan kalau
            // aplikasi mati sesudah pemilihan tema berubah, palet yang terpasang
            // adalah petunjuk pertama yang dibutuhkan - bukan sesuatu yang bisa
            // dilihat dari luar.
            try {
                sb.append("tema    : ").append(Tema.diagnostik(this)).append('\n');
            } catch (Throwable diabaikan) { /* laporan tetap jalan tanpa ini */ }
            sb.append('\n').append(stack(e)).append('\n');

            File f = new File(dir, "crash.txt");
            FileWriter w = new FileWriter(f, false);
            w.write(sb.toString());
            w.close();
        } catch (Throwable ignored) { }
    }

    private String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Throwable t = e;
        while (t != null) {
            pw.println(t.toString());
            for (StackTraceElement el : t.getStackTrace()) {
                pw.println(getString(R.string.crash_at) + el);
            }
            t = t.getCause();
            if (t != null) pw.println(getString(R.string.crash_cause));
        }
        pw.flush();
        return sw.toString();
    }

    private String versi() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    /** Path file crash, untuk ditampilkan di dialog kalau perlu. */
    public static String crashPath(android.content.Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        return dir == null ? null : new File(dir, "crash.txt").getAbsolutePath();
    }

    @SuppressWarnings("unused")
    private static final String TAG = "App";
}
