package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar pengaturan sederhana berbasis ListView (tanpa dependency).
 *
 * Setiap baris membuka dialog pilihan atau tombol aktif/mati. Nilai langsung
 * disimpan ke SharedPreferences lewat kelas Settings.
 */
public class SettingsActivity extends Activity {
    private static final int ROW_AUTOPAUSE = 0;
    private static final int ROW_AP_SPEED = 1;
    private static final int ROW_AP_DELAY = 2;
    private static final int ROW_LAP_MODE = 3;
    private static final int ROW_LAP_DIST = 4;
    private static final int ROW_LAP_TIME = 5;
    private static final int ROW_ALERT_KM = 6;
    private static final int ROW_ALERT_MIN = 7;
    private static final int ROW_BEEP = 8;
    private static final int ROW_RESET = 9;

    private ListView list;
    private final List<Row> rows = new ArrayList<Row>();

    /** Satu baris: judul + ringkasan nilai sekarang. */
    private static class Row {
        final int id;
        final int title;
        Row(int id, int title) { this.id = id; this.title = title; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings_title);

        rows.add(new Row(ROW_AUTOPAUSE, R.string.settings_autopause));
        rows.add(new Row(ROW_AP_SPEED, R.string.settings_ap_speed));
        rows.add(new Row(ROW_AP_DELAY, R.string.settings_ap_delay));
        rows.add(new Row(ROW_LAP_MODE, R.string.settings_lap));
        rows.add(new Row(ROW_LAP_DIST, R.string.settings_lap_dist));
        rows.add(new Row(ROW_LAP_TIME, R.string.settings_lap_time));
        rows.add(new Row(ROW_ALERT_KM, R.string.settings_alert_km));
        rows.add(new Row(ROW_ALERT_MIN, R.string.settings_alert_min));
        rows.add(new Row(ROW_BEEP, R.string.settings_beep));
        rows.add(new Row(ROW_RESET, R.string.settings_reset));

        list = (ListView) findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.empty));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                onRow(rows.get(pos).id);
            }
        });
        paint();
    }

    private void paint() {
        List<String> labels = new ArrayList<String>();
        for (Row r : rows) labels.add(getString(r.title) + "\n" + valueOf(r.id));
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, labels));
    }

    private String valueOf(int id) {
        switch (id) {
            case ROW_AUTOPAUSE:
                return onOff(Settings.autoPause(this));
            case ROW_AP_SPEED:
                return String.format("%.0f km/j", Settings.autoPauseSpeed(this));
            case ROW_AP_DELAY:
                return getString(R.string.settings_seconds, Settings.autoPauseDelay(this));
            case ROW_LAP_MODE:
                int m = Settings.lapMode(this);
                return m == Settings.LAP_DISTANCE ? getString(R.string.settings_by_distance)
                        : m == Settings.LAP_TIME ? getString(R.string.settings_by_time)
                        : getString(R.string.settings_manual);
            case ROW_LAP_DIST:
                return getString(R.string.settings_km, Settings.lapDistance(this));
            case ROW_LAP_TIME:
                return getString(R.string.settings_minutes, Settings.lapTime(this));
            case ROW_ALERT_KM:
                float km = Settings.alertKm(this);
                return km <= 0 ? getString(R.string.settings_off)
                        : getString(R.string.settings_km, km);
            case ROW_ALERT_MIN:
                int mn = Settings.alertMin(this);
                return mn <= 0 ? getString(R.string.settings_off)
                        : getString(R.string.settings_minutes, mn);
            case ROW_BEEP:
                return onOff(Settings.beep(this));
            default:
                return "";
        }
    }

    private String onOff(boolean v) { return getString(v ? R.string.settings_on : R.string.settings_off); }

    private void onRow(int id) {
        switch (id) {
            case ROW_AUTOPAUSE:
                Settings.setAutoPause(this, !Settings.autoPause(this));
                paint();
                break;
            case ROW_AP_SPEED:
                choose(getString(R.string.settings_ap_speed), new String[]{"2", "3", "5", "8", "10"},
                        indexOf(new String[]{"2", "3", "5", "8", "10"},
                                String.valueOf((int) Settings.autoPauseSpeed(this))), 2,
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAutoPauseSpeed(SettingsActivity.this,
                                        new float[]{2, 3, 5, 8, 10}[i]);
                                paint();
                            }
                        });
                break;
            case ROW_AP_DELAY:
                choose(getString(R.string.settings_ap_delay),
                        new String[]{"3", "5", "10", "15", "30"},
                        indexOf(new String[]{"3", "5", "10", "15", "30"},
                                String.valueOf(Settings.autoPauseDelay(this))), 1,
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAutoPauseDelay(SettingsActivity.this,
                                        new int[]{3, 5, 10, 15, 30}[i]);
                                paint();
                            }
                        });
                break;
            case ROW_LAP_MODE:
                choose(getString(R.string.settings_lap),
                        new String[]{getString(R.string.settings_manual),
                                getString(R.string.settings_by_distance),
                                getString(R.string.settings_by_time)},
                        Settings.lapMode(this), 0,
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setLapMode(SettingsActivity.this, i);
                                paint();
                            }
                        });
                break;
            case ROW_LAP_DIST:
                final float[] dists = {1, 2, 5, 10, 20};
                choose(getString(R.string.settings_lap_dist),
                        new String[]{"1 km", "2 km", "5 km", "10 km", "20 km"},
                        indexOf(new String[]{"1.0", "2.0", "5.0", "10.0", "20.0"},
                                String.valueOf(Settings.lapDistance(this))), 2,
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setLapDistance(SettingsActivity.this, dists[i]);
                                paint();
                            }
                        });
                break;
            case ROW_LAP_TIME:
                final int[] times = {5, 10, 20, 30, 60};
                choose(getString(R.string.settings_lap_time),
                        new String[]{"5 menit", "10 menit", "20 menit", "30 menit", "60 menit"},
                        indexOf(new String[]{"5", "10", "20", "30", "60"},
                                String.valueOf(Settings.lapTime(this))), 2,
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setLapTime(SettingsActivity.this, times[i]);
                                paint();
                            }
                        });
                break;
            case ROW_ALERT_KM:
                final float[] kms = {0, 5, 10, 20, 50};
                choose(getString(R.string.settings_alert_km),
                        new String[]{getString(R.string.settings_off), "5 km", "10 km", "20 km", "50 km"},
                        indexOf(new String[]{"0.0", "5.0", "10.0", "20.0", "50.0"},
                                String.valueOf(Settings.alertKm(this))), 0,
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAlertKm(SettingsActivity.this, kms[i]);
                                paint();
                            }
                        });
                break;
            case ROW_ALERT_MIN:
                final int[] mins = {0, 15, 30, 45, 60};
                choose(getString(R.string.settings_alert_min),
                        new String[]{getString(R.string.settings_off), "15 menit", "30 menit",
                                "45 menit", "60 menit"},
                        indexOf(new String[]{"0", "15", "30", "45", "60"},
                                String.valueOf(Settings.alertMin(this))), 0,
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAlertMin(SettingsActivity.this, mins[i]);
                                paint();
                            }
                        });
                break;
            case ROW_BEEP:
                Settings.setBeep(this, !Settings.beep(this));
                paint();
                break;
            case ROW_RESET:
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

    private static int indexOf(String[] options, String value) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(value)) return i;
        return 0;
    }

    private void choose(String title, final String[] labels, int checked, final int fallback,
                        final Pick pick) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked < 0 ? fallback : checked,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                pick.set(w);
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Batal", null)
                .show();
    }
}
