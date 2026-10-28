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

    /**
     * Warna kartu diambil dari resource, bukan hex mentah, supaya palet punya
     * SATU sumber kebenaran (colors.xml). ShareCard menggambar ke Bitmap di luar
     * hierarki view sehingga tidak bisa mewarisi tema dari parent; cache per
     * proses sudah cukup karena palet tidak berubah saat runtime.
     *
     * Sebelumnya delapan warna ditulis literal di sini - hasilnya aksen oranye
     * harus diubah di colors.xml DAN di berkas ini, dan mudah salah satu.
     */
    private static int BG, CARD, LABEL, VALUE, ACCENT, DIVIDER, GREEN;
    private static boolean paletSiap = false;

    private static void siapkanPalet(Context c) {
        if (paletSiap) return;
        BG      = c.getColor(R.color.bg);
        CARD    = c.getColor(R.color.card);
        LABEL   = c.getColor(R.color.label);
        VALUE   = c.getColor(R.color.value);
        ACCENT  = c.getColor(R.color.accent);
        DIVIDER = c.getColor(R.color.divider);
        GREEN   = c.getColor(R.color.rec);
        paletSiap = true;
    }

    /** Bayangan tipis di belakang huruf. Bukan warna tema: tetap hitis 80%. */
    private static final int HALO = 0xCC000000;

    /**
     * Warna tema dengan alpha tertentu. Dipakai untuk lapis pudar di atas rute -
     * sebelumnya hex-nya ditulis manual (0x660E1013, 0xCC0E1013) sehingga
     * mengubah warna latar harus mengubah tiga tempat sekaligus.
     */
    private static int alpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }

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
        siapkanPalet(c);
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
        cv.drawText(c.getString(R.string.unit_km), x0 + pDist.measureText(km) + 18f, y + 150f,
                text(body, transparent ? VALUE : LABEL, 44f));
        y += 186f;

        // ---- rute ----
        y += 10f;
        float mapTop = y;
        float mapH = 400f;
        if (!transparent) roundRect(cv, x0, mapTop, x1 - x0, mapH, CARD);
        drawRoute(cv, c, track, x0, mapTop, x1 - x0, mapH);
        y = mapTop + mapH + 34f;

        // ---- Metrik: 3 kolom ----
        // T-12 (B1): nilai dan satuan DIPISAH.
        //
        // Sebelumnya keduanya dirangkai jadi satu string ("45,0 km/jam") lalu
        // digambar dengan satu ukuran huruf besar. Di sel selebar sepertiga
        // kartu, string itu lebih lebar dari selnya sendiri, jadi angka kolom
        // tetangga tertimpa - paling jelas dengan "199,9 km/jam" dan "99:59:59".
        //
        // Sekarang satuan punya barisnya sendiri (kecil, abu), dan angka
        // menyusut sendiri kalau masih terlalu lebar untuk selnya.
        float colW = (x1 - x0 - 2f) / 3f;
        float statTop = y;
        drawStat(cv, cond, body, x0, colW, statTop, c.getString(R.string.label_moving),
                Fmt.duration(row.movingMs), null, transparent);
        drawStat(cv, cond, body, x0 + colW + 1f, colW, statTop, c.getString(R.string.label_avg),
                Fmt.speed(row.avgSpeedMs()), c.getString(R.string.unit_speed), transparent);
        drawStat(cv, cond, body, x0 + 2 * (colW + 1f), colW, statTop, c.getString(R.string.label_max),
                Fmt.speed((float) row.maxSpeedMs), c.getString(R.string.unit_speed), transparent);
        y = statTop + 152f;

        if (!transparent) cv.drawRect(x0, y, x1, y + 1f, solid(DIVIDER));
        y += 30f;

        // ---- Detail: Tanjakan + Waktu Total ----
        Paint pValLabel = text(body, transparent ? VALUE : LABEL, 30f);
        pValLabel.setLetterSpacing(0.18f);
        float half = (x1 - x0) / 2f;

        drawStatPair(cv, cond, body, x0, half, y, c.getString(R.string.label_elev),
                Fmt.meters(row.elevGainM), c.getString(R.string.unit_m), transparent);
        drawStatPair(cv, cond, body, x0 + half, half, y, c.getString(R.string.label_total),
                Fmt.duration(row.durationMs), null, transparent);
        y += 132f;

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

    /**
     * Ukuran huruf terbesar yang masih muat, tidak pernah di bawah min.
     *
     * Dibutuhkan karena angka kartu digambar ke kanwas berukuran tetap (1080px),
     * bukan diukur ulang oleh sistem. Sebelumnya satu ukuran (72f) dipakai untuk
     * semua nilai; kalau nilainya panjang, angka itu keluar selnya dan menimpa
     * kolom sebelah. T-12 (B1).
     */
    private static float autofit(Typeface tf, String value, float start, float min, float maxW) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(start);
        float size = start;
        while (size > min && p.measureText(value) > maxW) {
            size -= 2f;
            p.setTextSize(size);
        }
        return size;
    }

    /**
     * Satu sel statistik pada kartu 4:5.
     *
     * Satuan duduk di baris KECIL sendiri di bawah angka (T-12). Sebelumnya
     * satuan dirangkai ke belakang angka dengan satu ukuran huruf besar; di sel
     * selebar sepertiga kartu hasilnya lebih lebar dari selnya sendiri.
     */
    private static void drawStat(Canvas cv, Typeface cond, Typeface body, float x, float w, float top,
                                 String label, String value, String unit, boolean transparent) {
        Paint pL = text(body, transparent ? VALUE : LABEL, 30f);
        pL.setTextAlign(Paint.Align.CENTER);
        cv.drawText(label, x + w / 2f, top + 26f, pL);

        float vSize = autofit(cond, value, 72f, 40f, w - 16f);
        Paint pV = text(cond, VALUE, vSize);
        pV.setTextAlign(Paint.Align.CENTER);
        cv.drawText(value, x + w / 2f, top + 92f, pV);

        if (unit != null && !unit.isEmpty()) {
            Paint pU = text(body, transparent ? VALUE : LABEL, 30f);
            pU.setTextAlign(Paint.Align.CENTER);
            cv.drawText(unit, x + w / 2f, top + 132f, pU);
        }
    }

    /** Pasangan statistik setengah kartu (tanjakan + waktu total). */
    private static void drawStatPair(Canvas cv, Typeface cond, Typeface body, float x, float w, float top,
                                     String label, String value, String unit, boolean transparent) {
        Paint pL = text(body, transparent ? VALUE : LABEL, 30f);
        pL.setTextAlign(Paint.Align.LEFT);
        cv.drawText(label, x + 12f, top + 22f, pL);

        float vSize = autofit(cond, value, 72f, 40f, w - 24f);
        Paint pV = text(cond, VALUE, vSize);
        pV.setTextAlign(Paint.Align.LEFT);
        cv.drawText(value, x + 12f, top + 92f, pV);

        if (unit != null && !unit.isEmpty()) {
            Paint pU = text(body, transparent ? VALUE : LABEL, 30f);
            pU.setTextAlign(Paint.Align.LEFT);
            cv.drawText(unit, x + 12f, top + 132f, pU);
        }
    }

    private static void drawLaps(Canvas cv, Typeface cond, Typeface body, float x0, float x1, float y, String lapsStr) {
        String[] laps = lapsStr.split("\\|");
        int maxLaps = 3;
        float rowH = 40f;
        Paint pL = text(body, VALUE, 28f);
        Paint pV = text(cond, VALUE, 28f);

        cv.drawText(c.getString(R.string.laps_split), x0, y + 20f, pL);
        y += 30f;

        for (int i = 0; i < Math.min(laps.length, maxLaps); i++) {
            String[] d = laps[i].split(";");
            if (d.length < 4) continue;
            // Lewat Fmt.lap() supaya format dan satuannya sama dengan layar lain.
            // Sebelumnya string-nya ditulis manual di sini dengan satuan "km/h" -
            // bahasa Inggris di kartu berbahasa Indonesia, dan kedua-duanya jadi
            // dua sumber kebenaran untuk format yang sama.
            try {
                int idx = Integer.parseInt(d[0]);
                double meters = Double.parseDouble(d[1]);
                long movingMs = Long.parseLong(d[2]);
                long elapsedMs = Long.parseLong(d[3]);
                float avg = movingMs > 0 ? (float) (meters / (movingMs / 1000.0)) : 0f;
                String rowText = Fmt.lap(idx, meters, elapsedMs, avg);
                cv.drawText(rowText, x0, y + 20f, pV);
            } catch (NumberFormatException ignored) {
                continue;   // data rusak: lewati barisnya, jangan retakkan kartu
            }
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
        siapkanPalet(c);
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

        storyStat(cv, cond, body, top, labelSize, valueSize, c.getString(R.string.label_distance), Fmt.km(row.distanceM), c.getString(R.string.unit_km), transparent);
        // T-04: satu fungsi format. Sebelumnya kartu Story memakai
        // storyDuration() sendiri yang menghasilkan "10j 56m 0d", sementara layar
        // lain menampilkan gowes yang sama sebagai "0:10:56". Dua wajah untuk satu
        // angka adalah persis yang T-04 mau hapus.
        storyStat(cv, cond, body, top + step, labelSize, valueSize, c.getString(R.string.label_moving),
                Fmt.duration(row.movingMs), null, transparent);
        storyStat(cv, cond, body, top + 2 * step, labelSize, valueSize, c.getString(R.string.label_elev), Fmt.meters(row.elevGainM), c.getString(R.string.unit_m), transparent);
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
        siapkanPalet(c);
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        float x0 = MARGIN, x1 = W - MARGIN;

        float mapTop = MARGIN;
        float mapH = 620f;
        drawRoute(cv, c, pts, x0, mapTop, x1 - x0, mapH);
        float y = mapTop + mapH + 40f;

        float colW = (x1 - x0 - 2f) / 3f;
        stickerStat(cv, cond, body, x0, colW, y, c.getString(R.string.label_distance), Fmt.km(row.distanceM), c.getString(R.string.unit_km));
        stickerStat(cv, cond, body, x0 + colW + 1f, colW, y, c.getString(R.string.label_elev), Fmt.meters(row.elevGainM), c.getString(R.string.unit_m));
        stickerStat(cv, cond, body, x0 + 2 * (colW + 1f), colW, y, c.getString(R.string.label_moving), Fmt.duration(row.movingMs), null);
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

        // T-12: angka menyusut sendiri kalau lebih lebar dari selnya. Stempel
        // adalah kartu paling sempit di antara kartu bertiga-kolom, jadi justru
        // di sini tumpang tindih paling mudah terjadi.
        Paint pV = text(cond, VALUE, autofit(cond, value, 72f, 40f, w - 24f));
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

    // =====================================================================
    //  TIGA TEMPLATE BARU - daftar template Strava (TransparentStatsType)
    // =====================================================================

    /**
     * Template STRUK - angka berjajar seperti nota kasir.
     *
     * Dipakai huruf badan (bukan Barlow Condensed) dan garis putus-putus,
     * bukan garis penuh. Struk dikenali dari kerapatan barisnya, bukan dari
     * warnanya; kalau garisnya penuh dan hurufnya tebal hasilnya jadi tabel
     * biasa.
     */
    static Bitmap receiptBitmap(Context c, RideDb.Row row, List<GpxReader.P> track) {
        try {
            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawReceipt(c, cv, row, track);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    static Uri buildReceipt(Context c, RideDb.Row row, List<GpxReader.P> track) {
        Bitmap bmp = receiptBitmap(c, row, track);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            return writePngToGallery(c, bmp, fileName(gpxName, "_receipt"));
        } finally {
            bmp.recycle();
        }
    }

    private static void drawReceipt(Context c, Canvas cv, RideDb.Row row, List<GpxReader.P> track) {
        siapkanPalet(c);
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        cv.drawColor(BG);

        // Pinggiran bergerigi di atas dan bawah: struk dibedakan oleh bentuknya,
        // bukan hanya oleh isinya. Tanpa ini ia terbaca sebagai kartu biasa.
        float paperTop = 0f, paperBottom = H;
        float x0 = MARGIN, x1 = W - MARGIN;
        float notchW = 26f, notchH = 20f;
        Paint pNotch = solid(BG);
        for (float x = 0; x < W; x += notchW * 2f) {
            Path t = new Path();
            t.moveTo(x, paperTop);
            t.lineTo(x + notchW, paperTop);
            t.lineTo(x + notchW / 2f, paperTop + notchH);
            t.close();
            cv.drawPath(t, pNotch);
            Path b = new Path();
            b.moveTo(x, paperBottom);
            b.lineTo(x + notchW, paperBottom);
            b.lineTo(x + notchW / 2f, paperBottom - notchH);
            b.close();
            cv.drawPath(b, pNotch);
        }

        float y = notchH + 30f;

        // ---- kepala struk ----
        Paint pBrand = text(cond, VALUE, 76f);
        pBrand.setLetterSpacing(0.30f);
        pBrand.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.card_footer).toUpperCase(Locale.getDefault()), W / 2f, y + 58f, pBrand);

        Paint pTitle = text(body, LABEL, 30f);
        pTitle.setLetterSpacing(0.30f);
        pTitle.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.receipt_title), W / 2f, y + 100f, pTitle);
        y += 128f;

        y = dashed(cv, x0, x1, y);

        Paint pDate = text(body, LABEL, 28f);
        pDate.setLetterSpacing(0.12f);
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
        cv.drawText(df.format(new Date(row.startMs)).toUpperCase(Locale.getDefault()), x0, y + 30f, pDate);
        y += 52f;

        y = dashed(cv, x0, x1, y);

        // ---- baris rincian: label kiri, nilai kanan, pemisah titik ----
        int kcal = row.caloriesKcal > 0 ? row.caloriesKcal
                : Calories.estimate(row.distanceM, row.movingMs, row.elevGainM,
                        Settings.weight(c));
        int hrAvg = 0, hrMax = 0, hrCount = 0;
        if (track != null) {
            for (GpxReader.P p : track) {
                if (p.hr > 0) {
                    hrAvg += p.hr;
                    hrCount++;
                    if (p.hr > hrMax) hrMax = p.hr;
                }
            }
        }
        if (hrCount > 0) hrAvg = Math.round((float) hrAvg / hrCount);

        receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_distance),
                Fmt.km(row.distanceM) + " " + c.getString(R.string.unit_km));
        y += 62f;
        receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_moving),
                Fmt.duration(row.movingMs));
        y += 62f;
        receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_total),
                Fmt.duration(row.durationMs));
        y += 62f;
        receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_avg),
                Fmt.speed(row.avgSpeedMs()) + " " + c.getString(R.string.unit_speed));
        y += 62f;
        receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_max),
                Fmt.speed((float) row.maxSpeedMs) + " " + c.getString(R.string.unit_speed));
        y += 62f;
        receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_elev),
                Fmt.meters(row.elevGainM) + " " + c.getString(R.string.unit_m));
        y += 62f;
        if (kcal > 0) {
            // T-09: tanda "≈" dibuang, sama seperti di kartu kapsul.
            receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_kcal),
                    Fmt.kcal(kcal));
            y += 62f;
        }
        if (hrCount > 0) {
            receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_hr_avg),
                    String.valueOf(hrAvg) + " " + c.getString(R.string.unit_bpm));
            y += 62f;
            receiptRow(cv, cond, body, x0, x1, y, c.getString(R.string.label_hr_max),
                    String.valueOf(hrMax) + " " + c.getString(R.string.unit_bpm));
            y += 62f;
        }

        y = dashed(cv, x0, x1, y);

        // ---- TOTAL: hanya jarak, besar. Sisanya sudah dirinci di atas. ----
        Paint pTotLabel = text(body, LABEL, 30f);
        pTotLabel.setLetterSpacing(0.30f);
        pTotLabel.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(c.getString(R.string.receipt_total), x1, y + 26f, pTotLabel);

        Paint pTotVal = text(cond, ACCENT, 128f);
        pTotVal.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(Fmt.km(row.distanceM), x1, y + 148f, pTotVal);
        Paint pTotUnit = text(body, LABEL, 34f);
        pTotUnit.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(c.getString(R.string.unit_km), x1, y + 190f, pTotUnit);

        // ---- kaki: ucapan + garis barcode tiruan ----
        float footY = H - notchH - 200f;
        Paint pThanks = text(body, LABEL, 26f);
        pThanks.setLetterSpacing(0.24f);
        pThanks.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.receipt_thanks), W / 2f, footY, pThanks);

        // Barcode dekoratif: lebar batang dari hash baris rincian, supaya tiap
        // struk terlihat berbeda tanpa perlu generator barcode sungguhan.
        // distanceM adalah double, jadi dipaksa ke long dulu sebelum di-XOR.
        Paint pBar = solid(VALUE);
        float bx = W / 2f - 200f;
        long seed = row.startMs ^ ((long) row.distanceM * 31L);
        for (int i = 0; i < 44; i++) {
            seed = seed * 1103515245L + 12345L;
            int bw = 2 + (int) (Math.abs(seed >> 16) % 6);
            cv.drawRect(bx, footY + 26f, bx + bw, footY + 92f, pBar);
            bx += bw + 4f;
            if (bx > W / 2f + 200f) break;
        }
        Paint pFoot = text(body, LABEL, 22f);
        pFoot.setLetterSpacing(0.18f);
        pFoot.setTextAlign(Paint.Align.CENTER);
        cv.drawText(df.format(new Date(row.startMs)), W / 2f, footY + 124f, pFoot);
    }

    /** Garis putus-putus; kembalikan y setelah garis. */
    private static float dashed(Canvas cv, float x0, float x1, float y) {
        Paint p = new Paint();
        p.setColor(DIVIDER);
        p.setStrokeWidth(2f);
        float x = x0;
        while (x < x1) {
            cv.drawRect(x, y, Math.min(x + 14f, x1), y + 2f, p);
            x += 26f;
        }
        return y + 16f;
    }

    /**
     * Satu baris struk: label kiri, nilai kanan, dihubungkan titik-titik.
     *
     * Titik penghubung digambar terpisah, bukan dengan satu Path ber- dashed
     * effect, supaya jaraknya konsisten di semua kerapatan layar.
     */
    private static void receiptRow(Canvas cv, Typeface cond, Typeface body,
                                   float x0, float x1, float y, String label, String value) {
        Paint pL = text(body, LABEL, 30f);
        pL.setLetterSpacing(0.10f);
        cv.drawText(label.toUpperCase(Locale.getDefault()), x0, y + 26f, pL);

        Paint pV = text(cond, VALUE, 38f);
        pV.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(value, x1, y + 28f, pV);

        float dotStart = pL.measureText(label.toUpperCase(Locale.getDefault())) + 22f;
        float dotEnd = x1 - pV.measureText(value) - 22f;
        if (dotEnd - dotStart < 20f) return;
        Paint pDot = solid(DIVIDER);
        float r = 3f;
        float cx = dotStart;
        while (cx < dotEnd) {
            cv.drawCircle(cx, y + 21f, r, pDot);
            cx += 16f;
        }
    }

    /**
     * Template KAPSUL - angka di dalam pil, rute jadi latar redup.
     *
     * Rute digambar lebih dulu dengan alpha rendah supaya terbaca sebagai
     * tekstur, lalu pil diletakkan di atasnya. Kalau urutnya terbalik, garis
     * rute akan terpotong-potong oleh tepi pil dan hasilnya berantakan.
     */
    static Bitmap capsulesBitmap(Context c, RideDb.Row row, List<GpxReader.P> track) {
        try {
            Bitmap bmp = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawCapsules(c, cv, row, track);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    static Uri buildCapsules(Context c, RideDb.Row row, List<GpxReader.P> track) {
        Bitmap bmp = capsulesBitmap(c, row, track);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            return writePngToGallery(c, bmp, fileName(gpxName, "_capsules"));
        } finally {
            bmp.recycle();
        }
    }

    private static void drawCapsules(Context c, Canvas cv, RideDb.Row row, List<GpxReader.P> track) {
        siapkanPalet(c);
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        cv.drawColor(BG);

        // ---- rute sebagai latar: seluruh kanwas, alpha rendah ----
        // T-14 (B7): rute digambar PENUH di belakang, tidak lagi dipotong kotak
        // 40,260..SH-520. Sebelumnya rute hanya mengisi sebuah kotak di tengah,
        // jadi latar di atas dan bawah kartu terasa kosong - rute terlihat seperti
        // gambar tempelan, bukan latar.
        //
        // Opasitasnya juga diturunkan: dulu 0x66 (garis rute tampil ~60%), yang
        // masih lebih kuat dari angka di depannya. Sekarang 0xBF supaya rute
        // tampil sekitar 25% - cukup untuk terlihat sebagai tekstur, tidak cukup
        // untuk berebut perhatian dengan angka.
        if (track != null && track.size() >= 2) {
            Paint save = new Paint();
            int layer = cv.saveLayer(0f, 0f, SW, SH, save);
            drawRoute(cv, c, track, 0f, 0f, SW, SH);
            Paint fade = new Paint();
            fade.setColor(alpha(BG, 0xBF));   // rute tampil ~25%
            fade.setStyle(Paint.Style.FILL);
            cv.drawRect(0f, 0f, SW, SH, fade);
            cv.restoreToCount(layer);
        }

        float cx = SW / 2f;
        Paint pTitle = text(body, LABEL, 32f);
        pTitle.setLetterSpacing(0.34f);
        pTitle.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.capsule_title), cx, 190f, pTitle);

        Paint pBrand = text(cond, VALUE, 58f);
        pBrand.setLetterSpacing(0.30f);
        pBrand.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.card_footer).toUpperCase(Locale.getDefault()), cx, 258f, pBrand);

        int kcal = row.caloriesKcal > 0 ? row.caloriesKcal
                : Calories.estimate(row.distanceM, row.movingMs, row.elevGainM,
                        Settings.weight(c));

        float y = 360f;
        float pillH = 132f;
        float gap = 26f;
        float x0 = 88f, x1 = SW - 88f;

        capsule(cv, cond, body, x0, x1, y, pillH,
                c.getString(R.string.label_distance),
                Fmt.km(row.distanceM), c.getString(R.string.unit_km));
        y += pillH + gap;
        capsule(cv, cond, body, x0, x1, y, pillH,
                c.getString(R.string.label_moving),
                Fmt.duration(row.movingMs), null);
        y += pillH + gap;
        capsule(cv, cond, body, x0, x1, y, pillH,
                c.getString(R.string.label_avg),
                Fmt.speed(row.avgSpeedMs()), c.getString(R.string.unit_speed));
        y += pillH + gap;
        capsule(cv, cond, body, x0, x1, y, pillH,
                c.getString(R.string.label_max),
                Fmt.speed((float) row.maxSpeedMs), c.getString(R.string.unit_speed));
        y += pillH + gap;
        capsule(cv, cond, body, x0, x1, y, pillH,
                c.getString(R.string.label_elev),
                Fmt.meters(row.elevGainM), c.getString(R.string.unit_m));
        y += pillH + gap;
        if (kcal > 0) {
            // T-09: tanda "≈" dibuang sesuai keputusan pemilik. Kalori memang
            // perkiraan, tapi menandai satu baris dari tujuh membuat baris itu
            // terlihat kurang pasti daripada yang sebenarnya.
            capsule(cv, cond, body, x0, x1, y, pillH,
                    c.getString(R.string.label_kcal),
                    Fmt.kcal(kcal), null);
            y += pillH + gap;
        }

        Paint pDate = text(body, LABEL, 28f);
        pDate.setLetterSpacing(0.20f);
        pDate.setTextAlign(Paint.Align.CENTER);
        cv.drawText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(new Date(row.startMs)), cx, SH - 120f, pDate);
    }

    /** Satu pil: sudut membulat penuh (radius = setengah tinggi). */
    private static void capsule(Canvas cv, Typeface cond, Typeface body,
                                float x0, float x1, float y, float h,
                                String label, String value, String unit) {
        Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pFill.setColor(CARD);
        cv.drawRoundRect(new RectF(x0, y, x1, y + h), h / 2f, h / 2f, pFill);

        // Garis tipis di tepi pil: kalau pil ditempel di atas foto terang,
        // isinya tetap bisa dibedakan dari latar.
        Paint pEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        pEdge.setColor(DIVIDER);
        pEdge.setStyle(Paint.Style.STROKE);
        pEdge.setStrokeWidth(2f);
        cv.drawRoundRect(new RectF(x0, y, x1, y + h), h / 2f, h / 2f, pEdge);

        Paint pL = text(body, LABEL, 28f);
        pL.setLetterSpacing(0.22f);
        cv.drawText(label.toUpperCase(Locale.getDefault()), x0 + 56f, y + h / 2f + 10f, pL);

        // T-14 (B7): satuan duduk DI SAMPING angka, di dalam pil - bukan di
        // baris terpisah di bawahnya seperti sebelumnya. Pilnya sempit, jadi
        // menumpuk angka dan satuan di dua baris membuat pil terlihat penuh sesak
        // dan angkanya sendiri jadi kecil. Sebelah-sebelahan, pil tetap satu baris
        // dan angka bisa lebih besar.
        Paint pV = text(cond, VALUE, 66f);
        Paint pU = unit == null ? null : text(body, LABEL, 28f);
        float vW = pV.measureText(value);
        float uW = (pU == null) ? 0f : pU.measureText(unit) + 16f;
        // Kalau angka+satuan lebih lebar dari ruang yang ada di dalam pil
        // (setelah dikurangi ruang untuk label di kiri), angka menyusut.
        float ruangPil = (x1 - 56f) - (x0 + 56f + pL.measureText(
                label.toUpperCase(Locale.getDefault())) + 40f);
        if (vW + uW > ruangPil) {
            pV.setTextSize(autofit(cond, value, 66f, 40f, ruangPil - uW));
            vW = pV.measureText(value);
        }
        float right = x1 - 56f;
        float base = y + h / 2f + 24f;
        pV.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(value, right - uW, base, pV);
        if (pU != null) {
            pU.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(unit, right, base, pU);
        }
    }

    /**
     * Template POLYLINE BESAR - tanpa angka, untuk latar HP.
     *
     * Rasio 9:19.5 (1080x2340) mengikuti layar HP sekarang, bukan 9:16 seperti
     * kartu Story. Struk dan kapsul dipakai untuk dibagikan; yang ini dipakai
     * sebagai latar, jadi harus setinggi layar supaya tidak terpotong.
     *
     * Tidak ada satu pun angka gowes di sini — sengaja. Yang tampil hanya
     * tanda tangan kecil (nama aplikasi + tanggal), bukan statistik.
     */
    private static final int WW = 1080;
    private static final int WH = 2340;

    static Bitmap wallpaperBitmap(Context c, RideDb.Row row, List<GpxReader.P> track) {
        try {
            Bitmap bmp = Bitmap.createBitmap(WW, WH, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            drawWallpaper(c, cv, row, track);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    static Uri buildWallpaper(Context c, RideDb.Row row, List<GpxReader.P> track) {
        Bitmap bmp = wallpaperBitmap(c, row, track);
        if (bmp == null) return null;
        try {
            String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
            return writePngToGallery(c, bmp, fileName(gpxName, "_wallpaper"));
        } finally {
            bmp.recycle();
        }
    }

    private static void drawWallpaper(Context c, Canvas cv, RideDb.Row row, List<GpxReader.P> track) {
        siapkanPalet(c);
        Typeface cond = Ui.condBold(c);
        Typeface body = Ui.body(c);

        cv.drawColor(BG);

        if (track == null || track.size() < 2) {
            // Tanpa rute yang bisa digambar, kartu ini tidak ada isinya.
            // Ditandai supaya ShareActivity bisa menolak dengan jelas.
            Paint pErr = text(body, LABEL, 36f);
            pErr.setTextAlign(Paint.Align.CENTER);
            cv.drawText(c.getString(R.string.card_failed), WW / 2f, WH / 2f, pErr);
            return;
        }

        // Rute memenuhi hampir seluruh kanwas; padding kecil supaya garis
        // tidak menyentuh tepi layar saat dipasang sebagai latar.
        drawRoute(cv, c, track, 30f, 200f, WW - 60f, WH - 400f);

        Paint pMark = text(body, LABEL, 30f);
        pMark.setLetterSpacing(0.40f);
        pMark.setTextAlign(Paint.Align.CENTER);
        cv.drawText(c.getString(R.string.wallpaper_mark).toUpperCase(Locale.getDefault()),
                WW / 2f, WH - 176f, pMark);

        Paint pDate = text(body, LABEL, 26f);
        pDate.setLetterSpacing(0.18f);
        pDate.setTextAlign(Paint.Align.CENTER);
        cv.drawText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(new Date(row.startMs)), WW / 2f, WH - 132f, pDate);
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
        pHalo.setColor(alpha(BG, 0xCC));
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
        pRing.setColor(alpha(BG, 0xCC));
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
