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
    private GpxWriter gpx;
    private File gpxFile;
    private Listener listener;

    private State state = State.IDLE;
    private RideStats stats = new RideStats();
    private long startWall, startElapsed, pausedTotal, pauseStart, finalElapsed;
    private long lastFixElapsed;
    private float lastAccuracy;
    private int tick;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (state == State.IDLE) return;
            if (++tick % 5 == 0) updateNotification();
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
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        try { lm.removeUpdates(this); } catch (Exception ignored) { }
        if (gpx != null) { gpx.close(); gpx = null; }
        releaseWakeLock();
        db.close();
        super.onDestroy();
    }

    // ---------- API untuk UI ----------

    public void setListener(Listener l) { listener = l; }
    public State getState() { return state; }
    public RideStats getStats() { return stats; }
    public float getLastAccuracy() { return lastAccuracy; }

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
            File base = getExternalFilesDir(null);
            if (base == null) base = getFilesDir();
            File dir = new File(base, "rides");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();

            startWall = System.currentTimeMillis();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(startWall));
            gpxFile = new File(dir, "ride_" + stamp + ".gpx");
            gpx = new GpxWriter(gpxFile, "Ride " + stamp);
            stats = new RideStats();
            lastFixElapsed = 0;
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
        } catch (Exception e) { // SecurityException, IOException, IllegalArgumentException
            Log.e(TAG, "start failed", e);
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show();
            if (gpx != null) { gpx.close(); gpx = null; }
            stopForeground(true);
            stopSelf();
            return;
        }
        startElapsed = SystemClock.elapsedRealtime();
        pausedTotal = 0;
        finalElapsed = 0;
        tick = 0;
        state = State.RECORDING;
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

        if (stats.distanceM >= 20) {
            RideDb.Row r = new RideDb.Row();
            r.startMs = startWall;
            r.durationMs = finalElapsed;
            r.movingMs = stats.movingMs;
            r.distanceM = stats.distanceM;
            r.maxSpeedMs = stats.maxSpeedMs;
            r.elevGainM = stats.elevGainM;
            r.gpxPath = gpxFile.getAbsolutePath();
            db.insert(r);
        } else if (gpxFile != null) {
            //noinspection ResultOfMethodCallIgnored
            gpxFile.delete(); // rekaman terlalu pendek, buang
        }
        releaseWakeLock();
        state = State.IDLE;
        stopForeground(true);
        stopSelf();
        notifyUi();
    }

    // ---------- LocationListener ----------

    @Override
    public void onLocationChanged(Location loc) {
        if (state != State.RECORDING) return;
        lastFixElapsed = SystemClock.elapsedRealtime();
        lastAccuracy = loc.hasAccuracy() ? loc.getAccuracy() : 0f;
        if (stats.onLocation(loc) && gpx != null) {
            try {
                gpx.add(loc);
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
        int pf = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pf |= PendingIntent.FLAG_IMMUTABLE;
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
        String text = Fmt.speed(stats.currentSpeedMs) + " km/j  |  "
                + Fmt.km(stats.distanceM) + " km  |  " + Fmt.duration(getElapsedMs());
        b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }
}
