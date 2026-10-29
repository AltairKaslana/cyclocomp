package id.cyclocomp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

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
 *
 * T-19: total mingguan duduk di atas daftar.
 */
public class HistoryActivity extends Activity {
    private RideDb db;
    private ListView list;
    private List<RideDb.Row> rows;
    /** Kartu ringkasan mingguan; disembunyikan kalau belum ada gowes minggu ini. */
    private View weekCard;
    private TextView weekText;
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

        weekCard = findViewById(R.id.weekCard);
        weekText = (TextView) findViewById(R.id.weekText);
        reload();
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

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private void reload() {
        rows = db.list();
        tampilkanMinggu();

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

    /**
     * T-19: total tujuh hari terakhir, ditaruh di atas daftar.
     *
     * Kenapa di atas, bukan di bawah: yang paling sering dicari orang yang
     * membuka Riwayat adalah "sejauh mana aku minggu ini". Kalau jawabannya ada
     * di bawah daftar yang makin panjang, ia harus menggulir melewati semua
     * rekaman lama dulu - dan riwayat gowes bisa puluhan baris.
     *
     * Jendelanya tujuh hari ke belakang dari sekarang, BUKAN minggu kalender
     * (Senin-Minggu). Alasannya: kalau dibuka hari Minggu, minggu kalender baru
     * saja berganti dan totalnya nol - terlihat seperti data hilang, padahal
     * yang baru selesai justru kemarin. "Minggu ini" dalam percakapan berarti
     * tujuh hari terakhir yang masih utuh.
     *
     * Kalau belum ada gowes dalam jendela itu, kartunya disembunyikan - bukan
     * ditampilkan dengan angka nol. Kartu berisi "0,00 km" mengklaim sesuatu
     * yang belum diketahui pengguna, sementara daftar di bawahnya sudah jelas
     * kosong.
     */
    private void tampilkanMinggu() {
        if (weekCard == null) return;

        long batas = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L;
        double jarak = 0;
        long gerak = 0;
        int kali = 0;
        for (RideDb.Row r : rows) {
            if (r.startMs < batas) continue;
            jarak += r.distanceM;
            gerak += r.movingMs;
            kali++;
        }

        if (kali == 0) {
            weekCard.setVisibility(View.GONE);
            return;
        }
        weekCard.setVisibility(View.VISIBLE);
        weekText.setText(getString(R.string.history_week_fmt, Fmt.km(jarak), kali,
                Fmt.duration(gerak)));
    }
}
