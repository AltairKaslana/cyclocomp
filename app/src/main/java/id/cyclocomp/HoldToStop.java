package id.cyclocomp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Tahan-untuk-berhenti dengan progres (T-08).
 *
 * Masalahnya: BERHENTI mengakhiri perjalanan. Kalau cukup satu ketukan, satu
 * kepencet di jalan bergelombang - atau satu kali jari menyenggol saat mencoba
 * menekan JEDA - membuang seluruh gowes. Sebelumnya tombol ini memakai
 * OnLongClickListener: aman, tapi TANPA umpan balik. Pengguna menahan, tidak
 * ada yang bergerak, dan setelah jeda yang tidak diketahui lama lamanya tiba-
 * tiba gowesnya berakhir. Orang menekan lebih keras, atau menyerah dan
 * mengira tombolnya rusak.
 *
 * Solusinya: bar progres 4dp di dasar tombol yang tumbuh dari 0 ke lebar penuh
 * selama ditahan. Sekarang "berapa lama lagi" terlihat, bukan dirasakan. Bar
 * penuh + getar = berhenti. Dilepas sebelum penuh = kembali ke nol, tidak
 * berhenti, tidak ada yang hilang.
 *
 * Durasinya sengaja TIDAK diubah: {@link ViewConfiguration#getLongPressTimeout()}
 * adalah nilai yang sama yang dipakai OnLongClickListener sebelumnya, jadi
 * kebiasaan lama tetap berlaku.
 *
 * Kelas ini menggantikan OnTouchListener tombol - termasuk umpan balik tekan
 * dari {@link Nav#pressFeedback(View)}, karena satu View hanya bisa punya satu
 * OnTouchListener. Skala dan durasinya diambil dari {@link Nav#pressScale}
 * supaya tombol BERHENTI menyusut persis seperti tombol LAP.
 */
final class HoldToStop implements View.OnTouchListener {

    /** Dipanggil sekali, saat tahanan penuh. */
    interface Callback {
        void onStop();
    }

    /**
     * Dipanggil kalau jari dilepas sebelum penuh, dengan pecahan 0..1 sejauh
     * mana pengguna sempat menahan. Dipakai untuk mengingatkan "tahan", tapi
     * hanya kalau benar-benar baru diketuk - bukan kalau sudah 90% dan kurang
     * sedetik.
     */
    interface AbortListener {
        void onAbort(float fraction);
    }

    private final View bar;
    private final Callback cb;
    private final AbortListener abort;
    private final long holdMs;

    private ValueAnimator anim;

    HoldToStop(View progressBar, Callback cb, AbortListener abort) {
        this.bar = progressBar;
        this.cb = cb;
        this.abort = abort;
        this.holdMs = ViewConfiguration.getLongPressTimeout();
    }

    /** Lebar penuh bar progres: lebar induknya, bukan lebar bar itu sendiri. */
    private int trackWidth() {
        View parent = (View) bar.getParent();
        int w = parent == null ? 0 : parent.getWidth();
        return w > 0 ? w : bar.getWidth();
    }

    private void setBarWidth(int px) {
        if (bar.getLayoutParams().width == px) return;
        bar.getLayoutParams().width = px;
        bar.requestLayout();
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                Nav.pressScale(v, 0.97f);
                anim = ValueAnimator.ofFloat(0f, 1f).setDuration(holdMs);
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        float f = (Float) a.getAnimatedValue();
                        setBarWidth((int) (trackWidth() * f));
                    }
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    private boolean cancelled;

                    @Override
                    public void onAnimationCancel(Animator a) {
                        cancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator a) {
                        if (cancelled) return;
                        // Getar lebih dulu, baru berhenti: getar datang segera,
                        // sedangkan Ringkasan butuh waktu untuk muncul. Kalau
                        // urutannya dibalik, getar terasa datang setelah layar
                        // sudah pindah.
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        if (cb != null) cb.onStop();
                    }
                });
                anim.start();
                // true: kita mau terus menerima event sampai jari lepas.
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float f = anim == null ? 0f
                        : (Float) anim.getAnimatedValue();
                if (anim != null) anim.cancel();
                setBarWidth(0);
                Nav.pressScale(v, 1f);
                // Ingatkan "tahan" hanya kalau benar-benar baru diketuk. Yang
                // sudah menahan 90% dan kurang sedetik tidak perlu ditegur -
                // mereka sudah paham, dan Toast yang muncul justru mengganggu.
                if (abort != null && f < 0.5f) abort.onAbort(f);
                return true;

            default:
                return false;
        }
    }
}
