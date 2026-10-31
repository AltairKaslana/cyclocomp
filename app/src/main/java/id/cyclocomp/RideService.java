package id.cyclocomp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground service yang merekam GPS. UI hanya "melihat" lewat Binder;
 * perintah (start/pause/resume/stop) dikirim lewat Intent action.
 * Semua callback jalan di main thread, jadi tidak perlu locking.
 */
public class RideService extends Service implements LocationListener {
    private static final String TAG = "RideService";
    public static final String ACTION_START = "id.cyclocomp.START";
    public static final String ACTION_PAUSE = "id.cyclocomp.PAUSE";
    public static final String ACTION_RESUME = "id.cyclocomp.RESUME";
    public static final String ACTION_STOP = "id.cyclocomp.STOP";
    public static final String ACTION_LAP  = "id.cyclocomp.LAP";

    private static final int NOTIF_ID = 1;
    private static final String CHANNEL_ID = "ride";

    public enum State { IDLE, RECORDING, PAUSED }

    public interface Listener {
        void onRideUpdate();
    }

    public class LocalBinder extends Binder {
        public RideService getService() {
            return RideService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    /** Laju GPS saat MEREKAM: 1 fix/detik. Sama dengan laju refresh dasbor. */
    private static final long RECORDING_INTERVAL_MS = 1000L;

    /**
     * Laju GPS saat JEDA: 1 fix tiap 10 detik.
     *
     * Selama jeda, GPS cuma perlu menjawab satu pertanyaan - "pengendara sudah
     * bergerak lagi?" - dan pertanyaan itu tidak perlu dijawab 1x/detik.
     * Sepuluh detik masih terasa responsif (pengendara yang mengayuh lagi
     * tidak menunggu lama), sementara biaya baterainya sepersepuluh.
     */
    private static final long PAUSED_INTERVAL_MS = 10_000L;

    /**
     * Berapa kali menulis GPX boleh gagal berturut-turut sebelum ride dihentikan.
     *
     * Tiga, bukan satu: satu kegagalan bisa sesaat (kartu SD terlepas lalu
     * masuk lagi, berkas sedang dipindah). Tiga berturut-turut berarti ada yang
     * salah secara sistemik.
     */
    private static final int BATAS_GAGAL_TULIS = 3;

    /** Hitungan kegagalan tulis GPX berturut-turut. */
    private int gagalTulis;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationManager lm;
    private PowerManager.WakeLock wakeLock;
    private RideDb db;
    private BleHr ble;
    private String bleStatus;
    private BleCsc csc;
    private String cscStatus;
    private int cscBattery = -1;
    private GpxWriter gpx;
    private String gpxLoc;
    private Listener listener;

    private State state = State.IDLE;
    private RideStats stats = new RideStats();
    private long startWall, startElapsed, pausedTotal, pauseStart, finalElapsed;
    private long lastFixElapsed;
    private float lastAccuracy;
    private int tick;

    // auto-pause
    private long lowSpeedSince;          // sejak kapan kecepatan di bawah ambang (0 = tidak)

    // lap
    private final java.util.List<Lap> laps = new java.util.ArrayList<Lap>();
    private long lapStartElapsed;        // waktu mulai lap yang sedang berjalan

    // peringatan
    private long lastAlertKmStep;        // kelipatan km terakhir yang sudah diberitahu
    private long lastAlertMinStep;       // kelipatan menit terakhir yang sudah diberitahu

    private ToneGenerator tone;
    private Baro baro;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (state == State.IDLE) return;
            if (++tick % 5 == 0) {
                updateNotification();
                saveSession();
            }
            if (state == State.RECORDING) {
                checkAutoPause();
                checkAutoLap();
                checkAlerts();
            }
            notifyUi();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- lifecycle ----------

    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        db = new RideDb(this);
        baro = new Baro(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(a)) startRide();
        else if (ACTION_PAUSE.equals(a)) pauseRide();
        else if (ACTION_RESUME.equals(a)) resumeRide();
        else if (ACTION_STOP.equals(a)) stopRide();
        else if (ACTION_LAP.equals(a)) markLap();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        try { lm.removeUpdates(this); } catch (Exception ignored) { }
        if (gpx != null) { gpx.close(); gpx = null; }
        if (gpxLoc != null) { GpxStore.finish(this, gpxLoc); }
        releaseWakeLock();
        if (baro != null) { baro.stop(); baro = null; }
        stopBle();
        stopCsc();
        if (tone != null) { tone.release(); tone = null; }
        db.close();
        super.onDestroy();
    }

