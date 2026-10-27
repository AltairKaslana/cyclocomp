package id.cyclocomp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Kartu gambar (PNG) berisi angka akhir gowes + gambar rute, siap dibagikan.
 * Mendukung dua gaya: Opak (latar pekat) dan Transparan (overlay alpha).
 */
final class ShareCard {

    private ShareCard() {}

    private static final int W = 1080;
    private static final int H = 1350;

    private static final int BG = Color.parseColor("#0E1013");
    private static final int CARD = Color.parseColor("#1A1D21");
    private static final int LABEL = Color.parseColor("#8A9099");
    private static final int VALUE = Color.parseColor("#FFFFFF");
    private static final int ACCENT = Color.parseColor("#FC4C02");
    private static final int DIVIDER = Color.parseColor("#2A2F35");
    private static final int GREEN = Color.parseColor("#22C55E");
    private static final int HALO = 0xCC000000; // bayangan tipis di belakang huruf

    private static final float MARGIN = 56f;

    static String fileName(String gpxName, String suffix) {
        if (gpxName == null) gpxName = "ride";
        int dot = gpxName.lastIndexOf('.');
        String base = dot > 0 ? gpxName.substring(0, dot) : gpxName;
        return base + suffix + ".png";
    }

    /** Varian 4:5 */
    static Bitmap cardBitmap(Context c, RideDb.Row row, List<GpxReader.P> track, boolean transparent) {
        try {
            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            draw(c, cv, row, track, transparent);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    static Uri buildCard(Context c, RideDb.Row row, List<GpxReader.P> track, boolean transparent) {
        Bitmap bmp = cardBitmap(c, row, track, transparent);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            String suffix = transparent ? "_overlay" : "_card";
            return writePngToGallery(c, bmp, fileName(gpxName, suffix));
        } finally {
            bmp.recycle();
        }
    }

    private static void draw(Context c, Canvas cv, RideDb.Row row, List<GpxReader.P> track, boolean transparent) {
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        if (!transparent) cv.drawColor(BG);

        float x0 = MARGIN, x1 = W - MARGIN;
        float y = MARGIN;

        // ---- kepala ----
        Paint pLabel = text(body, transparent ? VALUE : LABEL, 34f);
        pLabel.setLetterSpacing(0.22f);
        cv.drawText(c.getString(R.string.summary_title).toUpperCase(Locale.getDefault()), x0, y + 26f, pLabel);

        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault());
        cv.drawText(df.format(new Date(row.startMs)), x0, y + 74f, text(body, transparent ? VALUE : LABEL, 34f));

        Paint pAcc = new Paint();
        pAcc.setColor(ACCENT);
        cv.drawRect(x1 - 130f, y + 14f, x1, y + 22f, pAcc);
        y += 104f;

        // ---- angka utama: jarak ----
        String km = Fmt.km(row.distanceM);
        Paint pDist = text(cond, VALUE, 210f);
        cv.drawText(km, x0, y + 150f, pDist);
        cv.drawText("km", x0 + pDist.measureText(km) + 18f, y + 150f, text(body, transparent ? VALUE : LABEL, 44f));
        y += 186f;

        // ---- rute ----
        y += 10f;
        float mapTop = y;
        float mapH = 400f;
        if (!transparent) roundRect(cv, x0, mapTop, x1 - x0, mapH, CARD);
        drawRoute(cv, c, track, x0, mapTop, x1 - x0, mapH);
        y = mapTop + mapH + 34f;

        // ---- Metrik: 3 kolom ----
        float colW = (x1 - x0 - 2f) / 3f;
        float statTop = y;
        drawStat(cv, cond, body, x0, colW, statTop, c.getString(R.string.label_moving), Fmt.shortDuration(row.movingMs), transparent);
        drawStat(cv, cond, body, x0 + colW + 1f, colW, statTop, c.getString(R.string.label_avg), Fmt.speed(row.avgSpeedMs()) + " " + c.getString(R.string.unit_speed), transparent);
        drawStat(cv, cond, body, x0 + 2 * (colW + 1f), colW, statTop, c.getString(R.string.label_max), Fmt.speed((float) row.maxSpeedMs) + " " + c.getString(R.string.unit_speed), transparent);
        y = statTop + 100f;

        if (!transparent) cv.drawRect(x0, y, x1, y + 1f, solid(DIVIDER));
        y += 30f;

        // ---- Detail: Tanjakan + Waktu Total ----
        Paint pValLabel = text(body, transparent ? VALUE : LABEL, 30f);
        pValLabel.setLetterSpacing(0.18f);
        float half = (x1 - x0) / 2f;

        drawStatPair(cv, cond, body, x0, half, y, c.getString(R.string.label_elev), Fmt.meters(row.elevGainM) + " m", transparent);
        drawStatPair(cv, cond, body, x0 + half, half, y, c.getString(R.string.label_total), Fmt.duration(row.durationMs), transparent);
        y += 116f;

        // ---- Split Laps (Jika ada) ----
        if (row.laps != null && !row.laps.isEmpty()) {
            y += 20f;
            drawLaps(cv, cond, body, x0, x1, y, row.laps);
            y += 150f;
        }

        // ---- kaki ----
        if (!transparent) cv.drawRect(x0, y, x1, y + 1f, solid(DIVIDER));
        cv.drawText(c.getString(R.string.card_footer), x0, y + 52f, text(body, transparent ? VALUE : LABEL, 32f));
        Paint pRight = text(body, transparent ? VALUE : LABEL, 32f);
        pRight.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(c.getString(R.string.card_note), x1, y + 52f, pRight);
    }

