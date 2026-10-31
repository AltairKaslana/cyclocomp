package id.cyclocomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Bar progres tipis untuk sisa jarak komponen (rancangan 5.7: tinggi 6dp,
 * radius 3dp, trek warna latar, isi mengikuti status).
 *
 * Digambar sendiri, bukan ProgressBar bawaan: ProgressBar Material membawa
 * animasi dan bingkai yang tidak dipakai di sini, sedangkan satu rounded rect
 * sudah cukup dan tetap ringan di HP lama.
 */
public class BarProgres extends View {
    private final Paint cat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF kotak = new RectF();
    private final int warnaTrek;
    private int warnaIsi;
    private float frac;

    public BarProgres(Context c) {
        super(c);
        warnaTrek = c.getResources().getColor(R.color.bg);
        warnaIsi = c.getResources().getColor(R.color.accent);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** @param f 0..1; nilai di luar dijepit supaya bar tidak keluar kartu. */
    public void set(float f, int warna) {
        frac = f < 0 ? 0 : (f > 1 ? 1 : f);
        warnaIsi = warna;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = h / 2f;

        kotak.set(0, 0, w, h);
        cat.setColor(warnaTrek);
        cv.drawRoundRect(kotak, r, r, cat);

        if (frac <= 0) return;
        // Isi minimum setinggi bar supaya titik awal tetap membulat, bukan
        // garis setipis satu piksel saat pemakaian masih 1%.
        float isi = Math.max(h, w * frac);
        kotak.set(0, 0, isi, h);
        cat.setColor(warnaIsi);
        cv.drawRoundRect(kotak, r, r, cat);
    }
}
