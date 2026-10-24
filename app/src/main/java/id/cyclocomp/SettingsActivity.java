package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Pengaturan berkelompok dalam kartu.
 *
 * Baris dibangun di kode, bukan lebar di XML: isi tiap baris (judul + ringkasan nilai)
 * bergantung pada nilai tersimpan, dan membangunnya langsung menjaga keduanya tetap
 * sinkron setelah diubah - tidak ada salinan daftar yang bisa basi.
 */
public class SettingsActivity extends Activity {

    // pengenal baris
    private static final int R_AUTOPAUSE = 1, R_AP_SPEED = 2, R_AP_DELAY = 3;
    private static final int R_LAP_MODE = 4, R_LAP_DIST = 5, R_LAP_TIME = 6;
    private static final int R_ALERT_KM = 7, R_ALERT_MIN = 8, R_BEEP = 9;
    private static final int R_RESET = 10;

    private LinearLayout groups;
    private LinearLayout lastCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.fonts(getWindow().getDecorView(), this);
        groups = (LinearLayout) findViewById(R.id.groups);
        paint();
    }

    // ---------- gambar ulang seluruh daftar ----------

    private void paint() {
        groups.removeAllViews();

        group("PEREKAMAN");
        switchRow(R_AUTOPAUSE, R.string.settings_autopause, "Jeda sendiri saat berhenti");
        valueRow(R_AP_SPEED, R.string.settings_ap_speed, "Di bawah kecepatan ini dianggap berhenti");
        valueRow(R_AP_DELAY, R.string.settings_ap_delay, "Lama berhenti sebelum dijeda");
        switchRow(R_BEEP, R.string.settings_beep, "Bunyi saat lap dan peringatan");

        group("LAP");
        valueRow(R_LAP_MODE, R.string.settings_lap, "Cara lap baru dimulai");
        if (Settings.lapMode(this) == Settings.LAP_DISTANCE) {
            valueRow(R_LAP_DIST, R.string.settings_lap_dist, "Panjang satu lap");
        } else if (Settings.lapMode(this) == Settings.LAP_TIME) {
            valueRow(R_LAP_TIME, R.string.settings_lap_time, "Durasi satu lap");
        }

        group("PERINGATAN");
        valueRow(R_ALERT_KM, R.string.settings_alert_km, "Beri tahu tiap jarak tertentu");
        valueRow(R_ALERT_MIN, R.string.settings_alert_min, "Beri tahu tiap selang waktu");

        group("LAINNYA");
        valueRow(R_RESET, R.string.settings_reset, "Kembalikan semua ke nilai bawaan");

        TextView note = new TextView(this);
        note.setText(baroSensorText());
        note.setTextColor(getResources().getColor(R.color.label));
        note.setTextSize(12);
        note.setPadding(dp(16), dp(12), dp(16), dp(4));
        Ui.fonts(note, this);
        groups.addView(note);
    }

    private String baroSensorText() {
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        boolean ada = sm != null && sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null;
        return ada
                ? "Barometer terdeteksi — tanjakan dihitung dari tekanan udara."
                : "HP ini tanpa barometer — tanjakan memakai ketinggian GPS.";
    }

    // ---------- pembangun baris ----------

    private void group(String title) {
        TextView h = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_group_header, groups, false);
        h.setText(title);
        Ui.fonts(h, this);
        groups.addView(h);

        lastCard = new LinearLayout(this);
        lastCard.setOrientation(LinearLayout.VERTICAL);
        lastCard.setBackgroundResource(R.drawable.bg_card);
        lastCard.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        groups.addView(lastCard);
    }

    private void valueRow(final int id, int titleRes, String summary) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_setting_value, lastCard, false);
        ((TextView) v.findViewById(R.id.title)).setText(titleRes);
        ((TextView) v.findViewById(R.id.summary)).setText(summary + " · " + valueOf(id));
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { onRow(id); }
        });
        Ui.fonts(v, this);
        addRow(v);
    }

    private void switchRow(final int id, int titleRes, String summary) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_setting_switch, lastCard, false);
        ((TextView) v.findViewById(R.id.title)).setText(titleRes);
        ((TextView) v.findViewById(R.id.summary)).setText(summary);
        Switch sw = (Switch) v.findViewById(R.id.sw);
        sw.setChecked(isOn(id));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                setOn(id, checked);
            }
        });
        Ui.fonts(v, this);
        addRow(v);
    }

    /** Tambahkan pembatas tipis sebelum baris, kecuali pada baris pertama kartu. */
    private void addRow(View v) {
        if (lastCard.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(getResources().getColor(R.color.divider));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lp.leftMargin = dp(16);
            lp.rightMargin = dp(16);
            line.setLayoutParams(lp);
            lastCard.addView(line);
        }
        lastCard.addView(v);
    }

    // ---------- nilai ----------

    private boolean isOn(int id) {
        return id == R_AUTOPAUSE ? Settings.autoPause(this) : Settings.beep(this);
    }

    private void setOn(int id, boolean v) {
        if (id == R_AUTOPAUSE) Settings.setAutoPause(this, v);
        else if (id == R_BEEP) Settings.setBeep(this, v);
    }

    private String valueOf(int id) {
        switch (id) {
            case R_AUTOPAUSE: return onOff(Settings.autoPause(this));
            case R_AP_SPEED:  return String.format("%.0f km/j", Settings.autoPauseSpeed(this));
            case R_AP_DELAY:  return getString(R.string.settings_seconds, Settings.autoPauseDelay(this));
            case R_LAP_MODE:
                int m = Settings.lapMode(this);
                return m == Settings.LAP_DISTANCE ? getString(R.string.settings_by_distance)
                        : m == Settings.LAP_TIME ? getString(R.string.settings_by_time)
                        : getString(R.string.settings_manual);
            case R_LAP_DIST:  return getString(R.string.settings_km, Settings.lapDistance(this));
            case R_LAP_TIME:  return getString(R.string.settings_minutes, Settings.lapTime(this));
            case R_ALERT_KM:
                float km = Settings.alertKm(this);
                return km <= 0 ? getString(R.string.settings_off) : getString(R.string.settings_km, km);
            case R_ALERT_MIN:
                int mn = Settings.alertMin(this);
                return mn <= 0 ? getString(R.string.settings_off) : getString(R.string.settings_minutes, mn);
            case R_BEEP:      return onOff(Settings.beep(this));
            default:          return "";
        }
    }

    private String onOff(boolean v) {
        return getString(v ? R.string.settings_on : R.string.settings_off);
    }

    // ---------- aksi ----------

    private void onRow(int id) {
        switch (id) {
            case R_AP_SPEED: {
                final float[] vals = {2, 3, 5, 8, 10};
                choose(R.string.settings_ap_speed,
                        new String[]{"2 km/j", "3 km/j", "5 km/j", "8 km/j", "10 km/j"},
                        nearest(vals, Settings.autoPauseSpeed(this)),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAutoPauseSpeed(SettingsActivity.this, vals[i]);
                                paint();
                            }
                        });
                break;
            }
            case R_AP_DELAY: {
                final int[] vals = {3, 5, 10, 15, 30};
                choose(R.string.settings_ap_delay,
                        new String[]{"3 detik", "5 detik", "10 detik", "15 detik", "30 detik"},
                        nearestInt(vals, Settings.autoPauseDelay(this)),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAutoPauseDelay(SettingsActivity.this, vals[i]);
                                paint();
                            }
                        });
                break;
            }
            case R_LAP_MODE:
                choose(R.string.settings_lap,
                        new String[]{getString(R.string.settings_manual),
                                getString(R.string.settings_by_distance),
                                getString(R.string.settings_by_time)},
                        Settings.lapMode(this),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setLapMode(SettingsActivity.this, i);
                                paint();
                            }
                        });
                break;
            case R_LAP_DIST: {
                final float[] vals = {1, 2, 5, 10, 20};
                choose(R.string.settings_lap_dist,
                        new String[]{"1 km", "2 km", "5 km", "10 km", "20 km"},
                        nearest(vals, Settings.lapDistance(this)),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setLapDistance(SettingsActivity.this, vals[i]);
                                paint();
                            }
                        });
                break;
            }
            case R_LAP_TIME: {
                final int[] vals = {5, 10, 20, 30, 60};
                choose(R.string.settings_lap_time,
                        new String[]{"5 menit", "10 menit", "20 menit", "30 menit", "60 menit"},
                        nearestInt(vals, Settings.lapTime(this)),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setLapTime(SettingsActivity.this, vals[i]);
                                paint();
                            }
                        });
                break;
            }
            case R_ALERT_KM: {
                final float[] vals = {0, 5, 10, 20, 50};
                choose(R.string.settings_alert_km,
                        new String[]{getString(R.string.settings_off), "5 km", "10 km", "20 km", "50 km"},
                        nearest(vals, Settings.alertKm(this)),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAlertKm(SettingsActivity.this, vals[i]);
                                paint();
                            }
                        });
                break;
            }
            case R_ALERT_MIN: {
                final int[] vals = {0, 15, 30, 45, 60};
                choose(R.string.settings_alert_min,
                        new String[]{getString(R.string.settings_off), "15 menit", "30 menit",
                                "45 menit", "60 menit"},
                        nearestInt(vals, Settings.alertMin(this)),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAlertMin(SettingsActivity.this, vals[i]);
                                paint();
                            }
                        });
                break;
            }
            case R_RESET:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_reset)
                        .setMessage("Kembalikan semua pengaturan ke nilai bawaan?")
                        .setPositiveButton("Ya", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                Settings.setAutoPause(SettingsActivity.this, true);
                                Settings.setAutoPauseSpeed(SettingsActivity.this, 3f);
                                Settings.setAutoPauseDelay(SettingsActivity.this, 5);
                                Settings.setLapMode(SettingsActivity.this, Settings.LAP_MANUAL);
                                Settings.setLapDistance(SettingsActivity.this, 5f);
                                Settings.setLapTime(SettingsActivity.this, 20);
                                Settings.setAlertKm(SettingsActivity.this, 0f);
                                Settings.setAlertMin(SettingsActivity.this, 0);
                                Settings.setBeep(SettingsActivity.this, true);
                                paint();
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
                break;
        }
    }

    private interface Pick { void set(int index); }

    private static int nearest(float[] vals, float v) {
        int best = 0;
        for (int i = 1; i < vals.length; i++) {
            if (Math.abs(vals[i] - v) < Math.abs(vals[best] - v)) best = i;
        }
        return best;
    }

    private static int nearestInt(int[] vals, int v) {
        int best = 0;
        for (int i = 1; i < vals.length; i++) {
            if (Math.abs(vals[i] - v) < Math.abs(vals[best] - v)) best = i;
        }
        return best;
    }

    private void choose(int titleRes, final String[] labels, int checked, final Pick pick) {
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        pick.set(w);
                        d.dismiss();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
