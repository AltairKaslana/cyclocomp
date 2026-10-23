package id.cyclocomp;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements RideService.Listener {
    private static final int REQ_PERM = 1;

    private TextView tvSpeed, tvDist, tvTime, tvAvg, tvMax, tvElev, tvAlt, tvStatus;
    private Button btnStartPause, btnStop;
    private RideService service;
    private boolean bound;

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
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvDist = (TextView) findViewById(R.id.tvDist);
        tvTime = (TextView) findViewById(R.id.tvTime);
        tvAvg = (TextView) findViewById(R.id.tvAvg);
        tvMax = (TextView) findViewById(R.id.tvMax);
        tvElev = (TextView) findViewById(R.id.tvElev);
        tvAlt = (TextView) findViewById(R.id.tvAlt);
        btnStartPause = (Button) findViewById(R.id.btnStartPause);
        btnStop = (Button) findViewById(R.id.btnStop);

        btnStartPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onStartPause(); }
        });
        // Berhenti harus ditahan lama supaya tidak kepencet di jalan bergelombang
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this, R.string.hint_stop, Toast.LENGTH_SHORT).show();
            }
        });
        btnStop.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                send(RideService.ACTION_STOP);
                return true;
            }
        });
        findViewById(R.id.btnHistory).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, RideService.class), conn, Context.BIND_AUTO_CREATE);
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

    // ---------- izin ----------

    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionsCompat() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> p = new ArrayList<String>();
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
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

    private void refresh() {
        RideService.State st = service == null ? RideService.State.IDLE : service.getState();
        RideStats s = service == null ? new RideStats() : service.getStats();
        long elapsed = service == null ? 0 : service.getElapsedMs();

        tvSpeed.setText(Fmt.speed(s.currentSpeedMs));
        tvDist.setText(Fmt.km(s.distanceM));
        tvTime.setText(Fmt.duration(s.movingMs));
        tvAvg.setText(Fmt.speed(s.avgSpeedMs()));
        tvMax.setText(Fmt.speed(s.maxSpeedMs));
        tvElev.setText(Fmt.meters(s.elevGainM));
        tvAlt.setText(Double.isNaN(s.altitudeM) ? "--" : Fmt.meters(s.altitudeM));

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
                if (age < 0 || age > 5000) tvStatus.setText(R.string.status_search);
                else tvStatus.setText(getString(R.string.status_ok, Math.round(service.getLastAccuracy())));
                break;
        }
        btnStop.setEnabled(st != RideService.State.IDLE);

        if (st == RideService.State.IDLE) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        // 'elapsed' (total termasuk berhenti) belum ditampilkan; tambahkan field kalau perlu
    }
}
