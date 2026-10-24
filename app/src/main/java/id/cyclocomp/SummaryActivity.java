package id.cyclocomp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ringkasan setelah gowes: rute yang dilalui + angka akhir + bagikan GPX.
 *
 * Dibuka dari dashboard saat perekaman berhenti, atau dari Riwayat.
 */
public class SummaryActivity extends Activity {
    static final String EXTRA_ID = "ride_id";

    private RideDb db;
    private RideDb.Row row;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);
        Ui.fonts(getWindow().getDecorView(), this);
        Ui.buttonFonts(getWindow().getDecorView(), this);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        db = new RideDb(this);
        row = id > 0 ? db.byId(id) : db.latest();
        if (row == null) {
            Toast.makeText(this, R.string.summary_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        bind();

        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { share(); }
        });
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void bind() {
        DateFormat df = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
        ((TextView) findViewById(R.id.tvWhen)).setText(df.format(new Date(row.startMs)));

        ((TextView) findViewById(R.id.tvDist)).setText(Fmt.km(row.distanceM));
        ((TextView) findViewById(R.id.tvMoving)).setText(Fmt.duration(row.movingMs));
        ((TextView) findViewById(R.id.tvTotal)).setText(Fmt.duration(row.durationMs));
        ((TextView) findViewById(R.id.tvAvg)).setText(Fmt.speed(row.avgSpeedMs()));
        ((TextView) findViewById(R.id.tvMax)).setText(Fmt.speed((float) row.maxSpeedMs));
        ((TextView) findViewById(R.id.tvElev)).setText(Fmt.meters(row.elevGainM));

        // rute
        TileMapView rv = (TileMapView) findViewById(R.id.route);
        rv.setMapEnabled(Settings.map(this));
        File gpx = row.gpxPath == null ? null : new File(row.gpxPath);
        List<GpxReader.P> track = GpxReader.read(gpx);
        rv.setTrack(track);

        // lap
        List<Lap> laps = RideService.decodeLaps(row.laps);
        LinearLayout wrap = (LinearLayout) findViewById(R.id.lapWrap);
        wrap.removeAllViews();
        if (laps.isEmpty()) {
            wrap.setVisibility(View.GONE);
        } else {
            wrap.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tvLapCount)).setText(
                    getString(R.string.summary_laps, laps.size()));
            for (Lap l : laps) {
                TextView t = new TextView(this);
                t.setText(getString(R.string.summary_lap_row, l.index,
                        Fmt.km(l.distanceM), Fmt.shortDuration(l.elapsedMs),
                        Fmt.speed(l.avgSpeedMs())));
                t.setTextColor(getResources().getColor(R.color.value));
                t.setTextSize(13);
                t.setPadding(0, dp(6), 0, dp(6));
                Ui.fonts(t, this);
                wrap.addView(t);
            }
        }

        boolean adaGpx = gpx != null && gpx.exists();
        Button share = (Button) findViewById(R.id.btnShare);
        share.setEnabled(adaGpx);
        share.setAlpha(adaGpx ? 1f : 0.4f);
    }

    /** Bagikan berkas GPX lewat content:// supaya bisa diterima aplikasi lain (termasuk Strava). */
    private void share() {
        if (row == null || row.gpxPath == null) return;
        File f = new File(row.gpxPath);
        if (!f.exists()) {
            Toast.makeText(this, R.string.gpx_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse("content://" + RideProvider.AUTHORITY + "/" + f.getName());
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/gpx+xml");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, f.getName()));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_title)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
