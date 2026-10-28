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
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationManager lm;
    private PowerManager.WakeLock wakeLock;
    private RideDb db;
    private BleHr ble;
    private String bleStatus;
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
        if (tone != null) { tone.release(); tone = null; }
        db.close();
        super.onDestroy();
    }

    // ---------- API untuk UI ----------

    public void setListener(Listener l) { listener = l; }
    public State getState() { return state; }
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
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
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
        saveSession();
        acquireWakeLock();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        notifyUi();
    }

    private void pauseRide() {
        if (state != State.RECORDING) return;
        lm.removeUpdates(this);
        pauseStart = SystemClock.elapsedRealtime();
        stats.resetLast();
        state = State.PAUSED;
        updateNotification();
        notifyUi();
    }

    private void resumeRide() {
        if (state != State.PAUSED) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
        } catch (Exception e) {
            Log.e(TAG, "resume failed", e);
            return;
        }
        pausedTotal += SystemClock.elapsedRealtime() - pauseStart;
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
                // Kalori dihitung dari daya perkiraan (gulung + udara + tanjakan),
                // bukan dari jarak saja. Lihat Calories untuk asumsinya.
                r.caloriesKcal = Calories.estimate(stats.distanceM, stats.movingMs,
                        stats.elevGainM, Settings.weight(this));
                long id = db.insert(r);
                Settings.setLastRideId(this, id);
            } catch (Throwable t) {
                Log.e(TAG, "simpan riwayat gagal", t);
                simpanGagal = true;
            }
            SessionRecovery.clear(this);
        } else if (gpxLoc != null) {
            // rekaman terlalu pendek, buang
            GpxStore.delete(this, gpxLoc);
            gpxLoc = null;
            SessionRecovery.clear(this);
        }
        SessionRecovery.clear(this);
        if (simpanGagal) toast(R.string.save_failed);
        if (baro != null) baro.stop();
        stopBle();
        releaseWakeLock();
        state = State.IDLE;
        stopForeground(true);
        stopSelf();
        notifyUi();
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

    // ---------- auto-pause ----------

    /**
     * Jeda otomatis saat berhenti. Memakai penanda waktu dari fix GPS supaya responsif,
     * dan hanya menjeda sekali (lowSpeedSince dinolkan setelah jeda).
     */
    private void checkAutoPause() {
        if (!Settings.autoPause(this) || lowSpeedSince == 0) return;
        long berhenti = SystemClock.elapsedRealtime() - lowSpeedSince;
        if (berhenti >= Settings.autoPauseDelayMs(this)) {
            lowSpeedSince = 0;
            pauseRide();
            toast(R.string.autopause_on);
        }
    }

    private void checkAutoResume() {
        if (!Settings.autoPause(this) || state != State.PAUSED) return;
        if (stats.currentSpeedMs >= Settings.autoPauseSpeedMs(this)) {
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
        // Penanda kecepatan rendah di sini (bukan di ticker) supaya responsif:
        // fix GPS datang 1x/detik, jadi tidak perlu menunggu tick berikutnya.
        if (state == State.RECORDING) {
            if (stats.currentSpeedMs < Settings.autoPauseSpeedMs(this)) {
                if (lowSpeedSince == 0) lowSpeedSince = SystemClock.elapsedRealtime();
            } else {
                lowSpeedSince = 0;
            }
        } else if (state == State.PAUSED) {
            checkAutoResume();
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
            } catch (Exception e) {
                Log.e(TAG, "gpx write failed", e);
            }
        }
        notifyUi();
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
        String text = getString(R.string.notif_line, Fmt.speed(stats.currentSpeedMs),
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
