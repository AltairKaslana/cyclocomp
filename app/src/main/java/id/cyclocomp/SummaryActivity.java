package id.cyclocomp;

import android.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
        Tema.pasangPada(this);   // palet benar SEBELUM layout dibaca
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
            bindPenilaian();
        } catch (Throwable t) {
            // Layar Ringkasan bukan tempat untuk mati: angka tetap yang paling penting,
            // dan kalau ada yang gagal, pengguna dapat pesan alih-alih aplikasi tertutup.
            Log.e("Summary", "tampilan ringkasan gagal", t);
            Toast.makeText(this, R.string.summary_broken, Toast.LENGTH_LONG).show();
        }

        // Satu jalan keluar saja: tombol X di bilah atas, yang tidak ikut
        // digulir. Tombol TUTUP kedua yang dulu ada di paling bawah isi gulir
        // sudah dibuang (T-09) - dua tombol bertuliskan "Tutup" di satu layar
        // adalah label ganda, dan yang di bawah butuh menggulir melewati peta,
        // metrik, lap, kalori, dan 10 tombol RPE hanya untuk keluar.
        View topClose = findViewById(R.id.btnTopClose);
        if (topClose != null) {
            topClose.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        }
        // T-10: Hapus dipindahkan ke sini dari Riwayat. Menghapus adalah
        // keputusan tentang SATU gowes, dan layar yang menampilkan gowes itu
        // adalah tempat paling masuk akal untuk memutuskan - bukan dari daftar,
        // di mana satu ketukan salah bisa menghapus rekaman yang lain.
        findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { konfirmasiHapus(); }
        });
        findViewById(R.id.btnShareCard).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { bukaLayarBagikan(); }
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

        bindKalori();

        // rute & peta: kalau gagal, angka di atas tetap tampil (rute tidak, itu saja)
        File gpx = GpxStore.materialize(this, row.gpxPath);
        try {
            TileMapView rv = (TileMapView) findViewById(R.id.route);
            rv.setMapEnabled(Settings.map(this));
            List<GpxReader.P> track = GpxReader.read(gpx);
            rv.setTrack(track);
            bindDetak(track);
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
                // T-04: satu fungsi format untuk semua layar dan kartu.
                // Sebelumnya baris ini dirangkai dari string summary_lap_row di
                // strings.xml - format yang SAMA dengan Fmt.lap(), ditulis di dua
                // tempat. Kalau salah satu berubah, layar ini dan kartu bagikan
                // jadi menampilkan angka yang sama dengan wajah berbeda.
                t.setText(Fmt.lap(l.index, l.distanceM, l.elapsedMs, l.avgSpeedMs()));
                t.setTextColor(getResources().getColor(R.color.value));
                // Ukuran lewat token, sama seperti tombol RPE di atas. Sebelumnya
                // 13 dan dp(6) ditulis langsung — dua-satunya nilai di layar ini
                // yang tidak lewat dimens.xml.
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.text_body_s));
                int padLap = getResources().getDimensionPixelSize(R.dimen.gap_cozy);
                t.setPadding(0, padLap, 0, padLap);
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
     * Apakah aplikasi menerima data daya dari sensor sungguhan (power meter
     * Bluetooth, atau sensor bawaan).
     *
     * Zylo TIDAK punya: tidak ada kode yang membaca daya dari sensor, dan kepala
     * Calories.java menyatakan watt-nya "DIPERKIRAAN dari fisika". Jadi nilai
     * ini false, dan konsekuensinya kolom DAYA RATA-RATA di layar ini tidak
     * pernah tampil.
     *
     * Kalau power meter ditambahkan nanti (misal lewat BLE), ubah baris ini jadi
     * true - seluruh tampilan sudah siap, tidak ada yang perlu diubah lain.
     */
    private static final boolean ADA_POWER_METER = false;

    /**
     * Kalori (dan daya rata-rata, kalau ada power meter).
     *
     * Kalau rekaman dibuat sebelum v3 (kolom belum ada), nilainya dihitung ulang di
     * sini daripada ditampilkan kosong — pengguna tidak perlu tahu bahwa skema basis
     * data pernah berubah.
     */
    private void bindKalori() {
        int kcal = row.caloriesKcal;
        if (kcal <= 0) {
            kcal = Calories.estimate(row.distanceM, row.movingMs, row.elevGainM,
                    Settings.weight(this));
        }
        TextView tvKcal = (TextView) findViewById(R.id.tvKcal);

        // Kolom daya + garis pemisahnya disembunyikan BERSAMAAN kalau tidak ada
        // power meter. Sengaja dua-duanya GONE, bukan hanya kolomnya: garis
        // pemisah yang menggantung sendiri di ujung kanan akan terlihat seperti
        // sisa yang lupa dibersihkan.
        //
        // Berat kolom kalori tidak diubah (tetap 1), jadi kalau daya disembunyikan
        // angka kalori cukup memusat di seluruh lebar - bukan terdorong ke kiri
        // meninggalkan separuh kartu kosong.
        View wattsCol = findViewById(R.id.wattsCol);
        View wattsDivider = findViewById(R.id.wattsDivider);
        if (wattsCol != null) {
            wattsCol.setVisibility(ADA_POWER_METER ? View.VISIBLE : View.GONE);
        }
        if (wattsDivider != null) {
            wattsDivider.setVisibility(ADA_POWER_METER ? View.VISIBLE : View.GONE);
        }

        if (!ADA_POWER_METER) {
            // Angkanya tetap dihitung supaya baris ini tidak perlu dicabut dari
            // kode: kalau power meter ditambahkan, tidak ada yang perlu disambung
            // ulang. Yang berubah hanya apakah selnya terlihat.
            tvKcal.setText(Fmt.kcal(kcal));
            return;
        }

        TextView tvWatts = (TextView) findViewById(R.id.tvWatts);
        if (kcal <= 0) {
            tvKcal.setText(R.string.kcal_none);
            tvWatts.setText("-");
            return;
        }
        // T-09: tanda "≈" dibuang. Dulu string-nya "≈ %s kcal" dan "≈ %d W
        // rata-rata", jadi selnya memuat angka + satuan + keterangan perkiraan
        // sekaligus. Sekarang satuannya TextView terpisah (T-05) dan sesuai
        // keputusan pemilik keduanya tampil tanpa keterangan perkiraan.
        //
        // Angkanya sendiri tidak berubah - hanya cara menyajikannya.
        tvKcal.setText(Fmt.kcal(kcal));
        tvWatts.setText(String.valueOf(
                Calories.avgWatts(row.distanceM, row.movingMs, row.elevGainM,
                        Settings.weight(this))));
    }

    /**
     * Hapus gowes ini, dengan konfirmasi kedua: berkas GPX tidak bisa
     * dipulihkan.
     *
     * Dipindahkan dari HistoryActivity (T-10). Menghapus adalah keputusan
     * tentang SATU gowes, dan layar yang menampilkannya adalah tempat paling
     * masuk akal untuk memutuskan - bukan dari daftar, di mana satu ketukan
     * salah bisa mengenai rekaman yang lain.
     */
    private void konfirmasiHapus() {
        if (row == null) return;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(R.string.hist_delete_title)
                .setMessage(R.string.hist_delete_msg)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (row.gpxPath != null)
                            GpxStore.delete(SummaryActivity.this, row.gpxPath);
                        db.delete(row.id);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        // Tombol konfirmasi HAPUS dibuat merah, bukan aksen cyan.
        //
        // Kenapa: warna tombol dialog mengikuti colorAccent tema, dan aksen cyan
        // adalah warna "lanjutkan" - dipakai oleh MULAI, Sambungkan, dan Simpan.
        // Kalau "Hapus" juga cyan, jalur keluar terlihat sama pentingnya dengan
        // jalur aman, padahal menghapus gowes tidak bisa dibatalkan.
        //
        // Kenapa lewat getButton() sesudah show(), bukan tema dialog terpisah:
        // tema dialog hanya bisa mengubah warna lewat atribut yang tersedia untuk
        // seluruh dialog (termasuk tombol Batal, yang justru harus netral).
        // Diambil sesudah show() karena tombolnya baru ada setelah dialog dibangun.
        Button ya = d.getButton(AlertDialog.BUTTON_POSITIVE);
        if (ya != null) ya.setTextColor(getResources().getColor(R.color.danger));
    }

    /**
     * Detak jantung rata-rata & maksimum, dibaca dari titik GPX.
     *
     * Kalau tidak ada satu pun nilai hr (tidak pakai sensor, atau berkas rekaman
     * lama), seluruh baris disembunyikan. Sengaja GONE, bukan dikosongkan: baris
     * hampa membuat kartu metrik terasa rusak.
     */
    private void bindDetak(List<GpxReader.P> track) {
        View hrRow = findViewById(R.id.hrRow);
        if (hrRow == null) return;
        // Pemisah di atas baris ini ikut disembunyikan. Kalau tidak, garis
        // pembatas akan terlihat sendirian di atas ruang kosong - dan itu
        // membuat kartu metrik terlihat rusak, bukan sekadar kosong.
        View hrDivider = findViewById(R.id.hrDivider);

        int sum = 0, count = 0, max = 0;
        if (track != null) {
            for (GpxReader.P p : track) {
                if (p.hr > 0) {
                    sum += p.hr;
                    count++;
                    if (p.hr > max) max = p.hr;
                }
            }
        }
        int tampil = count == 0 ? View.GONE : View.VISIBLE;
        hrRow.setVisibility(tampil);
        if (hrDivider != null) hrDivider.setVisibility(tampil);
        if (count == 0) return;
        ((TextView) findViewById(R.id.tvHrAvg)).setText(
                String.valueOf(Math.round((float) sum / count)));
        ((TextView) findViewById(R.id.tvHrMax)).setText(String.valueOf(max));
    }

    /**
     * Buka layar Bagikan: preview gambar yang bisa digeser kiri-kanan untuk
     * memilih kartu, dengan aksi simpan ke galeri / bagikan gambar / bagikan
     * GPX tepat di bawah gambar - pola yang dipakai Strava. Semua bangun dan
     * simpan kartu hidup di {@link ShareActivity}.
     */
    private void bukaLayarBagikan() {
        if (row == null) return;
        Intent i = new Intent(this, ShareActivity.class);
        i.putExtra(EXTRA_ID, row.id);
        try {
            startActivity(i);
        } catch (Exception e) {
            Log.e("Summary", "layar bagikan tidak bisa dibuka", e);
            Toast.makeText(this, R.string.card_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Bagikan berkas GPX lewat content:// supaya bisa diterima aplikasi lain (termasuk Strava). */
    private void share() {
        if (row == null || row.gpxPath == null) return;
        if (!GpxStore.exists(this, row.gpxPath)) {
            Toast.makeText(this, R.string.gpx_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        // content:// MediaStore (Android 10+) dipakai langsung; path biasa
        // tetap lewat RideProvider seperti sebelumnya.
        Uri uri = GpxStore.shareUri(this, row.gpxPath);
        String nama = GpxStore.displayName(row.gpxPath);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/gpx+xml");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, nama));
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
        if (!GpxStore.exists(this, row.gpxPath)) {
            Toast.makeText(this, R.string.gpx_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        final String gpxLoc = row.gpxPath;

        final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setMessage(getString(R.string.strava_uploading));
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override public void run() {
                final String err = Strava.upload(SummaryActivity.this, gpxLoc);
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

    // ---------- penilaian sesudah gowes: komuter + RPE ----------

    /**
     * Pasang tombol angka RPE 1..10 dan sakelar Komuter.
     *
     * Dipisah dari {@link #bind()} supaya kegagalan di sini tidak membatalkan
     * tampilan angka utama — pola yang sama dipakai untuk lap.
     */
    private void bindPenilaian() {
        final LinearLayout rpeRow = (LinearLayout) findViewById(R.id.rpeRow);
        final TextView clear = (TextView) findViewById(R.id.tvRpeClear);
        final CheckBox commute = (CheckBox) findViewById(R.id.swCommute);
        if (rpeRow == null || clear == null || commute == null) return;

        rpeRow.removeAllViews();
        final int[] nilai = {row.rpe};
        // Semua ukuran lewat token, bukan angka di Java. Sebelumnya 13sp dan 8dp
        // ditulis langsung di sini — satu-satunya ukuran huruf di aplikasi yang
        // tidak lewat dimens.xml, jadi tidak pernah ikut kalau skalanya diubah.
        final int textPx = getResources().getDimensionPixelSize(R.dimen.text_body_s);
        final int padPx = getResources().getDimensionPixelSize(R.dimen.gap_normal);
        // 48dp = target sentuh minimum yang aturannya sendiri ditulis di dimens.xml
        // ("48dp adalah ambang aksesibilitas"). Sebelumnya tingginya WRAP_CONTENT
        // dengan setMinHeight(0), jadi jatuh ke sekitar 33-36dp — sepuluh tombol
        // diketik dengan jari, di atas sepeda.
        final int touchPx = getResources().getDimensionPixelSize(R.dimen.touch_target_min);
        for (int i = 1; i <= 10; i++) {
            final int n = i;
            Button b = new Button(this);
            b.setText(String.valueOf(n));
            b.setTag("num");
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textPx);
            b.setTypeface(Ui.condBold(this));
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinHeight(0);
            b.setStateListAnimator(null);
            b.setPadding(0, padPx, 0, padPx);
            // Bentuk dan keadaan lewat drawable selector, sama seperti tombol lain
            // di aplikasi. Sebelumnya setBackgroundColor() di gayaTombolRpe():
            // sudutnya jadi kotak (satu-satunya tombol bersudut tajam), dan tidak
            // ada keadaan tekan sama sekali.
            b.setBackgroundResource(R.drawable.bg_rpe);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, touchPx, 1f);
            b.setLayoutParams(lp);
            gayaTombolRpe(b, n == nilai[0]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // Ketuk angka yang sama lagi = batalkan nilai. Lebih mudah
                    // ditemukan daripada mencari tombol "hapus" yang terpisah.
                    nilai[0] = (nilai[0] == n) ? 0 : n;
                    simpanPenilaian(commute.isChecked(), nilai[0]);
                    perbaruiGayaRpe(rpeRow, nilai[0]);
                    clear.setVisibility(nilai[0] > 0 ? View.VISIBLE : View.GONE);
                    if (nilai[0] > 0) {
                        Toast.makeText(SummaryActivity.this, R.string.rpe_saved,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            rpeRow.addView(b);
        }
        perbaruiGayaRpe(rpeRow, nilai[0]);
        clear.setVisibility(nilai[0] > 0 ? View.VISIBLE : View.GONE);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                nilai[0] = 0;
                simpanPenilaian(commute.isChecked(), 0);
                perbaruiGayaRpe(rpeRow, 0);
                clear.setVisibility(View.GONE);
            }
        });

        commute.setChecked(row.commute);
        commute.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                simpanPenilaian(on, nilai[0]);
            }
        });
    }

    private void simpanPenilaian(boolean isCommute, int rpe) {
        try {
            db.updateMeta(row.id, isCommute, rpe);
            row.commute = isCommute;
            row.rpe = rpe;
        } catch (Throwable t) {
            // Menilai bukan hal wajib. Kalau gagal, pengguna boleh terus melihat
            // ringkasan; jangan matikan layar hanya karena satu kolom.
            Log.e("Summary", "penilaian gagal disimpan", t);
        }
    }

    private void perbaruiGayaRpe(LinearLayout row, int terpilih) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) gayaTombolRpe(v, i + 1 == terpilih);
        }
    }

    /**
     * Angka terpilih dibedakan lewat WARNA, bukan ukuran.
     *
     * Sebelumnya dicoba memakai ukuran/tebal berbeda; di layar 320dp selisih itu
     * tidak terbaca dan 10 tombol jadi terasa tidak seragam. Warna aksen pada
     * latar gelap terbaca jelas sekaligus.
     */
    private void gayaTombolRpe(View v, boolean terpilih) {
        // Warna latar lewat state_selected pada bg_rpe, bukan setBackgroundColor.
        // Yang ditulis di sini hanya warna teks, dan itu pun dari token.
        v.setSelected(terpilih);
        ((Button) v).setTextColor(getResources().getColor(
                terpilih ? R.color.bg : R.color.value));
    }
}
