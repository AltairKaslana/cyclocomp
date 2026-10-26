package id.cyclocomp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Tempat menyimpan berkas GPX ride.
 *
 * Kenapa ada kelas ini: dulu GPX ditulis ke folder milik aplikasi sendiri
 * (Android/data/id.cyclocomp/files/rides). Folder itu DIHAPUS OTOMATIS oleh
 * Android saat aplikasi diuninstall - jadi pengguna yang update lewat
 * uninstall-instal kehilangan semua rekaman. Sekarang berkas ride disimpan di
 * penyimpanan bersama supaya bertahan lama:
 *
 *  - Android 10+ (API 29): lewat MediaStore, tanpa perlu izin apa pun.
 *    Berkas mendarat di Download/Zylo dan bisa dilihat dari file manager.
 *  - Android 9 ke bawah: folder publik Documents/Zylo, butuh izin
 *    WRITE_EXTERNAL_STORAGE; kalau izin belum diberikan, jatuh kembali ke
 *    folder aplikasi (perilaku lama) supaya rekaman tetap jalan.
 *
 * "Locator" adalah string yang menunjuk satu berkas: path absolut biasa
 * (/sdcard/...) untuk jalur lama, atau "content://..." untuk MediaStore.
 * Semua pembacaan (GpxReader, unggah Strava, kartu gambar) lewat
 * {@link #materialize} yang menyalin content:// ke cache dulu, jadi kode lain
 * tetap bekerja dengan File seperti sebelumnya.
 */
public final class GpxStore {
    private GpxStore() {}

    /** Nama subfolder publik; sama di semua jalur supaya mudah dicari pengguna. */
    public static final String PUBLIC_DIR = "Zylo";

    private static boolean isMedia(String loc) {
        return loc != null && loc.startsWith("content://");
    }

    /**
     * Buat lokasi tulis baru untuk satu rekaman.
     *
     * @param fileName nama berkas, mis. "ride_20261024_185751.gpx"
     * @return locator yang bisa dipakai {@link #openWrite} dan disimpan ke DB
     */
    public static String create(Context c, String fileName) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentResolver cr = c.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIR);
                // IS_PENDING: berkas "sedang ditulis", belum terlihat penuh oleh
                // aplikasi lain sampai finish() dipanggil.
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u != null) return u.toString();
            } catch (Throwable ignored) { }
        }

        // API 23-28: folder publik kalau izin ada, kalau tidak folder aplikasi.
        try {
            if (Build.VERSION.SDK_INT < 29
                    && c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOCUMENTS), PUBLIC_DIR);
                if (dir.exists() || dir.mkdirs()) {
                    return new File(dir, fileName).getAbsolutePath();
                }
            }
        } catch (Throwable ignored) { }

        File dir = new File(c.getExternalFilesDir(null), "rides");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return new File(dir, fileName).getAbsolutePath();
    }

    /**
     * Buka stream untuk menulis.
     *
     * @param append true = tambah di akhir (dipakai memulihkan rekaman terputus)
     */
    public static OutputStream openWrite(Context c, String loc, boolean append)
            throws java.io.IOException {
        if (isMedia(loc)) {
            OutputStream os = c.getContentResolver().openOutputStream(
                    Uri.parse(loc), append ? "wa" : "w");
            if (os == null) throw new java.io.IOException("stream null");
            return os;
        }
        return new FileOutputStream(new File(loc), append);
    }

    /**
     * Ubah locator menjadi File yang bisa dibaca. Untuk content://, berkas
     * disalin dulu ke cache (dipakai sekali lalu dihapus saat proses mati).
     *
     * @return File, atau null kalau berkas tidak bisa dibaca
     */
    public static File materialize(Context c, String loc) {
        if (loc == null) return null;
        if (!isMedia(loc)) return new File(loc);
        try {
            File cache = new File(c.getCacheDir(), "gpx-read");
            if (!cache.exists()) //noinspection ResultOfMethodCallIgnored
                cache.mkdirs();
            File out = new File(cache, displayName(loc));
            InputStream in = c.getContentResolver().openInputStream(Uri.parse(loc));
            if (in == null) return null;
            OutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            os.close();
            out.deleteOnExit();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Tulis ulang seluruh isi berkas (dipakai menambahkan footer GPX). */
    public static void writeAll(Context c, String loc, String isi)
            throws java.io.IOException {
        Writer w = new OutputStreamWriter(openWrite(c, loc, false), "UTF-8");
        w.write(isi);
        w.close();
    }

    /** Baca seluruh isi berkas sebagai teks. */
    public static String readAll(Context c, String loc) throws java.io.IOException {
        InputStream in;
        if (isMedia(loc)) {
            in = c.getContentResolver().openInputStream(Uri.parse(loc));
            if (in == null) throw new java.io.IOException("stream null");
        } else {
            in = new FileInputStream(new File(loc));
        }
        try {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            return sb.toString();
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
    }

    public static boolean exists(Context c, String loc) {
        if (loc == null) return false;
        try {
            if (isMedia(loc)) {
                Cursor cu = c.getContentResolver().query(Uri.parse(loc),
                        new String[]{MediaStore.MediaColumns.SIZE}, null, null, null);
                if (cu != null) {
                    boolean ada = cu.moveToFirst();
                    cu.close();
                    return ada;
                }
                return false;
            }
            return new File(loc).exists();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean delete(Context c, String loc) {
        if (loc == null) return false;
        try {
            if (isMedia(loc)) {
                return c.getContentResolver().delete(Uri.parse(loc), null, null) > 0;
            }
            return new File(loc).delete();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Tandai berkas MediaStore selesai ditulis. Tanpa ini, berkas tetap
     * "pending" dan aplikasi lain mungkin tidak melihatnya. Untuk path biasa,
     * pemanggilan ini tidak melakukan apa pun.
     */
    public static void finish(Context c, String loc) {
        if (!isMedia(loc)) return;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(Uri.parse(loc), v, null, null);
        } catch (Throwable ignored) { }
    }

    /** Nama berkas untuk tampilan, dari path maupun content://. */
    public static String displayName(String loc) {
        if (loc == null) return "ride.gpx";
        if (!isMedia(loc)) return new File(loc).getName();
        Uri u = Uri.parse(loc);
        String seg = u.getLastPathSegment();
        return seg == null ? "ride.gpx" : seg;
    }

    /**
     * Pindahkan berkas dari folder aplikasi lama ke lokasi publik.
     *
     * Dipakai sekali saat versi baru pertama kali dibuka, supaya rekaman lama
     * pengguna tidak ikut hilang kalau aplikasi diuninstall kemudian.
     *
     * @return locator baru, atau locator lama kalau gagal/berkas tidak ada
     */
    public static String migrate(Context c, String loc) {
        if (loc == null || isMedia(loc)) return loc;
        File old = new File(loc);
        if (!old.exists()) return loc;
        try {
            String baru = create(c, old.getName());
            OutputStream os = openWrite(c, baru, false);
            InputStream in = new FileInputStream(old);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            os.close();
            finish(c, baru);
            //noinspection ResultOfMethodCallIgnored
            old.delete();
            return baru;
        } catch (Throwable t) {
            return loc;
        }
    }

    /**
     * Uri yang aman dibagikan ke aplikasi lain ( WhatsApp, galeri, email).
     *
     * - content:// MediaStore: dipakai langsung, aplikasi pemilik berkas boleh
     *   memberi izin baca sementara.
     * - path biasa: lewat RideProvider (content://id.cyclocomp.files/<nama>),
     *   tetap seperti sebelumnya.
     */
    public static Uri shareUri(Context c, String loc) {
        if (isMedia(loc)) return Uri.parse(loc);
        return Uri.parse("content://" + RideProvider.AUTHORITY + "/" + displayName(loc));
    }
}
