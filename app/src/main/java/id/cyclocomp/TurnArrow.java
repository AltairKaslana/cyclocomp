package id.cyclocomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Panah arah belokan berikutnya.
 *
 * Digambar sendiri, bukan memakai glif teks: panah teks (↰ ↱) tidak tersedia
 * di semua font perangkat lama, dan bentuknya berubah-ubah. Satu bentuk
 * "batang lalu kepala panah" terbaca jelas di layar kecil.
 */
public class TurnArrow extends View {
    public static final int LURUS = 0, KIRI = -1, KANAN = 1;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path stem = new Path();
    private final Path head = new Path();
    private int turn = LURUS;

    public TurnArrow(Context c) { super(c); init(); }
    public TurnArrow(Context c, AttributeSet a) { super(c, a); init(); }
    public TurnArrow(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        p.setColor(getContext().getColor(R.color.accent));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(2.8f);
    }

    public void setTurn(int t) {
        int v = t == KIRI ? KIRI : (t == KANAN ? KANAN : LURUS);
        if (v == turn) return;
        turn = v;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float scale = Math.min(w, h) / 24f;

        stem.rewind();
        head.rewind();
        if (turn == KANAN) {
            stem.moveTo(7, 21);
            stem.lineTo(7, 12);
            stem.lineTo(16, 12);
            head.moveTo(21.5f, 12);
            head.lineTo(14, 6.5f);
            head.lineTo(14, 17.5f);
        } else if (turn == KIRI) {
            stem.moveTo(17, 21);
            stem.lineTo(17, 12);
            stem.lineTo(8, 12);
            head.moveTo(2.5f, 12);
            head.lineTo(10, 6.5f);
            head.lineTo(10, 17.5f);
        } else {
            stem.moveTo(12, 21);
            stem.lineTo(12, 9);
            head.moveTo(12, 2.5f);
            head.lineTo(6, 10);
            head.lineTo(18, 10);
        }
        head.close();

        cv.save();
        cv.scale(scale, scale);
        cv.drawPath(stem, p);
        p.setStyle(Paint.Style.FILL);
        cv.drawPath(head, p);
        p.setStyle(Paint.Style.STROKE);
        cv.restore();
    }
}
