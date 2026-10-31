package id.cyclocomp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Baca/tulis Garasi: sepeda, komponen perawatan, dan angka odometer.
 *
 * Semua metode membuka {@link RideDb} sendiri lalu menutupnya. Layar Garasi
 * hanya membuka basis data saat digambar, jadi satu koneksi per panggilan jauh
 * lebih murah daripada menahan koneksi hidup di layar yang bisa lama di
 * belakang. RideDb tetap satu-satunya pemilik skema (versi 5).
 */
final class GarasiStore {
    private GarasiStore() {}

    /** Angka ringkas satu sepeda, dihitung dari tabel rides. */
    static class Ringkas {
        public double odoM;
        public double jarakM;
        public long movingMs;
        public int gowes;
    }

    // ---------- sepeda ----------

    static List<Bike> bikes(Context c) {
        List<Bike> out = new ArrayList<Bike>();
        RideDb helper = new RideDb(c);
        Cursor cur = null;
        try {
            cur = helper.getReadableDatabase().query("bikes", null, null, null,
                    null, null, "created_ms ASC, id ASC");
            while (cur.moveToNext()) out.add(bacaBike(cur));
        } finally {
            if (cur != null) cur.close();
            helper.close();
        }
        return out;
    }

    static Bike bike(Context c, long id) {
        if (id <= 0) return null;
        RideDb helper = new RideDb(c);
        Cursor cur = null;
        try {
            cur = helper.getReadableDatabase().query("bikes", null, "id=?",
                    new String[]{String.valueOf(id)}, null, null, null);
            return cur.moveToFirst() ? bacaBike(cur) : null;
        } finally {
            if (cur != null) cur.close();
            helper.close();
        }
    }

    /** Sepeda yang sedang dipakai: pilihan terakhir, atau yang paling lama. */
    static Bike aktif(Context c) {
        Bike b = bike(c, Settings.lastBikeId(c));
        if (b != null) return b;
        List<Bike> semua = bikes(c);
        return semua.isEmpty() ? null : semua.get(0);
    }

    /**
     * Tambah sepeda. Kalau ini sepeda PERTAMA, semua gowes lama yang belum
     * punya sepeda ikut dikaitkan: sebelum Garasi ada, seluruh riwayat memang
     * berasal dari satu sepeda, dan tanpa ini odometer langsung mulai dari nol
     * padahal pengguna sudah punya puluhan gowes.
     *
     * @return id sepeda baru, atau -1 kalau gagal/nama kosong.
     */
    static long addBike(Context c, String name, String type, double offsetM) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return -1;
        if (offsetM < 0 || Double.isNaN(offsetM)) offsetM = 0;

        RideDb helper = new RideDb(c);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            int jumlah = 0;
            Cursor cur = db.rawQuery("SELECT COUNT(*) FROM bikes", null);
            try { if (cur.moveToFirst()) jumlah = cur.getInt(0); } finally { cur.close(); }

            ContentValues v = new ContentValues();
            v.put("name", n);
            v.put("type", type == null ? "" : type.trim());
            v.put("odo_offset_m", offsetM);
            v.put("created_ms", System.currentTimeMillis());
            long id = db.insert("bikes", null, v);
            if (id <= 0) return -1;

