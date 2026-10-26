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
 *
 * Kenapa digambar sendiri, bukan memotret layar: hasilnya sama di semua perangkat dan
 * resolusi, dan tidak bergantung pada apa yang sedang tampil di layar. Ukuran 1080x1350
 * (rasio 4:5) dipilih karena pas untuk WhatsApp, Telegram, dan Instagram tanpa terpotong.
 *
 * Tanpa dependency: hanya Canvas + Paint dari framework, plus font Barlow yang sudah
 * dibundel. Rute digambar dari titik GPX dengan proyeksi yang sama seperti {@link RouteView}
 * di atas latar gelap berpola kisi, jadi kartu tetap terbentuk walaupun peta ubin tidak
 * bisa diambil (offline).
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
    private static final int GRID = Color.parseColor("#16FFFFFF");
    private static final int GREEN = Color.parseColor("#22C55E");

    private static final float MARGIN = 56f;

    /** Nama berkas PNG untuk satu gowes, diturunkan dari nama GPX-nya. */
    static String fileName(String gpxName) {
        if (gpxName == null) gpxName = "ride";
        int dot = gpxName.lastIndexOf('.');
        String base = dot > 0 ? gpxName.substring(0, dot) : gpxName;
        return base + "_card.png";
    }

    /**
     * Bangun kartu 4:5 sebagai Bitmap (untuk preview maupun simpan).
     *
     * @return Bitmap, atau null kalau gagal. Pemanggil yang tidak lagi memakai
     *          hasilnya bertanggung jawab memanggil recycle().
     */
    static Bitmap cardBitmap(Context c, RideDb.Row row, List<GpxReader.P> track) {
        try {
            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            draw(c, cv, row, track);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Bangun kartu dan simpan sebagai PNG di folder rides - folder yang sama dengan GPX,
     * supaya {@link RideProvider} bisa membagikannya.
     *
     * @return berkas PNG, atau null kalau gagal. Gagal tidak boleh mematikan aplikasi:
     *          pemanggil cukup memberi tahu pengguna.
     */
    static File build(Context c, RideDb.Row row, List<GpxReader.P> track) {
        Bitmap bmp = cardBitmap(c, row, track);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            return writePngForShare(c, bmp, fileName(gpxName));
        } finally {
            bmp.recycle();
        }
    }

    private static void draw(Context c, Canvas cv, RideDb.Row row, List<GpxReader.P> track) {
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        cv.drawColor(BG);

        float x0 = MARGIN, x1 = W - MARGIN;
        float y = MARGIN;

        // ---- kepala: judul + waktu ----
        Paint pLabel = text(body, LABEL, 30f);
        pLabel.setLetterSpacing(0.22f);
        cv.drawText(c.getString(R.string.summary_title).toUpperCase(Locale.getDefault()),
                x0, y + 26f, pLabel);

        DateFormat df = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault());
        cv.drawText(df.format(new Date(row.startMs)), x0, y + 74f, text(body, LABEL, 30f));

        // garis aksen pendek di kanan kepala: tanda tangan visual, tanpa menambah data
        Paint pAcc = new Paint();
        pAcc.setColor(ACCENT);
        cv.drawRect(x1 - 130f, y + 14f, x1, y + 22f, pAcc);
        y += 104f;

        // ---- angka utama: jarak ----
        String km = Fmt.km(row.distanceM);
        Paint pDist = text(cond, VALUE, 190f);
        cv.drawText(km, x0, y + 150f, pDist);
        cv.drawText("km", x0 + pDist.measureText(km) + 18f, y + 150f, text(body, LABEL, 38f));
        y += 186f;

        // ---- rute ----
        y += 10f;
        float mapTop = y;
        float mapH = 470f;
        roundRect(cv, x0, mapTop, x1 - x0, mapH, CARD);
        drawRoute(cv, c, track, x0, mapTop, x1 - x0, mapH);
        y = mapTop + mapH + 34f;

        // ---- tiga angka: waktu gerak, rata-rata, maks ----
        float colW = (x1 - x0 - 2f) / 3f;
        float statTop = y;
        stat(cv, cond, body, x0, colW, statTop, c.getString(R.string.label_moving),
                Fmt.shortDuration(row.movingMs));
        stat(cv, cond, body, x0 + colW + 1f, colW, statTop, c.getString(R.string.label_avg),
                Fmt.speed(row.avgSpeedMs()) + " " + c.getString(R.string.unit_speed));
        stat(cv, cond, body, x0 + 2 * (colW + 1f), colW, statTop, c.getString(R.string.label_max),
                Fmt.speed((float) row.maxSpeedMs) + " " + c.getString(R.string.unit_speed));
        y = statTop + 100f;

        cv.drawRect(x0, y, x1, y + 1f, solid(DIVIDER));
        y += 30f;

        // ---- tanjakan + waktu total ----
        Paint pValLabel = text(body, LABEL, 26f);
        pValLabel.setLetterSpacing(0.18f);
        float half = (x1 - x0) / 2f;

        cv.drawText(c.getString(R.string.label_elev), x0, y + 22f, pValLabel);
        cv.drawText(Fmt.meters(row.elevGainM) + " m", x0, y + 84f, text(cond, VALUE, 62f));

        cv.drawText(c.getString(R.string.label_total), x0 + half, y + 22f, pValLabel);
        cv.drawText(Fmt.duration(row.durationMs), x0 + half, y + 84f, text(cond, VALUE, 62f));
        y += 116f;

        // ---- kaki ----
        cv.drawRect(x0, y, x1, y + 1f, solid(DIVIDER));
        cv.drawText(c.getString(R.string.card_footer), x0, y + 52f, text(body, LABEL, 28f));
        Paint pRight = text(body, LABEL, 28f);
        pRight.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(c.getString(R.string.card_note), x1, y + 52f, pRight);
    }

    /** Satu blok angka: label kecil di atas, nilai besar di bawah. */
    private static void stat(Canvas cv, Typeface cond, Typeface body,
                             float x, float w, float top, String label, String value) {
        Paint pL = text(body, LABEL, 26f);
        pL.setTextAlign(Paint.Align.CENTER);
        cv.drawText(label, x + w / 2f, top + 26f, pL);

        Paint pV = text(cond, VALUE, 52f);
        pV.setTextAlign(Paint.Align.CENTER);
        cv.drawText(value, x + w / 2f, top + 84f, pV);
    }

    /**
     * Gambar rute di dalam kotak (x,y,w,h).
     *
     * Proyeksi sama seperti RouteView: ekuirektangular di sekitar titik tengah rute, satu
     * skala untuk kedua sumbu supaya bentuk rute tidak gepeng. Garis dibuat dua lapis -
     * lebar transparan lalu tipis solid - supaya terlihat bercahaya tanpa memakai efek
     * blur yang mahal di perangkat lama.
     */
    private static void drawRoute(Canvas cv, Context c, List<GpxReader.P> pts,
                                  float x, float y, float w, float h) {
        Paint pGrid = new Paint();
        pGrid.setStyle(Paint.Style.STROKE);
        pGrid.setStrokeWidth(1f);
        pGrid.setColor(GRID);
        float step = Math.max(48f, w / 8f);
        for (float gx = x + step; gx < x + w; gx += step) cv.drawLine(gx, y, gx, y + h, pGrid);
        for (float gy = y + step; gy < y + h; gy += step) cv.drawLine(x, gy, x + w, gy, pGrid);

        if (pts == null || pts.size() < 2) {
            Paint pE = text(Ui.body(c), LABEL, 30f);
            pE.setTextAlign(Paint.Align.CENTER);
            cv.drawText(c.getString(R.string.route_none), x + w / 2f, y + h / 2f, pE);
            return;
        }

        double lat0 = 0, lon0 = 0;
        for (GpxReader.P p : pts) { lat0 += p.lat; lon0 += p.lon; }
        lat0 /= pts.size();
        lon0 /= pts.size();

        double cosLat = Math.cos(Math.toRadians(lat0));
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GpxReader.P p : pts) {
            double px = (p.lon - lon0) * cosLat;
            double py = (p.lat - lat0);
            if (px < minX) minX = px;
            if (px > maxX) maxX = px;
            if (py < minY) minY = py;
            if (py > maxY) maxY = py;
        }
        double spanX = Math.max(maxX - minX, 1e-9);
        double spanY = Math.max(maxY - minY, 1e-9);
        float pad = 46f;
        float scale = (float) Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY);
        float offX = (float) (x + w / 2.0 - (minX + spanX / 2) * scale);
        float offY = (float) (y + h / 2.0 + (minY + spanY / 2) * scale);   // sumbu Y dibalik

        Path path = new Path();
        float sx = 0, sy = 0, ex = 0, ey = 0;
        for (int i = 0; i < pts.size(); i++) {
            GpxReader.P p = pts.get(i);
            float px = (float) ((p.lon - lon0) * cosLat * scale + offX);
            float py = (float) (offY - (p.lat - lat0) * scale);
            if (i == 0) { path.moveTo(px, py); sx = px; sy = py; }
            else path.lineTo(px, py);
            ex = px; ey = py;
        }

        // lapis luar transparan lebih lebar, lalu lapis dalam solid
        Paint pGlow = line(ACCENT, 16f);
        pGlow.setAlpha(70);
        cv.drawPath(path, pGlow);
        cv.drawPath(path, line(ACCENT, 7f));

        // penanda awal (hijau) dan akhir (putih)
        Paint pRing = solid(CARD);
        Paint pStart = solid(GREEN);
        Paint pEnd = solid(VALUE);
        cv.drawCircle(sx, sy, 15f, pRing);
        cv.drawCircle(sx, sy, 8f, pStart);
        cv.drawCircle(ex, ey, 15f, pRing);
        cv.drawCircle(ex, ey, 8f, pEnd);
    }

    private static Paint solid(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    private static Paint line(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    private static Paint text(Typeface tf, int color, float size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(tf == null ? Typeface.SANS_SERIF : tf);
        return p;
    }

    private static void roundRect(Canvas cv, float x, float y, float w, float h, int color) {
        Paint p = solid(color);
        cv.drawRoundRect(new RectF(x, y, x + w, y + h), 28f, 28f, p);
    }

    // ==================================================================
    // Kartu Story 9:16 - latar hitam pekat, angka berjajar tegak di tengah.
    //
    // Dipakai untuk Story Instagram/WhatsApp: rasio 1080x1920 mengisi layar
    // penuh tanpa terpotong. Bedanya dengan kartu 4:5 di atas: di sini ANGKA
    // adalah tokoh utama, bukan peta. Tidak ada rute digambar - kalau ada
    // rute, mata orang justru beralih ke gambar, bukan ke jarak dan tanjakan.
    //
    // Proporsi diambil dari pengukuran kartu rujukan (1080 lebar):
    //   wordmark ~5% tinggi, label 38px, nilai 96px -> rasio nilai:label 2,5:1,
    //   jarak antar blok 282px. Latar hitam murni #000000 supaya angka putih
    //   yang menonjol, dan aksen oranye hanya untuk wordmark + garis tipis.
    // ==================================================================

    private static final int SW = 1080;
    private static final int SH = 1920;

    /** Berkas PNG kartu story - nama berbeda dari kartu 4:5 supaya tidak saling menimpa. */
    static String storyFileName(String gpxName) {
        if (gpxName == null) gpxName = "ride";
        int dot = gpxName.lastIndexOf('.');
        String base = dot > 0 ? gpxName.substring(0, dot) : gpxName;
        return base + "_story.png";
    }

    /** Bangun kartu story 9:16 sebagai Bitmap (untuk preview maupun simpan). */
    static Bitmap storyBitmap(Context c, RideDb.Row row) {
        try {
            Bitmap bmp = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawStory(c, cv, row);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Bangun kartu story dan simpan sebagai PNG di folder rides. */
    static File buildStory(Context c, RideDb.Row row) {
        Bitmap bmp = storyBitmap(c, row);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            return writePngForShare(c, bmp, storyFileName(gpxName));
        } finally {
            bmp.recycle();
        }
    }

    private static void drawStory(Context c, Canvas cv, RideDb.Row row) {
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        cv.drawColor(Color.BLACK);

        // ---- kepala: wordmark + garis aksen + tanggal, semuanya di tengah ----
        Paint pBrand = text(body, ACCENT, 52f);
        pBrand.setLetterSpacing(0.34f);
        pBrand.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.card_footer).toUpperCase(Locale.getDefault()),
                SW / 2f, 168f, pBrand);

        Paint pRule = solid(ACCENT);
        cv.drawRect(SW / 2f - 62f, 204f, SW / 2f + 62f, 211f, pRule);

        Paint pDate = text(body, LABEL, 34f);
        pDate.setTextAlign(Paint.Align.CENTER);
        cv.drawText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(new Date(row.startMs)), SW / 2f, 274f, pDate);

        // ---- lima blok angka. Hanya data yang benar-benar tersedia di RideDb:
        //      jarak, waktu gerak, tanjakan, rata-rata, maksimum. Kalori dan
        //      detak jantung TIDAK ditampilkan karena belum disimpan per gowes -
        //      menampilkannya berarti mengarang angka. ----
        final float top = 430f, step = 282f;
        final float labelSize = 38f, valueSize = 96f;

        storyStat(cv, cond, body, top, labelSize, valueSize,
                c.getString(R.string.label_distance),
                Fmt.km(row.distanceM), c.getString(R.string.unit_distance));

        storyStat(cv, cond, body, top + step, labelSize, valueSize,
                c.getString(R.string.label_moving),
                storyDuration(row.movingMs), null);

        storyStat(cv, cond, body, top + 2 * step, labelSize, valueSize,
                c.getString(R.string.label_elev),
                Fmt.meters(row.elevGainM), c.getString(R.string.unit_elev_short));

        storyStat(cv, cond, body, top + 3 * step, labelSize, valueSize,
                c.getString(R.string.label_avg),
                Fmt.speed(row.avgSpeedMs()), c.getString(R.string.unit_speed));

        storyStat(cv, cond, body, top + 4 * step, labelSize, valueSize,
                c.getString(R.string.label_max),
                Fmt.speed((float) row.maxSpeedMs), c.getString(R.string.unit_speed));
    }

    /** Satu blok story: label kecil di tengah, nilai besar di bawahnya, satuan menyusul. */
    private static void storyStat(Canvas cv, Typeface cond, Typeface body, float top,
                                  float labelSize, float valueSize,
                                  String label, String value, String unit) {
        Paint pL = text(body, LABEL, labelSize);
        pL.setLetterSpacing(0.20f);
        pL.setTextAlign(Paint.Align.CENTER);
        cv.drawText(label.toUpperCase(Locale.getDefault()), SW / 2f, top + labelSize, pL);

        float vBase = top + labelSize + 34f + valueSize;
        Paint pV = text(cond, VALUE, valueSize);
        pV.setTextAlign(Paint.Align.CENTER);

        if (unit == null || unit.isEmpty()) {
            cv.drawText(value, SW / 2f, vBase, pV);
            return;
        }

        // nilai + satuan sejajar baseline; satuan ~42% ukuran nilai supaya
        // angka tetap jadi fokus pertama, sama seperti kartu rujukan.
        Paint pU = text(body, LABEL, valueSize * 0.42f);
        float gap = valueSize * 0.16f;
        float total = pV.measureText(value) + gap + pU.measureText(unit);
        float x0 = SW / 2f - total / 2f;
        cv.drawText(value, x0, vBase, pV);
        pU.setTextAlign(Paint.Align.LEFT);
        cv.drawText(unit, x0 + pV.measureText(value) + gap, vBase, pU);
    }

    /** Durasi gaya kartu story: "40m 57d", bukan "40:57". */
    private static String storyDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%dj %dm %dd", h, m, sec);
        return String.format(Locale.US, "%dm %dd", m, sec);
    }

    // ==================================================================
    // Kartu TRANSPARAN ("stempel") - latar bening, isinya mengambang.
    //
    // Kenapa ada varian ketiga: kartu 4:5 dan Story sama-sama punya latar
    // pekat, jadi kalau ditempel di atas foto hasilnya kotak besar yang
    // menutupi foto. Stempel ini menggambar APA SAJA kecuali latar -
    // bitmap ARGB_8888 tidak pernah diisi warna dasar, jadi piksel yang
    // tak disentuh tetap transparan dan PNG menyimpan alpha itu.
    //
    // Yang digambar: rute (tokoh utama), tiga angka penting, wordmark.
    // Agar tetap terbaca di atas foto terang, setiap blok angka dapat
    // bantalan gelap semi-transparan yang bentuknya mengikuti isinya -
    // bukan kotak selebar kartu, jadi kesan "kartu" tetap hilang.
    // ==================================================================

    /** Berkas PNG stempel - nama berbeda dari dua kartu lain supaya tidak saling menimpa. */
    static String stickerFileName(String gpxName) {
        if (gpxName == null) gpxName = "ride";
        int dot = gpxName.lastIndexOf('.');
        String base = dot > 0 ? gpxName.substring(0, dot) : gpxName;
        return base + "_sticker.png";
    }

    /** Bangun stempel transparan sebagai Bitmap (untuk preview maupun simpan). */
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

    /**
     * Bangun stempel transparan 1080x1350 dan simpan sebagai PNG di folder rides.
     *
     * @return berkas PNG, atau null kalau gagal.
     */
    static File buildSticker(Context c, RideDb.Row row, List<GpxReader.P> track) {
        Bitmap bmp = stickerBitmap(c, row, track);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            return writePngForShare(c, bmp, stickerFileName(gpxName));
        } finally {
            bmp.recycle();
        }
    }

    private static void drawSticker(Context c, Canvas cv, RideDb.Row row,
                                    List<GpxReader.P> pts) {
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        // Tanpa drawColor di sini - itu inti dari kartu transparan.

        float x0 = MARGIN, x1 = W - MARGIN;

        // ---- rute: tokoh utama, tinggi 620, tanpa kotak pembungkus ----
        float mapTop = MARGIN;
        float mapH = 620f;
        drawRoute(cv, c, pts, x0, mapTop, x1 - x0, mapH);
        float y = mapTop + mapH + 40f;

        // ---- tiga angka dengan bantalan gelap semi-transparan ----
        float colW = (x1 - x0 - 2f) / 3f;
        stickerStat(cv, cond, body, x0, colW, y,
                c.getString(R.string.label_distance), Fmt.km(row.distanceM),
                c.getString(R.string.unit_distance));
        stickerStat(cv, cond, body, x0 + colW + 1f, colW, y,
                c.getString(R.string.label_elev), Fmt.meters(row.elevGainM),
                c.getString(R.string.unit_elev_short));
        stickerStat(cv, cond, body, x0 + 2 * (colW + 1f), colW, y,
                c.getString(R.string.label_moving), Fmt.shortDuration(row.movingMs), null);
        y += 150f;

        // ---- wordmark + garis aksen: identitas, mengikuti gaya stempel ----
        Paint pBrand = text(body, ACCENT, 52f);
        pBrand.setLetterSpacing(0.34f);
        cv.drawText(c.getString(R.string.card_footer).toUpperCase(Locale.getDefault()),
                x0, y + 46f, pBrand);
        Paint pRule = solid(ACCENT);
        cv.drawRect(x0, y + 66f, x0 + 124f, y + 73f, pRule);
    }

    /**
     * Satu blok angka stempel: bantalan gelap alpha 150 (sebagian foto
     * masih terlihat), label kecil, nilai besar. Bantalan dibuat selebar
     * teks terlebar + padding, jadi bentuknya pil, bukan kotak kartu.
     */
    private static void stickerStat(Canvas cv, Typeface cond, Typeface body,
                                    float x, float w, float top,
                                    String label, String value, String unit) {
        Paint pL = text(body, LABEL, 26f);
        pL.setLetterSpacing(0.18f);
        float labelW = pL.measureText(label);

        Paint pV = text(cond, VALUE, 62f);
        Paint pU = unit == null ? null : text(body, LABEL, 26f);
        float valW = pV.measureText(value) + (unit == null ? 0f
                : 12f + pU.measureText(unit));

        float padH = 24f, padV = 22f;
        float boxW = Math.max(Math.max(labelW, valW) + 2 * padH, w - 8f);
        float boxH = 26f + 62f + 2 * padV;

        // bantalan gelap semi-transparan; warna dasar #0E1013, alpha 150
        Paint pPad = solid(Color.argb(150, 0x0E, 0x10, 0x13));
        cv.drawRoundRect(new RectF(x, top, x + boxW, top + boxH), 22f, 22f, pPad);

        pL.setTextAlign(Paint.Align.LEFT);
        cv.drawText(label, x + padH, top + padV + 22f, pL);

        pV.setTextAlign(Paint.Align.LEFT);
        if (unit == null) {
            cv.drawText(value, x + padH, top + padV + 22f + 20f + 62f, pV);
        } else {
            pU.setTextAlign(Paint.Align.LEFT);
            cv.drawText(value, x + padH, top + padV + 22f + 20f + 62f, pV);
            cv.drawText(unit, x + padH + pV.measureText(value) + 12f,
                    top + padV + 22f + 20f + 62f, pU);
        }
    }

    // ==================================================================
    // Simpan ke galeri publik.
    //
    // Dua jalur karena MediaStore berubah di Android 10 (Q, API 29):
    //  - Q+ : sisipkan lewat ContentResolver dengan RELATIVE_PATH; aplikasi
    //         tidak perlu izin apa pun, dan galeri langsung melihat berkas.
    //  - 23-28: jalur lama ke Pictures/Zylo + MediaScannerConnection supaya
    //         berkas terindeks; butuh WRITE_EXTERNAL_STORAGE yang diminta
    //         saat runtime oleh pemanggil (SummaryActivity).
    //
    // @return null kalau berhasil, pesan singkat kalau gagal.
    // ==================================================================
    static String saveToGallery(Context c, File png) {
        String name = png.getName();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver cr = c.getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Zylo");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "insert-failed";
                OutputStream os = cr.openOutputStream(uri);
                if (os == null) return "open-failed";
                copyFile(new FileInputStream(png), os);
                cv.clear();
                cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, cv, null, null);
                return null;
            }

            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "Zylo");
            if (!dir.exists() && !dir.mkdirs()) return "mkdir-failed";
            File out = new File(dir, name);
            copyFile(new FileInputStream(png), new FileOutputStream(out));
            // beri tahu galeri supaya berkas baru muncul tanpa reboot
            MediaScannerConnection.scanFile(c,
                    new String[]{out.getAbsolutePath()},
                    new String[]{"image/png"}, null);
            return null;
        } catch (Throwable t) {
            return t.getClass().getSimpleName();
        }
    }

    private static void copyFile(FileInputStream in, OutputStream out) throws Exception {
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Exception ignored) { }
            try { out.close(); } catch (Exception ignored) { }
        }
    }

    // ==================================================================
    // Jalur baru: Bitmap -> berkas/galeri langsung (dipakai layar Bagikan
    // yang menampilkan preview ketiga kartu sekaligus).
    // ==================================================================

    /**
     * Tulis Bitmap sebagai PNG ke folder rides supaya bisa dibagikan lewat
     * {@link RideProvider}. Nama bebas (dipakai juga untuk menyimpan kartu
     * yang sedang dipilih pengguna di layar preview).
     *
     * @return berkas PNG, atau null kalau gagal
     */
    static File writePngForShare(Context c, Bitmap bmp, String name) {
        if (bmp == null || name == null) return null;
        FileOutputStream out = null;
        try {
            File dir = new File(c.getExternalFilesDir(null), "rides");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, name);
            out = new FileOutputStream(f);
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) return null;
            out.flush();
            return f;
        } catch (Throwable t) {
            return null;
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * Simpan Bitmap langsung ke galeri (Pictures/Zylo). Dipakai tombol
     * "Simpan ke galeri" di layar preview - kartu yang sedang tampil.
     *
     * @param name nama berkas, mis. "ride_20261024_card.png"
     * @return null kalau berhasil, pesan singkat kalau gagal
     */
    static String saveBitmapToGallery(Context c, Bitmap bmp, String name) {
        if (bmp == null || name == null) return "bitmap-null";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)) return "compress-failed";
        byte[] png = bos.toByteArray();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver cr = c.getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Zylo");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "insert-failed";
                OutputStream os = cr.openOutputStream(uri);
                if (os == null) return "open-failed";
                os.write(png);
                os.flush();
                os.close();
                cv.clear();
                cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, cv, null, null);
                return null;
            }

            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "Zylo");
            if (!dir.exists() && !dir.mkdirs()) return "mkdir-failed";
            File out = new File(dir, name);
            copyFile(new FileInputStream(writeTemp(c, png)), new FileOutputStream(out));
            MediaScannerConnection.scanFile(c,
                    new String[]{out.getAbsolutePath()},
                    new String[]{"image/png"}, null);
            return null;
        } catch (Throwable t) {
            return t.getClass().getSimpleName();
        }
    }

    /** Menyimpan byte PNG ke berkas sementara di cache (jalur Android 9 ke bawah). */
    private static File writeTemp(Context c, byte[] png) throws Exception {
        File f = new File(c.getCacheDir(), "share_tmp.png");
        OutputStream os = new FileOutputStream(f);
        os.write(png);
        os.close();
        return f;
    }
}
