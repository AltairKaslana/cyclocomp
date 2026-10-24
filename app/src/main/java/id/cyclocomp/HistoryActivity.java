package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends Activity {
    private RideDb db;
    private ListView list;
    private List<RideDb.Row> rows;
    /** Tanggal mengikuti bahasa perangkat, bukan pola tetap Indonesia/Inggris. */
    private final DateFormat df = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        Ui.fonts(getWindow().getDecorView(), this);
        setTitle(R.string.title_history);
        db = new RideDb(this);
        list = (ListView) findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.empty));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                showDetail(rows.get(pos));
            }
        });
        reload();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private void reload() {
        rows = db.list();
        String[] items = new String[rows.size()];
        for (int i = 0; i < items.length; i++) {
            RideDb.Row r = rows.get(i);
            java.util.List<Lap> lp = RideService.decodeLaps(r.laps);
            items[i] = lp.isEmpty()
                    ? getString(R.string.hist_item, df.format(new Date(r.startMs)),
                            Fmt.km(r.distanceM), Fmt.duration(r.movingMs),
                            Fmt.speed(r.avgSpeedMs()))
                    : getString(R.string.hist_item_laps, df.format(new Date(r.startMs)),
                            Fmt.km(r.distanceM), Fmt.duration(r.movingMs),
                            Fmt.speed(r.avgSpeedMs()), lp.size());
        }
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
    }

    /** Ringkasan lap: tiap baris jarak + waktu. Dibatasi 8 lap supaya dialog tidak terlalu panjang. */
    private String lapSummary(java.util.List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(getString(R.string.hist_lap_header));
        int n = 0;
        for (Lap l : laps) {
            if (n >= 8) {
                sb.append("\n").append(getString(R.string.hist_lap_more, laps.size() - n));
                break;
            }
            sb.append("\n").append(getString(R.string.hist_lap_row, l.index,
                    Fmt.km(l.distanceM), Fmt.shortDuration(l.elapsedMs),
                    Fmt.speed(l.avgSpeedMs())));
            n++;
        }
        return sb.toString();
    }

    /** Hapus butuh konfirmasi kedua: berkas GPX tidak bisa dipulihkan. */
    private void konfirmasiHapus(final RideDb.Row r) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.hist_delete_title)
                .setMessage(R.string.hist_delete_msg)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (r.gpxPath != null) //noinspection ResultOfMethodCallIgnored
                            new File(r.gpxPath).delete();
                        db.delete(r.id);
                        reload();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDetail(final RideDb.Row r) {
        String msg = getString(R.string.hist_dist, Fmt.km(r.distanceM)) + "\n"
                + getString(R.string.hist_moving, Fmt.duration(r.movingMs)) + "\n"
                + getString(R.string.hist_total, Fmt.duration(r.durationMs)) + "\n"
                + getString(R.string.hist_avg, Fmt.speed(r.avgSpeedMs())) + "\n"
                + getString(R.string.hist_max, Fmt.speed((float) r.maxSpeedMs)) + "\n"
                + getString(R.string.hist_elev, Fmt.meters(r.elevGainM)) + "\n\n"
                + lapSummary(RideService.decodeLaps(r.laps))
                + "\n" + getString(R.string.hist_gpx_file, r.gpxPath);
        new AlertDialog.Builder(this)
                .setTitle(df.format(new Date(r.startMs)))
                .setMessage(msg)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.btn_route, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        startActivity(new android.content.Intent(HistoryActivity.this,
                                SummaryActivity.class)
                                .putExtra(SummaryActivity.EXTRA_ID, r.id));
                    }
                })
                .setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        konfirmasiHapus(r);
                    }
                })
                .show();
    }
}
