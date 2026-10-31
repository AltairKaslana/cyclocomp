package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Riwayat: daftar gowes yang sudah selesai.
 *
 * T-10 (B8): ketukan pada baris langsung membuka Ringkasan gowes itu. Dialog
 * pratinjau bawaan sistem sudah dihapus: ia menampilkan angka yang sama dengan
 * Ringkasan, jadi hanya menambah satu langkah.
 *
 * N-04: kartu tujuh hari dengan batang per hari, thumbnail rute di tiap baris,
 * chip LAP/KOMUTER, dan hapus lewat tekan lama. Thumbnail digambar dari
 * polyline ringkas di database - tidak ada ubin peta di daftar.
 */
public class HistoryActivity extends Activity {
    private RideDb db;
    private ListView list;
    private List<RideDb.Row> rows;

    /** Kartu ringkasan mingguan; disembunyikan kalau belum ada gowes di jendela. */
    private View weekCard;
    private TextView weekDist;
    private TextView weekText;
    private WeekBars weekBars;

    /**
     * Satu pengisian pratinjau rekaman lama berjalan pada satu waktu. Membuka
     * beberapa sesi berturut-turut tidak boleh melahirkan beberapa pembaca GPX
     * yang berebut berkas dan database.
     */
    private volatile boolean isiPratinjauAktif;

