package id.cyclocomp;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * Pemuatan font dari assets.
 *
 * Barlow Condensed (angka/judul) dan Barlow (label) dibundel di assets/fonts, bukan
 * bergantung pada font sistem: tampilan harus sama di Android 6 maupun 13, sedangkan
 * font sistem berubah antar versi dan antar pabrikan.
 *
 * Cara kerja: setelah layout di-inflate, seluruh pohon view disisir. TextView yang
 * diberi android:tag="num" memakai Barlow Condensed; sisanya Barlow. Tombol aksi
 * memakai Barlow Condensed SemiBold.
 */
final class Ui {
    private Ui() {}

    private static final String TAG_NUM = "num";

    private static Typeface condBold, condSemi, body;

    static Typeface condBold(Context c) {
        if (condBold == null) condBold = load(c, "BarlowCondensed-Bold.ttf");
        return condBold;
    }

    static Typeface condSemi(Context c) {
        if (condSemi == null) condSemi = load(c, "BarlowCondensed-SemiBold.ttf");
        return condSemi;
    }

    static Typeface body(Context c) {
        if (body == null) body = load(c, "Barlow-Regular.ttf");
        return body;
    }

    /** Gagal memuat font tidak boleh mematikan aplikasi; pakai font sistem sebagai cadangan. */
    private static Typeface load(Context c, String path) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + path);
        } catch (Throwable t) {
            return Typeface.SANS_SERIF;
        }
    }

    /** Terapkan font ke seluruh pohon view. */
    static void fonts(View root, Context c) {
        if (root == null) return;
        if (root instanceof TextView) {
            TextView t = (TextView) root;
            t.setTypeface(TAG_NUM.equals(t.getTag()) ? condBold(c) : body(c));
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) fonts(g.getChildAt(i), c);
        }
    }

    /** Tombol aksi memakai condensed semibold: lebih tegas, huruf lebih rapat. */
    static void buttonFonts(View root, Context c) {
        if (root == null) return;
        if (root instanceof Button) {
            ((Button) root).setTypeface(condSemi(c));
            return;
        }
        // Tombol yang ditulis sebagai TextView (bilah atas Ringkasan) tidak ikut
        // otomatis karena bukan Button. Dulu daftarnya dikunci ke tiga ID, jadi
        // setiap tombol baru yang ditulis sebagai TextView diam-diam jatuh ke font
        // badan — satu aplikasi jadi punya dua wajah tombol tanpa siapa-siapa
        // memutuskan begitu. Sekarang cukup beri android:tag="btn" di layout.
        if (root instanceof TextView && "btn".equals(root.getTag())) {
            ((TextView) root).setTypeface(condSemi(c));
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) buttonFonts(g.getChildAt(i), c);
        }
    }
}
