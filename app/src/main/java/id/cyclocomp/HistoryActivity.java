package id.cyclocomp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Riwayat: daftar gowes yang sudah selesai.
 *
 * T-10 (B8): ketukan pada baris langsung membuka Ringkasan gowes itu.
 * Sebelumnya ia membuka dialog bawaan sistem berisi angka yang sama - dua
 * layar untuk satu data, dan dialog bawaan tidak mengikuti tema gelap
 * aplikasi (tombolnya biru Holo). Hapus dan Rute pindah ke menu kecil di
 * layar Ringkasan.
 */
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
        db = new RideDb(this);
        list = (ListView) findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.empty));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                // Satu ketukan = satu tujuan. Dialog pratinjau yang dulu ada di
                // sini menampilkan angka yang sama seperti Ringkasan, jadi ia
                // cuma menambah satu langkah sebelum sampai ke tempat yang sebenarnya
                // berguna (rute, bagikan kartu, hapus).
                startActivity(new Intent(HistoryActivity.this, SummaryActivity.class)
                        .putExtra(SummaryActivity.EXTRA_ID, rows.get(pos).id));
                overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
            }
        });

        // Bilah atas: tombol kembali + judul. Tema aplikasi NoActionBar, jadi
        // setTitle() tidak pernah terlihat - judul dan jalan keluar harus
        // dibuat sendiri.
        ImageButton back = (ImageButton) findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        }
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
            // Komuter masuk ke tag, bukan ke baris keterangan: kalau tidak ada lap,
            // tag masih bisa menampung satu penanda tanpa mengubah format baris
            // keterangan yang sudah dipakai di seluruh layar.
            if (r.commute) {
                tag = (tag == null ? "" : tag) + getString(R.string.row_commute);
            }
            items.add(new RowItem(
                    df.format(new Date(r.startMs)),
                    tag,
                    Fmt.km(r.distanceM),
                    getString(R.string.row_sub_ride, Fmt.duration(r.movingMs),
                            Fmt.speed(r.avgSpeedMs()))));
        }
        list.setAdapter(new RowAdapter(this, items));
    }
}
