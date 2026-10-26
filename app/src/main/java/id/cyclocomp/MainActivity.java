package id.cyclocomp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard. Hanya menampilkan dan mengirim perintah; semua state ada di RideService.
 *
 * Tiga halaman data digeser: SEKILAS (angka dominan), DETAIL (kisi metrik), LAP.
 * Tanpa ViewPager (proyek tanpa dependency): geser ditangani GestureDetector dan
 * halaman digeser dengan TranslateAnimation.
 */
public class MainActivity extends Activity implements RideService.Listener {
    private static final int REQ_PERM = 1;
    private static final int PAGE_COUNT = 3;
    private static final int ANIM_MS = 180;

    private TextView tvStatus, tvLapBadge;
    private View vRec;
    private TextView tvSpeed, tvDist, tvTime, tvAvg, tvMax, tvElev, tvAlt, tvGrade;
    private GradeRoad gradeRoad;
    private TextView tvHr, tvHrHeart;
    private TextView tvSpeed2, tvDist2, tvTime2, tvAvg2, tvMax2, tvElev2, tvAlt2;
    private TextView tvMoving, tvTotal, tvLapDist, tvLapTime, tvLapAvg, tvLapMoving, tvLapTitle;
    private ListView lapList;
    private TextView lapEmpty;
    private Button btnStartPause, btnStop, btnLap;
    private ViewGroup pages;
    private LinearLayout dots;
    private RideService service;
    private boolean bound;
    private int page;
    private RideService.State prevState = RideService.State.IDLE;
    private boolean summaryPending;

