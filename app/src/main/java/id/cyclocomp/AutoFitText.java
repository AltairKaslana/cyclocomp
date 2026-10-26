package id.cyclocomp;

import android.content.Context;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * TextView yang mengecil sampai muat.
 *
 * Masalah yang dipecahkan: angka kecepatan di halaman SEKILAS berukuran 112sp dan
 * duduk di dalam wadah setinggi sisa layar. Di ponsel tinggi itu aman, tapi di layar
 * PENDEK atau LANDSCAPE wadahnya menjadi lebih kecil dari tinggi baris teks —
 * LinearLayout tidak mengerutkan anak di bawah ukuran terukurnya, jadi angkanya
 * TERPOTONG, bukan mengecil. Justru pada pemasangan di setang sepeda (landscape)
 * inilah yang paling mungkin terjadi.
 *
 * Kenapa tidak pakai android:autoSizeTextType: butuh API 26, sedangkan minSdk
 * proyek ini 23. Tanpa dependency pihak ketiga, opsi yang tersisa adalah menghitung
 * sendiri.
 *
 * Cara pakai: beri layout_height="match_parent" (atau 0dp) supaya view DIBATASI
 * wadah, bukan mengukur diri sendiri sebesar hurufnya.
 */
public class AutoFitText extends TextView {

    /** Ukuran terkecil yang masih boleh dipakai, px. Di bawah ini angka tak terbaca. */
    private static final float MIN_PX_DP = 26f;

    /** Langkah pengecilan per percobaan, px. Cukup halus supaya tak ada lompatan terlihat. */
    private static final float STEP_DP = 1.5f;

    private float maxPx = 0f;
    private boolean adjusting = false;

    public AutoFitText(Context c) { super(c); }
    public AutoFitText(Context c, AttributeSet a) { super(c, a); }
    public AutoFitText(Context c, AttributeSet a, int s) { super(c, a, s); }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refit(w, h);
    }

    @Override
    protected void onTextChanged(CharSequence t, int start, int before, int after) {
        super.onTextChanged(t, start, before, after);
        // Panjang teks berubah (mis. "9,9" jadi "100,0") butuh ruang berbeda.
        refit(getWidth(), getHeight());
    }

    /**
     * Pilih ukuran huruf terbesar, tidak lebih dari ukuran gaya aslinya, yang masih
     * muat di ruang yang tersedia.
     */
    private void refit(int availW, int availH) {
        if (adjusting) return;                       // cegah rekursi dari setTextSize
        if (availW <= 0 || availH <= 0) return;

        CharSequence t = getText();
        if (TextUtils.isEmpty(t)) return;

        // Ukuran asli dari gaya hanya dicatat SEKALI. Kalau tidak, setiap pengecilan
        // akan jadi ukuran baru "maksimum" dan huruf menyusut terus sampai kecil.
        if (maxPx <= 0f) maxPx = getTextSize();

        float d = getResources().getDisplayMetrics().density;
        float minPx = MIN_PX_DP * d;
        float step = STEP_DP * d;

        // Ukur dengan Paint SALINAN: tidak mengubah ukuran sungguhan tiap percobaan,
        // jadi tidak memicu layout berulang. Typeface ikut tersalin, jadi pengukuran
        // akurat untuk Barlow Condensed.
        Paint probe = new Paint(getPaint());
        String s = t.toString();
        float size = maxPx;
        while (size > minPx) {
            probe.setTextSize(size);
            Paint.FontMetrics fm = probe.getFontMetrics();
            float textH = fm.descent - fm.ascent;
            if (probe.measureText(s) <= availW && textH <= availH) break;
            size -= step;
        }

        if (Math.abs(size - getTextSize()) > 0.5f) {
            adjusting = true;
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            adjusting = false;
        }
    }
}