    private static void drawStat(Canvas cv, Typeface cond, Typeface body, float x, float w, float top, String label, String value, boolean transparent) {
        Paint pL = text(body, transparent ? VALUE : LABEL, 30f);
        pL.setTextAlign(Paint.Align.CENTER);
        cv.drawText(label, x + w / 2f, top + 26f, pL);
        Paint pV = text(cond, VALUE, 72f);
        pV.setTextAlign(Paint.Align.CENTER);
        cv.drawText(value, x + w / 2f, top + 84f, pV);
    }

    private static void drawStatPair(Canvas cv, Typeface cond, Typeface body, float x, float w, float top, String label, String value, boolean transparent) {
        Paint pL = text(body, transparent ? VALUE : LABEL, 30f);
        pL.setTextAlign(Paint.Align.LEFT);
        cv.drawText(label, x + 12f, top + 22f, pL);
        Paint pV = text(cond, VALUE, 72f);
        pV.setTextAlign(Paint.Align.LEFT);
        cv.drawText(value, x + 12f, top + 84f, pV);
    }

    private static void drawLaps(Canvas cv, Typeface cond, Typeface body, float x0, float x1, float y, String lapsStr) {
        String[] laps = lapsStr.split("\\|");
        int maxLaps = 3;
        float rowH = 40f;
        Paint pL = text(body, VALUE, 28f);
        Paint pV = text(cond, VALUE, 28f);

        cv.drawText("SPLIT LAPS", x0, y + 20f, pL);
        y += 30f;

        for (int i = 0; i < Math.min(laps.length, maxLaps); i++) {
            String[] d = laps[i].split(";");
            if (d.length < 3) continue;
            String rowText = String.format(Locale.US, "Lap %d: %s km  •  %s  •  %s km/h", i + 1, d[0], d[1], d[2]);
            cv.drawText(rowText, x0, y + 20f, pV);
            y += rowH;
        }
    }

    /** Varian Story 9:16 */
    private static final int SW = 1080;
    private static final int SH = 1920;

