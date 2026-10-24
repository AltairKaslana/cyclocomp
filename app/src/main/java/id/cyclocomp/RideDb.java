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
    private static final int VERSION = 2;

    public static class Row {
        public long id, startMs, durationMs, movingMs;
        public double distanceM, maxSpeedMs, elevGainM;
        public String gpxPath;
        public String laps;      // "jarak;gerak;total|jarak;gerak;total|..."

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
                + "laps TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            // v2: kolom laps. ALTER tidak bisa menambah NOT NULL, dan TEXT biasa cukup.
            try {
                db.execSQL("ALTER TABLE rides ADD COLUMN laps TEXT");
            } catch (Exception ignored) { }   // sudah ada (mis. downgrade-upgrade)
        }
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
        return r;
    }

    public void delete(long id) {
        getWritableDatabase().delete("rides", "id=?", new String[]{String.valueOf(id)});
    }
}
