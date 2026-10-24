package id.cyclocomp;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

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

    /** True bila sesi gowes sebelumnya berhasil diselamatkan saat aplikasi dibuka. */
    public static boolean recovered;

    @Override public void onCreate() {
        super.onCreate();

        // Selamatkan rekaman yang terputus (proses dibunuh / HP restart) sebelum UI jalan.
        try {
            recovered = SessionRecovery.recover(this);
        } catch (Throwable ignored) { }

        if (sistem == null) sistem = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                tulis(t, e);
                if (sistem != null) sistem.uncaughtException(t, e);
            }
        });
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
            for (StackTraceElement el : t.getStackTrace()) pw.println("    at " + el);
            t = t.getCause();
            if (t != null) pw.println("Disebabkan oleh:");
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
