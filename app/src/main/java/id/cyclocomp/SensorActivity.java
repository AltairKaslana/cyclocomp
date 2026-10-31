package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar Sensor & roda (rancangan UI/UX 5.4).
 *
 * Satu tempat untuk kecepatan/kadensi (CSC), detak jantung, dan ukuran roda.
 * Pengaturan hanya menyimpan pilihan; sambungan BLE yang sebenarnya hidup di
 * RideService selama gowes. Karena itu layar ini membaca status langsung dari
 * service yang sedang berjalan, dan menampilkan "Tersimpan" saat belum gowes.
 *
 * Kartu dibangun di kode karena isinya bergantung pada status sambungan yang
 * berubah tiap detik; layout statis akan memaksa menyalin nilai ke banyak
 * TextView dengan aturan yang mudah basi.
 */
public class SensorActivity extends Activity {

    private static final int REQ_BLE = 91;
    private static final int SCAN_CSC = 1, SCAN_HR = 2;

    private LinearLayout cards;
    private TextView cscStatusView, cscBatteryView, cscLiveView;
    private TextView hrStatusView, hrLiveView;
    private View cscDot, hrDot;

    private RideService service;
    private boolean bound;
    private int pendingScan;

    private AlertDialog scanDialog;
    private final List<String> foundNames = new ArrayList<String>();
    private final List<String> foundMacs = new ArrayList<String>();

