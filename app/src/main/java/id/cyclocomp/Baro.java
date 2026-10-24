package id.cyclocomp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Ketinggian dari barometer (Sensor.TYPE_PRESSURE).
 *
 * Kenapa perlu: altitude GPS dari HP kasar - bisa melompat beberapa meter antar titik,
 * sehingga "tanjakan" dan "kemiringan" ikut gelisah. Sensor tekanan jauh lebih halus,
 * dan hampir semua HP menengah ke atas punya. Garmin/Wahoo melakukan hal yang sama.
 *
 * Cara kalibrasi: tekanan berbeda tiap hari dan tiap ketinggian, jadi angka absolut
 * sensor tidak bisa dipakai langsung. Kita jadikan altitude GPS pertama sebagai acuan,
 * lalu barometer mengikuti perubahannya. Acuan diperbaiki berkala saat GPS masih
 * memberi altitude yang layak, supaya tidak melenceng karena perubahan cuaca.
 */
final class Baro implements SensorEventListener {
    private static final String TAG = "Baro";

    private final SensorManager sm;
    private final Sensor sensor;

    private volatile double rawAlt = Double.NaN;   // altitude mentah dari tekanan
    private double offset = Double.NaN;            // rawAlt + offset = altitude sebenarnya
    private boolean calibrated;
    private long samples;

    Baro(Context c) {
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        sensor = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    boolean available() { return sensor != null; }

    void start() {
        if (sensor == null) return;
        try {
            // SENSOR_DELAY_NORMAL cukup: ketinggian tidak perlu 50 Hz, dan ini hemat baterai
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Throwable t) {
            Log.w(TAG, "sensor tekanan tidak bisa dipakai", t);
        }
    }

    void stop() {
        if (sm == null || sensor == null) return;
        try { sm.unregisterListener(this); } catch (Throwable ignored) { }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e == null || e.values == null || e.values.length == 0) return;
        float hPa = e.values[0];
        if (hPa <= 0) return;
        rawAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa);
        samples++;
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }

    /**
     * Ketinggian terkoreksi, atau NaN kalau belum terkalibrasi / sensor tidak ada.
     * gpsAlt dipakai sebagai acuan saat pertama kali dan untuk koreksi berkala.
     */
    double altitude(double gpsAlt) {
        if (Double.isNaN(rawAlt)) return Double.NaN;

        if (!calibrated) {
            if (Double.isNaN(gpsAlt)) return Double.NaN;   // tunggu GPS dulu
            offset = gpsAlt - rawAlt;
            calibrated = true;
            return gpsAlt;
        }

        double alt = rawAlt + offset;
        // Koreksi acuan bila GPS memberi altitude dan selisihnya sudah jauh (drift cuaca).
        if (!Double.isNaN(gpsAlt) && Math.abs(alt - gpsAlt) > 25.0) {
            offset = gpsAlt - rawAlt;
            alt = gpsAlt;
        }
        return alt;
    }

    /** Sudah menerima data tekanan? */
    boolean ready() { return samples > 0; }
}
