package id.cyclocomp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Ringkasan tiap rekaman. Titik detailnya ada di file GPX. */
public class RideDb extends SQLiteOpenHelper {
    private static final String NAME = "cyclocomp.db";
    private static final int VERSION = 4;

    public static class Row {
        public long id, startMs, durationMs, movingMs;
        public double distanceM, maxSpeedMs, elevGainM;
        public String gpxPath;
        public String laps;      // "jarak;gerak;total|jarak;gerak;total|..."

        // ---- v3 ----
        /** Perkiraan kalori terbakar (kcal); 0 = belum dihitung (rekaman lama). */
        public int caloriesKcal;
        /** Perceived exertion 1..10 (skala RPE); 0 = belum dinilai. */
        public int rpe;
        /** true kalau perjalanan ini komuter/pulang-pergi. */
        public boolean commute;

        // ---- v4 ----
        /** Rute ringkas "lat,lon;lat,lon;..." untuk thumbnail; null = belum ada. */
        public String preview;
        /** Sepeda yang dipakai; 0 = belum dikaitkan (fitur Garasi, v1.2). */
        public long bikeId;

        public float avgSpeedMs() {
            return movingMs > 0 ? (float) (distanceM / (movingMs / 1000.0)) : 0f;
        }
    }

    public RideDb(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE rides ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "start_ms INTEGER, duration_ms INTEGER, moving_ms INTEGER,"
                + "distance_m REAL, max_speed REAL, elev_gain REAL, gpx_path TEXT,"
                + "laps TEXT, calories_kcal INTEGER, rpe INTEGER, commute INTEGER,"
                + "preview TEXT, bike_id INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            // v2: kolom laps. ALTER tidak bisa menambah NOT NULL, dan TEXT biasa cukup.
            try {
                db.execSQL("ALTER TABLE rides ADD COLUMN laps TEXT");
            } catch (Exception ignored) { }   // sudah ada (mis. downgrade-upgrade)
        }
        if (oldV < 3) {
            // v3: kalori, RPE, penanda komuter. Tiap kolom dipisah supaya satu
            // kegagalan tidak membatalkan kolom lain.
            addColumn(db, "calories_kcal INTEGER");
            addColumn(db, "rpe INTEGER");
            addColumn(db, "commute INTEGER");
        }
        if (oldV < 4) {
            // v4: pratinjau rute untuk thumbnail Riwayat dan kaitan sepeda
            // (dipakai Garasi di rilis berikutnya). Rekaman lama tetap null;
            // Riwayat mengisinya sekali dari berkas GPX di latar belakang.
            addColumn(db, "preview TEXT");
            addColumn(db, "bike_id INTEGER");
        }
    }

    private static void addColumn(SQLiteDatabase db, String ddl) {
        try {
            db.execSQL("ALTER TABLE rides ADD COLUMN " + ddl);
        } catch (Exception ignored) { }   // sudah ada
    }

    public long insert(Row r) {
        ContentValues v = new ContentValues();
        v.put("start_ms", r.startMs);
        v.put("duration_ms", r.durationMs);
        v.put("moving_ms", r.movingMs);
        v.put("distance_m", r.distanceM);
        v.put("max_speed", r.maxSpeedMs);
        v.put("elev_gain", r.elevGainM);
        v.put("gpx_path", r.gpxPath);
        v.put("laps", r.laps);
        v.put("calories_kcal", r.caloriesKcal);
        v.put("rpe", r.rpe);
        v.put("commute", r.commute ? 1 : 0);
        v.put("preview", r.preview);
        if (r.bikeId > 0) v.put("bike_id", r.bikeId);
        else v.putNull("bike_id");
        return getWritableDatabase().insert("rides", null, v);
    }

    public List<Row> list() {
        List<Row> out = new ArrayList<Row>();
        Cursor c = getReadableDatabase().query("rides", null, null, null, null, null, "start_ms DESC");
        try {
            while (c.moveToNext()) {
                Row r = new Row();
                r.id = c.getLong(c.getColumnIndexOrThrow("id"));
                r.startMs = c.getLong(c.getColumnIndexOrThrow("start_ms"));
                r.durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms"));
                r.movingMs = c.getLong(c.getColumnIndexOrThrow("moving_ms"));
                r.distanceM = c.getDouble(c.getColumnIndexOrThrow("distance_m"));
                r.maxSpeedMs = c.getDouble(c.getColumnIndexOrThrow("max_speed"));
                r.elevGainM = c.getDouble(c.getColumnIndexOrThrow("elev_gain"));
                r.gpxPath = c.getString(c.getColumnIndexOrThrow("gpx_path"));
                int li = c.getColumnIndex("laps");
                r.laps = li >= 0 && !c.isNull(li) ? c.getString(li) : null;
                readMeta(c, r);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Satu baris berdasarkan id, atau null. */
    public Row byId(long id) {
        Cursor c = getReadableDatabase().query("rides", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    /** Rekaman terbaru, atau null kalau belum ada. */
    public Row latest() {
        Cursor c = getReadableDatabase().query("rides", null, null, null, null, null,
                "start_ms DESC", "1");
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    private Row read(Cursor c) {
        Row r = new Row();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.startMs = c.getLong(c.getColumnIndexOrThrow("start_ms"));
        r.durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms"));
        r.movingMs = c.getLong(c.getColumnIndexOrThrow("moving_ms"));
        r.distanceM = c.getDouble(c.getColumnIndexOrThrow("distance_m"));
        r.maxSpeedMs = c.getDouble(c.getColumnIndexOrThrow("max_speed"));
        r.elevGainM = c.getDouble(c.getColumnIndexOrThrow("elev_gain"));
        r.gpxPath = c.getString(c.getColumnIndexOrThrow("gpx_path"));
        int li = c.getColumnIndex("laps");
        r.laps = li >= 0 && !c.isNull(li) ? c.getString(li) : null;
        readMeta(c, r);
        return r;
    }

    /**
     * Baca kolom v3/v4. Sengaja lewat getColumnIndex (bukan OrThrow) supaya basis
     * data yang belum sempat di-upgrade tidak membuat layar Riwayat dan Ringkasan
     * mati — rekaman lama cukup tampil tanpa kalori/RPE/komuter/pratinjau.
     */
    private static void readMeta(Cursor c, Row r) {
        int ci = c.getColumnIndex("calories_kcal");
        r.caloriesKcal = ci >= 0 && !c.isNull(ci) ? c.getInt(ci) : 0;
        int ri = c.getColumnIndex("rpe");
        r.rpe = ri >= 0 && !c.isNull(ri) ? c.getInt(ri) : 0;
        int mi = c.getColumnIndex("commute");
        r.commute = mi >= 0 && !c.isNull(mi) && c.getInt(mi) != 0;
        int pi = c.getColumnIndex("preview");
        r.preview = pi >= 0 && !c.isNull(pi) ? c.getString(pi) : null;
        int bi = c.getColumnIndex("bike_id");
        r.bikeId = bi >= 0 && !c.isNull(bi) ? c.getLong(bi) : 0L;
    }

    /**
     * Perbarui penanda komuter dan nilai RPE sebuah rekaman.
     *
     * Dipakai layar Ringkasan setelah gowes selesai. Kedua kolom ini memang
     * dinilai SETELAH berkendara, jadi tidak masuk saat insert.
     */
    public void updateMeta(long id, boolean commute, int rpe) {
        if (id <= 0) return;
        ContentValues v = new ContentValues();
        v.put("commute", commute ? 1 : 0);
        v.put("rpe", rpe < 1 || rpe > 10 ? 0 : rpe);
        getWritableDatabase().update("rides", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete("rides", "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * Perbarui lokasi berkas GPX sebuah rekaman. Dipakai migrasi sekali jalan
     * saat berkas dipindah dari folder aplikasi ke penyimpanan bersama.
     */
    public void updateGpxPath(long id, String path) {
        if (id <= 0) return;
        ContentValues v = new ContentValues();
        v.put("gpx_path", path);
        getWritableDatabase().update("rides", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** Simpan pratinjau rute sekali; dipakai rekaman lama yang belum punya thumbnail. */
    public void updatePreview(long id, String preview) {
        if (id <= 0 || preview == null || preview.length() == 0) return;
        ContentValues v = new ContentValues();
        v.put("preview", preview);
        getWritableDatabase().update("rides", v, "id=?", new String[]{String.valueOf(id)});
    }
}
