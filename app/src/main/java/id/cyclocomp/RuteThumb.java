package id.cyclocomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Thumbnail rute di baris Riwayat.
 *
 * Menggambar polyline hasil {@link RuteRingkas} dengan Canvas, bukan peta.
 * Peta ubin di dalam daftar berarti setiap baris mengunduh gambar jaringan -
 * daftar yang digulir akan menghabiskan kuota dan memori untuk informasi yang
 * sebenarnya cuma "bentuk perjalanan ini". Latar kotak dibuat oleh drawable
 * (bg_thumb), jadi kelas ini hanya menggambar garis dan ujung rute.
 */
public class RuteThumb extends View {
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStart = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEnd = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<GpxReader.P> pts = new ArrayList<GpxReader.P>();

    public RuteThumb(Context c) { super(c); init(); }
    public RuteThumb(Context c, AttributeSet a) { super(c, a); init(); }
    public RuteThumb(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        pLine.setStyle(Paint.Style.STROKE);
        pLine.setStrokeWidth(dp(2));
        pLine.setStrokeCap(Paint.Cap.ROUND);
        pLine.setStrokeJoin(Paint.Join.ROUND);
        pLine.setColor(getContext().getColor(R.color.accent));

        pStart.setStyle(Paint.Style.FILL);
        pStart.setColor(getContext().getColor(R.color.rec));

        pEnd.setStyle(Paint.Style.FILL);
        pEnd.setColor(getContext().getColor(R.color.value));
    }

    /** Isi dari teks pratinjau tersimpan; null/kosong mengosongkan thumbnail. */
    public void setPreview(String preview) {
        pts.clear();
        pts.addAll(RuteRingkas.decode(preview));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        if (pts.isEmpty()) return;

        float pad = dp(7);
        float w = getWidth() - pad * 2;
        float h = getHeight() - pad * 2;
        if (w <= 0 || h <= 0) return;

        // Proyeksi ekuirektangular di sekitar titik tengah rute: cukup akurat
        // untuk satu perjalanan dan jauh lebih murah daripada proyeksi peta.
        double lat0 = 0, lon0 = 0;
        for (GpxReader.P p : pts) { lat0 += p.lat; lon0 += p.lon; }
        lat0 /= pts.size();
        lon0 /= pts.size();
        double cosLat = Math.cos(Math.toRadians(lat0));

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GpxReader.P p : pts) {
            double x = (p.lon - lon0) * cosLat;
            double y = p.lat - lat0;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        double spanX = maxX - minX, spanY = maxY - minY;
        double sx = spanX > 1e-12 ? w / spanX : Double.MAX_VALUE;
        double sy = spanY > 1e-12 ? h / spanY : Double.MAX_VALUE;
        double scale = Math.min(sx, sy);
        if (scale == Double.MAX_VALUE) scale = 1;   // rute satu titik: gambar di tengah

        float drawW = (float) (spanX * scale);
        float drawH = (float) (spanY * scale);
        float offX = pad + (w - drawW) / 2f;
        float offY = pad + (h - drawH) / 2f;

        path.reset();
        for (int i = 0; i < pts.size(); i++) {
            GpxReader.P p = pts.get(i);
            float x = offX + (float) (((p.lon - lon0) * cosLat - minX) * scale);
            float y = offY + (float) ((p.lat - lat0 - minY) * scale);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }

        if (pts.size() >= 2) cv.drawPath(path, pLine);
        drawUjung(cv, pts.get(0), offX, offY, minX, minY, cosLat, lat0, lon0, scale, pStart);
        drawUjung(cv, pts.get(pts.size() - 1), offX, offY, minX, minY, cosLat, lat0, lon0, scale, pEnd);
    }

    private void drawUjung(Canvas cv, GpxReader.P p, float offX, float offY,
                           double minX, double minY, double cosLat, double lat0,
                           double lon0, double scale, Paint paint) {
        float x = offX + (float) (((p.lon - lon0) * cosLat - minX) * scale);
        float y = offY + (float) ((p.lat - lat0 - minY) * scale);
        cv.drawCircle(x, y, dp(2), paint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