    // ---------- API untuk UI ----------

    public void setListener(Listener l) { listener = l; }
    public State getState() { return state; }

    /**
     * Locator berkas GPX ride yang sedang direkam (null saat IDLE). Read-only:
     * dipakai peta live (MainActivity, MapActivity) untuk membaca rute yang sudah
     * dilalui. Tidak menyentuh logika perekaman maupun isi berkas.
     */
    public String activeGpxLoc() { return gpxLoc; }
    public RideStats getStats() { return stats; }
    public float getLastAccuracy() { return lastAccuracy; }
    public boolean hasBaro() { return baro != null && baro.available(); }
    public java.util.List<Lap> getLaps() { return laps; }
    public Lap getCurrentLap() {
        if (state != State.RECORDING && state != State.PAUSED) return null;
        Lap l = new Lap();
        l.index = stats.lapIndex;
        l.distanceM = stats.lapDistanceM;
        l.movingMs = stats.lapMovingMs;
        l.elapsedMs = Math.max(0, SystemClock.elapsedRealtime() - lapStartElapsed);
        return l;
    }

    /** Umur fix GPS terakhir dalam ms, atau -1 kalau belum pernah dapat fix. */
    public long getFixAgeMs() {
        return lastFixElapsed == 0 ? -1 : SystemClock.elapsedRealtime() - lastFixElapsed;
    }

    public long getElapsedMs() {
        long now = SystemClock.elapsedRealtime();
        switch (state) {
            case RECORDING: return now - startElapsed - pausedTotal;
            case PAUSED:    return pauseStart - startElapsed - pausedTotal;
            default:        return finalElapsed;
        }
    }

    // ---------- kontrol perekaman ----------

    private void startRide() {
        if (state != State.IDLE) return;
        // Wajib dipanggil cepat setelah startForegroundService() di Android 8+
        startForegroundCompat(buildNotification());
        try {
            // Berkas ride ditaruh di penyimpanan bersama (Download/Zylo di
            // Android 10+) supaya TIDAK hilang kalau aplikasi diuninstall.
            // Kalau gagal, GpxStore jatuh kembali ke folder aplikasi.
            startWall = System.currentTimeMillis();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(startWall));
            gpxLoc = GpxStore.create(this, "ride_" + stamp + ".gpx");
            gpx = new GpxWriter(GpxStore.openWrite(this, gpxLoc, false),
                    getString(R.string.gpx_creator) + " " + stamp);
            stats = new RideStats();
            lastFixElapsed = 0;
            daftarkanGps(RECORDING_INTERVAL_MS);
        } catch (Exception e) { // SecurityException, IOException, IllegalArgumentException
            Log.e(TAG, "start failed", e);
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show();
            if (gpx != null) { gpx.close(); gpx = null; }
        if (gpxLoc != null) { GpxStore.finish(this, gpxLoc); }
            stopForeground(true);
            stopSelf();
            return;
        }
        startElapsed = SystemClock.elapsedRealtime();
        pausedTotal = 0;
        finalElapsed = 0;
        tick = 0;
        lowSpeedSince = 0;
        laps.clear();
        stats.startLap();                 // lap 1 mulai sekarang
        lapStartElapsed = startElapsed;
        lastAlertKmStep = 0;
        lastAlertMinStep = 0;
        state = State.RECORDING;
        if (baro != null) baro.start();
        startBle();
        startCsc();
        saveSession();
        acquireWakeLock();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        notifyUi();
    }