    /** Tanggal mengikuti bahasa perangkat, bukan pola tetap Indonesia/Inggris. */
    private final DateFormat df = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tema.pasangPada(this);   // palet benar SEBELUM layout dibaca
        setContentView(R.layout.activity_history);
        Ui.fonts(getWindow().getDecorView(), this);
        db = new RideDb(this);
        list = (ListView) findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.empty));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                // Satu ketukan = satu tujuan.
                startActivity(new Intent(HistoryActivity.this, SummaryActivity.class)
                        .putExtra(SummaryActivity.EXTRA_ID, rows.get(pos).id));
                overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
            }
        });
        // N-04: hapus tersedia dari daftar lewat TEKAN LAMA, bukan ketukan
        // biasa. Satu ketukan tetap membuka Ringkasan; menghapus satu rekaman
        // tidak boleh bisa terjadi karena jari terpeleset saat menggulir.
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= rows.size()) return false;
                konfirmasiHapus(rows.get(pos));
                return true;
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
        weekDist = (TextView) findViewById(R.id.weekDist);
        weekText = (TextView) findViewById(R.id.weekText);
        weekBars = (WeekBars) findViewById(R.id.weekBars);
    }

    /**
     * Muat ulang setiap kali layar kembali ke depan, bukan hanya saat dibuat.
     *
     * Ringkasan bisa MENGHAPUS gowes; dulu kembali dari sana menampilkan daftar
     * lama - baris yang baru dihapus masih ada dan masih bisa diketuk, lalu
     * membuka Ringkasan kosong. Sekalian menutup kasus pemulihan sesi yang
     * selesai saat layar ini sudah terbuka.
     */
    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    /**
     * Paksa penanda mode gelap/terang SEBELUM resource apa pun dibaca.
     *
     * Lihat {@link Tema} untuk alasannya memakai penanda konfigurasi dan bukan
     * dua tema terpisah.
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Tema.bungkus(base));
    }

    /** Transisi cermin saat keluar lewat tombol kembali. Lihat Nav.close. */
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
            String tag = lp.isEmpty() ? null : getString(R.string.chip_laps, lp.size());
            items.add(new RowItem(
                    r.id,
                    df.format(new Date(r.startMs)),
                    tag,
                    r.commute,
                    r.preview,
                    Fmt.km(r.distanceM),
                    getString(R.string.row_sub_ride, Fmt.duration(r.movingMs),
                            Fmt.speed(r.avgSpeedMs()))));
        }
        list.setAdapter(new RowAdapter(this, items));
        isiPratinjauLama();
    }

    /**
     * Kartu tujuh hari: total, jumlah gowes, waktu bergerak, dan batang per hari.
     *
     * Jendelanya hari ini + enam hari sebelumnya menurut kalender lokal (lihat
     * {@link Minggu}), bukan tujuh kali 24 jam. Dengan begitu batang hari
     * pertama dan total di atasnya selalu memakai batas yang sama.
     *
     * Kalau belum ada gowes di jendela itu, kartunya disembunyikan - bukan
     * ditampilkan dengan angka nol. "0,00 km" mengklaim sesuatu yang belum
     * diketahui pengguna, sementara daftar di bawahnya sudah jelas kosong.
     */
    private void tampilkanMinggu() {
        if (weekCard == null || weekBars == null) return;

        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        long[] b = Minggu.batas(now, tz);

        long[] mulai = new long[rows.size()];
        double[] meter = new double[rows.size()];
        double jarak = 0;
        long gerak = 0;
        int kali = 0;
        for (int i = 0; i < rows.size(); i++) {
            RideDb.Row r = rows.get(i);
            mulai[i] = r.startMs;
            meter[i] = r.distanceM;
            if (r.startMs >= b[0] && r.startMs < b[Minggu.HARI]) {
                jarak += r.distanceM;
                gerak += r.movingMs;
                kali++;
            }
        }

        if (kali == 0) {
            weekCard.setVisibility(View.GONE);
            return;
        }

        weekCard.setVisibility(View.VISIBLE);
        weekDist.setText(Fmt.km(jarak));
        weekText.setText(getString(R.string.history_week_sub, kali, Fmt.duration(gerak)));
        weekBars.setData(Minggu.jarak(now, mulai, meter, tz));
        // Batang hanya bentuk; pembaca layar mendapat ringkasan yang sama
        // dengan yang tertulis di kartu.
        weekBars.setContentDescription(getString(R.string.history_week_cd,
                Fmt.km(jarak), kali, Fmt.duration(gerak)));
    }

    /**
     * Isi pratinjau rekaman yang dibuat SEBELUM kolom preview ada.
     *
     * Dijalankan di latar belakang dengan koneksi database sendiri, supaya
     * layar tidak menunggu membaca puluhan berkas GPX dan supaya onDestroy
     * layar ini tidak menutup koneksi yang sedang dipakai. Dibatasi 24 rekaman
     * per kunjungan: membuka Riwayat pertama kali sesudah pembaruan tidak boleh
     * menjadi pekerjaan berat; kunjungan berikutnya melengkapi sisanya.
     */
    private void isiPratinjauLama() {
        if (isiPratinjauAktif) return;

        final List<Long> ids = new ArrayList<Long>();
        final List<String> jalur = new ArrayList<String>();
        for (RideDb.Row r : rows) {
            if (ids.size() >= 24) break;
            if (r.gpxPath != null && (r.preview == null || r.preview.isEmpty())) {
                ids.add(r.id);
                jalur.add(r.gpxPath);
            }
        }
        if (ids.isEmpty()) return;

        isiPratinjauAktif = true;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                RideDb latar = new RideDb(app);
                try {
                    for (int i = 0; i < ids.size(); i++) {
                        final long rid = ids.get(i);
                        File f = GpxStore.materialize(app, jalur.get(i));
                        final String preview = RuteRingkas.dariGpx(f, RuteRingkas.MAKS_TITIK);
                        if (preview == null) continue;
                        latar.updatePreview(rid, preview);

                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (isFinishing() || isDestroyed()) return;
                                RowAdapter a = (RowAdapter) list.getAdapter();
                                if (a == null) return;
                                for (int k = 0; k < a.getCount(); k++) {
                                    RowItem it = a.getItem(k);
                                    if (it != null && it.id == rid) it.preview = preview;
                                }
                                a.notifyDataSetChanged();
                            }
                        });
                    }
                } catch (Throwable ignored) {
                    // pratinjau hanya hiasan; kegagalan membaca GPX tidak boleh
                    // mengganggu daftar yang sudah tampil
                } finally {
                    latar.close();
                    isiPratinjauAktif = false;
                }
            }
        }, "pratinjau-riwayat").start();
    }

    /** Konfirmasi hapus dari daftar. Berkas GPX ikut dihapus, tidak bisa dibatalkan. */
    private void konfirmasiHapus(final RideDb.Row r) {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(R.string.hist_delete_title)
                .setMessage(R.string.hist_delete_msg)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (r.gpxPath != null) GpxStore.delete(HistoryActivity.this, r.gpxPath);
                        db.delete(r.id);
                        reload();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        // Merah, bukan aksen: menghapus bukan jalur "lanjutkan". Alasan lengkap
        // ada di SummaryActivity.konfirmasiHapus().
        Button ya = d.getButton(AlertDialog.BUTTON_POSITIVE);
        if (ya != null) ya.setTextColor(getResources().getColor(R.color.danger));
    }
}
