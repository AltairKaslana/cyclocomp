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
    private TextView tvSpeed, tvDist, tvTime, tvAvg, tvMax, tvElev, tvAlt, tvGrade, tvGradeArrow;
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

    private GestureDetector gestures;

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

        pages = (ViewGroup) findViewById(R.id.pages);
        dots = (LinearLayout) findViewById(R.id.dots);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvLapBadge = (TextView) findViewById(R.id.tvLapBadge);

        // halaman SEKILAS
        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvDist = (TextView) findViewById(R.id.tvDist);
        tvTime = (TextView) findViewById(R.id.tvTime);
        tvAvg = (TextView) findViewById(R.id.tvAvg);
        tvGrade = (TextView) findViewById(R.id.tvGrade);
        tvGradeArrow = (TextView) findViewById(R.id.tvGradeArrow);

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

        buildDots();
        showPage(0);
    }

    // ---------- halaman bergeser ----------

    private void buildDots() {
        dots.removeAllViews();
        for (int i = 0; i < PAGE_COUNT; i++) {
            TextView d = new TextView(this);
            d.setText("●");
            d.setTextSize(9);
            d.setPadding(6, 0, 6, 0);
            d.setTextColor(getResources().getColor(R.color.label));
            dots.addView(d);
        }
    }

    private void paintDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            TextView d = (TextView) dots.getChildAt(i);
            d.setTextColor(getResources().getColor(i == page ? R.color.accent : R.color.label));
            d.setAlpha(i == page ? 1f : 0.35f);
        }
    }

    private void showPage(int target) {
        if (target < 0 || target >= PAGE_COUNT || target == page) return;
        final boolean keKiri = target > page;
        page = target;

        float from = keKiri ? pages.getWidth() : -pages.getWidth();
        Animation a = new TranslateAnimation(from, 0f, 0f, 0f);
        a.setDuration(ANIM_MS);
        a.setFillAfter(true);
        pages.startAnimation(a);
        pages.postDelayed(new Runnable() {
            @Override public void run() {
                pages.clearAnimation();
                refresh();
            }
        }, ANIM_MS);

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

        // gradien: panah arah + angka, supaya terbaca sekilas tanpa memikirkan tanda
        double g = s.gradePercent;
        if (Math.abs(g) < 0.15) {
            tvGradeArrow.setText("→");
            tvGrade.setText("0.0");
        } else if (g > 0) {
            tvGradeArrow.setText("▲");
            tvGrade.setText(Fmt.grade(g));
        } else {
            tvGradeArrow.setText("▼");
            tvGrade.setText(Fmt.grade(-g));
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
        tvLapBadge.setText("LAP " + idx);
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
                rows.add("Lap " + l.index + "   " + Fmt.km(l.distanceM) + " km   "
                        + Fmt.shortDuration(l.elapsedMs) + "   "
                        + Fmt.speed(l.avgSpeedMs()) + " km/j");
            }
            lapList.setAdapter(new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1, rows));
        }
    }
}