    private GestureDetector gestures;
    private View[] pageViews;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            service = ((RideService.LocalBinder) b).getService();
            service.setListener(MainActivity.this);
            refresh();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Dibuat di onCreate, BUKAN di konstruktor: konstruktor Activity jalan sebelum
        // attachBaseContext(), jadi context-nya belum siap.
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                if (Math.abs(dx) < 90 || Math.abs(dx) < Math.abs(dy)) return false;  // geser mendatar saja
                if (dx < 0) showPage(page + 1);
                else showPage(page - 1);
                return true;
            }
        });

        // font dibundel di assets: Barlow Condensed untuk angka, Barlow untuk label
        Ui.fonts(getWindow().getDecorView(), this);
        Ui.buttonFonts(getWindow().getDecorView(), this);

        pages = (ViewGroup) findViewById(R.id.pages);
        dots = (LinearLayout) findViewById(R.id.dots);
        vRec = findViewById(R.id.vRec);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvLapBadge = (TextView) findViewById(R.id.tvLapBadge);

        // halaman SEKILAS
        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvDist = (TextView) findViewById(R.id.tvDist);
        tvTime = (TextView) findViewById(R.id.tvTime);
        tvAvg = (TextView) findViewById(R.id.tvAvg);
        tvGrade = (TextView) findViewById(R.id.tvGrade);
        gradeRoad = (GradeRoad) findViewById(R.id.gradeRoad);
        tvHr = (TextView) findViewById(R.id.tvHr);
        tvHrHeart = (TextView) findViewById(R.id.tvHrHeart);

        // halaman DETAIL
        tvSpeed2 = (TextView) findViewById(R.id.tvSpeed2);
        tvDist2 = (TextView) findViewById(R.id.tvDist2);
        tvAvg2 = (TextView) findViewById(R.id.tvAvg2);
        tvMax2 = (TextView) findViewById(R.id.tvMax2);
        tvMoving = (TextView) findViewById(R.id.tvMoving);
        tvTotal = (TextView) findViewById(R.id.tvTotal);
        tvElev2 = (TextView) findViewById(R.id.tvElev2);
        tvAlt2 = (TextView) findViewById(R.id.tvAlt2);

        // halaman LAP
        tvLapTitle = (TextView) findViewById(R.id.tvLapTitle);
        tvLapDist = (TextView) findViewById(R.id.tvLapDist);
        tvLapTime = (TextView) findViewById(R.id.tvLapTime);
        tvLapAvg = (TextView) findViewById(R.id.tvLapAvg);
        tvLapMoving = (TextView) findViewById(R.id.tvLapMoving);
        lapList = (ListView) findViewById(R.id.lapList);
        lapEmpty = (TextView) findViewById(R.id.lapEmpty);

        btnStartPause = (Button) findViewById(R.id.btnStartPause);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnLap = (Button) findViewById(R.id.btnLap);

        btnStartPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onStartPause(); }
        });
        // Berhenti harus ditahan lama supaya tidak kepencet di jalan bergelombang
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this, R.string.hint_stop, Toast.LENGTH_SHORT).show();
            }
        });
        btnStop.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                send(RideService.ACTION_STOP);
                return true;
            }
        });
        btnLap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { send(RideService.ACTION_LAP); }
        });
        findViewById(R.id.btnHistory).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        pageViews = new View[]{
                findViewById(R.id.page0),
                findViewById(R.id.page1),
                findViewById(R.id.page2)
        };
        // Penting: ketiga halaman ditumpuk di satu FrameLayout, jadi yang tidak aktif
        // HARUS disembunyikan. Tanpa ini semua angka tergambar bertumpuk.
        for (int i = 0; i < pageViews.length; i++) {
            pageViews[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        }
        page = 0;
        if (App.recovered) {
            App.recovered = false;
            Toast.makeText(this, R.string.recovered_msg, Toast.LENGTH_LONG).show();
        }
        buildDots();
        paintDots();
        refresh();
        showLastCrash();
    }

    /**
     * Kalau percobaan sebelumnya berakhir dengan crash, tampilkan penyebabnya di layar.
     * Perangkat pengujian tanpa adb dan folder Android/data sulit diakses di Android 11+;
     * dialog ini membuat laporan crash bisa di-screenshot dan dikirim.
     */
    private void showLastCrash() {
        try {
            java.io.File f = new java.io.File(App.crashPath(this));
            if (!f.exists()) return;
            String isi = "";
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String baris;
            while ((baris = r.readLine()) != null) {
                sb.append(baris).append('\n');
                if (sb.length() > 2200) break;
            }
            r.close();
            isi = sb.toString();

            new AlertDialog.Builder(this)
                    .setTitle(R.string.crash_title)
                    .setMessage(isi)
                    .setPositiveButton(R.string.close, null)
                    .setNeutralButton(R.string.delete, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            new java.io.File(App.crashPath(MainActivity.this)).delete();
                        }
                    })
                    .show();
        } catch (Throwable ignored) { }
    }

    // ---------- halaman bergeser ----------

    private void buildDots() {
        dots.removeAllViews();
        for (int i = 0; i < PAGE_COUNT; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(3));
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            d.setLayoutParams(lp);
            dots.addView(d);
        }
    }

    /** Halaman aktif jadi garis oranye lebih panjang - pola khas aplikasi olahraga. */
    private void paintDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == page;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = dp(on ? 26 : 12);
            d.setLayoutParams(lp);
            d.setBackgroundResource(on ? R.drawable.dot_on : R.drawable.dot_off);
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void showPage(int target) {
        if (pageViews == null || target < 0 || target >= PAGE_COUNT || target == page) return;
        final boolean keKiri = target > page;

        // tukar dulu halaman yang terlihat, baru geser - supaya tidak ada momen
        // dua halaman tampil bersamaan
        for (int i = 0; i < pageViews.length; i++) {
            pageViews[i].clearAnimation();
            pageViews[i].setVisibility(i == target ? View.VISIBLE : View.GONE);
        }
        page = target;

        float from = keKiri ? pages.getWidth() : -pages.getWidth();
        Animation a = new TranslateAnimation(from, 0f, 0f, 0f);
        a.setDuration(ANIM_MS);
        pages.startAnimation(a);

        paintDots();
        refresh();
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        return gestures.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures != null) gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, RideService.class), conn, Context.BIND_AUTO_CREATE);
        refresh();
    }

    @Override
    protected void onStop() {
        if (bound) {
            if (service != null) service.setListener(null);
            unbindService(conn);
            bound = false;
            service = null;
        }
        super.onStop();
    }

    // ---------- aksi tombol ----------

    private void onStartPause() {
        RideService.State st = service == null ? RideService.State.IDLE : service.getState();
        switch (st) {
            case IDLE:
                if (!hasLocationPermission()) {
                    requestPermissionsCompat();
                    return;
                }
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Toast.makeText(this, R.string.gps_off, Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    return;
                }
                if (batteryOptimized()) { askIgnoreBatteryOptimization(); return; }
                send(RideService.ACTION_START);
                break;
            case RECORDING:
                send(RideService.ACTION_PAUSE);
                break;
            case PAUSED:
                send(RideService.ACTION_RESUME);
                break;
        }
    }

    private void send(String action) {
        Intent i = new Intent(this, RideService.class).setAction(action);
        if (RideService.ACTION_START.equals(action) && Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    // ---------- optimasi baterai ----------

    /**
     * True kalau aplikasi masih kena optimasi baterai (Doze / pembatasan pabrikan).
     * Ini penyebab nomor satu rekaman berhenti sendiri di Android modern: foreground
     * service tetap boleh jalan, tapi pabrikan sering membunuhnya lebih agresif.
     */
    private boolean batteryOptimized() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void askIgnoreBatteryOptimization() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.batt_title)
                .setMessage(R.string.batt_msg)
                .setPositiveButton(R.string.batt_open, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:" + getPackageName())));
                        }
                    }
                })
                .setNegativeButton(R.string.batt_skip, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        send(RideService.ACTION_START);   // tetap rekam meski belum di-whitelist
                    }
                })
                .show();
    }

    // ---------- izin ----------

    private boolean hasLocationPermission() {
        // minSdk 23: izin runtime selalu berlaku, tidak perlu guard versi
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionsCompat() {
        List<String> p = new ArrayList<String>();
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        // izin Bluetooth hanya diminta kalau Tuan memang sudah memilih sensor,
        // supaya aplikasi tidak meminta izin yang tidak dipakai
        if (!id.cyclocomp.Settings.hrMac(this).isEmpty() && !BleHr.hasPermission(this)) {
            for (String s : BleHr.neededPermissions()) p.add(s);
        }
        requestPermissions(p.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERM) return;
        if (hasLocationPermission()) onStartPause();
        else Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_LONG).show();
    }

    // ---------- UI ----------

    @Override
    public void onRideUpdate() {
        refresh();
    }

    private void refresh() {
        RideService.State st = service == null ? RideService.State.IDLE : service.getState();
        RideStats s = service == null ? new RideStats() : service.getStats();
        long elapsed = service == null ? 0 : service.getElapsedMs();

        // ---------- SEKILAS ----------
        tvSpeed.setText(Fmt.speed(s.currentSpeedMs));
        tvDist.setText(Fmt.km(s.distanceM));
        tvTime.setText(Fmt.duration(s.movingMs));
        tvAvg.setText(Fmt.speed(s.avgSpeedMs()));

        // gradien: ruas jalan memberi ARAH dan KESAN besarannya, angka memberi nilai
        // persis. Dua hal berbeda, tidak salung ulang — itu sebabnya panah naik/turun
        // bisa dibuang.
        double g = s.gradePercent;
        gradeRoad.setGrade(g);
        // Angka selalu magnitudo; arah sudah dikatakan jalan. Kasus rata ikut lewat
        // Fmt.grade() supaya pemisah desimalnya konsisten dengan locale — sebelumnya
        // ditulis "0.0" dengan titik, padahal locale Indonesia memakai koma.
        tvGrade.setText(Fmt.grade(Math.abs(g) < 0.15 ? 0.0 : Math.abs(g)));

        // detak jantung: pil disembunyikan bila belum ada data (tanpa sensor = tidak berisik)
        int hr = s.hr;
        if (hr > 0) {
            tvHr.setText(String.valueOf(hr));
            tvHr.setVisibility(View.VISIBLE);
            tvHrHeart.setVisibility(View.VISIBLE);
        } else {
            tvHr.setVisibility(View.GONE);
            tvHrHeart.setVisibility(View.GONE);
        }

        // ---------- DETAIL ----------
        tvSpeed2.setText(Fmt.speed(s.currentSpeedMs));
        tvDist2.setText(Fmt.km(s.distanceM));
        tvAvg2.setText(Fmt.speed(s.avgSpeedMs()));
        tvMax2.setText(Fmt.speed(s.maxSpeedMs));
        tvMoving.setText(Fmt.duration(s.movingMs));
        tvTotal.setText(Fmt.duration(elapsed));
        tvElev2.setText(Fmt.meters(s.elevGainM));
        tvAlt2.setText(Double.isNaN(s.altitudeM) ? "--" : Fmt.meters(s.altitudeM));

        // ---------- status ----------
        switch (st) {
            case IDLE:
                btnStartPause.setText(R.string.btn_start);
                tvStatus.setText(R.string.status_idle);
                break;
            case PAUSED:
                btnStartPause.setText(R.string.btn_resume);
                tvStatus.setText(R.string.status_paused);
                break;
            case RECORDING:
                btnStartPause.setText(R.string.btn_pause);
                long age = service.getFixAgeMs();
                if (age < 0 || age > 5000) tvStatus.setText(R.string.status_search);
                else tvStatus.setText(getString(R.string.status_ok, Math.round(service.getLastAccuracy())));
                break;
        }

        // titik hijau berkedip-tetap hanya saat merekam
        if (vRec != null) vRec.setVisibility(st == RideService.State.RECORDING ? View.VISIBLE : View.GONE);

        // Saat perekaman berakhir, buka Ringkasan sekali (rute + angka akhir).
        if (prevState != RideService.State.IDLE && st == RideService.State.IDLE) {
            summaryPending = true;
        }
        prevState = st;
        if (summaryPending && st == RideService.State.IDLE) {
            summaryPending = false;
            // Di kelas ini "Settings" berarti android.provider.Settings (dipakai untuk
            // intent action), jadi kelas pengaturan milik aplikasi ditulis lengkap.
            long lastId = id.cyclocomp.Settings.lastRideId(this);
            if (lastId > 0) {
                startActivity(new Intent(this, SummaryActivity.class)
                        .putExtra(SummaryActivity.EXTRA_ID, lastId));
            }
        }

        // ---------- lap ----------
        refreshLap(st);

        btnStop.setEnabled(st != RideService.State.IDLE);
        btnLap.setEnabled(st != RideService.State.IDLE);

        if (st == RideService.State.IDLE) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void refreshLap(RideService.State st) {
        if (service == null || st == RideService.State.IDLE) {
            tvLapBadge.setVisibility(View.GONE);
            tvLapTitle.setText(R.string.lap_current);
            tvLapDist.setText(Fmt.km(0));
            tvLapTime.setText("0:00");
            tvLapAvg.setText(Fmt.speed(0));
            tvLapMoving.setText("0:00");
            lapList.setAdapter(null);
            lapEmpty.setVisibility(View.VISIBLE);
            lapEmpty.setText(R.string.lap_none);
            return;
        }

        Lap cur = service.getCurrentLap();
        int idx = cur == null ? 1 : cur.index;
        double d = cur == null ? 0 : cur.distanceM;
        long mv = cur == null ? 0 : cur.movingMs;

        tvLapBadge.setVisibility(View.VISIBLE);
        tvLapBadge.setText(getString(R.string.lap_badge, idx));
        tvLapTitle.setText(getString(R.string.lap_current) + " " + idx);
        tvLapDist.setText(Fmt.km(d));
        tvLapTime.setText(Fmt.shortDuration(cur == null ? 0 : cur.elapsedMs));
        tvLapAvg.setText(Fmt.speed(mv > 0 ? (float) (d / (mv / 1000.0)) : 0f));
        tvLapMoving.setText(Fmt.shortDuration(mv));

        List<Lap> done = service.getLaps();
        if (done.isEmpty()) {
            lapList.setAdapter(null);
            lapEmpty.setVisibility(View.VISIBLE);
            lapEmpty.setText(R.string.lap_none);
        } else {
            lapEmpty.setVisibility(View.GONE);
            List<String> rows = new ArrayList<String>();
            for (Lap l : done) {
                rows.add(getString(R.string.summary_lap_row, l.index,
                        Fmt.km(l.distanceM), Fmt.shortDuration(l.elapsedMs),
                        Fmt.speed(l.avgSpeedMs())));
            }
            lapList.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1, rows));
        }
    }
}
