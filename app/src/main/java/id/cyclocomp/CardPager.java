package id.cyclocomp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

/**
 * Pager sederhana tanpa dependency: HorizontalScrollView yang melompat ke
 * halaman terdekat saat jari diangkat, dan memperkirakan arah gerak saat
 * pengguna "melempar" (fling). Dipakai layar Bagikan untuk menggeser preview
 * kartu kiri-kanan.
 *
 * Kenapa bukan ViewPager dari androidx: proyek ini sengaja tanpa dependency
 * pihak ketiga, dan kebutuhan kita sempit - tiga halaman lebar penuh dengan
 * penggeseran dan penanda titik.
 *
 * Halaman diisi oleh Activity sebagai anak LinearLayout (horizontal);
 * lebar tiap halaman disetel otomatis saat ukuran view diketahui.
 */
public class CardPager extends HorizontalScrollView {

    public interface OnPageSelectedListener {
        void onPageSelected(int position);
    }

    private int pageW = -1;
    private int pages = 0;
    private int current = 0;
    private OnPageSelectedListener listener;

    public CardPager(Context c) { super(c); init(); }
    public CardPager(Context c, AttributeSet a) { super(c, a); init(); }
    public CardPager(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
    }

    public void setOnPageSelectedListener(OnPageSelectedListener l) {
        listener = l;
    }

    public int getCurrentPage() { return current; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0) return;
        boolean berubah = w != pageW;
        pageW = w;
        if (berubah) sizeChildren();
    }

    /**
     * Samakan lebar setiap halaman dengan lebar viewport. LinearLayout
     * horizontal dengan anak match_parent TIDAK menumpuk dengan benar kalau
     * dibiarkan diukur sendiri, jadi lebar dipasang di kode.
     */
    private void sizeChildren() {
        if (getChildCount() != 1 || !(getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup inner = (ViewGroup) getChildAt(0);
        for (int i = 0; i < inner.getChildCount(); i++) {
            View ch = inner.getChildAt(i);
            ViewGroup.LayoutParams lp = ch.getLayoutParams();
            if (lp == null) lp = new ViewGroup.LayoutParams(pageW, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.width = pageW;
            ch.setLayoutParams(lp);
        }
        pages = inner.getChildCount();
        scrollTo(current * pageW, 0);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent ev) {
        boolean r = super.onTouchEvent(ev);
        int a = ev.getAction();
        if (a == android.view.MotionEvent.ACTION_UP
                || a == android.view.MotionEvent.ACTION_CANCEL) {
            snap();
        }
        return r;
    }

    /**
     * Lemparan cepat: arah kecepatan menentukan halaman tujuan. Fling asli
     * dari HorizontalScrollView sengaja TIDAK dipanggil supaya posisi akhir
     * selalu rapi di batas halaman.
     */
    @Override
    public void fling(int velocityX) {
        if (pageW <= 0) { super.fling(velocityX); return; }
        int target;
        if (velocityX > 1500) target = current + 1;
        else if (velocityX < -1500) target = current - 1;
        else target = Math.round(getScrollX() / (float) pageW);
        goTo(target);
    }

    /** Geser pelan tanpa lepas jari: lompat ke halaman terdekat. */
    private void snap() {
        if (pageW <= 0) return;
        goTo(Math.round(getScrollX() / (float) pageW));
    }

    private void goTo(int target) {
        if (pageW <= 0 || pages <= 0) return;
        if (target < 0) target = 0;
        if (target > pages - 1) target = pages - 1;
        smoothScrollTo(target * pageW, 0);
        if (target != current) {
            current = target;
            if (listener != null) listener.onPageSelected(current);
        }
    }
}
