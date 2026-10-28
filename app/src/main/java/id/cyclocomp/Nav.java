package id.cyclocomp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Navigasi antar-layar dan umpan balik tekan tombol.
 *
 * Sebelum ada kelas ini, Cyclocomp tidak punya animasi sama sekali: nol berkas di
 * res/anim, nol overridePendingTransition, nol animator. Setiap perpindahan layar
 * adalah potongan keras. Emil-design-eng (skill open-design) menyebut persis ini:
 * "Preventing jarring changes: elements appearing or disappearing without
 * transition feel broken."
 *
 * Durasi dan easing dipilih mengikuti aturan yang sama:
 *   - "UI animations should stay under 300ms" -> 160 ms masuk / 140 ms keluar
 *   - "Never use ease-in for UI animations"   -> yang masuk decelerate
 *   - "Never animate from scale(0)"           -> skala mulai 0.98, terlihat
 *
 * Sengaja TIDAK memakai library apa pun: Cyclocomp tanpa dependency pihak ketiga.
 * View.animate() dan overridePendingTransition() sudah ada di framework sejak
 * API 5/12, jauh di bawah minSdk 23.
 */
final class Nav {
    private Nav() {}

    private static final int ENTER_MS = 160;
    private static final int EXIT_MS = 140;

    /** Skala tombol saat ditekan. 0.97 cukup terasa, tidak menggeser tata letak. */
    private static final float PRESS_SCALE = 0.97f;
    private static final int PRESS_MS = 90;

    /**
     * Buka aktivitas lain dengan transisi. Ganti startActivity(new Intent(...)).
     */
    static void open(Context from, Class<? extends Activity> to) {
        from.startActivity(new Intent(from, to));
        if (from instanceof Activity) {
            ((Activity) from).overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
        }
    }

    /**
     * Tutup aktivitas ini dengan transisi cermin dari open(). Panggil dari finish().
     */
    static void close(Activity a) {
        a.finish();
        a.overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
    }

    /**
     * Skala tombol ke nilai tertentu (1f = ukuran asli).
     *
     * Dipisah dari {@link #pressFeedback(View)} supaya kelas yang mengambil alih
     * onTouch sebuah tombol - {@link HoldToStop} pada tombol BERHENTI - bisa
     * memberi umpan balik tekan yang SAMA tanpa menduplikasi konstanta skala dan
     * durasi. Kalau konstanta itu tersalin, suatu hari tombol BERHENTI menyusut
     * dengan durasi berbeda dari tombol LAP dan tidak ada yang tahu kenapa.
     */
    static void pressScale(View v, float scale) {
        v.animate().scaleX(scale).scaleY(scale).setDuration(PRESS_MS).start();
    }

    /**
     * Umpan balik tekan: tombol menyusut sedikit saat disentuh, kembali saat dilepas.
     *
     * Emil-design-eng: "Feedback: a button scales down on press, confirming the
     * interface heard the user." Di Cyclocomp ini bukan hiasan — pemakai memakai
     * sarung tangan di atas sepeda yang bergerak, jadi konfirmasi bahwa sentuhan
     * diterima jauh lebih berharga daripada di aplikasi biasa.
     *
     * Ini UMPAN BALIK, bukan transisi. Karena itu ia tetap dipakai pada tombol yang
     * ditekan berulang (LAP) tanpa melanggar aturan "never animate
     * keyboard-initiated actions" — larangan itu menyangkut transisi yang membuat
     * aksi berulang terasa lambat, bukan konfirmasi sentuhan.
     *
     * Mengembalikan false dari OnTouchListener supaya OnClickListener tetap jalan.
     */
    static void pressFeedback(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pressScale(view, PRESS_SCALE);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        pressScale(view, 1f);
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }
}
