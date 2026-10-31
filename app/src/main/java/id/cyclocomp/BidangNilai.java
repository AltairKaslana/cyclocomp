package id.cyclocomp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Menerjemahkan satu bidang menjadi teks yang siap tampil.
 *
 * Dipakai dua tempat: dashboard (MainActivity) dan pratinjau di layar Atur
 * layar. Karena dipakai dua tempat, logikanya harus tinggal di SATU kelas -
 * kalau ditulis dua kali, pratinjau dan dashboard bisa berbeda pendapat tentang
 * arti sebuah angka, dan itu justru bug yang paling sulit dilacak.
 *
 * Bidang yang butuh sensor yang belum ada (kadensi, daya) selalu tampil "-",
 * bukan "0": nol terbaca seperti "sensor membaca nol".
 */
public final class BidangNilai {

    private BidangNilai() {
    }

    /** Nilai baterai HP dalam persen, -1 kalau tidak bisa dibaca. */
    public static int baterai(Context c) {
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = c.registerReceiver(null, f);
            if (b == null) return -1;
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            if (level < 0 || scale <= 0) return -1;
            return Math.round(level * 100f / scale);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Jam dinding, 24 jam, sesuai locale. */
    public static String jam(Context c) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    /**
     * Teks untuk satu bidang.
     *
     * @param s       statistik gowes yang sedang berjalan
     * @param elapsed waktu total berjalan sejak MULAI (termasuk jeda)
     * @param lap     lap yang sedang berjalan; null = bukan halaman LAP
     */
    public static String teks(Context c, int f, RideStats s, long elapsed, Lap lap) {
        switch (f) {
            case Bidang.KECEPATAN:
                return Fmt.speed(s.speedMs(SystemClock.elapsedRealtime()));
            case Bidang.RATA_RATA:
                return Fmt.speed(lap != null && lap.movingMs > 0
                        ? (float) (lap.distanceM / (lap.movingMs / 1000.0))
                        : s.avgSpeedMs());
            case Bidang.MAKS:
                return Fmt.speed(s.maxSpeedMs);
            case Bidang.JARAK:
                return Fmt.km(lap != null ? lap.distanceM : s.distanceM);
            case Bidang.WAKTU_GERAK:
                return Fmt.duration(lap != null ? lap.movingMs : s.movingMs);
            case Bidang.WAKTU_TOTAL:
                return Fmt.duration(lap != null ? lap.elapsedMs : elapsed);
            case Bidang.KADENSI: {
                int rpm = s.cadenceNow(SystemClock.elapsedRealtime());
                return rpm < 0 ? "-" : String.valueOf(rpm);
            }
            case Bidang.DETAK:
                return s.hr > 0 ? String.valueOf(s.hr) : "-";
            case Bidang.DAYA:
                return "-";          // sensor daya belum ada
            case Bidang.TANJAKAN:
                return Fmt.meters(s.elevGainM);
            case Bidang.KEMIRINGAN:
                // Angka selalu magnitudo; arah sudah dikatakan ruas jalan di
                // SEKILAS. Kasus rata ikut lewat Fmt.grade() supaya pemisah
                // desimalnya konsisten dengan locale.
                return Fmt.grade(Math.abs(s.gradePercent) < 0.15 ? 0.0 : Math.abs(s.gradePercent));
            case Bidang.KETINGGIAN:
                return Double.isNaN(s.altitudeM) ? "--" : Fmt.meters(s.altitudeM);
            case Bidang.KALORI:
                return Fmt.kcal(Calories.estimate(s.distanceM, s.movingMs,
                        s.elevGainM, id.cyclocomp.Settings.weight(c)));
            case Bidang.JAM:
                return jam(c);
            case Bidang.BATERAI: {
                int pct = baterai(c);
                return pct < 0 ? "--" : pct + "%";
            }
            default:
                return "--";
        }
    }

    /**
     * Bidang yang butuh sensor DAN sensornya belum terpasang.
     *
     * Detak jantung dianggap tersedia kalau ada sensor terpilih ATAU ada nilai
     * detak yang masuk - yang kedua penting karena HR bisa datang dari jam
     * terhubung yang tidak terdaftar di pengaturan.
     */
    public static boolean butuhSensorYangBelumAda(Context c, int f, RideStats s) {
        switch (Bidang.sensor(f)) {
            case Bidang.SENSOR_HR:
                return id.cyclocomp.Settings.hrMac(c).isEmpty() && s.hr <= 0;
            case Bidang.SENSOR_CSC:
                return id.cyclocomp.Settings.cscMac(c).isEmpty()
                        && !s.hasCadence(SystemClock.elapsedRealtime());
            case Bidang.SENSOR_DAYA:
                return true;    // sensor daya belum ada di versi ini
            default:
                return false;
        }
    }

    /**
     * "Atur layar" butuh contoh nilai supaya pratinjaunya terlihat seperti
     * dashboard sungguhan, bukan kotak kosong. Nilai di sini contoh berjalan
     * (bukan nol) supaya panjang teksnya mewakili keadaan terburuk.
     */
    public static String contoh(Context c, int f) {
        RideStats s = new RideStats();
        s.currentSpeedMs = 28.4f;
        s.maxSpeedMs = 47.2f;
        s.distanceM = 42_180;
        s.movingMs = 5_412_000;      // 1:30:12
        s.elevGainM = 512;
        s.altitudeM = 318.6;
        s.gradePercent = 4.2;
        s.hr = 142;
        // contoh sensor: kadensi 84 rpm dan kecepatan roda 28,4 km/jam, supaya
        // pratinjau KADENSI tidak tampil "-" di layar Atur layar
        long now = SystemClock.elapsedRealtime();
        s.setCadence(84, now);
        s.setSensorSpeed(28.4f / 3.6f, now);
        Lap lap = null;
        if (f == Bidang.JARAK || f == Bidang.WAKTU_GERAK || f == Bidang.WAKTU_TOTAL
                || f == Bidang.RATA_RATA) {
            lap = new Lap();
            lap.distanceM = 8_640;
            lap.movingMs = 1_014_000;
            lap.elapsedMs = 1_062_000;
        }
        return teks(c, f, s, 5_472_000, lap);
    }
}