    /**
     * Daftarkan (ulang) GPS dengan laju tertentu.
     *
     * Dipisah supaya laju MEREKAM dan laju JEDA terlihat sebagai dua angka yang
     * berbeda, bukan angka ajaib yang muncul di tiga tempat.
     *
     * @param intervalMs jeda minimum antar fix, milidetik
     */
    private void daftarkanGps(long intervalMs) {
        // minDistance 0: yang membatasi laju adalah interval. Jarak minimum
        // justru membuat fix terlewat kalau pengendara lambat.
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, this);
    }

    private void pauseRide() {
        if (state != State.RECORDING) return;
        // GPS TIDAK dicabut lagi.
        //
        // Dulu di sini ada lm.removeUpdates(this), dan itu membuat auto-resume
        // MATI: checkAutoResume() cuma dipanggil dari onLocationChanged(), yang
        // tidak akan pernah terpicu lagi kalau updates sudah dicabut. Akibatnya
        // pengendara berhenti di lampu merah, ride jeda sendiri, GPS mati, lalu
        // pengendara mengayuh lagi - dan ride tetap diam sampai dia sadar dan
        // menekan resume manual. Waktu dan jarak ride under-count tanpa ada
        // tanda apa pun.
        //
        // Sekarang GPS tetap terdaftar tapi diperlambat: 1 fix tiap beberapa
        // detik cukup untuk mendeteksi "sudah bergerak lagi", dan biaya baterai
        // jauh lebih kecil daripada 1 Hz. onLocationChanged() yang memutuskan
        // apa yang dilakukan dengan fix itu - selama JEDA, fix hanya dipakai
        // untuk mendeteksi gerakan, tidak untuk mencatat jarak.
        pauseStart = SystemClock.elapsedRealtime();
        stats.resetLast();
        state = State.PAUSED;
        daftarkanGps(PAUSED_INTERVAL_MS);
        updateNotification();
        notifyUi();
    }

    private void resumeRide() {
        if (state != State.PAUSED) return;
        try {
            daftarkanGps(RECORDING_INTERVAL_MS);
        } catch (Exception e) {
            Log.e(TAG, "resume failed", e);
            return;
        }
        // B3: masa jeda juga harus dikeluarkan dari penghitung LAP, bukan hanya
        // dari waktu total.
        //
        // Sebelumnya hanya pausedTotal yang ditambah. Akibatnya getCurrentLap()
        // (now - lapStartElapsed) dan closeLap() ikut menghitung masa jeda,
        // sehingga jumlah lap = waktu total + masa jeda - misalnya ride 60 detik
        // dengan satu jeda 30 detik menampilkan lap 90 detik di layar Ringkasan.
        // Lap tunggal pun menyimpang dari total, bukan +/- 2 detik.
        //
        // Menggeser lapStartElapsed ke depan sebesar masa jeda membuat lap dan
        // total memakai garis waktu yang sama: keduanya berhenti selama jeda.
        // checkAutoLap() untuk mode LAP WAKTU ikut terbawa perbaikan yang sama,
        // karena ia juga mengukur dari lapStartElapsed.
        long jeda = SystemClock.elapsedRealtime() - pauseStart;
        pausedTotal += jeda;
        lapStartElapsed += jeda;
        lowSpeedSince = 0;
        state = State.RECORDING;
        updateNotification();
        notifyUi();
    }

    private void stopRide() {
        if (state == State.IDLE) {
            stopForeground(true);
            stopSelf();
            return;
        }
        finalElapsed = getElapsedMs();
        try { lm.removeUpdates(this); } catch (Exception ignored) { }
        handler.removeCallbacks(ticker);
        if (gpx != null) { gpx.close(); gpx = null; }
        if (gpxLoc != null) { GpxStore.finish(this, gpxLoc); }

        // Menyimpan riwayat tidak boleh mematikan aplikasi: kalau gagal (penyimpanan
        // penuh, DB terkunci), status tetap kembali ke IDLE dan pengguna dapat pesan.
        boolean simpanGagal = false;
        if (stats.distanceM >= 20) {
            try {
                // lap yang masih berjalan ikut dicatat, supaya jumlah lap = jarak penuh
                Lap cur = getCurrentLap();
                if (cur != null && cur.distanceM > 1) laps.add(cur);
                stats.startLap();

                RideDb.Row r = new RideDb.Row();
                r.startMs = startWall;
                r.durationMs = finalElapsed;
                r.movingMs = stats.movingMs;
                r.distanceM = stats.distanceM;
                r.maxSpeedMs = stats.maxSpeedMs;
                r.elevGainM = stats.elevGainM;
                r.gpxPath = gpxLoc;
                r.laps = encodeLaps(laps);
                // Pratinjau thumbnail dihitung SEKALI di sini, saat berkas GPX
                // baru selesai ditulis. Kalau ditunda sampai Riwayat dibuka,
                // daftar harus membaca semua GPX setiap kali tampil - puluhan
                // berkas besar di layar yang seharusnya ringan.
                r.preview = buatPratinjau(gpxLoc);
                // Kalori dihitung dari daya perkiraan (gulung + udara + tanjakan),
                // bukan dari jarak saja. Lihat Calories untuk asumsinya.
                r.caloriesKcal = Calories.estimate(stats.distanceM, stats.movingMs,
                        stats.elevGainM, Settings.weight(this));
                long id = db.insert(r);
                if (id > 0) {
                    Settings.setLastRideId(this, id);
                    SessionRecovery.clear(this);
                } else {
                    // insert() bisa mengembalikan -1 tanpa melempar; itu sama
                    // gagalnya dengan pengecualian.
                    simpanGagal = true;
                }
            } catch (Throwable t) {
                Log.e(TAG, "simpan riwayat gagal", t);
                simpanGagal = true;
            }
            // Saat simpan gagal, penanda TIDAK dibersihkan: GPX masih utuh di
            // disk dan SessionRecovery.recover() mendapat kesempatan lagi saat
            // aplikasi dibuka berikutnya.
        } else {
            // rekaman terlalu pendek, buang - tidak ada yang layak dipulihkan
            if (gpxLoc != null) {
                GpxStore.delete(this, gpxLoc);
                gpxLoc = null;
            }
            SessionRecovery.clear(this);
        }
        if (simpanGagal) toast(R.string.save_failed);
        if (baro != null) baro.stop();
        stopBle();
        stopCsc();
        releaseWakeLock();
        state = State.IDLE;
        stopForeground(true);
        stopSelf();
        notifyUi();
    }

    /**
     * Rute ringkas untuk thumbnail Riwayat, dari GPX yang baru ditutup.
     *
     * Gagal membaca bukan alasan membatalkan penyimpanan: rute tanpa thumbnail
     * masih berguna, sedangkan kehilangan seluruh gowes tidak.
     */
    private String buatPratinjau(String loc) {
        if (loc == null) return null;
        try {
            File f = GpxStore.materialize(this, loc);
            return RuteRingkas.dariGpx(f, RuteRingkas.MAKS_TITIK);
        } catch (Throwable t) {
            Log.w(TAG, "pratinjau rute gagal", t);
            return null;
        }
    }

    // ---------- sensor detak jantung ----------

    private void startBle() {
        if (!BleHr.hasPermission(this)) {
            bleStatus = getString(R.string.ble_no_perm);
            return;
        }
        if (!BleHr.available(this)) {
            bleStatus = getString(R.string.ble_no_bt);
            return;
        }
        stopBle();
        ble = new BleHr(this, new BleHr.Callback() {
            @Override
            public void onHr(final int bpm) {
                // callback GATT datang dari thread lain; pindahkan ke main thread
                handler.post(new Runnable() {
                    @Override public void run() { stats.setHr(bpm); notifyUi(); }
                });
            }

            @Override
            public void onStatus(final String text) {
                handler.post(new Runnable() {
                    @Override public void run() { bleStatus = text; notifyUi(); }
                });
            }
        });
        ble.setMac(Settings.hrMac(this));
        ble.start();
    }

    private void stopBle() {
        if (ble != null) {
            ble.stop();
            ble = null;
        }
        bleStatus = null;
    }

    /** Status sensor untuk ditampilkan; null = tidak ada sensor dikonfigurasi. */
    public String getBleStatus() {
        return bleStatus;
    }

    /** true kalau ada data detak jantung yang masuk. */
    public boolean hasHr() {
        return stats.hr > 0;
    }

    public boolean isHrConnected() {
        return ble != null && ble.connected();
    }

    // ---------- sensor kecepatan/kadensi ----------

    private void startCsc() {
        if (!BleHr.hasPermission(this)) {
            cscStatus = getString(R.string.ble_no_perm);
            return;
        }
        if (!BleHr.available(this)) {
            cscStatus = getString(R.string.ble_no_bt);
            return;
        }
        stopCsc();
        csc = new BleCsc(this, new BleCsc.Callback() {
            @Override
            public void onData(final float speedMps, final int cadenceRpm) {
                handler.post(new Runnable() {
                    @Override public void run() {
                        long now = SystemClock.elapsedRealtime();
                        if (speedMps >= 0f) stats.setSensorSpeed(speedMps, now);
                        if (cadenceRpm >= 0) stats.setCadence(cadenceRpm, now);
                        notifyUi();
                    }
                });
            }

            @Override
            public void onBattery(final int pct) {
                handler.post(new Runnable() {
                    @Override public void run() { cscBattery = pct; notifyUi(); }
                });
            }

            @Override
            public void onStatus(final String text) {
                handler.post(new Runnable() {
                    @Override public void run() { cscStatus = text; notifyUi(); }
                });
            }
        });
        csc.setWheelMm(Settings.wheelMm(this));
        csc.setMac(Settings.cscMac(this));
        csc.start();
    }

    private void stopCsc() {
        if (csc != null) {
            csc.stop();
            csc = null;
        }
        stats.clearSensor();
        cscStatus = null;
        cscBattery = -1;
    }

    /** Status sensor CSC; null = tidak ada sensor yang dipilih/diaktifkan. */
    public String getCscStatus() {
        return cscStatus;
    }

    public boolean isCscConnected() {
        return csc != null && csc.connected();
    }

    /** Baterai sensor CSC dalam persen; -1 kalau tidak terbaca. */
    public int getCscBattery() {
        return cscBattery;
    }

    /**
     * Ukuran roda baru dipakai sensor yang SEDANG tersambung.
     *
     * Tanpa ini, pengguna yang mengganti ukuran roda saat gowes masih melihat
     * kecepatan lama sampai berhenti dan mulai lagi - padahal layar Sensor
     * menampilkan angka langsung, jadi selisihnya terlihat seperti bug.
     */
    public void setWheelMm(int mm) {
        if (csc != null) csc.setWheelMm(mm);
    }

    // ---------- auto-pause ----------

    /**
     * Jeda otomatis saat berhenti. Memakai penanda waktu dari fix GPS supaya responsif,
     * dan hanya menjeda sekali (lowSpeedSince dinolkan setelah jeda).
     */
    private void checkAutoPause() {
        if (!Settings.autoPause(this) || lowSpeedSince == 0) return;
        if (AutoJeda.harusJeda(stats.currentSpeedMs, Settings.autoPauseSpeedMs(this),
                lowSpeedSince, Settings.autoPauseDelayMs(this),
                SystemClock.elapsedRealtime())) {
            lowSpeedSince = 0;
            pauseRide();
            toast(R.string.autopause_on);
        }
    }

    /**
     * Periksa apakah pengendara sudah bergerak lagi, supaya ride lanjut sendiri.
     *
     * @param loc fix GPS terbaru selama JEDA
     */
    private void checkAutoResume(Location loc) {
        if (!Settings.autoPause(this) || state != State.PAUSED) return;
        // loc bisa null kalau sistem mengirim callback kosong; jangan sampai
        // itu membuat service mati di tengah ride.
        if (loc == null) return;
        // Kecepatan dihitung dari fix JEDA sendiri, BUKAN dari stats.
        //
        // Selama jeda stats sengaja tidak diperbarui (lihat
        // onLocationChanged: fix JEDA tidak dicatat ke jarak), dan
        // pauseRide() memanggil stats.resetLast() yang mengosongkan
        // currentSpeedMs. Kalau tetap membaca stats.currentSpeedMs di sini,
        // nilainya akan selalu 0 dan auto-resume tidak akan pernah menyala -
        // persis bug yang sedang diperbaiki, hanya pindah tempat.
        float v = AutoJeda.kecepatanDari(loc.hasSpeed(), loc.getSpeed());
        if (AutoJeda.harusLanjut(v, Settings.autoPauseSpeedMs(this))) {
            resumeRide();
            toast(R.string.autopause_off);
        }
    }

    // ---------- lap ----------

    /** Lap manual: dari tombol atau notifikasi. */
    private void markLap() {
        if (state != State.RECORDING && state != State.PAUSED) return;
        closeLap();
        beep();
        updateNotification();
        notifyUi();
    }

    private void checkAutoLap() {
        int mode = Settings.lapMode(this);
        if (mode == Settings.LAP_DISTANCE) {
            if (stats.lapDistanceM >= Settings.lapDistanceM(this)) { closeLap(); beep(); }
        } else if (mode == Settings.LAP_TIME) {
            long lapMs = SystemClock.elapsedRealtime() - lapStartElapsed;
            if (lapMs >= Settings.lapTimeMs(this)) { closeLap(); beep(); }
        }
    }

    /** Simpan lap yang sedang berjalan lalu mulai lap baru. */
    private void closeLap() {
        Lap l = getCurrentLap();
        if (l != null) laps.add(l);
        stats.startLap();
        lapStartElapsed = SystemClock.elapsedRealtime();
    }

    // ---------- peringatan ----------

    private void checkAlerts() {
        float kmTarget = Settings.alertKm(this);
        if (kmTarget > 0) {
            long step = (long) (stats.distanceM / (kmTarget * 1000.0));
            if (step > lastAlertKmStep) {
                lastAlertKmStep = step;
                toast(getString(R.string.alert_km, Fmt.km(step * kmTarget * 1000.0)));
                beep();
            }
        }
        int minTarget = Settings.alertMin(this);
        if (minTarget > 0) {
            long step = getElapsedMs() / (minTarget * 60000L);
            if (step > lastAlertMinStep) {
                lastAlertMinStep = step;
                toast(getString(R.string.alert_min, step * minTarget));
                beep();
            }
        }
    }

    private void beep() {
        if (!Settings.beep(this)) return;
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        } catch (Throwable ignored) { }
    }

    private void toast(int resId, Object... args) {
        toast(args.length == 0 ? getString(resId) : getString(resId, args));
    }

    private void toast(final String msg) {
        handler.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(RideService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Simpan penanda sesi ke SharedPreferences. Kalau proses dibunuh di tengah gowes,
     * App bisa memulihkan rekaman ini saat aplikasi dibuka lagi.
     */
    private void saveSession() {
        if (state == State.IDLE || gpxLoc == null) return;
        try {
            SessionRecovery.mark(this, gpxLoc, startWall, getElapsedMs(),
                    stats.distanceM, stats.movingMs, stats.maxSpeedMs, stats.elevGainM,
                    encodeLaps(laps));
        } catch (Throwable ignored) { }
    }

    /** Lap -> teks ringkas untuk satu kolom DB (tanpa tabel tambahan). */
    static String encodeLaps(java.util.List<Lap> list) {
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Lap l : list) {
            if (sb.length() > 0) sb.append('|');
            sb.append(l.index).append(';')
              .append((long) l.distanceM).append(';')
              .append(l.movingMs).append(';')
              .append(l.elapsedMs);
        }
        return sb.toString();
    }

    /** Kebalikan encodeLaps; mengembalikan daftar kosong kalau teks rusak/kosong. */
    static java.util.List<Lap> decodeLaps(String s) {
        java.util.List<Lap> out = new java.util.ArrayList<Lap>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split("\\|")) {
            String[] f = part.split(";");
            if (f.length < 4) continue;
            try {
                Lap l = new Lap();
                l.index = Integer.parseInt(f[0]);
                l.distanceM = Double.parseDouble(f[1]);
                l.movingMs = Long.parseLong(f[2]);
                l.elapsedMs = Long.parseLong(f[3]);
                out.add(l);
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    // ---------- LocationListener ----------

    @Override
    public void onLocationChanged(Location loc) {
        if (state != State.RECORDING && state != State.PAUSED) return;
        lastFixElapsed = SystemClock.elapsedRealtime();

        if (state == State.PAUSED) {
            // Selama JEDA fix GPS hanya dipakai untuk mendeteksi "sudah
            // bergerak lagi". TIDAK dicatat ke jarak, TIDAK ditulis ke GPX:
            // masa jeda bukan bagian dari rute, dan rute yang menampilkan
            // pengendara "meluncur" saat dia sedang berhenti di lampu merah
            // salah.
            checkAutoResume(loc);
            return;
        }

        // Penanda kecepatan rendah di sini (bukan di ticker) supaya responsif:
        // fix GPS datang 1x/detik, jadi tidak perlu menunggu tick berikutnya.
        if (stats.currentSpeedMs < Settings.autoPauseSpeedMs(this)) {
            if (lowSpeedSince == 0) lowSpeedSince = SystemClock.elapsedRealtime();
        } else {
            lowSpeedSince = 0;
        }
        lastAccuracy = loc.hasAccuracy() ? loc.getAccuracy() : 0f;
        // Barometer lebih halus daripada altitude GPS: pakai bila tersedia & terkalibrasi
        if (baro != null && baro.ready()) {
            double gpsAlt = loc.hasAltitude() ? loc.getAltitude() : Double.NaN;
            double alt = baro.altitude(gpsAlt);
            if (!Double.isNaN(alt)) stats.setBaroAltitude(alt);
        }
        if (stats.onLocation(loc) && gpx != null) {
            try {
                gpx.add(loc, stats.hr);
                gagalTulis = 0;              // berhasil, hitungan gagal diulang
            } catch (Exception e) {
                Log.e(TAG, "gpx write failed", e);
                gagalTulisGpx();
            }
        }
        notifyUi();
    }

    /**
     * Catat satu kegagalan menulis GPX, dan hentikan ride kalau ternyata
     * berulang.
     *
     * Dulu kegagalan di sini cuma masuk Log.e. Akibatnya paling buruk: kalau
     * penyimpanan penuh, SETIAP titik gagal ditulis - pengendara gowes satu
     * jam penuh dengan angka yang tampak normal (statistik dihitung di memori,
     * bukan dari berkas), lalu saat selesai rutenya kosong atau terpotus. DB
     * tetap dapat statistik, jadi riwayat menampilkan ride tanpa rute - dan
     * pengendara baru tahu setelah semuanya selesai.
     *
     * Sekarang kegagalan dihitung. Beberapa kegagalan berturut-turut berarti
     * ada yang salah secara sistemik, bukan sesaat, dan ride dihentikan
     * dengan penjelasan - lebih baik ride berakhir dengan jujur daripada
     * menghasilkan rekaman yang kelihatan utuh tapi rutenya hilang.
     */
    private void gagalTulisGpx() {
        if (++gagalTulis < BATAS_GAGAL_TULIS) return;
        // Jangan berulang kali memicu penghentian untuk masalah yang sama.
        gagalTulis = 0;
        Log.e(TAG, "gpx gagal ditulis " + BATAS_GAGAL_TULIS
                + "x berturut-turut, rekaman dihentikan");
        toast(R.string.record_stopped_storage);
        stopRide();
    }

    // onStatusChanged sudah usang dan dikeluarkan dari LocationListener sejak API 30:
    // sengaja TANPA @Override supaya tetap kompilasi di compileSdk 34.
    public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    // ---------- helper ----------

    private void notifyUi() {
        if (listener != null) listener.onRideUpdate();
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cyclocomp:ride");
            wakeLock.setReferenceCounted(false);
        }
        if (!wakeLock.isHeld()) wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // minSdk 23: FLAG_IMMUTABLE selalu bisa dipakai, dan WAJIB di Android 12+
        // (tanpa flag mutable/immutable, PendingIntent melempar IllegalArgumentException).
        int pf = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, pf);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW));
            }
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        String text = getString(R.string.notif_line,
                Fmt.speed(stats.speedMs(SystemClock.elapsedRealtime())),
                Fmt.km(stats.distanceM), Fmt.duration(getElapsedMs()));
        b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(0, getString(R.string.notif_lap),
                        PendingIntent.getService(this, 10,
                                new Intent(this, RideService.class).setAction(ACTION_LAP), pf));
        return b.build();
    }
}
