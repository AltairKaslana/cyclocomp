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
 * Gambar rute gowes.
 *
 * Kelas ini sengaja dipisah dari sumber latar: subkelasnya ({@link TileMapView}) menempelkan
 * ubin peta di belakang rute, sedangkan kelas ini sendiri menggambar latar gelap berpola kisi.
 * Jadi kalau peta gagal dimuat - offline, server lambat, atau izin jaringan dicabut - rute
 * tetap terlihat, bukan kotak kosong.
 *
 * Proyeksi bawaan: ekuirektangular di sekitar titik tengah rute (cukup akurat untuk skala
 * satu perjalanan), lalu diskalakan agar pas dengan bingkai dan margin. Subkelas boleh
 * mengganti proyeksi lewat {@link #prepareProjection(int, int)} dan {@link #project}.
 */
public class RouteView extends View {
    protected final List<GpxReader.P> pts = new ArrayList<GpxReader.P>();

    private final Paint pRoute = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStart = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEnd = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEmpty = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();

    // proyeksi aktif, diisi oleh prepareProjection()
    protected double lat0, lon0, cosLat;
    protected double scale;
    protected float offX, offY;

    public RouteView(Context c) { super(c); init(); }
    public RouteView(Context c, AttributeSet a) { super(c, a); init(); }
    public RouteView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    protected void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // aman di perangkat lama untuk garis halus

        // Warna lewat resource, bukan hex mentah, supaya palet punya SATU sumber
        // kebenaran (colors.xml). Sebelumnya oranye rute ditulis literal di sini,
        // jadi ganti aksen harus mengubah dua tempat sekaligus.
        Context ctx = getContext();

        pGrid.setStyle(Paint.Style.STROKE);
        pGrid.setStrokeWidth(1f);
        pGrid.setColor(0x14FFFFFF);

        pRoute.setStyle(Paint.Style.STROKE);
        pRoute.setStrokeWidth(5f);
        pRoute.setStrokeCap(Paint.Cap.ROUND);
        pRoute.setStrokeJoin(Paint.Join.ROUND);
        pRoute.setColor(ctx.getColor(R.color.accent));

        pRing.setStyle(Paint.Style.FILL);
        pRing.setColor(ctx.getColor(R.color.card));

        pStart.setStyle(Paint.Style.FILL);
        pStart.setColor(ctx.getColor(R.color.rec));

        pEnd.setStyle(Paint.Style.FILL);
        pEnd.setColor(Color.WHITE);

        pEmpty.setColor(ctx.getColor(R.color.label));
        pEmpty.setTextSize(30f);
        pEmpty.setTextAlign(Paint.Align.CENTER);
    }

    public void setTrack(List<GpxReader.P> track) {
        pts.clear();
        if (track != null) pts.addAll(track);
        invalidate();
    }

    public List<GpxReader.P> track() { return pts; }

    /** Latar di belakang rute. Subkelas boleh menggantinya dengan ubin peta. */
    protected void drawBackground(Canvas cv, int w, int h) {
        // Permukaan kartu, bukan hex khusus (#141719 dulu): ganti warna latar
        // cukup satu tempat. Sengaja lebih terang dari bg supaya bidang rute
        // terbaca sebagai panel terpisah.
        cv.drawColor(getContext().getColor(R.color.card));
        // kisi halus: memberi kesan "peta" tanpa menambah data palsu
        int step = Math.max(40, w / 8);
        for (int x = 0; x < w; x += step) cv.drawLine(x, 0, x, h, pGrid);
        for (int y = 0; y < h; y += step) cv.drawLine(0, y, w, y, pGrid);
    }

    /**
     * Siapkan proyeksi untuk ukuran tampilan sekarang.
     * Mengembalikan false bila tidak ada yang bisa digambar.
     */
    protected boolean prepareProjection(int w, int h) {
        if (pts.size() < 2) return false;

        lat0 = 0; lon0 = 0;
        for (GpxReader.P p : pts) { lat0 += p.lat; lon0 += p.lon; }
        lat0 /= pts.size();
        lon0 /= pts.size();

        cosLat = Math.cos(Math.toRadians(lat0));
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (GpxReader.P p : pts) {
            double x = (p.lon - lon0) * cosLat;
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
        scale = Math.min(availW / spanX, availH / spanY);

        offX = (float) (w / 2.0 - (minX + spanX / 2) * scale);
        offY = (float) (h / 2.0 + (minY + spanY / 2) * scale);   // sumbu Y dibalik
        return true;
    }

    /** Ubah lat/lon menjadi koordinat layar memakai proyeksi dari {@link #prepareProjection}. */
    protected void project(double lat, double lon, float[] out) {
        out[0] = (float) ((lon - lon0) * cosLat * scale + offX);
        out[1] = (float) (offY - (lat - lat0) * scale);
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        drawBackground(cv, w, h);

        if (!prepareProjection(w, h)) {
            cv.drawText(getContext().getString(R.string.route_none),
                    w / 2f, h / 2f + pEmpty.getTextSize() / 3f, pEmpty);
            return;
        }

        float[] xy = new float[2];
        path.rewind();
        float sx = 0, sy = 0, ex = 0, ey = 0;
        for (int i = 0; i < pts.size(); i++) {
            GpxReader.P p = pts.get(i);
            project(p.lat, p.lon, xy);
            if (i == 0) { path.moveTo(xy[0], xy[1]); sx = xy[0]; sy = xy[1]; }
            else path.lineTo(xy[0], xy[1]);
            ex = xy[0]; ey = xy[1];
        }
        cv.drawPath(path, pRoute);

        // penanda awal (hijau) dan akhir (putih)
        cv.drawCircle(sx, sy, 11f, pRing);
        cv.drawCircle(sx, sy, 6f, pStart);
        cv.drawCircle(ex, ey, 11f, pRing);
        cv.drawCircle(ex, ey, 6f, pEnd);
    }
}
