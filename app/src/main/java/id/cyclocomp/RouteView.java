package id.cyclocomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Gambar rute gowes tanpa peta daring.
 *
 * Alasan tidak memakai pustaka peta: aplikasi ini tanpa dependency dan tanpa izin
 * internet. Rute digambar dari koordinat GPS sendiri, jadi tetap tampil walau HP
 * sedang offline - justru kondisi yang umum saat gowes di luar kota.
 *
 * Proyeksi: ekuirektangular di sekitar titik tengah rute (cukup akurat untuk skala
 * satu perjalanan), lalu diskalakan agar pas dengan bingkai dan margin.
 */
public class RouteView extends View {
    private final List<GpxReader.P> pts = new ArrayList<GpxReader.P>();

    private final Paint pRoute = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStart = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEnd = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEmpty = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();
    private boolean kosong;

    public RouteView(Context c) { super(c); init(); }
    public RouteView(Context c, AttributeSet a) { super(c, a); init(); }
    public RouteView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // aman di perangkat lama untuk garis halus

        pGrid.setStyle(Paint.Style.STROKE);
        pGrid.setStrokeWidth(1f);
        pGrid.setColor(0x14FFFFFF);

        pRoute.setStyle(Paint.Style.STROKE);
        pRoute.setStrokeWidth(5f);
        pRoute.setStrokeCap(Paint.Cap.ROUND);
        pRoute.setStrokeJoin(Paint.Join.ROUND);
        pRoute.setColor(Color.parseColor("#FC4C02"));

        pRing.setStyle(Paint.Style.FILL);
        pRing.setColor(Color.parseColor("#1A1D21"));

        pStart.setStyle(Paint.Style.FILL);
        pStart.setColor(Color.parseColor("#22C55E"));

        pEnd.setStyle(Paint.Style.FILL);
        pEnd.setColor(Color.WHITE);

        pEmpty.setColor(Color.parseColor("#8A9099"));
        pEmpty.setTextSize(30f);
        pEmpty.setTextAlign(Paint.Align.CENTER);
    }

    public void setTrack(List<GpxReader.P> track) {
        pts.clear();
        if (track != null) pts.addAll(track);
        kosong = pts.size() < 2;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // bingkai
        cv.drawColor(Color.parseColor("#141719"));

        // kisi halus: memberi kesan "peta" tanpa menambah data palsu
        int step = Math.max(40, w / 8);
        for (int x = 0; x < w; x += step) cv.drawLine(x, 0, x, h, pGrid);
        for (int y = 0; y < h; y += step) cv.drawLine(0, y, w, y, pGrid);

        if (kosong) {
            cv.drawText("Rute tidak tersedia", w / 2f, h / 2f, pEmpty);
            return;
        }

        double lat0 = 0, lon0 = 0;
        for (GpxReader.P p : pts) { lat0 += p.lat; lon0 += p.lon; }
        lat0 /= pts.size();
        lon0 /= pts.size();

        double cos = Math.cos(Math.toRadians(lat0));
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GpxReader.P p : pts) {
            double x = (p.lon - lon0) * cos;
            double y = (p.lat - lat0);
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        double spanX = Math.max(maxX - minX, 1e-9);
        double spanY = Math.max(maxY - minY, 1e-9);
        float pad = 26f;
        float availW = w - 2 * pad, availH = h - 2 * pad;
        // satu skala untuk kedua sumbu supaya bentuk rute tidak gepeng
        double scale = Math.min(availW / spanX, availH / spanY);

        float offX = (float) (w / 2.0 - (minX + spanX / 2) * scale);
        float offY = (float) (h / 2.0 + (minY + spanY / 2) * scale);   // sumbu Y dibalik

        path.rewind();
        float sx = 0, sy = 0, ex = 0, ey = 0;
        for (int i = 0; i < pts.size(); i++) {
            GpxReader.P p = pts.get(i);
            float x = (float) ((p.lon - lon0) * cos * scale + offX);
            float y = (float) (offY - (p.lat - lat0) * scale);
            if (i == 0) { path.moveTo(x, y); sx = x; sy = y; }
            else path.lineTo(x, y);
            ex = x; ey = y;
        }
        cv.drawPath(path, pRoute);

        // penanda awal (hijau) dan akhir (putih)
        cv.drawCircle(sx, sy, 11f, pRing);
        cv.drawCircle(sx, sy, 6f, pStart);
        cv.drawCircle(ex, ey, 11f, pRing);
        cv.drawCircle(ex, ey, 6f, pEnd);
    }
}
