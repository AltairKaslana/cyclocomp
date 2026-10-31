package id.cyclocomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Tujuh batang jarak untuk kartu "7 hari terakhir" di Riwayat.
 *
 * Batang kosong sengaja tetap digambar pendek dan abu-abu, bukan nol: lubang
 * di antara batang terbaca seperti data yang gagal dimuat, sedangkan batang
 * pendek terbaca sebagai "hari itu tidak gowes".
 *
 * Nilai tidak ditulis di sini - teks total di atas kartu sudah memuat angkanya.
 * Batang hanya menunjukkan POLA, jadi tidak ada label sumbu yang harus dibaca.
 */
public class WeekBars extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final double[] data = new double[Minggu.HARI];
    private final int accent;
    private final int kosong;

    public WeekBars(Context c) { super(c); accent = c.getColor(R.color.accent); kosong = c.getColor(R.color.stroke_ctrl); }
    public WeekBars(Context c, AttributeSet a) { super(c, a); accent = c.getColor(R.color.accent); kosong = c.getColor(R.color.stroke_ctrl); }
    public WeekBars(Context c, AttributeSet a, int s) { super(c, a, s); accent = c.getColor(R.color.accent); kosong = c.getColor(R.color.stroke_ctrl); }

    public void setData(double[] perHari) {
        for (int i = 0; i < data.length; i++) {
            data[i] = perHari != null && i < perHari.length ? Math.max(0, perHari[i]) : 0;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float gap = dp(5);
        float barW = (w - gap * (Minggu.HARI - 1)) / Minggu.HARI;
        float minTinggi = dp(6);
        float kosongTinggi = dp(2);

        double maks = 0;
        for (double v : data) if (v > maks) maks = v;

        for (int i = 0; i < Minggu.HARI; i++) {
            float tinggi = data[i] <= 0 || maks <= 0
                    ? kosongTinggi
                    : (float) Math.max(minTinggi, h * data[i] / maks);
            paint.setColor(data[i] <= 0 ? kosong : accent);
            float left = i * (barW + gap);
            rect.set(left, h - tinggi, left + barW, h);
            cv.drawRoundRect(rect, dp(3), dp(3), paint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
