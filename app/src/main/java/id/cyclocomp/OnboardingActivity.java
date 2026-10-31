package id.cyclocomp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/**
 * Onboarding 3 langkah (rancangan UI/UX 5.2): lokasi, baterai, sensor.
 *
 * Dua hal yang membuat aktivitas ini tidak sekadar deretan layar:
 *
 *  1. Langkah lokasi TIDAK punya tombol lewat. Tanpa izin lokasi aplikasi ini
 *     tidak berguna sama sekali, jadi menawarkan "lewati" di situ hanya
 *     menjanjikan sesuatu yang tidak bisa dipenuhi. Yang ada: "Izinkan". Kalau
 *     pengguna menolak, onboarding tetap menutup - izin diminta lagi nanti oleh
 *     MainActivity saat tombol MULAI ditekan, bukan dipaksa di wajah pengguna.
 *  2. Setiap langkah bisa dilewati lewat tombol "Lewati" di kanan atas, dan
 *     melewatinya menandai onboarding selesai. Tidak ada alasan untuk memaksa
 *     pengguna masuk ke layar yang sama dua kali.
 *
 * Tidak ada ViewPager: proyek ini tanpa dependency selain junit, dan tiga
 * halaman statis tidak membutuhkannya. Geser ditangani GestureDetector.
 */
public class OnboardingActivity extends Activity {

    private static final int REQ_LOKASI = 0x4F4E; // "ON"

    private LinearLayout dots;
    private View[] pages;
    private Button btnBack;
    private Button btnNext;
    private ImageButton btnSkip;

    private int langkah;
    private GestureDetector geser;

    /**
     * Tema (gelap/siang) dipasang lewat attachBaseContext, sama seperti
     * MainActivity - bukan setTheme di onCreate. Alasan lengkapnya ada di
     * {@link Tema}: penanda konfigurasi, bukan dua tema terpisah.
     */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Tema.bungkus(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_onboarding);

        pages = new View[]{
                findViewById(R.id.onbPage0),
                findViewById(R.id.onbPage1),
                findViewById(R.id.onbPage2)
        };
        dots = (LinearLayout) findViewById(R.id.onbDots);
        btnBack = (Button) findViewById(R.id.btnBack);
        btnNext = (Button) findViewById(R.id.btnNext);
        btnSkip = (ImageButton) findViewById(R.id.btnSkip);

        geser = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (Math.abs(vx) < 220f) return false;
                if (vx < 0) {
                    if (Onboarding.berikutnya(langkah) >= 0) tampilkan(langkah + 1);
                } else if (Onboarding.sebelumnya(langkah) >= 0) {
                    tampilkan(langkah - 1);
                }
                return true;
            }
        });

        btnSkip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selesai(); }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tampilkan(langkah - 1); }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { aksiUtama(); }
        });

        // Kalau izin lokasi sudah ada (diberikan sebelum onboarding dibuka),
        // jangan mulai dari langkah lokasi - meminta izin yang sudah ada hanya
        // membingungkan. Lihat Onboarding.langkahAwal.
        tampilkan(Onboarding.langkahAwal(lokasiDiizinkan()));
    }

    // ---------- keadaan langkah ----------

    private boolean lokasiDiizinkan() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean bateraiDikecualikan() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void tampilkan(int target) {
        if (target < 0 || target >= Onboarding.JUMLAH) return;
        langkah = target;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == langkah ? View.VISIBLE : View.GONE);
        }
        gambarTitik();
        segarkanTombol();
    }

    /** Titik langkah: garis 3dp di tengah bidang sentuh 48dp (pola sama dengan dashboard). */
    private void gambarTitik() {
        if (dots.getChildCount() == 0) {
            for (int i = 0; i < Onboarding.JUMLAH; i++) {
                View d = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
                d.setLayoutParams(lp);
                dots.addView(d);
            }
        }
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == langkah;
            int lineW = dp(on ? 26 : 12);
            int lineH = dp(3);
            int side = (dp(48) - lineW) / 2;
            int top = (dp(48) - lineH) / 2;
            d.setBackground(new InsetDrawable(
                    getResources().getDrawable(on ? R.drawable.dot_on : R.drawable.dot_off),
                    side, top, side, top));
        }
    }

    private void segarkanTombol() {
        btnBack.setVisibility(langkah == 0 ? View.INVISIBLE : View.VISIBLE);
        btnNext.setText(Onboarding.tombolUtama(langkah, lokasiDiizinkan(), bateraiDikecualikan()));
    }

    // ---------- aksi ----------

    private void aksiUtama() {
        switch (langkah) {
            case Onboarding.LOKASI:
                if (lokasiDiizinkan()) {
                    tampilkan(Onboarding.berikutnya(langkah));
                } else {
                    // minSdk 23: izin runtime selalu berlaku, tanpa guard versi
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQ_LOKASI);
                }
                break;
            case Onboarding.BATERAI:
                if (bateraiDikecualikan()) {
                    tampilkan(Onboarding.berikutnya(langkah));
                } else {
                    bukaPengaturanBaterai();
                }
                break;
            default:
                selesai();
        }
    }

    private void bukaPengaturanBaterai() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            // Sebagian ROM (dan beberapa emulator) tidak punya layar itu. Jatuh
            // ke detail aplikasi supaya pengguna tetap punya jalan, bukan
            // menemui tombol yang tidak melakukan apa pun.
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    private void selesai() {
        Settings.setOnboardingDone(this, true);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_LOKASI) return;
        // Diberi izin -> maju sendiri. Ditolak -> tutup juga: izin akan diminta
        // ulang oleh MainActivity saat tombol MULAI ditekan. Menahan pengguna
        // di layar yang tidak bisa mereka lewati bukan onboarding, itu jebakan.
        if (lokasiDiizinkan()) {
            tampilkan(Onboarding.berikutnya(Onboarding.LOKASI));
        } else {
            selesai();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pengguna bisa kembali dari Pengaturan baterai - keadaan berubah di
        // luar aplikasi, jadi tombol utama harus dihitung ulang.
        segarkanTombol();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return geser.onTouchEvent(e) || super.onTouchEvent(e);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