    static Bitmap storyBitmap(Context c, RideDb.Row row, boolean transparent) {
        try {
            Bitmap bmp = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawStory(c, cv, row, transparent);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    static Uri buildStory(Context c, RideDb.Row row, boolean transparent) {
        Bitmap bmp = storyBitmap(c, row, transparent);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            String suffix = transparent ? "_story_overlay" : "_story";
            return writePngToGallery(c, bmp, fileName(gpxName, suffix));
        } finally {
            bmp.recycle();
        }
    }

    private static void drawStory(Context c, Canvas cv, RideDb.Row row, boolean transparent) {
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        if (!transparent) cv.drawColor(Color.BLACK);

        Paint pBrand = text(body, transparent ? VALUE : ACCENT, 56f);
        pBrand.setLetterSpacing(0.34f);
        pBrand.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.card_footer).toUpperCase(Locale.getDefault()), SW / 2f, 168f, pBrand);

        Paint pRule = solid(ACCENT);
        cv.drawRect(SW / 2f - 62f, 204f, SW / 2f + 62f, 211f, pRule);

        Paint pDate = text(body, transparent ? VALUE : LABEL, 38f);
        pDate.setTextAlign(Paint.Align.CENTER);
        cv.drawText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(new Date(row.startMs)), SW / 2f, 274f, pDate);

        final float top = 430f, step = 282f;
        final float labelSize = 38f, valueSize = 96f;

        storyStat(cv, cond, body, top, labelSize, valueSize, c.getString(R.string.label_distance), Fmt.km(row.distanceM), c.getString(R.string.unit_distance), transparent);
        storyStat(cv, cond, body, top + step, labelSize, valueSize, c.getString(R.string.label_moving), storyDuration(row.movingMs), null, transparent);
        storyStat(cv, cond, body, top + 2 * step, labelSize, valueSize, c.getString(R.string.label_elev), Fmt.meters(row.elevGainM), c.getString(R.string.unit_elev_short), transparent);
        storyStat(cv, cond, body, top + 3 * step, labelSize, valueSize, c.getString(R.string.label_avg), Fmt.speed(row.avgSpeedMs()), c.getString(R.string.unit_speed), transparent);
        storyStat(cv, cond, body, top + 4 * step, labelSize, valueSize, c.getString(R.string.label_max), Fmt.speed((float) row.maxSpeedMs), c.getString(R.string.unit_speed), transparent);
    }

    private static void storyStat(Canvas cv, Typeface cond, Typeface body, float top, float labelSize, float valueSize, String label, String value, String unit, boolean transparent) {
        Paint pL = text(body, transparent ? VALUE : LABEL, labelSize + 4f);
        pL.setLetterSpacing(0.20f);
        pL.setTextAlign(Paint.Align.CENTER);
        cv.drawText(label.toUpperCase(Locale.getDefault()), SW / 2f, top + labelSize, pL);

        float vBase = top + labelSize + 34f + valueSize;
        Paint pV = text(cond, VALUE, valueSize + 14f);
        pV.setTextAlign(Paint.Align.CENTER);

        if (unit == null || unit.isEmpty()) {
            cv.drawText(value, SW / 2f, vBase, pV);
            return;
        }

        Paint pU = text(body, transparent ? VALUE : LABEL, valueSize * 0.42f + 4f);
        float gap = valueSize * 0.16f;
        float total = pV.measureText(value) + gap + pU.measureText(unit);
        float x0 = SW / 2f - total / 2f;
        cv.drawText(value, x0, vBase, pV);
        pU.setTextAlign(Paint.Align.LEFT);
        cv.drawText(unit, x0 + pV.measureText(value) + gap, vBase, pU);
    }

    private static String storyDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%dj %dm %dd", h, m, sec);
        return String.format(Locale.US, "%dm %dd", m, sec);
    }

    /** Varian Stempel (Always Transparent) */
    static Bitmap stickerBitmap(Context c, RideDb.Row row, List<GpxReader.P> track) {
        try {
            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawSticker(c, cv, row, track);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    static Uri buildSticker(Context c, RideDb.Row row, List<GpxReader.P> track) {
        Bitmap bmp = stickerBitmap(c, row, track);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            return writePngToGallery(c, bmp, fileName(gpxName, "_sticker"));
        } finally {
            bmp.recycle();
        }
    }

    private static void drawSticker(Context c, Canvas cv, RideDb.Row row, List<GpxReader.P> pts) {
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        float x0 = MARGIN, x1 = W - MARGIN;

        float mapTop = MARGIN;
        float mapH = 620f;
        drawRoute(cv, c, pts, x0, mapTop, x1 - x0, mapH);
        float y = mapTop + mapH + 40f;

        float colW = (x1 - x0 - 2f) / 3f;
        stickerStat(cv, cond, body, x0, colW, y, c.getString(R.string.label_distance), Fmt.km(row.distanceM), c.getString(R.string.unit_distance));
        stickerStat(cv, cond, body, x0 + colW + 1f, colW, y, c.getString(R.string.label_elev), Fmt.meters(row.elevGainM), c.getString(R.string.unit_elev_short));
        stickerStat(cv, cond, body, x0 + 2 * (colW + 1f), colW, y, c.getString(R.string.label_moving), Fmt.shortDuration(row.movingMs), null);
        y += 150f;

        Paint pBrand = text(body, VALUE, 56f);
        pBrand.setLetterSpacing(0.34f);
        cv.drawText(c.getString(R.string.card_footer).toUpperCase(Locale.getDefault()), x0, y + 46f, pBrand);
        Paint pRule = solid(ACCENT);
        cv.drawRect(x0, y + 66f, x0 + 124f, y + 73f, pRule);
    }

    private static void stickerStat(Canvas cv, Typeface cond, Typeface body, float x, float w, float top, String label, String value, String unit) {
        Paint pL = text(body, VALUE, 30f);
        pL.setLetterSpacing(0.18f);
        float labelW = pL.measureText(label);

        Paint pV = text(cond, VALUE, 72f);
        Paint pU = unit == null ? null : text(body, VALUE, 30f);
        float valW = pV.measureText(value) + (unit == null ? 0f : 12f + pU.measureText(unit));

        // Tanpa bantalan: stempel harus benar-benar transparan supaya foto
        // di belakangnya tetap terlihat utuh.
        cv.drawText(label, x + (w - labelW) / 2f, top + 26f, pL);
        cv.drawText(value, x + (w - valW) / 2f, top + 84f, pV);
        if (pU != null) {
            float vW = pV.measureText(value);
            cv.drawText(unit, x + (w - valW) / 2f + vW + 12f, top + 84f, pU);
        }
    }

    /** Paint teks polos - tanpa bayangan, tanpa kotak, apa adanya. */
    private static Paint text(Typeface tf, int color, float size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setColor(color);
        p.setTextSize(size);
        return p;
    }

    private static Paint solid(int color) {
        Paint p = new Paint();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static void roundRect(Canvas cv, float x, float y, float w, float h, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        cv.drawRoundRect(new RectF(x, y, x + w, y + h), 32f, 32f, p);
    }

    private static void drawRoute(Canvas cv, Context c, List<GpxReader.P> pts, float x, float y, float w, float h) {
        if (pts == null || pts.size() < 2) return;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (GpxReader.P p : pts) {
            if (p.lat < minLat) minLat = p.lat;
            if (p.lat > maxLat) maxLat = p.lat;
            if (p.lon < minLon) minLon = p.lon;
            if (p.lon > maxLon) maxLon = p.lon;
        }
        double latRange = maxLat - minLat;
        double lonRange = maxLon - minLon;
        if (latRange == 0) latRange = 0.0001;
        if (lonRange == 0) lonRange = 0.0001;

        // Skala seragam untuk kedua sumbu supaya bentuk rute tidak gepeng:
        // ambil faktor terkecil, lalu pusatkan sisa ruang di kotak peta.
        double pad = 0.10;
        double sx = (w * (1.0 - 2.0 * pad)) / lonRange;
        double sy = (h * (1.0 - 2.0 * pad)) / latRange;
        double s = Math.min(sx, sy);
        double cx = x + w / 2.0, cy = y + h / 2.0;
        double mx = (minLon + maxLon) / 2.0, my = (minLat + maxLat) / 2.0;

        Path path = new Path();
        for (int i = 0; i < pts.size(); i++) {
            GpxReader.P p = pts.get(i);
            float px = (float) (cx + (p.lon - mx) * s);
            float py = (float) (cy - (p.lat - my) * s);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }

        // Dua lapis: halo gelap lebih dulu supaya garis tetap terbaca walau
        // ditempel di atas foto ramai, lalu inti oranye di atasnya.
        Paint pHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
        pHalo.setStyle(Paint.Style.STROKE);
        pHalo.setStrokeJoin(Paint.Join.ROUND);
        pHalo.setStrokeCap(Paint.Cap.ROUND);
        pHalo.setColor(0xCC0E1013);
        pHalo.setStrokeWidth(22f);
        cv.drawPath(path, pHalo);

        Paint pCore = new Paint(Paint.ANTI_ALIAS_FLAG);
        pCore.setStyle(Paint.Style.STROKE);
        pCore.setStrokeJoin(Paint.Join.ROUND);
        pCore.setStrokeCap(Paint.Cap.ROUND);
        pCore.setColor(ACCENT);
        pCore.setStrokeWidth(12f);
        cv.drawPath(path, pCore);

        // Penanda titik mulai (hijau) dan selesai (oranye) - mirip Strava.
        GpxReader.P first = pts.get(0);
        GpxReader.P last = pts.get(pts.size() - 1);
        Paint pStart = new Paint(Paint.ANTI_ALIAS_FLAG);
        pStart.setColor(GREEN);
        pStart.setStyle(Paint.Style.FILL);
        Paint pFinish = new Paint(Paint.ANTI_ALIAS_FLAG);
        pFinish.setColor(ACCENT);
        pFinish.setStyle(Paint.Style.FILL);
        Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        pRing.setColor(0xCC0E1013);
        pRing.setStyle(Paint.Style.STROKE);
        pRing.setStrokeWidth(5f);

        float sx0 = (float) (cx + (first.lon - mx) * s);
        float sy0 = (float) (cy - (first.lat - my) * s);
        cv.drawCircle(sx0, sy0, 16f, pRing);
        cv.drawCircle(sx0, sy0, 12f, pStart);

        float sx1 = (float) (cx + (last.lon - mx) * s);
        float sy1 = (float) (cy - (last.lat - my) * s);
        cv.drawCircle(sx1, sy1, 16f, pRing);
        cv.drawCircle(sx1, sy1, 12f, pFinish);
    }

    /** Folder galeri tempat PNG disimpan; sama dengan yang dijanjikan di strings.xml. */
    static final String GALLERY_DIR = "Zylo";

    /**
     * Alasan kegagalan {@link #writePngToGallery} yang terakhir. Dibaca
     * ShareActivity supaya pesan ke pengguna spesifik dan bisa ditindaklanjuti,
     * bukan "gagal" tanpa sebab — pengecualian yang ditelan diam-diam di sinilah
     * yang membuat "gagal menyimpan" tidak pernah bisa didiagnosis.
     */
    static volatile String lastErrorReason = null;

    /**
     * Tulis bitmap PNG ke penyimpanan bersama supaya muncul di Galeri.
     *
     * <p>Android 10+ (API 29) <b>wajib</b> lewat MediaStore: jalur {@code File}
     * ke folder publik sudah ditolak dengan EACCES karena {@code targetSdk 33}
     * tanpa {@code requestLegacyExternalStorage}. Itulah sebab tombol Simpan
     * melaporkan gagal padahal bitmap-nya sudah jadi di memori. API 23-28 tetap
     * memakai jalur File lama, tetapi butuh izin runtime WRITE_EXTERNAL_STORAGE.
     *
     * @return uri berkas yang tertulis, atau null kalau gagal (lihat lastErrorReason)
     */
    static Uri writePngToGallery(Context c, Bitmap bmp, String name) {
        lastErrorReason = null;
        if (bmp == null || bmp.isRecycled()) {
            lastErrorReason = "bitmap sudah tidak ada";
            return null;
        }

        if (Build.VERSION.SDK_INT >= 29) {
            Uri uri = null;
            try {
                ContentResolver cr = c.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + GALLERY_DIR);
                // IS_PENDING: berkas "sedang ditulis", belum terlihat penuh oleh
                // aplikasi lain sampai IS_PENDING dilepas di bawah.
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) {
                    lastErrorReason = "MediaStore menolak membuat berkas";
                    return null;
                }
                OutputStream os = cr.openOutputStream(uri, "w");
                if (os == null) {
                    lastErrorReason = "tidak bisa membuka stream tulis";
                    cr.delete(uri, null, null);
                    return null;
                }
                boolean ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
                if (!ok) {
                    // Bitmap sudah direcycle atau konfigurasinya tak bisa dikodekan:
                    // berkas setengah tertulis harus dibuang, jangan dibiarkan.
                    lastErrorReason = "gagal mengompres PNG";
                    cr.delete(uri, null, null);
                    return null;
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, done, null, null);
                return uri;
            } catch (Throwable t) {
                if (uri != null) {
                    try { c.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { }
                }
                lastErrorReason = t.getClass().getSimpleName() + ": " + t.getMessage();
                return null;
            }
        }

        // API 23-28: folder publik Pictures/Zylo lewat File, butuh izin runtime.
        try {
            if (c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                lastErrorReason = "izin WRITE_EXTERNAL_STORAGE belum diberikan";
                return null;
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), GALLERY_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                lastErrorReason = "tidak bisa membuat folder " + GALLERY_DIR;
                return null;
            }
            File file = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(file);
            boolean ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            if (!ok) {
                lastErrorReason = "gagal mengompres PNG";
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                return null;
            }
            // Daftarkan ke galeri supaya langsung terlihat tanpa restart perangkat.
            MediaScannerConnection.scanFile(c, new String[]{file.getAbsolutePath()}, null, null);
            return Uri.fromFile(file);
        } catch (Throwable t) {
            lastErrorReason = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        }
    }

    /**
     * Tulis bitmap PNG ke folder milik aplikasi sendiri, untuk <b>dibagikan</b>.
     *
     * <p>Tujuannya berbeda dari {@link #writePngToGallery} dan itu menentukan
     * lokasinya: berkas ini hanya perlu hidup selama aplikasi penerima selesai
     * membacanya, jadi tidak boleh ditaruh di penyimpanan publik. Di Android 10+
     * aplikasi tidak bisa lagi menulis folder publik lewat jalur {@code File}
     * (EACCES), dan {@link RideProvider} juga tidak bisa lagi membacanya dari
     * sana — sehingga tombol Bagikan/Salin ikut gagal dengan gejala yang sama.
     * Folder aplikasi sendiri selalu bisa ditulis dan dibaca, dan sudah menjadi
     * root pertama yang dicari {@code RideProvider.resolve()}.
     *
     * @return berkas yang tertulis, atau null kalau gagal (lihat lastErrorReason)
     */
    static File writePngForShare(Context c, Bitmap bmp, String name) {
        lastErrorReason = null;
        try {
            File dir = new File(c.getExternalFilesDir(null), "rides");
            if (!dir.exists() && !dir.mkdirs()) {
                lastErrorReason = "tidak bisa membuat folder rides";
                return null;
            }
            File file = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(file);
            boolean ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            if (!ok) {
                lastErrorReason = "gagal mengompres PNG";
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                return null;
            }
            return file;
        } catch (Throwable t) {
            lastErrorReason = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        }
    }
}
