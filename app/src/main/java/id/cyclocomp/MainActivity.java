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
import android.os.SystemClock;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
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
    // Peta live dibaca ulang tiap 3 detik (bukan tiap fix GPS): tiap pembacaan
    // mem-parse ulang seluruh berkas GPX, jadi tidak perlu sesering angka.
    private static final long MAP_REFRESH_MS = 3000;

    private TextView tvStatus, tvLapBadge;
    private View vRec;
    private ImageButton btnHistory, btnSettings, btnLock, btnMap;
    private View vStopProgress;
    private LinearLayout lapCard;
    private TextView tvSpeed, tvDist, tvTime, tvAvg, tvMax, tvElev, tvAlt, tvGrade;
    private GradeRoad gradeRoad;
    private TextView tvHr, tvHrHeart;
    private TileMapView liveMap;          // peta live rute (halaman SEKILAS)
    private long lastLiveMapMs;           // throttle pembacaan rute live
    private TextView tvSpeed2, tvDist2, tvTime2, tvAvg2, tvMax2, tvElev2, tvAlt2, tvKcal2, tvHr2;
    private TextView tvMoving, tvTotal, tvLapDist, tvLapTime, tvLapAvg, tvLapMoving, tvLapTitle;
    private ListView lapList;
    private TextView lapEmpty;

    // ---------- slot bidang (Atur layar) ----------
    // Setiap slot = satu sel di dashboard: label, nilai, satuan. Bidang apa yang
    // mengisinya ditentukan pengguna di layar Atur layar, bukan ditulis tetap di
    // layout. Urutan bawaan = susunan yang sudah ada, supaya dashboard tidak
    // berubah sendiri saat versi ini dipasang.
    private static final class Slot {
        final TextView label, value, unit;
        Slot(TextView l, TextView v, TextView u) { label = l; value = v; unit = u; }
    }

    private int[] fieldsSekilas = Bidang.bawaan(Bidang.SEKILAS);
    private int[] fieldsDetail = Bidang.bawaan(Bidang.DETAIL);
    private int[] fieldsLap = Bidang.bawaan(Bidang.LAP);
    private Slot[] slotSekilas, slotDetail, slotLap;
    private Button btnStartPause, btnStop, btnLap;
    private ViewGroup pages;
    private LinearLayout dots;
    private RideService service;
    private boolean bound;
    private int page;
    private RideService.State prevState = RideService.State.IDLE;
    private boolean summaryPending;
    // Gowes yang Ringkasannya SUDAH pernah dibuka. Pemicu buka Ringkasan dulu hanya
    // peralihan state (RECORDING/PAUSED -> IDLE), dan peralihan itu bisa terlihat
    // lebih dari sekali: stopRide() memanggil stopSelf() sementara MainActivity masih
    // ter-bind, jadi stopSelf-nya TERTUNDA; ketika Ringkasan ditutup dan service
    // di-bind ulang, refresh() bisa melihat lagi transisi ke IDLE. Akibatnya
    // Ringkasan terbuka lagi setiap kali pengguna menekan TUTUP — tidak ada jalan
    // kembali ke menu utama. Menyimpan id gowes membuatnya sekali per gowes.
    private long summaryShownForId = -1;

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

        // Onboarding 3 langkah: hanya sekali, hanya kalau memang ada yang perlu
        // ditanyakan (izin lokasi belum diberikan atau baterai belum
        // dikecualikan). Pengguna yang sudah melewatinya tidak akan melihatnya
        // lagi, dan yang semuanya sudah beres tidak diganggu sama sekali.
        if (Onboarding.layakDitampilkan(id.cyclocomp.Settings.onboardingDone(this),
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED,
                batteryOptimized())) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }

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
        liveMap = (TileMapView) findViewById(R.id.liveMap);
        // Layout landscape TIDAK punya peta (tidak muat peta + angka sekaligus),
        // jadi liveMap null di sana. Tanpa penjagaan ini aplikasi langsung mati
        // begitu HP dimiringkan - yang dilaporkan sebagai "landscape crash".
        if (liveMap != null) liveMap.setMapEnabled(id.cyclocomp.Settings.map(this));

        // halaman DETAIL
        tvSpeed2 = (TextView) findViewById(R.id.tvSpeed2);
        tvDist2 = (TextView) findViewById(R.id.tvDist2);
        tvAvg2 = (TextView) findViewById(R.id.tvAvg2);
        tvMax2 = (TextView) findViewById(R.id.tvMax2);
        tvMoving = (TextView) findViewById(R.id.tvMoving);
        tvTotal = (TextView) findViewById(R.id.tvTotal);
        tvElev2 = (TextView) findViewById(R.id.tvElev2);
        tvAlt2 = (TextView) findViewById(R.id.tvAlt2);
        tvKcal2 = (TextView) findViewById(R.id.tvKcal2);
        tvHr2 = (TextView) findViewById(R.id.tvHr2);

        // halaman LAP
        tvLapTitle = (TextView) findViewById(R.id.tvLapTitle);
        tvLapDist = (TextView) findViewById(R.id.tvLapDist);
        tvLapTime = (TextView) findViewById(R.id.tvLapTime);
        tvLapAvg = (TextView) findViewById(R.id.tvLapAvg);
        tvLapMoving = (TextView) findViewById(R.id.tvLapMoving);
        muatSlot();
        lapList = (ListView) findViewById(R.id.lapList);
        lapEmpty = (TextView) findViewById(R.id.lapEmpty);
        lapCard = (LinearLayout) findViewById(R.id.lapCard);

        btnStartPause = (Button) findViewById(R.id.btnStartPause);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnLap = (Button) findViewById(R.id.btnLap);
        btnHistory = (ImageButton) findViewById(R.id.btnHistory);
        btnSettings = (ImageButton) findViewById(R.id.btnSettings);
        btnLock = (ImageButton) findViewById(R.id.btnLock);
        btnMap = (ImageButton) findViewById(R.id.btnMap);
        vStopProgress = findViewById(R.id.vStopProgress);

        // Umpan balik tekan: tombol menyusut sedikit saat disentuh. Lihat Nav.pressFeedback.
        Nav.pressFeedback(btnStartPause);
        Nav.pressFeedback(btnLap);
        // btnStop TIDAK dapat pressFeedback: HoldToStop mengambil alih onTouch-nya
        // (satu View hanya bisa punya satu OnTouchListener) dan sudah memberi
        // umpan balik tekan yang sama lewat Nav.pressScale.

        btnStartPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onStartPause(); }
        });

        // T-08: tahan-untuk-berhenti. Sebelumnya tombol ini memakai
        // OnLongClickListener - aman dari kepencet, tapi TANPA umpan balik:
        // pengguna menahan, tidak ada yang bergerak, lalu gowesnya tiba-tiba
        // berakhir setelah jeda yang tidak diketahui lamanya. Sekarang bar
        // progres 4dp di dasar tombol tumbuh selama ditahan, jadi "berapa lama
        // lagi" terlihat daripada dirasakan.
        // T-08: label tombol jadi dua baris - "BERHENTI" besar, "TAHAN" kecil di
        // bawahnya. Kenapa satu Button dengan Spannable dan bukan dua TextView
        // terpisah: teks tombol tetap satu objek, jadi warna, padding, dan
        // ellipsize-nya tidak perlu diulang di dua tempat. Dan yang kedua baris
        // ini justru inti T-08 - tanpa "TAHAN", bar progres yang tumbuh tidak
        // menjelaskan apa pun sampai pengguna sudah menahan.
        android.text.SpannableStringBuilder stopLabel =
                new android.text.SpannableStringBuilder();
        stopLabel.append(getString(R.string.btn_stop));
        stopLabel.append('\n');
        int hintStart = stopLabel.length();
        stopLabel.append(getString(R.string.hint_hold));
        stopLabel.setSpan(
                new android.text.style.AbsoluteSizeSpan(
                        getResources().getDimensionPixelSize(R.dimen.text_hint_tiny)),
                hintStart, stopLabel.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        btnStop.setText(stopLabel);

        btnStop.setOnTouchListener(new HoldToStop(vStopProgress,
                new HoldToStop.Callback() {
                    @Override public void onStop() { send(RideService.ACTION_STOP); }
                },
                new HoldToStop.AbortListener() {
                    @Override public void onAbort(float fraction) {
                        // Dilepas sebelum penuh. Ingatkan, tapi jangan menegur
                        // yang sudah menahan hampir penuh - lihat HoldToStop.
                        Toast.makeText(MainActivity.this, R.string.hint_stop,
                                Toast.LENGTH_SHORT).show();
                    }
                }));
        btnLap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { send(RideService.ACTION_LAP); }
        });
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Nav.open(MainActivity.this, HistoryActivity.class);
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Nav.open(MainActivity.this, SettingsActivity.class);
            }
        });
        // Tombol peta juga tidak ada di layout landscape (peta dibuka dari
        // layar penuh sendiri). Dicek null supaya landscape tidak mati.
        if (btnMap != null) btnMap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Nav.open(MainActivity.this, MapActivity.class);
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
     * Paksa penanda mode gelap/terang SEBELUM resource apa pun dibaca.
     *
     * Harus di attachBaseContext, bukan di onCreate: onCreate sudah dipanggil
     * sesudah sistem menyiapkan resource aktivitas ini. Kalau ditulis di
     * onCreate, palet yang sudah terbaca tetap dipakai dan sakelar tema tidak
     * bereaksi. Lihat {@link Tema} untuk alasannya memakai penanda konfigurasi
     * dan bukan dua tema terpisah.
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Tema.bungkus(base));
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
            // Bidang sentuh 48dp x 48dp (aturan target sentuh di rancangan UI/UX),
            // tapi garis yang TERLIHAT tetap 3dp tinggi: latarnya dipasang sebagai
            // InsetDrawable di paintDots(), jadi ruang yang dimakan layar bertambah
            // tanpa mengubah rupa indikatornya.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
            d.setLayoutParams(lp);
            d.setClickable(true);
            d.setFocusable(false);
            final int idx = i;
            d.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showPage(idx); }
            });
            d.setContentDescription(getString(R.string.page_of, idx + 1, PAGE_COUNT));
            dots.addView(d);
        }
    }

    /**
     * Halaman aktif jadi garis lebih panjang - pola khas aplikasi olahraga.
     *
     * Garis digambar di tengah bidang sentuh 48dp lewat InsetDrawable, supaya
     * titik bisa diketuk lebar tanpa baris indikator ikut bertinggi.
     */
    private void paintDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == page;
            int lineW = dp(on ? 26 : 12);
            int lineH = dp(3);
            int side = (dp(48) - lineW) / 2;
            int top = (dp(48) - lineH) / 2;
            // Tanpa androidx (proyek ini tanpa dependency selain junit): ambil
            // drawable langsung dari Resources - API 21+, minSdk proyek 23.
            android.graphics.drawable.InsetDrawable in = new android.graphics.drawable.InsetDrawable(
                    getResources().getDrawable(on ? R.drawable.dot_on : R.drawable.dot_off),
                    side, top, side, top);
            d.setBackground(in);
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
        // Jaring pengaman palet: kalau tema diganti di Pengaturan lalu pengguna
        // menekan kembali, layar ini (yang masih hidup) ikut palet baru. Lihat
        // Tema.perbaikiKalauPerlu.
        Tema.perbaikiKalauPerlu(this);
        // SEMENTARA - diagnosa tema, sama seperti di layar Pengaturan.
        android.widget.Toast.makeText(this, Tema.diagnostik(this), android.widget.Toast.LENGTH_LONG).show();
        bound = bindService(new Intent(this, RideService.class), conn, Context.BIND_AUTO_CREATE);
        // Susunan bidang bisa berubah di layar Atur layar; onStart (bukan
        // onCreate) supaya perubahan itu terlihat juga saat pengguna menekan
        // tombol kembali, bukan hanya setelah aplikasi ditutup dan dibuka ulang.
        muatSlot();
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

    /**
     * Peta live di halaman SEKILAS: gambar rute yang sudah dilalui dengan membaca
     * berkas GPX yang sedang ditulis. Di-throttle karena tiap pembacaan mem-parse
     * ulang seluruh berkas. Saat tidak gowes peta disembunyikan (bukan tampil
     * kosong) supaya dasbor tidak berisik.
     */
    private void updateLiveMap(RideService.State st) {
        if (liveMap == null) return;
        boolean riding = service != null
                && (st == RideService.State.RECORDING || st == RideService.State.PAUSED);
        if (!riding) {
            if (liveMap.getVisibility() != View.GONE) {
                liveMap.setVisibility(View.GONE);
                lastLiveMapMs = 0;   // ride berikutnya tampil segera
            }
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastLiveMapMs < MAP_REFRESH_MS) return;
        lastLiveMapMs = now;
        List<GpxReader.P> track = LiveTrack.read(this, service.activeGpxLoc());
        if (track != null && track.size() >= 2) {
            liveMap.setVisibility(View.VISIBLE);
            liveMap.setTrack(track);
        }
    }

    private void refresh() {
        RideService.State st = service == null ? RideService.State.IDLE : service.getState();
        RideStats s = service == null ? new RideStats() : service.getStats();
        long elapsed = service == null ? 0 : service.getElapsedMs();

        // ---------- SEKILAS ----------
        tvSpeed.setText(Fmt.speed(s.currentSpeedMs));
        isiNilaiSlot(slotSekilas, fieldsSekilas, s, elapsed, null);

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

        // peta live: rute yang sudah dilalui (halaman SEKILAS)
        updateLiveMap(st);

        // ---------- DETAIL ----------
        isiNilaiSlot(slotDetail, fieldsDetail, s, elapsed, null);

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
                if (age < 0 || age > 5000) tvStatus.setText(R.string.status_searching);
                else tvStatus.setText(getString(R.string.status_recording_fmt,
                        Math.round(service.getLastAccuracy())));
                break;
        }

        // T-07: warna titik status. Hijau = merekam dan akurat. Kuning = merekam
        // tapi sinyal masih dicari (angka masih bisa berubah kasar). Merah = GPS
        // mati total, angka tidak bisa dipercaya.
        //
        // Titik hanya tampil saat merekam - saat idle tidak ada status GPS yang
        // perlu diberitahukan, dan titik yang diam tanpa arti cuma menambah bising.
        if (vRec != null) {
            if (st == RideService.State.RECORDING) {
                long age2 = service.getFixAgeMs();
                if (age2 < 0 || age2 > 5000) {
                    vRec.setBackgroundResource(R.drawable.dot_warn);
                    // T-17: warna titik punya makna, tapi makna itu tidak bisa
                    // dibaca pembaca layar. Deskripsinya harus ikut berubah -
                    // kalau tidak, pengguna tunanetra hanya mendengar "Status
                    // GPS" untuk ketiga warna yang membawa informasi berbeda.
                    vRec.setContentDescription(getString(R.string.status_gps_searching));
                } else {
                    // rec_dot: oval hijau @color/rec. Bukan dot_on - itu kotak
                    // membulat berwarna aksen, dipakai indikator lain.
                    vRec.setBackgroundResource(R.drawable.rec_dot);
                    vRec.setContentDescription(getString(R.string.status_gps_ok));
                }
                vRec.setVisibility(View.VISIBLE);
            } else if (st == RideService.State.PAUSED) {
                // Dijeda: sinyal tidak relevan, tapi titik merah mengingatkan
                // bahwa angka yang tampil tidak sedang bertambah.
                vRec.setBackgroundResource(R.drawable.dot_danger);
                vRec.setContentDescription(getString(R.string.status_gps_paused));
                vRec.setVisibility(View.VISIBLE);
            } else {
                vRec.setVisibility(View.GONE);
            }
        }

        // T-07: menu terkunci saat merekam. Riwayat dan Pengaturan diganti satu
        // ikon gembok non-aktif. Kenapa digembok dan bukan sekadar setEnabled(false):
        // tombol mati yang masih terlihat membuat orang mencoba mengkliknya, dan
        // menekan sesuatu yang tidak bereaksi terlihat seperti bug. Satu gembok
        // yang jelas "terkunci" menjelaskan diri sendiri.
        //
        // Alasan menguncinya: membuka Riwayat atau Pengaturan saat merekam
        // memindahkan fokus dari jalan, dan di Pengaturan ada Client ID/Secret
        // yang mengubah tempat data gowes berikutnya dikirim.
        boolean locked = st == RideService.State.RECORDING || st == RideService.State.PAUSED;
        if (btnHistory != null) btnHistory.setVisibility(locked ? View.GONE : View.VISIBLE);
        if (btnSettings != null) btnSettings.setVisibility(locked ? View.GONE : View.VISIBLE);
        if (btnLock != null) btnLock.setVisibility(locked ? View.VISIBLE : View.GONE);

        // Saat perekaman berakhir, buka Ringkasan sekali (rute + angka akhir).
        if (service != null && prevState != RideService.State.IDLE && st == RideService.State.IDLE) {
            summaryPending = true;
        }
        prevState = st;
        if (summaryPending && st == RideService.State.IDLE) {
            summaryPending = false;
            // Di kelas ini "Settings" berarti android.provider.Settings (dipakai untuk
            // intent action), jadi kelas pengaturan milik aplikasi ditulis lengkap.
            long lastId = id.cyclocomp.Settings.lastRideId(this);
            if (lastId > 0 && lastId != summaryShownForId) {
                summaryShownForId = lastId;
                startActivity(new Intent(this, SummaryActivity.class)
                        .putExtra(SummaryActivity.EXTRA_ID, lastId));
                overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
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

    /**
     * Baca susunan bidang dari pengaturan dan pasang ke sel dashboard.
     *
     * Dipanggil dari onCreate dan dari onResume: pengguna bisa saja baru
     * mengubah susunan di layar Atur layar, dan dashboard yang tidak ikut berubah
     * sampai aplikasi ditutup adalah bug yang paling membingungkan.
     */
    private void muatSlot() {
        fieldsSekilas = id.cyclocomp.Settings.pageFields(this, Bidang.SEKILAS);
        fieldsDetail = id.cyclocomp.Settings.pageFields(this, Bidang.DETAIL);
        fieldsLap = id.cyclocomp.Settings.pageFields(this, Bidang.LAP);

        slotSekilas = new Slot[]{
                new Slot((TextView) findViewById(R.id.lblS0), tvDist, (TextView) findViewById(R.id.unS0)),
                new Slot((TextView) findViewById(R.id.lblS1), tvTime, (TextView) findViewById(R.id.unS1)),
                new Slot((TextView) findViewById(R.id.lblS2), tvAvg, (TextView) findViewById(R.id.unS2))
        };
        slotDetail = new Slot[]{
                new Slot((TextView) findViewById(R.id.lblD0), tvSpeed2, (TextView) findViewById(R.id.unD0)),
                new Slot((TextView) findViewById(R.id.lblD1), tvAvg2, (TextView) findViewById(R.id.unD1)),
                new Slot((TextView) findViewById(R.id.lblD2), tvMax2, (TextView) findViewById(R.id.unD2)),
                new Slot((TextView) findViewById(R.id.lblD3), tvDist2, (TextView) findViewById(R.id.unD3)),
                new Slot((TextView) findViewById(R.id.lblD4), tvMoving, (TextView) findViewById(R.id.unD4)),
                new Slot((TextView) findViewById(R.id.lblD5), tvTotal, (TextView) findViewById(R.id.unD5)),
                new Slot((TextView) findViewById(R.id.lblD6), tvElev2, (TextView) findViewById(R.id.unD6)),
                new Slot((TextView) findViewById(R.id.lblD7), tvAlt2, (TextView) findViewById(R.id.unD7)),
                new Slot((TextView) findViewById(R.id.lblD8), tvKcal2, (TextView) findViewById(R.id.unD8)),
                new Slot((TextView) findViewById(R.id.lblD9), tvHr2, (TextView) findViewById(R.id.unD9))
        };
        slotLap = new Slot[]{
                new Slot((TextView) findViewById(R.id.lblL0), tvLapDist, (TextView) findViewById(R.id.unL0)),
                new Slot((TextView) findViewById(R.id.lblL1), tvLapTime, (TextView) findViewById(R.id.unL1)),
                new Slot((TextView) findViewById(R.id.lblL2), tvLapAvg, (TextView) findViewById(R.id.unL2)),
                new Slot((TextView) findViewById(R.id.lblL3), tvLapMoving, (TextView) findViewById(R.id.unL3))
        };
        terapkanLabelSlot();
    }

    /**
     * Label dan satuan dipasang sekali per perubahan susunan, bukan tiap detik:
     * keduanya tidak berubah saat angka diperbarui, dan menyentuh TextView yang
     * sama 1 Hz tanpa alasan hanya membuat layout diukur ulang.
     */
    private void terapkanLabelSlot() {
        pasangLabel(slotSekilas, fieldsSekilas);
        pasangLabel(slotDetail, fieldsDetail);
        pasangLabel(slotLap, fieldsLap);
    }

    private void pasangLabel(Slot[] slots, int[] fields) {
        for (int i = 0; i < slots.length; i++) {
            slots[i].label.setText(Bidang.label(fields[i]));
            int u = Bidang.unit(fields[i]);
            if (u == 0) {
                // Bidang tanpa satuan terpisah (waktu, jam, baterai) - TextView-nya
                // GONE, bukan dibiarkan hampa: ruang kosong di sebelah angka
                // terbaca sebagai satuan yang hilang.
                slots[i].unit.setVisibility(View.GONE);
            } else {
                slots[i].unit.setVisibility(View.VISIBLE);
                slots[i].unit.setText(u);
            }
        }
    }

    /** Isi nilai semua slot satu halaman. Dipanggil tiap refresh (1 Hz). */
    private void isiNilaiSlot(Slot[] slots, int[] fields, RideStats s, long elapsed, Lap lap) {
        for (int i = 0; i < slots.length; i++) {
            slots[i].value.setText(BidangNilai.teks(this, fields[i], s, elapsed, lap));
        }
    }

    private void refreshLap(RideService.State st) {
        if (service == null || st == RideService.State.IDLE) {
            tvLapBadge.setVisibility(View.GONE);
            tvLapTitle.setText(R.string.lap_current);
            // Slot lap diisi lewat BidangNilai yang sama dengan saat lap
            // berjalan - bentuk angka (pemisah desimal, format waktu) karena itu
            // persis sama, hanya nilainya nol.
            isiNilaiSlot(slotLap, fieldsLap, new RideStats(), 0, null);
            lapList.setAdapter(null);
            applyLapCardMode(true);
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
        isiNilaiSlot(slotLap, fieldsLap, service == null ? new RideStats() : service.getStats(),
                service == null ? 0 : service.getElapsedMs(), cur);

        List<Lap> done = service.getLaps();
        if (done.isEmpty()) {
            lapList.setAdapter(null);
            applyLapCardMode(true);
            lapEmpty.setText(R.string.lap_none);
        } else {
            applyLapCardMode(false);
            List<RowItem> rows = new ArrayList<RowItem>();
            for (Lap l : done) {
                rows.add(new RowItem(
                        getString(R.string.lap_badge, l.index),
                        null,
                        Fmt.km(l.distanceM),
                        getString(R.string.row_sub_lap, Fmt.duration(l.elapsedMs),
                                Fmt.speed(l.avgSpeedMs()))));
            }
            lapList.setAdapter(new RowAdapter(this, rows));
        }
    }

    /**
     * T-08b: kartu "LAP SELESAI" menyusut sesuai isi.
     *
     * Sebelumnya kartunya selalu setinggi ruang yang tersisa (weight=1). Jadi
     * saat belum ada lap yang selesai, yang tampil adalah kartu kosong setinggi
     * separuh layar dengan satu baris teks berdiri sendiri di LUAR kartu. Itu
     * terbaca seperti "ada yang rusak" - padahal maksudnya cuma "belum ada data".
     *
     * Sekarang: kalau kosong, daftarnya disembunyikan dan kartunya mengecil pas
     * di sekeliling pesan kosongnya. Kalau ada isi, kartunya kembali mengisi
     * ruang yang tersisa supaya daftarnya bisa digulir sendiri.
     *
     * Kenapa tinggi terbatas (weight=1) TIDAK bisa langsung diganti
     * wrap_content di XML: ListView dengan tinggi wrap_content mengukur SELURUH
     * barisnya. Dengan banyak lap, isi halaman meluber keluar layar dan tidak ada
     * yang bisa digulir - daftarnya justru kehilangan kemampuan menggulir. Karena
     * itu tinggi terbatas tetap dipakai saat ada isi, dan hanya dibuang saat
     * kosong, ketika daftarnya sendiri juga disembunyikan.
     */
    private void applyLapCardMode(boolean empty) {
        if (lapCard == null) return;
        ViewGroup.LayoutParams lp = lapCard.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams mlp = (LinearLayout.LayoutParams) lp;
            if (empty) {
                mlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mlp.weight = 0f;
            } else {
                mlp.height = 0;
                mlp.weight = 1f;
            }
            lapCard.setLayoutParams(mlp);
        }
        lapList.setVisibility(empty ? View.GONE : View.VISIBLE);
        lapEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