            if (jumlah == 0) {
                ContentValues u = new ContentValues();
                u.put("bike_id", id);
                db.update("rides", u, "bike_id IS NULL", null);
            }
            db.setTransactionSuccessful();
            Settings.setLastBikeId(c, id);
            return id;
        } finally {
            db.endTransaction();
            helper.close();
        }
    }

    /**
     * Hapus sepeda beserta komponennya. Gowesnya TIDAK dihapus, hanya dilepas
     * dari sepeda - kehilangan riwayat karena salah menekan terlalu mahal.
     */
    static void deleteBike(Context c, long id) {
        if (id <= 0) return;
        RideDb helper = new RideDb(c);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("components", "bike_id=?", new String[]{String.valueOf(id)});
            ContentValues u = new ContentValues();
            u.putNull("bike_id");
            db.update("rides", u, "bike_id=?", new String[]{String.valueOf(id)});
            db.delete("bikes", "id=?", new String[]{String.valueOf(id)});

            if (Settings.lastBikeId(c) == id) {
                Cursor cur = db.rawQuery("SELECT id FROM bikes ORDER BY created_ms ASC, id ASC", null);
                long ganti = 0;
                try { if (cur.moveToFirst()) ganti = cur.getLong(0); } finally { cur.close(); }
                Settings.setLastBikeId(c, ganti);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            helper.close();
        }
    }

    /** Pilih sepeda untuk gowes berikutnya dan untuk ditampilkan. */
    static void pilihBike(Context c, long id) {
        if (id > 0) Settings.setLastBikeId(c, id);
    }

    // ---------- komponen ----------

    static List<Komponen> components(Context c, long bikeId) {
        List<Komponen> out = new ArrayList<Komponen>();
        if (bikeId <= 0) return out;
        RideDb helper = new RideDb(c);
        Cursor cur = null;
        try {
            cur = helper.getReadableDatabase().query("components", null, "bike_id=?",
                    new String[]{String.valueOf(bikeId)}, null, null, "id ASC");
            while (cur.moveToNext()) out.add(bacaKomponen(cur));
        } finally {
            if (cur != null) cur.close();
            helper.close();
        }
        return out;
    }

    /** @return id komponen baru, atau -1 kalau nama/batas tidak sah. */
    static long addComponent(Context c, long bikeId, String name, double limitM,
                             double startOdoM) {
        String n = name == null ? "" : name.trim();
        if (bikeId <= 0 || n.isEmpty() || limitM <= 0 || Double.isNaN(limitM)) return -1;
        if (startOdoM < 0 || Double.isNaN(startOdoM)) startOdoM = 0;

        RideDb helper = new RideDb(c);
        try {
            ContentValues v = new ContentValues();
            v.put("bike_id", bikeId);
            v.put("name", n);
            v.put("limit_m", limitM);
            v.put("start_odo_m", startOdoM);
            return helper.getWritableDatabase().insert("components", null, v);
        } finally {
            helper.close();
        }
    }

    static void deleteComponent(Context c, long id) {
        if (id <= 0) return;
        RideDb helper = new RideDb(c);
        try {
            helper.getWritableDatabase().delete("components", "id=?",
                    new String[]{String.valueOf(id)});
        } finally {
            helper.close();
        }
    }

    // ---------- angka ----------

    /**
     * Odometer = offset awal + jumlah jarak gowes sepeda ini. Satu kueri
     * agregat, bukan membaca seluruh baris riwayat.
     */
    static Ringkas ringkas(Context c, Bike b) {
        Ringkas r = new Ringkas();
        if (b == null) return r;
        RideDb helper = new RideDb(c);
        Cursor cur = null;
        try {
            cur = helper.getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) AS n, IFNULL(SUM(distance_m),0) AS dist,"
                            + " IFNULL(SUM(moving_ms),0) AS mov FROM rides WHERE bike_id=?",
                    new String[]{String.valueOf(b.id)});
            if (cur.moveToFirst()) {
                r.gowes = cur.getInt(0);
                r.jarakM = cur.getDouble(1);
                r.movingMs = cur.getLong(2);
            }
        } finally {
            if (cur != null) cur.close();
            helper.close();
        }
        r.odoM = b.odoOffsetM + r.jarakM;
        return r;
    }

    /**
     * Komponen yang SEGERA/LEWAT untuk satu sepeda. Dipakai pengingat di
     * Ringkasan (N-08); perhitungan hanya saat layar dibuka, tanpa layanan
     * latar belakang.
     */
    static List<Komponen> perluPerhatian(Context c, long bikeId) {
        List<Komponen> out = new ArrayList<Komponen>();
        Bike b = bike(c, bikeId);
        if (b == null) return out;
        double odo = ringkas(c, b).odoM;
        for (Komponen k : components(c, b.id)) {
            if (k.status(odo) != Komponen.BAIK) out.add(k);
        }
        return out;
    }

    /** Odometer sekarang untuk satu sepeda; 0 kalau sepeda tidak ada. */
    static double odoM(Context c, long bikeId) {
        Bike b = bike(c, bikeId);
        return b == null ? 0 : ringkas(c, b).odoM;
    }

    // ---------- baris -> model ----------

    private static Bike bacaBike(Cursor cur) {
        Bike b = new Bike();
        b.id = cur.getLong(cur.getColumnIndexOrThrow("id"));
        b.name = teks(cur, "name");
        b.type = teks(cur, "type");
        b.odoOffsetM = cur.getDouble(cur.getColumnIndexOrThrow("odo_offset_m"));
        b.createdMs = cur.getLong(cur.getColumnIndexOrThrow("created_ms"));
        return b;
    }

    private static Komponen bacaKomponen(Cursor cur) {
        Komponen k = new Komponen();
        k.id = cur.getLong(cur.getColumnIndexOrThrow("id"));
        k.bikeId = cur.getLong(cur.getColumnIndexOrThrow("bike_id"));
        k.name = teks(cur, "name");
        k.limitM = cur.getDouble(cur.getColumnIndexOrThrow("limit_m"));
        k.startOdoM = cur.getDouble(cur.getColumnIndexOrThrow("start_odo_m"));
        return k;
    }

    private static String teks(Cursor cur, String kolom) {
        int i = cur.getColumnIndexOrThrow(kolom);
        return cur.isNull(i) ? "" : cur.getString(i);
    }
}
