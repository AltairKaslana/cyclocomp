package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.text.DateFormat;
import java.util.ArrayList;
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

    /**
     * Transisi cermin saat keluar lewat tombol kembali. Lihat Nav.close.
     */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private void reload() {
        rows = db.list();
        List<RowItem> items = new ArrayList<RowItem>();
        for (RideDb.Row r : rows) {
            List<Lap> lp = RideService.decodeLaps(r.laps);
            String tag = lp.isEmpty() ? null : getString(R.string.row_laps, lp.size());
            items.add(new RowItem(
                    df.format(new Date(r.startMs)),
                    tag,
                    Fmt.km(r.distanceM),
                    getString(R.string.row_sub_ride, Fmt.duration(r.movingMs),
                            Fmt.speed(r.avgSpeedMs()))));
        }
        list.setAdapter(new RowAdapter(this, items));
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
                        if (r.gpxPath != null)
                            GpxStore.delete(HistoryActivity.this, r.gpxPath);
                        db.delete(r.id);
                        reload();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDetail(final RideDb.Row r) {
        // Path GPX sengaja TIDAK ditampilkan. Sebelumnya dialog ini diakhiri
        // "File GPX:\n/storage/emulated/0/Android/data/…/ride_xxx.gpx" — itu rincian
        // internal yang tidak bisa dipakai pengguna, dan membuat ringkasan terlihat
        // seperti layar debug. Berkasnya sendiri tetap bisa diambil lewat tombol
        // Rute -> bagikan gambar/GPX di layar Ringkasan.
        String msg = getString(R.string.hist_dist, Fmt.km(r.distanceM)) + "\n"
                + getString(R.string.hist_moving, Fmt.duration(r.movingMs)) + "\n"
                + getString(R.string.hist_total, Fmt.duration(r.durationMs)) + "\n"
                + getString(R.string.hist_avg, Fmt.speed(r.avgSpeedMs())) + "\n"
                + getString(R.string.hist_max, Fmt.speed((float) r.maxSpeedMs)) + "\n"
                + getString(R.string.hist_elev, Fmt.meters(r.elevGainM)) + "\n\n"
                + lapSummary(RideService.decodeLaps(r.laps));
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
