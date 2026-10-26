package id.cyclocomp;

import android.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
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
        try {
            bind();
        } catch (Throwable t) {
            // Layar Ringkasan bukan tempat untuk mati: angka tetap yang paling penting,
            // dan kalau ada yang gagal, pengguna dapat pesan alih-alih aplikasi tertutup.
            Log.e("Summary", "tampilan ringkasan gagal", t);
            Toast.makeText(this, R.string.summary_broken, Toast.LENGTH_LONG).show();
        }

        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnShareCard).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareCard(); }
        });
        Button strava = (Button) findViewById(R.id.btnStrava);
        strava.setVisibility(StravaStore.connected(this) ? View.VISIBLE : View.GONE);
        strava.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { uploadStrava(); }
        });
        findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { share(); }
        });
    }

    /**
     * Transisi cermin saat keluar (tombol kembali, atau tombol TUTUP di layar ini).
     * Lihat Nav.close.
     */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
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

        // rute & peta: kalau gagal, angka di atas tetap tampil (rute tidak, itu saja)
        File gpx = row.gpxPath == null ? null : new File(row.gpxPath);
        try {
            TileMapView rv = (TileMapView) findViewById(R.id.route);
            rv.setMapEnabled(Settings.map(this));
            List<GpxReader.P> track = GpxReader.read(gpx);
            rv.setTrack(track);
        } catch (Throwable t) {
            Log.e("Summary", "rute gagal ditampilkan", t);
        }

        // lap
        List<Lap> laps;
        try {
            laps = RideService.decodeLaps(row.laps);
        } catch (Throwable t) {
            Log.e("Summary", "lap gagal dibaca", t);
            laps = java.util.Collections.emptyList();
        }
        LinearLayout wrap = (LinearLayout) findViewById(R.id.lapWrap);
        TextView lapCount = (TextView) findViewById(R.id.tvLapCount);
        LinearLayout rows = (LinearLayout) findViewById(R.id.lapRows);
        if (wrap == null || lapCount == null || rows == null) return;   // jangan crash

        // Hanya wadah baris yang dibersihkan. Sebelumnya removeAllViews() dipanggil pada
        // kartu lapWrap sendiri, sehingga header tvLapCount ikut terbuang dan pemanggilan
        // berikutnya menemukan null -> NPE tepat saat layar Ringkasan dibuka.
        rows.removeAllViews();

        if (laps.isEmpty()) {
            wrap.setVisibility(View.GONE);
        } else {
            wrap.setVisibility(View.VISIBLE);
            lapCount.setText(getString(R.string.summary_laps, laps.size()));
            for (Lap l : laps) {
                TextView t = new TextView(this);
                t.setText(getString(R.string.summary_lap_row, l.index,
                        Fmt.km(l.distanceM), Fmt.shortDuration(l.elapsedMs),
                        Fmt.speed(l.avgSpeedMs())));
                t.setTextColor(getResources().getColor(R.color.value));
                t.setTextSize(13);
                t.setPadding(0, dp(6), 0, dp(6));
                Ui.fonts(t, this);
                rows.addView(t);
            }
        }

        boolean adaGpx = gpx != null && gpx.exists();
        Button share = (Button) findViewById(R.id.btnShare);
        share.setEnabled(adaGpx);
        share.setAlpha(adaGpx ? 1f : 0.4f);
    }

    /**
     * Tanya dulu bentuk kartu yang mau dibagikan, lalu bangun kartunya.
     *
     * Dua bentuk, dua kegunaan berbeda: kartu 4:5 punya peta rute dan pas untuk
     * WhatsApp/Telegram, kartu Story 9:16 latar hitam dengan angka besar dibuat
     * untuk Story Instagram/WhatsApp yang mengisi layar penuh. Memilih sekali di
     * dialog ini lebih baik daripada menebak - pemakai yang tahu dia mau posting di mana.
     */
    private void shareCard() {
        if (row == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.card_pick_title)
                .setItems(new CharSequence[]{
                                getString(R.string.card_variant_map),
                                getString(R.string.card_variant_story)},
                        (d, which) -> buildAndShare(which == 1))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Bangun kartu terpilih (story = kartu 9:16) lalu buka pemilih aplikasi.
     *
     * Kartu digambar sendiri lewat {@link ShareCard}, bukan memotret layar: hasilnya sama
     * di semua perangkat dan tetap lengkap walaupun peta ubin tidak bisa diambil.
     * Seluruh proses dibungkus try/catch - membuat gambar tidak boleh mematikan aplikasi.
     */
    private void buildAndShare(boolean story) {
        if (row == null) return;

        // Rute hanya dibaca untuk kartu 4:5; kartu story tidak menggambar peta,
        // jadi membaca GPX di sana cuma buang waktu di perangkat lambat.
        List<GpxReader.P> track = java.util.Collections.emptyList();
        if (!story) {
            try {
                track = GpxReader.read(row.gpxPath == null ? null : new File(row.gpxPath));
            } catch (Throwable t) {
                Log.e("Summary", "rute untuk kartu gagal dibaca", t);
                track = java.util.Collections.emptyList();
            }
        }

        File png;
        try {
            png = story ? ShareCard.buildStory(this, row) : ShareCard.build(this, row, track);
        } catch (Throwable t) {
            Log.e("Summary", "kartu gagal dibuat", t);
            png = null;
        }
        if (png == null) {
            Toast.makeText(this, R.string.card_failed, Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = Uri.parse("content://" + RideProvider.AUTHORITY + "/" + png.getName());
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.card_share_subject, png.getName()));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.card_share_title)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_LONG).show();
        }
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

    /**
     * Unggah GPX gowes ini ke Strava.
     *
     * Berjalan di thread latar karena unggahan multipart + penanyaan status bisa memakan
     * waktu puluhan detik; kalau di thread UI aplikasi akan ANR.
     */
    private void uploadStrava() {
        if (row == null || row.gpxPath == null) return;
        final File gpx = new File(row.gpxPath);
        if (!gpx.exists()) {
            Toast.makeText(this, R.string.gpx_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setMessage(getString(R.string.strava_uploading));
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override public void run() {
                final String err = Strava.upload(SummaryActivity.this, gpx);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        pd.dismiss();
                        // ternary tidak boleh mencampur int (R.string) dan String,
                        // jadi pesannya dipilih dulu sebagai String
                        String msg = err == null
                                ? getString(R.string.strava_uploaded) : err;
                        Toast.makeText(SummaryActivity.this, msg,
                                err == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