    private final Handler ticker = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStatus();
            ticker.postDelayed(this, 1000);
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            service = ((RideService.LocalBinder) b).getService();
            updateStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            updateStatus();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Tema.bungkus(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tema.pasangPada(this);
        setContentView(R.layout.activity_sensor);

        cards = (LinearLayout) findViewById(R.id.cards);
        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        paint();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Tema.perbaikiKalauPerlu(this);
        if (!bound) {
            bindService(new Intent(this, RideService.class), conn, Context.BIND_AUTO_CREATE);
            bound = true;
        }
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, 1000);
    }

    @Override
    protected void onStop() {
        ticker.removeCallbacks(tick);
        if (bound) {
            try { unbindService(conn); } catch (Throwable ignored) { }
            bound = false;
            service = null;
        }
        super.onStop();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
    }

    // ---------- menggambar ----------

    private void paint() {
        cards.removeAllViews();
        cards.addView(kartuCsc());
        cards.addView(kartuHr());
        cards.addView(kartuDaya());
        cards.addView(kartuRoda());
        Ui.fonts(cards, this);
        Ui.buttonFonts(cards, this);
        updateStatus();
    }

    private LinearLayout kartuCsc() {
        LinearLayout c = kartu();
        c.addView(judul(R.string.sensor_csc));

        cscDot = titik();
        cscStatusView = teksStatus();
        c.addView(barisStatus(cscDot, cscStatusView));

        cscLiveView = teksIsi("");
        cscLiveView.setVisibility(View.GONE);
        c.addView(cscLiveView);

        cscBatteryView = teksIsi("");
        cscBatteryView.setVisibility(View.GONE);
        c.addView(cscBatteryView);

        LinearLayout aksi = barisTombol();
        aksi.addView(tombol(R.string.sensor_connect, true, new View.OnClickListener() {
            @Override public void onClick(View v) { mintaIzin(SCAN_CSC); }
        }), bobot(1f));
        if (!Settings.cscMac(this).isEmpty()) {
            aksi.addView(tombol(R.string.sensor_disconnect, false, new View.OnClickListener() {
                @Override public void onClick(View v) { lupakanCsc(); }
            }), bobot(1f));
        }
        c.addView(aksi);

        c.addView(catatan(R.string.sensor_csc_note));
        return c;
    }

    private LinearLayout kartuHr() {
        LinearLayout c = kartu();
        c.addView(judul(R.string.sensor_hr));

        hrDot = titik();
        hrStatusView = teksStatus();
        c.addView(barisStatus(hrDot, hrStatusView));

        hrLiveView = teksIsi("");
        hrLiveView.setVisibility(View.GONE);
        c.addView(hrLiveView);

        LinearLayout aksi = barisTombol();
        aksi.addView(tombol(R.string.sensor_connect, true, new View.OnClickListener() {
            @Override public void onClick(View v) { mintaIzin(SCAN_HR); }
        }), bobot(1f));
        if (!Settings.hrMac(this).isEmpty()) {
            aksi.addView(tombol(R.string.sensor_disconnect, false, new View.OnClickListener() {
                @Override public void onClick(View v) { lupakanHr(); }
            }), bobot(1f));
        }
        c.addView(aksi);

        c.addView(catatan(R.string.sensor_hr_note));
        return c;
    }

    private LinearLayout kartuDaya() {
        LinearLayout c = kartu();
        c.addView(judul(R.string.sensor_power));
        c.addView(teksIsi(R.string.sensor_power_note));
        return c;
    }

    private LinearLayout kartuRoda() {
        LinearLayout c = kartu();
        c.addView(judul(R.string.sensor_wheel));

        final int mm = Settings.wheelMm(this);
        String nama = Roda.label(mm);
        c.addView(teksIsi(nama == null
                ? getString(R.string.wheel_current_manual, mm)
                : getString(R.string.wheel_current, nama, mm)));

        // Dua baris dua chip, bukan satu baris empat: di layar 320dp empat chip
        // "700×23C" tidak muat dan chip terakhir terpotong. Dua per baris
        // memberi setiap chip setengah lebar kartu dan target sentuh 48dp.
        LinearLayout kisi = new LinearLayout(this);
        kisi.setOrientation(LinearLayout.VERTICAL);
        kisi.setPadding(0, dp(10), 0, 0);
        for (int baris = 0; baris < 2; baris++) {
            LinearLayout isi = new LinearLayout(this);
            isi.setOrientation(LinearLayout.HORIZONTAL);
            if (baris > 0) isi.setPadding(0, dp(6), 0, 0);
            for (int i = baris * 2; i < baris * 2 + 2 && i < Roda.PRESET_MM.length; i++) {
                isi.addView(chipRoda(Roda.PRESET_LABEL[i],
                        Roda.PRESET_MM[i] == mm, Roda.PRESET_MM[i]));
            }
            kisi.addView(isi);
        }
        c.addView(kisi);

        Button manual = tombol(R.string.wheel_manual, false, new View.OnClickListener() {
            @Override public void onClick(View v) { isiRodaManual(); }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        mlp.topMargin = dp(10);
        manual.setLayoutParams(mlp);
        c.addView(manual);

        c.addView(catatan(R.string.wheel_note));
        return c;
    }

    private TextView chipRoda(String label, final boolean aktif, final int mm) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        chip.setTextColor(getResources().getColor(aktif ? R.color.on_accent : R.color.value));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(22));
        bg.setColor(getResources().getColor(aktif ? R.color.accent : R.color.card2));
        bg.setStroke(dp(1), getResources().getColor(aktif ? R.color.accent : R.color.stroke_ctrl));
        chip.setBackground(bg);
        chip.setMinHeight(dp(48));
        chip.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        chip.setLayoutParams(lp);
        chip.setContentDescription(label);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (aktif) return;
                Settings.setWheelMm(SensorActivity.this, mm);
                if (service != null) service.setWheelMm(mm);
                setResult(RESULT_OK);
                paint();
                toast(R.string.wheel_saved);
            }
        });
        return chip;
    }

    // ---------- status ----------

    private void updateStatus() {
        long now = SystemClock.elapsedRealtime();

        // ----- kecepatan & kadensi -----
        boolean cscAda = !Settings.cscMac(this).isEmpty();
        boolean cscNyala = service != null && service.isCscConnected();
        String cscPesan = service == null ? null : service.getCscStatus();
        if (cscNyala) {
            cscStatusView.setText(R.string.sensor_status_connected);
            cscStatusView.setTextColor(getResources().getColor(R.color.rec));
            cscDot.setBackgroundResource(R.drawable.rec_dot);
        } else if (cscPesan != null && cscPesan.length() > 0) {
            cscStatusView.setText(cscPesan);
            cscStatusView.setTextColor(getResources().getColor(R.color.warn));
            cscDot.setBackgroundResource(R.drawable.dot_warn);
        } else if (cscAda) {
            String nama = Settings.cscName(this);
            cscStatusView.setText(getString(R.string.sensor_status_saved,
                    nama.isEmpty() ? Settings.cscMac(this) : nama));
            cscStatusView.setTextColor(getResources().getColor(R.color.label));
            cscDot.setBackgroundResource(R.drawable.dot_off);
        } else {
            cscStatusView.setText(R.string.sensor_status_off);
            cscStatusView.setTextColor(getResources().getColor(R.color.label));
            cscDot.setBackgroundResource(R.drawable.dot_off);
        }
        cscDot.setContentDescription(cscStatusView.getText());

        int batt = service == null ? -1 : service.getCscBattery();
        if (batt >= 0) {
            cscBatteryView.setText(getString(R.string.sensor_battery, batt));
            cscBatteryView.setVisibility(View.VISIBLE);
        } else {
            cscBatteryView.setVisibility(View.GONE);
        }

        if (cscNyala) {
            RideStats s = service.getStats();
            int rpm = s.cadenceNow(now);
            cscLiveView.setText(getString(R.string.sensor_csc_live,
                    Fmt.speed(s.speedMs(now)), rpm < 0 ? "-" : String.valueOf(rpm)));
            cscLiveView.setVisibility(View.VISIBLE);
        } else {
            cscLiveView.setVisibility(View.GONE);
        }

        // ----- detak jantung -----
        boolean hrAda = !Settings.hrMac(this).isEmpty();
        boolean hrNyala = service != null && service.isHrConnected();
        String hrPesan = service == null ? null : service.getBleStatus();
        if (hrNyala) {
            hrStatusView.setText(R.string.sensor_status_connected);
            hrStatusView.setTextColor(getResources().getColor(R.color.rec));
            hrDot.setBackgroundResource(R.drawable.rec_dot);
        } else if (hrPesan != null && hrPesan.length() > 0) {
            hrStatusView.setText(hrPesan);
            hrStatusView.setTextColor(getResources().getColor(R.color.warn));
            hrDot.setBackgroundResource(R.drawable.dot_warn);
        } else if (hrAda) {
            String nama = Settings.hrName(this);
            hrStatusView.setText(getString(R.string.sensor_status_saved,
                    nama.isEmpty() ? Settings.hrMac(this) : nama));
            hrStatusView.setTextColor(getResources().getColor(R.color.label));
            hrDot.setBackgroundResource(R.drawable.dot_off);
        } else {
            hrStatusView.setText(R.string.sensor_status_off);
            hrStatusView.setTextColor(getResources().getColor(R.color.label));
            hrDot.setBackgroundResource(R.drawable.dot_off);
        }
        hrDot.setContentDescription(hrStatusView.getText());

        if (service != null && service.hasHr()) {
            hrLiveView.setText(getString(R.string.sensor_hr_live, service.getStats().hr));
            hrLiveView.setVisibility(View.VISIBLE);
        } else {
            hrLiveView.setVisibility(View.GONE);
        }
    }

    // ---------- pemilihan sensor ----------

    private void mintaIzin(int jenis) {
        if (!BleHr.hasPermission(this)) {
            pendingScan = jenis;
            requestPermissions(BleHr.neededPermissions(), REQ_BLE);
            return;
        }
        mulaiScan(jenis);
    }

    private void mulaiScan(int jenis) {
        // Jenis harus dicatat SEBELUM pemindaian dimulai: kalau izin sudah
        // diberikan, onRequestPermissionsResult tidak pernah jalan dan
        // pemilihan perangkat akan memakai jenis yang salah (sensor CSC
        // tersimpan sebagai sensor detak jantung).
        scanTerakhir = jenis;
        if (jenis == SCAN_CSC) scanCsc();
        else scanHr();
    }

    private void scanCsc() {
        foundNames.clear();
        foundMacs.clear();
        scanDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.sensor_scan_title)
                .setMessage(R.string.sensor_scan_msg)
                .setNegativeButton(R.string.cancel, null)
                .create();
        scanDialog.show();

        BleCsc.scan(this, 8000, new BleCsc.ScanListener() {
            @Override public void onFound(String name, String mac, boolean hasCscService) {
                int at = hasCscService ? 0 : foundMacs.size();
                foundNames.add(at, hasCscService ? name
                        : name + getString(R.string.sensor_no_csc_tag));
                foundMacs.add(at, mac);
            }

            @Override public void onDone(String error) {
                if (scanDialog != null && scanDialog.isShowing()) scanDialog.dismiss();
                if (error != null) { toast(error); return; }
                if (foundMacs.isEmpty()) { tidakAdaSensor(); return; }
                pilihDariDaftar();
            }
        });
    }

    private void scanHr() {
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
                int at = hasHrService ? 0 : foundMacs.size();
                foundNames.add(at, hasHrService ? name
                        : name + getString(R.string.hr_no_hr_tag));
                foundMacs.add(at, mac);
            }

            @Override public void onDone(String error) {
                if (scanDialog != null && scanDialog.isShowing()) scanDialog.dismiss();
                if (error != null) { toast(error); return; }
                if (foundMacs.isEmpty()) { tidakAdaSensor(); return; }
                pilihDariDaftar();
            }
        });
    }

    private void tidakAdaSensor() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sensor_none_title)
                .setMessage(R.string.sensor_none_msg)
                .setPositiveButton(R.string.hr_retry, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        mintaIzin(pendingScanJenis());
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    /** Jenis scan terakhir dipakai tombol "Coba lagi" di dialog kosong. */
    private int scanTerakhir;

    private int pendingScanJenis() {
        return scanTerakhir;
    }

    private void pilihDariDaftar() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sensor_pick_title)
                .setItems(foundNames.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String nama = foundNames.get(which);
                        String mac = foundMacs.get(which);
                        if (scanTerakhir == SCAN_CSC) {
                            Settings.setCscMac(SensorActivity.this, mac);
                            Settings.setCscName(SensorActivity.this,
                                    bersihkanTag(nama, R.string.sensor_no_csc_tag));
                        } else {
                            Settings.setHrMac(SensorActivity.this, mac);
                            Settings.setHrName(SensorActivity.this,
                                    bersihkanTag(nama, R.string.hr_no_hr_tag));
                        }
                        setResult(RESULT_OK);
                        paint();
                        toast(R.string.sensor_saved);
                    }
                })
                .show();
    }

    private String bersihkanTag(String nama, int tagRes) {
        return nama.replace(getString(tagRes), "");
    }

    private void lupakanCsc() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sensor_disconnect)
                .setMessage(R.string.sensor_forget_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sensor_forget_btn, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Settings.setCscMac(SensorActivity.this, "");
                        Settings.setCscName(SensorActivity.this, "");
                        setResult(RESULT_OK);
                        paint();
                    }
                })
                .show();
    }

    private void lupakanHr() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sensor_disconnect)
                .setMessage(R.string.hr_forget_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sensor_forget_btn, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Settings.setHrMac(SensorActivity.this, "");
                        Settings.setHrName(SensorActivity.this, "");
                        setResult(RESULT_OK);
                        paint();
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req != REQ_BLE) return;
        boolean semua = res.length > 0;
        for (int r : res) if (r != android.content.pm.PackageManager.PERMISSION_GRANTED) semua = false;
        if (semua && pendingScan != 0) {
            mulaiScan(pendingScan);
        } else {
            toast(R.string.hr_need_perm);
        }
        pendingScan = 0;
    }

    // ---------- roda manual ----------

    private void isiRodaManual() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(Settings.wheelMm(this)));
        et.setHint(R.string.wheel_manual_hint);
        Ui.fonts(et, this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wheel_manual)
                .setMessage(R.string.wheel_manual_hint)
                .setView(et)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int mm;
                        try {
                            mm = Integer.parseInt(et.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            toast(R.string.wheel_invalid);
                            return;
                        }
                        if (mm < Roda.MIN_MM || mm > Roda.MAX_MM) {
                            toast(R.string.wheel_invalid);
                            return;
                        }
                        Settings.setWheelMm(SensorActivity.this, mm);
                        if (service != null) service.setWheelMm(mm);
                        setResult(RESULT_OK);
                        paint();
                        toast(R.string.wheel_saved);
                    }
                })
                .show();
    }

    // ---------- komponen kecil ----------

    private LinearLayout kartu() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView judul(int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_label));
        t.setTextColor(getResources().getColor(R.color.label));
        t.setLetterSpacing(0.2f);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout barisStatus(View dot, TextView teks) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(8);
        dot.setLayoutParams(dlp);
        row.addView(dot);
        row.addView(teks);
        return row;
    }

    private View titik() {
        View v = new View(this);
        v.setBackgroundResource(R.drawable.dot_off);
        return v;
    }

    private TextView teksStatus() {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body_s));
        t.setTextColor(getResources().getColor(R.color.label));
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView teksIsi(int res) {
        TextView t = teksIsi("");
        t.setText(res);
        return t;
    }

    private TextView teksIsi(String isi) {
        TextView t = new TextView(this);
        t.setText(isi);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body_s));
        t.setTextColor(getResources().getColor(R.color.label));
        t.setPadding(0, dp(6), 0, 0);
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView catatan(int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        t.setTextColor(getResources().getColor(R.color.label));
        t.setPadding(0, dp(8), 0, 0);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout barisTombol() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        return row;
    }

    private Button tombol(int res, boolean utama, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(res);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body));
        b.setTextColor(getResources().getColor(utama ? R.color.on_accent : R.color.value));
        b.setBackgroundResource(utama ? R.drawable.bg_btn_primary : R.drawable.bg_btn_secondary);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams bobot(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), w);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        return lp;
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
