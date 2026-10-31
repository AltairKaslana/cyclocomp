package id.cyclocomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Pita rute di Sekilas: rute impor yang diputar mengikuti arah jalan.
 *
 * Arah maju selalu ke atas dan penanda pengendara tetap di bawah tengah, jadi
 * yang bergerak adalah jalannya - bukan panahnya. Belokan berikutnya terbaca
 * tanpa perlu memutar peta di kepala. Ini pengganti peta ubin selama ada rute
 * navigasi; tanpa rute, Sekilas tetap memakai {@link TileMapView}.
 *
 * Hanya jalan di sekitar posisi yang digambar (50 m ke belakang, 195 m ke
 * depan). Rute 12000 titik tidak perlu digambar seluruhnya tiap detik.
 */
public class NavRibbon extends View {
    private static final double BEHIND_M = 50.0;
    private static final double FORWARD_M = 130.0;
    private static final double WINDOW_M = FORWARD_M * 1.5;
    private static final double SIDE_MIN_M = 250.0;

    private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPast = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNext = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTurn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDanger = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private NavRoute route;
    private double alongM, headingDeg, turnAlongM;
    private int turnDir;
    private boolean off;

    // transformasi gambar, diisi di onDraw
    private float scale, markerX, markerY;
    private double cx, cy, sinH, cosH;

    public NavRibbon(Context c) { super(c); init(); }
    public NavRibbon(Context c, AttributeSet a) { super(c, a); init(); }
    public NavRibbon(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        Context ctx = getContext();
        float d = getResources().getDisplayMetrics().density;

        pGrid.setStyle(Paint.Style.STROKE);
        pGrid.setStrokeWidth(1f);
        pGrid.setColor(ctx.getColor(R.color.divider));

        pPast.setStyle(Paint.Style.STROKE);
        pPast.setStrokeWidth(3f * d);
        pPast.setStrokeCap(Paint.Cap.ROUND);
        pPast.setStrokeJoin(Paint.Join.ROUND);
        pPast.setColor(ctx.getColor(R.color.stroke_ctrl));

        pNext.setStyle(Paint.Style.STROKE);
        pNext.setStrokeWidth(3.5f * d);
        pNext.setStrokeCap(Paint.Cap.ROUND);
        pNext.setStrokeJoin(Paint.Join.ROUND);
        pNext.setColor(ctx.getColor(R.color.accent));

        pMark.setStyle(Paint.Style.FILL);
        pMark.setColor(ctx.getColor(R.color.value));

        pRing.setStyle(Paint.Style.FILL);
        pRing.setColor(ctx.getColor(R.color.card));

        pTurn.setStyle(Paint.Style.FILL);
        pTurn.setColor(ctx.getColor(R.color.accent));

        pDanger.setStyle(Paint.Style.FILL);
        pDanger.setColor(ctx.getColor(R.color.danger));
    }

    /**
     * @param diLuar posisi GPS terlalu jauh dari garis rute; penanda diberi
     *               cincin merah. Ambangnya milik pemanggil, supaya teks dan
     *               gambar tidak memakai dua batas berbeda.
     */
    public void setSnap(NavRoute r, NavRoute.Snap s, boolean diLuar) {
        route = r;
        alongM = s == null ? 0 : s.alongM;
        headingDeg = s == null ? 0 : s.courseDeg;
        turnAlongM = s == null ? 0 : s.turnAlongM;
        turnDir = s == null ? NavRoute.LURUS : s.turnDir;
        off = diLuar;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = getResources().getDisplayMetrics().density;

        float step = Math.max(28f * d, h / 4f);
        for (float y = h - step; y > 0; y -= step) cv.drawLine(0, y, w, y, pGrid);
        for (float x = w / 2f - step; x > 0; x -= step) cv.drawLine(x, 0, x, h, pGrid);
        for (float x = w / 2f + step; x < w; x += step) cv.drawLine(x, 0, x, h, pGrid);

        if (route == null || route.size() < 2) return;

        markerX = w / 2f;
        markerY = h - 14f * d;
        scale = (float) ((markerY - 8f * d) / FORWARD_M);
        if (w / scale < SIDE_MIN_M) scale = (float) (w / SIDE_MIN_M);
        cx = route.eastAt(alongM);
        cy = route.northAt(alongM);
        double rad = Math.toRadians(headingDeg);
        sinH = Math.sin(rad);
        cosH = Math.cos(rad);

        int s0 = route.indexNear(alongM);
        int i0 = route.indexNear(Math.max(0, alongM - BEHIND_M));
        int i1 = route.indexNear(Math.min(route.lengthM(), alongM + WINDOW_M));
        int last = Math.min(i1 + 1, route.size() - 1);

        // Bagian yang sudah dilalui: abu, supaya yang belum dilewati menonjol.
        path.rewind();
        if (i0 <= s0) {
            path.moveTo(px(i0), py(i0));
            for (int i = i0 + 1; i <= s0; i++) path.lineTo(px(i), py(i));
            path.lineTo(markerX, markerY);
            cv.drawPath(path, pPast);
        }

        // Bagian di depan: warna aksen, dari posisi sekarang sampai ujung jendela.
        path.rewind();
        path.moveTo(markerX, markerY);
        for (int i = Math.max(s0 + 1, 0); i <= last; i++) path.lineTo(px(i), py(i));
        cv.drawPath(path, pNext);

        // Titik belokan berikutnya (kalau masih di jendela dan bukan ujung rute).
        if (turnDir != NavRoute.LURUS && turnAlongM < route.lengthM() - 0.5
                && turnAlongM >= alongM - BEHIND_M && turnAlongM <= alongM + WINDOW_M) {
            float tx = projectX(route.eastAt(turnAlongM), route.northAt(turnAlongM));
            float ty = projectY(route.eastAt(turnAlongM), route.northAt(turnAlongM));
            cv.drawCircle(tx, ty, 7f * d, pRing);
            cv.drawCircle(tx, ty, 4f * d, pTurn);
        }

        // Ujung rute terlihat kalau sudah dekat.
        double end = route.lengthM();
        if (end >= alongM - BEHIND_M && end <= alongM + WINDOW_M) {
            float ex = projectX(route.eastM(route.size() - 1), route.northM(route.size() - 1));
            float ey = projectY(route.eastM(route.size() - 1), route.northM(route.size() - 1));
            cv.drawCircle(ex, ey, 6f * d, pRing);
            cv.drawCircle(ex, ey, 3.5f * d, pMark);
        }

        // Penanda pengendara: cincin merah kalau posisi jauh dari garis rute.
        cv.drawCircle(markerX, markerY, 8f * d, off ? pDanger : pRing);
        cv.drawCircle(markerX, markerY, 4.5f * d, pMark);
    }

    private float px(int i) { return projectX(route.eastM(i), route.northM(i)); }

    private float py(int i) { return projectY(route.eastM(i), route.northM(i)); }

    private float projectX(double east, double northMeters) {
        double de = east - cx, dn = northMeters - cy;
        return (float) (markerX + scale * (de * cosH - dn * sinH));
    }

    private float projectY(double east, double northMeters) {
        double de = east - cx, dn = northMeters - cy;
        return (float) (markerY - scale * (de * sinH + dn * cosH));
    }
}
