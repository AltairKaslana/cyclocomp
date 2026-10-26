package id.cyclocomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * ELEMEN TANDA JANGAN APLIKASI INI: ruas jalan yang miring.
 *
 * Sepeda adalah salah satu dari sangat sedikit aplikasi di mana "tanjakan" bukan
 * angka abstrak, tapi benda fisik yang ada di depan mata pemakainya. Jadi indikator
 * gradien di sini digambar sebagai RUAS JALAN yang benar-benar miring: sudutnya
 * mengikuti gradien. Naik 10% jalan naik, turun 10% jalan turun, rata jalan datar.
 *
 * Kenapa menggantikan panah naik/turun:
 *   - Panah hanya MEMBERI TAHU arah. Jalan yang miring memperlihatkan arah SEKALIGUS
 *     besarannya dalam satu gerak mata — keduanya terbaca dari satu bentuk.
 *   - Panah naik/turun bisa dipakai aplikasi lain. Jalan yang miring tidak: elemen
 *     ini hanya punya arti di aplikasi sepeda.
 *   - Buang satu glyph berarti pil lebih pendek, dan satu hal kurang untuk dibaca.
 *
 * Catatan jujur soal sudut: sudut NYATA tanjakan g% adalah atan(g/100), tapi 10%
 * hanya 5,7 derajat — nyaris tak terlihat di ruang seluas ini. Jadi sudut
 * diperbesar {@link #AMPLIFY}x lalu dibatasi {@link #MAX_DEG}. Ini indikator
 * KUALITATIF; nilai persisnya tetap ditunjukkan angka di sebelahnya. Yang tidak
 * dilakukan: membiarkan jalan terbalik atau menyesatkan arah.
 *
 * Warna sengaja TIDAK menambah warna baru ke palet. Permukaan jalan pakai
 * card_pressed (satu tingkat di atas pil), garis tengah pakai accent_soft — peran
 * yang dulu dipegang panah. Satu aksen oranye, seperti sebelumnya.
 */
public class GradeRoad extends View {

    /** Pengali sudut supaya tanjakan khas terlihat. Lihat catatan di dokumentasi kelas. */
    private static final double AMPLIFY = 3.5;

    /** Batas sudut, derajat. Di atas ini jalan sudah terbaca "dinding", bukan tanjakan. */
    private static final double MAX_DEG = 32.0;

    /** Di bawah ini dianggap rata. Sama dengan ambang yang dipakai MainActivity. */
    private static final double FLAT_EPS = 0.15;

    private final Paint pRoad = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF roadRect = new RectF();
    private final Path linePath = new Path();

    private double grade = 0.0;

    private float roadH, padX;

    public GradeRoad(Context c) { super(c); init(); }
    public GradeRoad(Context c, AttributeSet a) { super(c, a); init(); }
    public GradeRoad(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        // getResources().getColor(int, Theme) tersedia sejak API 23 = minSdk proyek ini,
        // dan TIDAK deprecated — tidak perlu androidx hanya untuk ambil warna.
        pRoad.setStyle(Paint.Style.FILL);
        pRoad.setColor(getResources().getColor(R.color.card_pressed, getContext().getTheme()));

        pLine.setStyle(Paint.Style.STROKE);
        pLine.setStrokeCap(Paint.Cap.ROUND);
        pLine.setColor(getResources().getColor(R.color.accent_soft, getContext().getTheme()));

        float d = getResources().getDisplayMetrics().density;
        roadH = 9f * d;   // tebal permukaan jalan
        padX = 4f * d;    // supaya ujung jalan tidak mepet tepi view
        pLine.setStrokeWidth(1.6f * d);

        // Garis putus-putus: meniru tanda tengah jalan, dan membuat kemiringan JAUH
        // lebih terbaca daripada pita polos — mata punya banyak acuan sepanjang garis.
        pLine.setPathEffect(new DashPathEffect(new float[]{3f * d, 2.6f * d}, 0));
    }

    /**
     * Atur gradien, persen. Positif = naik, negatif = turun.
     * Aman dipanggil dari thread mana pun: View.invalidate() mengantre ke UI thread.
     */
    public void setGrade(double percent) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)) percent = 0.0;
        // Data GPS masuk beberapa kali per detik; jangan gambar ulang kalau tak berubah.
        if (Math.abs(percent - grade) < 0.001) return;
        grade = percent;
        invalidate();
    }

    /** Sudut rotasi jalan, derajat. 0 = datar. */
    private double angleDeg() {
        if (Math.abs(grade) < FLAT_EPS) return 0.0;
        double deg = Math.toDegrees(Math.atan(grade / 100.0)) * AMPLIFY;
        return Math.max(-MAX_DEG, Math.min(MAX_DEG, deg));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;

        // Ruas jalan digambar MENDATAR dulu, lalu seluruh kanvas diputar. Lebih mudah
        // dipikirkan daripada menghitung ujung-ujung garis miring sendiri.
        roadRect.set(padX, cy - roadH / 2f, w - padX, cy + roadH / 2f);

        linePath.reset();
        linePath.moveTo(roadRect.left, cy);
        linePath.lineTo(roadRect.right, cy);

        int save = cv.save();
        cv.rotate((float) angleDeg(), cx, cy);
        cv.drawRoundRect(roadRect, roadH / 2f, roadH / 2f, pRoad);
        cv.drawPath(linePath, pLine);
        cv.restoreToCount(save);
    }
}
