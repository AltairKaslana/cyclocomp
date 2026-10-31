package id.cyclocomp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rute navigasi yang diimpor pengguna, disimpan di penyimpanan aplikasi.
 *
 * Formatnya teks biasa ("ZYLONAV 1", nama, lalu "lat lon" per baris), bukan
 * GPX: berkas asli bisa ratusan ribu titik, sedangkan yang disimpan sudah
 * disaring. Teks dipilih supaya isinya bisa diperiksa dengan editor saat ada
 * laporan aneh, tanpa alat tambahan. Rute ikut terhapus kalau aplikasi
 * diuninstall - itu wajar, berkas aslinya tetap ada di tempat pengguna.
 */
final class NavStore {
    private NavStore() {}

    private static final String DIR = "nav";
    private static final String NAMA_BERKAS = "rute.txt";
    private static final String MAGIC = "ZYLONAV 1";

    static final class Info {
        public String name;
        public NavRoute route;
    }

    private static File berkas(Context c) {
        return new File(new File(c.getFilesDir(), DIR), NAMA_BERKAS);
    }

    static boolean exists(Context c) {
        return berkas(c).isFile();
    }

    /**
     * Cap perubahan berkas, supaya MainActivity hanya mem-parse ulang saat
     * rute benar-benar berubah.
     */
    static long stamp(Context c) {
        File f = berkas(c);
        return f.isFile() ? f.lastModified() * 1000L + f.length() : 0L;
    }

    /** Nama rute saja, tanpa mem-parse seluruh titik (dipakai baris Pengaturan). */
    static String name(Context c) {
        File f = berkas(c);
        if (!f.isFile()) return "";
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String magic = r.readLine();
            if (magic == null || !magic.startsWith("ZYLONAV")) return "";
            String nama = r.readLine();
            return nama == null ? "" : nama.trim();
        } catch (Throwable t) {
            return "";
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    static Info load(Context c) {
        File f = berkas(c);
        if (!f.isFile()) return null;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String magic = r.readLine();
            if (magic == null || !magic.startsWith("ZYLONAV")) return null;
            String nama = r.readLine();
            List<double[]> pts = new ArrayList<double[]>();
            String line;
            while ((line = r.readLine()) != null) {
                int sp = line.indexOf(' ');
                if (sp <= 0) continue;
                try {
                    double lat = Double.parseDouble(line.substring(0, sp));
                    double lon = Double.parseDouble(line.substring(sp + 1).trim());
                    pts.add(new double[]{lat, lon});
                } catch (NumberFormatException ignored) { }
            }
            if (pts.size() < 2) return null;
            double[][] raw = new double[2][pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                raw[0][i] = pts.get(i)[0];
                raw[1][i] = pts.get(i)[1];
            }
            NavRoute route = NavRoute.build(raw);
            if (route == null) return null;
            Info info = new Info();
            info.name = nama == null ? "" : nama.trim();
            info.route = route;
            return info;
        } catch (Throwable t) {
            return null;
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    /** Tulis lewat berkas sementara: rute lama tidak setengah tertimpa kalau gagal. */
    static boolean save(Context c, String nama, NavRoute route) {
        if (route == null || route.size() < 2) return false;
        File dir = new File(c.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) return false;
        File target = berkas(c);
        File tmp = new File(dir, NAMA_BERKAS + ".tmp");
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"));
            w.write(MAGIC);
            w.write('\n');
            w.write(satuBaris(nama));
            w.write('\n');
            for (int i = 0; i < route.size(); i++) {
                w.write(String.format(Locale.US, "%.6f %.6f\n",
                        route.latAt(i), route.lonAt(i)));
            }
            w.close();
            w = null;
            if (target.isFile() && !target.delete()) return false;
            return tmp.renameTo(target);
        } catch (Throwable t) {
            return false;
        } finally {
            if (w != null) try { w.close(); } catch (Exception ignored) { }
        }
    }

    static void clear(Context c) {
        File f = berkas(c);
        if (f.isFile()) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    /** Nama rute dari berkas yang dipilih pengguna, tanpa akhiran .gpx. */
    static String nameFromUri(Context c, Uri uri) {
        String n = null;
        Cursor cu = null;
        try {
            cu = c.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cu != null && cu.moveToFirst()) n = cu.getString(0);
        } catch (Throwable ignored) {
        } finally {
            if (cu != null) cu.close();
        }
        if (n == null || n.trim().isEmpty()) n = uri.getLastPathSegment();
        if (n == null || n.trim().isEmpty()) return "Rute";
        n = n.trim();
        int dot = n.toLowerCase(Locale.US).lastIndexOf(".gpx");
        if (dot > 0) n = n.substring(0, dot);
        return satuBaris(n);
    }

    private static String satuBaris(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
