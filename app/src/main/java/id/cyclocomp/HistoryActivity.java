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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends Activity {
    private RideDb db;
    private ListView list;
    private List<RideDb.Row> rows;
    private final SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
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
            items[i] = df.format(new Date(r.startMs)) + "\n"
                    + Fmt.km(r.distanceM) + " km  •  " + Fmt.duration(r.movingMs)
                    + "  •  " + Fmt.speed(r.avgSpeedMs()) + " km/j"
                    + (lp.isEmpty() ? "" : "  •  " + lp.size() + " lap");
        }
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
    }

    /** Ringkasan lap: tiap baris jarak + waktu. Dibatasi 8 lap supaya dialog tidak terlalu panjang. */
    private String lapSummary(java.util.List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Lap:");
        int n = 0;
        for (Lap l : laps) {
            if (n >= 8) { sb.append("\n… dan ").append(laps.size() - n).append(" lap lagi"); break; }
            sb.append("\n  ").append(l.index).append(". ")
              .append(Fmt.km(l.distanceM)).append(" km  ")
              .append(Fmt.shortDuration(l.elapsedMs)).append("  ")
              .append(Fmt.speed(l.avgSpeedMs())).append(" km/j");
            n++;
        }
        return sb.toString();
    }

    private void showDetail(final RideDb.Row r) {
        String msg = "Jarak: " + Fmt.km(r.distanceM) + " km\n"
                + "Waktu gerak: " + Fmt.duration(r.movingMs) + "\n"
                + "Waktu total: " + Fmt.duration(r.durationMs) + "\n"
                + "Rata-rata: " + Fmt.speed(r.avgSpeedMs()) + " km/j\n"
                + "Maks: " + Fmt.speed((float) r.maxSpeedMs) + " km/j\n"
                + "Tanjakan: " + Fmt.meters(r.elevGainM) + " m\n\n"
                + lapSummary(RideService.decodeLaps(r.laps))
                + "\nFile GPX:\n" + r.gpxPath;
        new AlertDialog.Builder(this)
                .setTitle(df.format(new Date(r.startMs)))
                .setMessage(msg)
                .setPositiveButton(R.string.close, null)
                .setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (r.gpxPath != null) //noinspection ResultOfMethodCallIgnored
                            new File(r.gpxPath).delete();
                        db.delete(r.id);
                        reload();
                    }
                })
                .show();
    }
}
