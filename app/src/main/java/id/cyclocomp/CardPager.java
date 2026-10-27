package id.cyclocomp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

/**
 * Pager sederhana tanpa dependency: HorizontalScrollView dengan snap per
 * halaman lebar penuh.
 *
 * Kenapa bukan ViewPager: proyek ini sengaja tanpa dependency pihak ketiga.
 *
 * Dua hal yang menentukan "enak atau tidak" saat digeser:
 * 1. Lebar konten harus benar. LinearLayout horizontal di dalam scroll view
 *    WAJIB wrap_content - kalau match_parent, lebarnya dipaksa selebar
 *    viewport, sisa halaman terpotong dan geseran terasa tertahan.
 * 2. Ambang pindah halaman harus kecil. Cukup geser 20% lebar layar atau
 *    lepas dengan kecepatan ringan; tidak perlu sampai setengah layar.
 */
public class CardPager extends HorizontalScrollView {

    public interface OnPageSelectedListener {
        void onPageSelected(int position);
    }

    /** Ambang geser (fraksi lebar halaman) supaya pindah ke halaman sebelah. */
    private static final float FLIP_FRACTION = 0.2f;
    /** Ambang kecepatan lepas jari, px/detik. Ringan saja sudah cukup. */
    private static final int FLIP_VELOCITY = 350;

    private int pageW = -1;
    private int pages = 0;
    private int current = 0;
    private OnPageSelectedListener listener;

    private VelocityTracker tracker;
    private int downX;
    private int slop;

    public CardPager(Context c) { super(c); init(); }
    public CardPager(Context c, AttributeSet a) { super(c, a); init(); }
    public CardPager(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        setSmoothScrollingEnabled(true);
        slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
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
     * Pastikan wadahnya selebar (jumlah halaman x lebar layar) dan tiap
     * halaman selebar viewport.
     */
    private void sizeChildren() {
        if (getChildCount() != 1 || !(getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup inner = (ViewGroup) getChildAt(0);
        ViewGroup.LayoutParams ilp = inner.getLayoutParams();
        if (ilp != null && ilp.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
            ilp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            inner.setLayoutParams(ilp);
        }
        for (int i = 0; i < inner.getChildCount(); i++) {
            View ch = inner.getChildAt(i);
            ViewGroup.LayoutParams lp = ch.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(pageW, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            lp.width = pageW;
            ch.setLayoutParams(lp);
        }
        pages = inner.getChildCount();
        inner.requestLayout();
        scrollTo(current * pageW, 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (pageW <= 0 || pages <= 1) return super.onTouchEvent(ev);

        if (tracker == null) tracker = VelocityTracker.obtain();
        tracker.addMovement(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = (int) ev.getX();
                return super.onTouchEvent(ev);

            case MotionEvent.ACTION_MOVE:
                return super.onTouchEvent(ev);

            case MotionEvent.ACTION_UP: {
                tracker.computeCurrentVelocity(1000);
                float vx = tracker.getXVelocity();
                int dx = (int) (ev.getX() - downX);
                int target = decide(dx, vx);
                // biarkan HorizontalScrollView menutup gestur dulu, lalu kita
                // ambil alih posisinya - supaya tidak ada fling liar.
                boolean r = super.onTouchEvent(ev);
                goTo(target);
                recycleTracker();
                return r;
            }

            case MotionEvent.ACTION_CANCEL:
                recycleTracker();
                snapNearest();
                return super.onTouchEvent(ev);

            default:
                return super.onTouchEvent(ev);
        }
    }

    /**
     * Tentukan halaman tujuan dari jarak geser dan kecepatan lepas.
     * Prioritas: lemparan ringan -> arah lemparan; geseran cukup jauh ->
     * halaman sebelah; selain itu kembali ke halaman terdekat.
     */
    private int decide(int dx, float vx) {
        int jauh = (int) (pageW * FLIP_FRACTION);
        if (vx > FLIP_VELOCITY) return current - 1;   // dilempar ke kanan
        if (vx < -FLIP_VELOCITY) return current + 1;  // dilempar ke kiri
        if (dx < -jauh) return current + 1;
        if (dx > jauh) return current - 1;
        return Math.round(getScrollX() / (float) pageW);
    }

    /** Fling bawaan dimatikan: posisi akhir selalu rapi di batas halaman. */
    @Override
    public void fling(int velocityX) {
        if (pageW <= 0) { super.fling(velocityX); return; }
        if (velocityX > FLIP_VELOCITY) goTo(current - 1);
        else if (velocityX < -FLIP_VELOCITY) goTo(current + 1);
        else snapNearest();
    }

    private void snapNearest() {
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

    private void recycleTracker() {
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        recycleTracker();
        super.onDetachedFromWindow();
    }
}
