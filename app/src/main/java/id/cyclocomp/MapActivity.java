package id.cyclocomp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageButton;

import java.util.List;

/**
 * Peta layar penuh (fitur navigasi): posisi terkini + rute yang sudah dilalui,
 * dengan geser &amp; pinch-zoom. Mode ikut memusat posisi terbaru; begitu pengguna
 * menggeser sendiri, ikut berhenti sampai tombol "ke posisi ku" ditekan.
 *
 * Sama seperti MainActivity: layar ini HANYA menampilkan, semua state ada di
 * RideService. Bind di onStart / lepas di onStop supaya listener diserahkan dengan
 * bersih ke MainActivity saat layar ini ditutup (glance tidak ikut macet).
 */
public class MapActivity extends Activity implements RideService.Listener {
    private static final long MAP_REFRESH_MS = 3000;

    private TileMapView map;
    private RideService service;
    private boolean bound;
    private long lastMapMs;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder b) {
            service = ((RideService.LocalBinder) b).getService();
            service.setListener(MapActivity.this);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    /**
     * Paksa palet gelap/terang SEBELUM resource dibaca.
     *
     * Tanpa ini layar peta memakai palet bawaan perangkat: pengguna yang memilih
     * tema siang tetap melihat peta gelap - satu-satunya layar yang tidak ikut.
     * Lihat {@link Tema} kenapa harus di attachBaseContext, bukan onCreate.
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Tema.bungkus(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tema.pasangPada(this);   // palet benar SEBELUM layout dibaca
        setContentView(R.layout.activity_map);

        // font dibundel: samakan tipografi dengan layar lain
        Ui.fonts(getWindow().getDecorView(), this);
        Ui.buttonFonts(getWindow().getDecorView(), this);

        map = (TileMapView) findViewById(R.id.map);
        map.setMapEnabled(id.cyclocomp.Settings.map(this));
        map.setFollow(true);   // mulai dengan mengikuti posisi terkini

        ImageButton close = (ImageButton) findViewById(R.id.btnClose);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        ImageButton recenter = (ImageButton) findViewById(R.id.btnRecenter);
        recenter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                map.recenter();
                map.setFollow(true);
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
        }
        service = null;
        super.onStop();
    }

    @Override
    public void onRideUpdate() {
        refresh();
    }

    private void refresh() {
        if (map == null || service == null) return;
        RideService.State st = service.getState();
        boolean riding = st == RideService.State.RECORDING || st == RideService.State.PAUSED;
        if (!riding) return;   // tak ada rute live saat idle; biarkan peta apa adanya

        long now = SystemClock.uptimeMillis();
        if (now - lastMapMs < MAP_REFRESH_MS) return;
        lastMapMs = now;

        List<GpxReader.P> track = LiveTrack.read(this, service.activeGpxLoc());
        if (track != null && track.size() >= 2) {
            map.setTrack(track);
        }
    }
}
