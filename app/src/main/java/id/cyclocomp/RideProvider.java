package id.cyclocomp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Provider kecil untuk membagikan berkas GPX.
 *
 * Android 7+ melarang mengirim "file://" ke aplikasi lain, jadi harus lewat
 * content:// dengan izin baca sementara. FileProvider dari androidx bisa dipakai,
 * tetapi proyek ini tanpa dependency, dan kebutuhan kita sempit: hanya berkas di
 * folder rides milik aplikasi sendiri.
 *
 * Keamanan: nama berkas divalidasi (hanya huruf/angka/garis bawah/titik, tanpa ".."),
 * dan hanya berkas di dalam folder rides yang bisa dibuka.
 */
public class RideProvider extends ContentProvider {
    static final String AUTHORITY = "id.cyclocomp.files";

    @Override public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        // ContentProvider bukan Context: string lewat getContext()
        if (name == null || name.isEmpty()) {
            throw new FileNotFoundException(getContext().getString(R.string.prov_empty_name));
        }
        if (!name.matches("[A-Za-z0-9_.-]+") || name.contains("..")) {
            throw new FileNotFoundException(getContext().getString(R.string.prov_bad_name));
        }
        Context c = getContext();
        if (c == null) {
            throw new FileNotFoundException(getContext().getString(R.string.prov_no_storage));
        }

        // Urutan pencarian: folder kartu milik aplikasi dulu (PNG kartu selalu
        // ditulis ke sana), lalu tempat GPX bisa berada. Nama sudah divalidasi
        // (tanpa ".."), jadi mencoba beberapa root tetap aman.
        File[] roots = new File[]{
                new File(c.getExternalFilesDir(null), "rides"),   // kartu PNG + GPX lama
                new File(c.getCacheDir(), "gpx-read"),            // salinan baca content://
                new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS), "Zylo"),
                new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "Zylo"),
        };
        for (File base : roots) {
            if (base == null) continue;
            File f = new File(base, name);
            if (f.exists()) return f;
        }
        throw new FileNotFoundException(name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        try {
            File f = resolve(uri);
            String[] cols = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor c = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
                else row[i] = null;
            }
            c.addRow(row);
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * Jenis berkas mengikuti ekstensi: PNG untuk kartu gambar, GPX untuk rute.
     *
     * Aplikasi penerima (termasuk galeri dan Strava) memperlakukan berkas sesuai MIME
     * ini, jadi kalau PNG salah dilaporkan sebagai GPX berkasnya akan ditolak.
     */
    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.toLowerCase(java.util.Locale.US).endsWith(".png")) {
            return "image/png";
        }
        return "application/gpx+xml";
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
