package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;

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
    private static final int R_HR = 11, R_HR_FORGET = 12;
    private static final int R_STRAVA_ID = 21, R_STRAVA_SECRET = 22, R_STRAVA_LINK = 23;
    private static final int R_MAP = 13;
    private static final int R_WEIGHT = 14;
    private static final int R_THEME = 15;
    private static final int R_ATUR_LAYAR = 16;

    private static final int REQ_BLE = 71;

    private LinearLayout groups;
    private LinearLayout lastCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tema.pasangPada(this);   // palet benar SEBELUM layout dibaca
        setContentView(R.layout.activity_settings);
        Ui.fonts(getWindow().getDecorView(), this);
        groups = (LinearLayout) findViewById(R.id.groups);
        paint();
    }

    /**
     * Jaring pengaman palet. attachBaseContext sudah memasang palet yang benar
     * saat layar ini dibangun; onStart mengecek ulang supaya kalau ada yang
     * tidak ikut (layar lama di tumpukan belakang, atau recreate() yang tidak
     * memasang ulang konteks di sebagian perangkat) paletnya tetap mengikuti
     * pilihan pengguna. Lihat {@link Tema#perbaikiKalauPerlu}.
     */
    @Override
    protected void onStart() {
        super.onStart();
        Tema.perbaikiKalauPerlu(this);
        // SEMENTARA - diagnosa tema. Kalimat ini menunjukkan apa yang
        // BENAR-BENAR terpasang, bukan pilihan tersimpan, supaya kalau palet
        // tidak berganti kita tahu tepat di lapisan mana putusnya. Dibuang lagi
        // sesudah penyebabnya ditemukan.
        android.widget.Toast.makeText(this, Tema.diagnostik(this), android.widget.Toast.LENGTH_LONG).show();
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
     * Transisi cermin saat keluar lewat tombol kembali. Lihat Nav.close.
     */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
    }

    // ---------- gambar ulang seluruh daftar ----------

    private void paint() {
        groups.removeAllViews();

        group(getString(R.string.group_recording));
        switchRow(R_AUTOPAUSE, R.string.settings_autopause, getString(R.string.sum_autopause));
        valueRow(R_AP_SPEED, R.string.settings_ap_speed, getString(R.string.sum_ap_speed));
        valueRow(R_AP_DELAY, R.string.settings_ap_delay, getString(R.string.sum_ap_delay));
        switchRow(R_BEEP, R.string.settings_beep, getString(R.string.sum_beep));

        group(getString(R.string.group_lap));
        valueRow(R_LAP_MODE, R.string.settings_lap, getString(R.string.sum_lap));
        if (Settings.lapMode(this) == Settings.LAP_DISTANCE) {
            valueRow(R_LAP_DIST, R.string.settings_lap_dist, getString(R.string.sum_lap_dist));
        } else if (Settings.lapMode(this) == Settings.LAP_TIME) {
            valueRow(R_LAP_TIME, R.string.settings_lap_time, getString(R.string.sum_lap_time));
        }

        group(getString(R.string.group_alert));
        valueRow(R_ALERT_KM, R.string.settings_alert_km, getString(R.string.sum_alert_km));
        valueRow(R_ALERT_MIN, R.string.settings_alert_min, getString(R.string.sum_alert_min));

        group(getString(R.string.group_sensor));
        valueRow(R_HR, R.string.settings_hr, getString(R.string.sum_hr));
        if (!Settings.hrMac(this).isEmpty()) {
            valueRow(R_HR_FORGET, R.string.settings_hr_forget, getString(R.string.sum_hr_forget));
        }
        // Massa badan diletakkan di kelompok Sensor, bukan kelompok baru sendiri:
        // satu-satunya kegunaannya adalah memasukkan angka ke perhitungan kalori,
        // jadi ia lebih dekat ke detak jantung daripada ke lap atau peringatan.
        valueRow(R_WEIGHT, R.string.settings_weight, getString(R.string.sum_weight));

        // Tema diletakkan di kelompok TAMPILAN sendiri, bukan dikelompokkan
        // dengan peta: pilihan ini mengubah seluruh layar sekaligus, sementara
        // setelan peta hanya menyentuh satu bagian di layar Ringkasan.
        group(getString(R.string.group_display));
        switchRow(R_THEME, R.string.settings_theme, keteranganTema());
        // Atur layar diletakkan di kelompok TAMPILAN, tepat di bawah sakelar
        // tema: keduanya mengubah rupa dashboard, bukan isi perekaman.
        valueRow(R_ATUR_LAYAR, R.string.settings_atur_layar, getString(R.string.sum_atur_layar));

        group(getString(R.string.group_map));
        switchRow(R_MAP, R.string.settings_map, getString(R.string.sum_map));

        group(getString(R.string.group_strava));

        // T-11: Client ID, Client Secret, dan Sambungkan pindah ke subbagian
        // "Lanjutan".
        //
        // Sebelumnya ketiganya berdiri sebagai baris biasa di kartu Strava,
        // sejajar dengan "Putuskan sambungan". Akibatnya orang yang belum
        // pernah menghubungkan Strava melihat tiga baris teknis bertumpuk -
        // Client ID, Client Secret, Sambungkan - tanpa tahu urutannya mana.
        // Yang paling sering dipakai (putuskan sambungan) justru paling bawah.
        //
        // Sekarang: status dulu, lalu "LANJUTAN" dengan tiga langkah singkat
        // di atas ketiga baris itu. Tiga langkah, bukan satu paragraf: panduan
        // yang berupa blok teks tidak dibaca, panduan bernomor dibaca.
        TextView sv = new TextView(this);
        sv.setText(StravaStore.connected(this)
                ? getString(R.string.strava_status_on, StravaStore.athlete(this))
                : getString(R.string.strava_status_off));
        sv.setTextColor(getResources().getColor(R.color.label));
        sv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        sv.setPadding(dp(16), dp(10), dp(16), dp(4));
        Ui.fonts(sv, this);
        groups.addView(sv);

        // header subbagian, di dalam kartu yang sama supaya tidak terlihat
        // seperti kelompok terpisah yang setara dengan Strava/Perekaman
        TextView adv = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_group_header, lastCard, false);
        adv.setText(getString(R.string.settings_advanced));
        adv.setPadding(dp(16), dp(6), dp(16), 0);
        Ui.fonts(adv, this);
        lastCard.addView(adv);

        TextView guide = new TextView(this);
        guide.setText(getString(R.string.strava_guide));
        guide.setTextColor(getResources().getColor(R.color.label));
        guide.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        guide.setPadding(dp(16), dp(4), dp(16), dp(10));
        Ui.fonts(guide, this);
        lastCard.addView(guide);

        valueRow(R_STRAVA_ID, R.string.settings_strava_id, getString(R.string.sum_strava_id));
        valueRow(R_STRAVA_SECRET, R.string.settings_strava_secret, getString(R.string.sum_strava_secret));
        valueRow(R_STRAVA_LINK, StravaStore.connected(this)
                        ? R.string.settings_strava_unlink : R.string.settings_strava_link,
                StravaStore.connected(this)
                        ? getString(R.string.sum_strava_unlink) : getString(R.string.sum_strava_link));

        group(getString(R.string.group_other));
        valueRow(R_RESET, R.string.settings_reset, getString(R.string.sum_reset));

        TextView note = new TextView(this);
        note.setText(baroSensorText());
        note.setTextColor(getResources().getColor(R.color.label));
        note.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        note.setPadding(dp(16), dp(12), dp(16), dp(4));
        Ui.fonts(note, this);
        groups.addView(note);
    }

    private String baroSensorText() {
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        boolean ada = sm != null && sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null;
        return ada ? getString(R.string.baro_on) : getString(R.string.baro_off);
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
        // T-11: keterangan cuma menjelaskan FUNGSI baris. Nilai pindah ke TextView
        // sendiri di sisi kanan, sebelum tanda panah.
        //
        // Sebelumnya keduanya digabung jadi satu kalimat ("Interval komuter ·
        // 2 km"). Akibatnya keterangan terbaca sebagai label dan nilai terjepit
        // di tengah kalimat - dan di baris bernilai panjang, keterangan yang
        // panjang menggeser nilai keluar layar.
        ((TextView) v.findViewById(R.id.summary)).setText(summary);
        String val = valueOf(id);
        TextView valueView = (TextView) v.findViewById(R.id.value);
        // Nilai kosong -> TextView-nya GONE, bukan dibiarkan hampa. Kalau
        // dibiarkan, barisnya menyisakan ruang kosong di kanan dan tandanya
        // ("·") yang dulu menempel di keterangan muncul tanpa isi.
        if (val == null || val.isEmpty()) {
            valueView.setVisibility(View.GONE);
        } else {
            valueView.setText(val);
        }
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { onRow(id); }
        });
        Ui.fonts(v, this);
        addRow(v);
    }

    /**
     * SEMENTARA - keterangan baris tema ikut membawa versi terpasang dan
     * apa yang benar-benar terpasang. Supaya kalau tema tidak berganti,
     * keterangan ini menjawab "apakah HP benar-benar menjalankan APK yang
     * aku kirim" dan "lapisan mana yang putus" dalam satu bacaan, tanpa
     * perlu menangkap Toast yang hilang sendiri. Dibuang lagi sesudah
     * penyebabnya ditemukan.
     */
    private String keteranganTema() {
        String versi = "?";
        try {
            versi = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable diabaikan) { /* versi saja */ }
        return getString(R.string.sum_theme) + "  ·  v" + versi + "  ·  " + Tema.diagnostik(this);
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

    // ---------- sensor detak jantung ----------

    private AlertDialog scanDialog;
    private final List<String> foundNames = new ArrayList<String>();
    private final List<String> foundMacs = new ArrayList<String>();

    private void pickSensor() {
        if (!BleHr.hasPermission(this)) {
            requestPermissions(BleHr.neededPermissions(), REQ_BLE);
            return;
        }
        foundNames.clear();
        foundMacs.clear();

        scanDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.hr_scan_title)
                .setMessage(R.string.hr_scan_msg)
                .setNegativeButton(R.string.cancel, null)
                .create();
        scanDialog.show();

        BleHr.scan(this, 8000, new BleHr.ScanListener() {
            @Override public void onFound(String name, String mac, boolean hasHrService) {
                // yang menyiarkan layanan detak jantung ditaruh paling atas
                int at = hasHrService ? 0 : foundMacs.size();
                foundNames.add(at, hasHrService ? name
                        : name + getString(R.string.hr_no_hr_tag));
                foundMacs.add(at, mac);
            }

            @Override public void onDone(String error) {
                if (scanDialog != null && scanDialog.isShowing()) scanDialog.dismiss();
                if (error != null) { Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_LONG).show(); return; }
                if (foundMacs.isEmpty()) { showNoSensor(); return; }
                showFound();
            }
        });
    }

    private void showNoSensor() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.hr_none_title)
                .setMessage(R.string.hr_none_msg)
                .setPositiveButton(R.string.hr_retry, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { pickSensor(); }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void showFound() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.hr_pick_title)
                .setItems(foundNames.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Settings.setHrMac(SettingsActivity.this, foundMacs.get(which));
                        String n = foundNames.get(which).replace(getString(R.string.hr_no_hr_tag), "");
                        Settings.setHrName(SettingsActivity.this, n);
                        paint();
                        Toast.makeText(SettingsActivity.this, R.string.hr_saved,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req != REQ_BLE) return;
        boolean all = res.length > 0;
        for (int r : res) if (r != android.content.pm.PackageManager.PERMISSION_GRANTED) all = false;
        if (all) pickSensor();
        else Toast.makeText(this, R.string.hr_need_perm, Toast.LENGTH_LONG).show();
    }

    // ---------- nilai ----------

    private boolean isOn(int id) {
        switch (id) {
            case R_AUTOPAUSE: return Settings.autoPause(this);
            case R_THEME:     return Settings.theme(this);
            case R_MAP:       return Settings.map(this);
            case R_BEEP:
            default:          return Settings.beep(this);
        }
    }

    private void setOn(int id, boolean v) {
        switch (id) {
            case R_AUTOPAUSE: Settings.setAutoPause(this, v); break;
            case R_MAP:       Settings.setMap(this, v); break;
            case R_THEME:
                // Tema.terapkan: tandai palet baru seketika + simpan. Penanda
                // memori diperbarui lebih dulu, jadi layar hasil recreate()
                // di bawah ini pasti memakai palet baru walau tulisan ke disk
                // belum sempat terbaca.
                Tema.terapkan(this, v);
                // Palet dipilih saat aktivitas DIBANGKAN (attachBaseContext),
                // jadi aktivitas yang sedang berjalan tetap memakai palet lama.
                // recreate() membangun ulang aktivitas ini dengan palet baru -
                // tanpa itu sakelarnya akan terasa "tersimpan tapi tidak
                // bereaksi".
                //
                // Sengaja hanya aktivitas ini: layar lain ikut sendiri lewat
                // Tema.perbaikiKalauPerlu di onStart-nya, jadi tidak perlu
                // daftar aktivitas yang dirawat dari sini.
                recreate();
                break;
            case R_BEEP:      Settings.setBeep(this, v); break;
            default: break;
        }
    }

    private String valueOf(int id) {
        switch (id) {
            case R_AUTOPAUSE: return onOff(Settings.autoPause(this));
            case R_AP_SPEED:  return getString(R.string.opt_speed,
                    Math.round(Settings.autoPauseSpeed(this)));
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
            case R_THEME:     return onOff(Settings.theme(this));
            case R_WEIGHT:    return getString(R.string.opt_kg, Math.round(Settings.weight(this)));
            case R_MAP:       return onOff(Settings.map(this));
            case R_HR: {
                String n = Settings.hrName(this);
                return n.isEmpty() ? getString(R.string.hr_none) : n;
            }
            case R_HR_FORGET: return "";
            case R_STRAVA_ID:
                String cid = StravaStore.clientId(this);
                return cid.isEmpty() ? "-" : cid;
            case R_STRAVA_SECRET:
                // rahasia tidak pernah ditampilkan utuh; cukup tanda sudah terisi
                return StravaStore.clientSecret(this).isEmpty() ? "-"
                        : getString(R.string.strava_secret_set);
            case R_STRAVA_LINK: return "";
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
                        new String[]{getString(R.string.opt_speed, 2), getString(R.string.opt_speed, 3),
                                getString(R.string.opt_speed, 5), getString(R.string.opt_speed, 8),
                                getString(R.string.opt_speed, 10)},
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
                        new String[]{getString(R.string.opt_sec, 3), getString(R.string.opt_sec, 5),
                                getString(R.string.opt_sec, 10), getString(R.string.opt_sec, 15),
                                getString(R.string.opt_sec, 30)},
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
                        new String[]{getString(R.string.opt_km, 1), getString(R.string.opt_km, 2),
                                getString(R.string.opt_km, 5), getString(R.string.opt_km, 10),
                                getString(R.string.opt_km, 20)},
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
                        new String[]{getString(R.string.opt_min, 5), getString(R.string.opt_min, 10),
                                getString(R.string.opt_min, 20), getString(R.string.opt_min, 30),
                                getString(R.string.opt_min, 60)},
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
                        new String[]{getString(R.string.settings_off), getString(R.string.opt_km, 5),
                                getString(R.string.opt_km, 10), getString(R.string.opt_km, 20),
                                getString(R.string.opt_km, 50)},
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
                        new String[]{getString(R.string.settings_off), getString(R.string.opt_min, 15),
                                getString(R.string.opt_min, 30), getString(R.string.opt_min, 45),
                                getString(R.string.opt_min, 60)},
                        nearestInt(vals, Settings.alertMin(this)),
                        new Pick() {
                            @Override public void set(int i) {
                                Settings.setAlertMin(SettingsActivity.this, vals[i]);
                                paint();
                            }
                        });
                break;
            }
            case R_HR:
                pickSensor();
                break;
            case R_WEIGHT: {
                // Langkah 1 kg dari 40 sampai 120: cukup rapat untuk orang normal,
                // cukup sedikit supaya dialog tidak perlu digulir di layar 320dp.
                final int[] vals = new int[81];
                for (int i = 0; i < vals.length; i++) vals[i] = 40 + i;
                String[] labels = new String[vals.length];
                for (int i = 0; i < vals.length; i++) {
                    labels[i] = getString(R.string.opt_kg, vals[i]);
                }
                int now = Math.round(Settings.weight(this));
                int sel = 0;
                for (int i = 0; i < vals.length; i++) {
                    if (vals[i] == now) { sel = i; break; }
                    if (vals[i] > now) { sel = Math.max(0, i - 1); break; }
                }
                choose(R.string.settings_weight, labels, sel, new Pick() {
                    @Override public void set(int i) {
                        Settings.setWeight(SettingsActivity.this, vals[i]);
                        paint();
                    }
                });
                break;
            }
            case R_HR_FORGET:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_hr_forget)
                        .setMessage(R.string.hr_forget_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.hr_forget_btn,
                                new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                Settings.setHrMac(SettingsActivity.this, "");
                                Settings.setHrName(SettingsActivity.this, "");
                                paint();
                            }
                        })
                        .show();
                break;
            case R_STRAVA_ID:
                askText(R.string.settings_strava_id, StravaStore.clientId(this),
                        R.string.strava_id_hint, new Text() {
                    @Override public void set(String v) {
                        StravaStore.clientId(SettingsActivity.this, v);
                        paint();
                    }
                });
                break;
            case R_STRAVA_SECRET:
                askText(R.string.settings_strava_secret, StravaStore.clientSecret(this),
                        R.string.strava_secret_hint, new Text() {
                    @Override public void set(String v) {
                        StravaStore.clientSecret(SettingsActivity.this, v);
                        paint();
                    }
                });
                break;
            case R_STRAVA_LINK:
                if (StravaStore.connected(this)) unlinkStrava();
                else linkStrava();
                break;
            case R_ATUR_LAYAR:
                startActivity(new Intent(this, AturLayarActivity.class));
                break;
            case R_RESET:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_reset)
                        .setMessage(R.string.reset_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
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
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                break;
        }
    }

    private interface Pick { void set(int index); }

    private interface Text { void set(String value); }

    // ---------- Strava ----------

    /**
     * Minta izin ke Strava.
     *
     * Seluruh alur berjalan di thread latar: membuka peramban, menunggu pengalihan kembali
     * ke 127.0.0.1, lalu menukar kode dengan token. Kalau ditahan di thread UI, aplikasi
     * akan membeku selama pengguna login - dan ANR lebih buruk daripada galat.
     */
    private void linkStrava() {
        if (StravaStore.clientId(this).isEmpty()) {
            Toast.makeText(this, R.string.strava_need_id, Toast.LENGTH_LONG).show();
            return;
        }
        if (StravaStore.clientSecret(this).isEmpty()) {
            Toast.makeText(this, R.string.strava_need_secret, Toast.LENGTH_LONG).show();
            return;
        }

        final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setMessage(getString(R.string.strava_uploading));
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override public void run() {
                final String err = Strava.runAuth(SettingsActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        pd.dismiss();
                        paint();
                        String msg = err == null
                                ? getString(R.string.strava_authorized) : err;
                        Toast.makeText(SettingsActivity.this, msg,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void unlinkStrava() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_strava_unlink)
                .setMessage(R.string.sum_strava_unlink)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.hr_forget_btn, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        StravaStore.clearTokens(SettingsActivity.this);
                        paint();
                        Toast.makeText(SettingsActivity.this, R.string.strava_unlinked,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    /** Dialog isian teks satu baris, dipakai untuk Client ID dan Client Secret. */
    private void askText(int titleRes, String current, int hintRes, final Text out) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (current != null && !current.isEmpty()) et.setText(current);
        et.setHint(hintRes);
        Ui.fonts(et, this);
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(et)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        out.set(et.getText() == null ? "" : et.getText().toString().trim());
                    }
                })
                .show();
    }

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
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
