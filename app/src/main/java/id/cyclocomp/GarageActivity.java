package id.cyclocomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Garasi (rancangan UI/UX 5.7).
 *
 * Satu sepeda aktif ditampilkan sebagai chip; odometer dan perawatan selalu
 * milik sepeda itu. Semua angka dihitung dari tabel rides, jadi menghapus
 * gowes langsung mengoreksi odometer tanpa tabel ringkasan yang bisa basi.
 */
public class GarageActivity extends Activity {

    private LinearLayout isi;
    private Bike aktif;
    private double odoM;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Tema.bungkus(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tema.pasangPada(this);
        setContentView(R.layout.activity_garage);

        isi = (LinearLayout) findViewById(R.id.garage);
        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        paint();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Tema.perbaikiKalauPerlu(this);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit);
    }

    // ---------- menggambar ----------

    private void paint() {
        isi.removeAllViews();
        List<Bike> daftar = GarasiStore.bikes(this);
        aktif = GarasiStore.aktif(this);

        if (daftar.isEmpty()) {
            isi.addView(kartuKosong());
        } else {
            isi.addView(pemilihSepeda(daftar));
            GarasiStore.Ringkas r = GarasiStore.ringkas(this, aktif);
            odoM = r.odoM;
            isi.addView(kartuOdometer(r));

            isi.addView(judulSeksi(R.string.garage_maintenance));
            List<Komponen> komponen = GarasiStore.components(this, aktif.id);
            if (komponen.isEmpty()) {
                isi.addView(kartuTanpaKomponen());
            } else {
                for (Komponen k : komponen) isi.addView(kartuKomponen(k));
            }
            isi.addView(tombolTambahKomponen());
        }

        Ui.fonts(isi, this);
        Ui.buttonFonts(isi, this);
    }

    private View kartuKosong() {
        LinearLayout c = kartu();
        ImageView ikon = new ImageView(this);
        ikon.setImageResource(R.drawable.ic_garage);
        ikon.setColorFilter(getResources().getColor(R.color.label));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
        ikon.setLayoutParams(ilp);
        c.addView(ikon);

        TextView judul = new TextView(this);
        judul.setText(R.string.garage_empty_title);
        judul.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body_m));
        judul.setTextColor(getResources().getColor(R.color.value));
        judul.setPadding(0, dp(10), 0, 0);
        judul.setIncludeFontPadding(false);
        c.addView(judul);

        TextView pesan = new TextView(this);
        pesan.setText(R.string.garage_empty_msg);
        pesan.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body_s));
        pesan.setTextColor(getResources().getColor(R.color.label));
        pesan.setPadding(0, dp(4), 0, 0);
        pesan.setIncludeFontPadding(false);
        c.addView(pesan);

        Button tambah = tombol(R.string.garage_add_bike, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(12);
        tambah.setLayoutParams(lp);
        tambah.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialogSepeda(); }
        });
        c.addView(tambah);
        return c;
    }

    private View pemilihSepeda(List<Bike> daftar) {
        HorizontalScrollView gulir = new HorizontalScrollView(this);
        gulir.setHorizontalScrollBarEnabled(false);
        LinearLayout baris = new LinearLayout(this);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        gulir.addView(baris);

        for (final Bike b : daftar) {
            baris.addView(chip(b));
        }

        TextView tambah = chipKosong("+");
        tambah.setContentDescription(getString(R.string.garage_add_bike));
        tambah.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialogSepeda(); }
        });
        baris.addView(tambah);
        return gulir;
    }

    private TextView chip(final Bike b) {
        final boolean on = aktif != null && b.id == aktif.id;
        TextView chip = chipKosong(b.label());
        chip.setTextColor(getResources().getColor(on ? R.color.on_accent : R.color.value));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(22));
        bg.setColor(getResources().getColor(on ? R.color.accent : R.color.card2));
        bg.setStroke(dp(1), getResources().getColor(on ? R.color.accent : R.color.stroke_ctrl));
        chip.setBackground(bg);
        chip.setContentDescription(b.label());
        chip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (on) return;
                GarasiStore.pilihBike(GarageActivity.this, b.id);
                paint();
            }
        });
        chip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                konfirmasiHapusSepeda(b);
                return true;
            }
        });
        return chip;
    }

    private TextView chipKosong(String teks) {
        TextView chip = new TextView(this);
        chip.setText(teks);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        chip.setTextColor(getResources().getColor(R.color.value));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(22));
        bg.setColor(getResources().getColor(R.color.card2));
        bg.setStroke(dp(1), getResources().getColor(R.color.stroke_ctrl));
        chip.setBackground(bg);
        chip.setMinHeight(dp(48));
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        lp.rightMargin = dp(6);
        chip.setLayoutParams(lp);
        return chip;
    }

    private View kartuOdometer(GarasiStore.Ringkas r) {
        LinearLayout c = kartu();
        c.addView(labelKecil(R.string.garage_odometer));

        LinearLayout angka = new LinearLayout(this);
        angka.setOrientation(LinearLayout.HORIZONTAL);
        angka.setGravity(Gravity.BOTTOM);
        angka.setPadding(0, dp(2), 0, 0);

        // AutoFitText: 72sp sesuai rancangan, tapi odometer enam digit pada
        // layar 320dp dengan font 1,3x tidak muat. Angka mengecil seperlunya
        // alih-alih terpotong.
        AutoFitText nilai = new AutoFitText(this);
        nilai.setText(Fmt.odoKm(r.odoM));
        nilai.setTag("num");
        nilai.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_value_odo));
        nilai.setTextColor(getResources().getColor(R.color.value));
        nilai.setIncludeFontPadding(false);
        nilai.setMaxLines(1);
        angka.addView(nilai, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView satuan = new TextView(this);
        satuan.setText(R.string.unit_km);
        satuan.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        satuan.setTextColor(getResources().getColor(R.color.label));
        satuan.setPadding(dp(6), 0, 0, dp(10));
        satuan.setIncludeFontPadding(false);
        angka.addView(satuan);
        c.addView(angka);

        TextView stats = new TextView(this);
        stats.setText(getString(R.string.garage_stats,
                Fmt.duration(r.movingMs), r.gowes));
        stats.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body_s));
        stats.setTextColor(getResources().getColor(R.color.label));
        stats.setPadding(0, dp(4), 0, 0);
        stats.setIncludeFontPadding(false);
        c.addView(stats);
        return c;
    }

    private View kartuKomponen(final Komponen k) {
        LinearLayout c = kartu();
        int status = k.status(odoM);

        LinearLayout kepala = new LinearLayout(this);
        kepala.setOrientation(LinearLayout.HORIZONTAL);
        kepala.setGravity(Gravity.CENTER_VERTICAL);

        TextView nama = new TextView(this);
        nama.setText(k.name);
        nama.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body_m));
        nama.setTextColor(getResources().getColor(R.color.value));
        nama.setIncludeFontPadding(false);
        kepala.addView(nama, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView statusView = new TextView(this);
        statusView.setText(statusTeks(k, status));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        statusView.setTextColor(getResources().getColor(warnaStatus(status)));
        statusView.setIncludeFontPadding(false);
        statusView.setGravity(Gravity.END);
        kepala.addView(statusView);
        c.addView(kepala);

        TextView terpakai = new TextView(this);
        terpakai.setText(getString(R.string.garage_comp_used,
                Fmt.odoKm(k.terpakaiM(odoM)), Fmt.odoKm(k.limitM)));
        terpakai.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_caption));
        terpakai.setTextColor(getResources().getColor(R.color.label));
        terpakai.setPadding(0, dp(6), 0, 0);
        terpakai.setIncludeFontPadding(false);
        c.addView(terpakai);

        BarProgres bar = new BarProgres(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        blp.topMargin = dp(10);
        bar.setLayoutParams(blp);
        float frac = k.limitM > 0 ? (float) (k.terpakaiM(odoM) / k.limitM) : 0f;
        bar.set(frac, getResources().getColor(warnaStatus(status)));
        c.addView(bar);

        // Seluruh kartu menerima tekan-panjang: tidak ada tombol hapus yang
        // memakan ruang, dan menghapus komponen bukan aksi sehari-hari.
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                konfirmasiHapusKomponen(k);
                return true;
            }
        });
        c.setContentDescription(k.name + ", " + statusTeks(k, status));
        return c;
    }

    private View kartuTanpaKomponen() {
        LinearLayout c = kartu();
        TextView t = new TextView(this);
        t.setText(R.string.garage_no_components);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body_s));
        t.setTextColor(getResources().getColor(R.color.label));
        t.setIncludeFontPadding(false);
        c.addView(t);
        return c;
    }

    private View tombolTambahKomponen() {
        Button b = tombol(R.string.garage_add_component, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialogKomponen(); }
        });
        return b;
    }

    private int warnaStatus(int status) {
        return status == Komponen.LEWAT ? R.color.danger
                : status == Komponen.SEGERA ? R.color.warn : R.color.rec;
    }

    /** Teks status selalu ada, bukan hanya warna - aturan aksesibilitas 8. */
    private String statusTeks(Komponen k, int status) {
        if (status == Komponen.LEWAT) {
            return getString(R.string.garage_status_over, Fmt.odoKm(k.lebihM(odoM)));
        }
        String dasar = getString(status == Komponen.SEGERA
                ? R.string.garage_status_soon : R.string.garage_status_ok);
        return getString(R.string.garage_status_pct, dasar, k.persen(odoM));
    }

    // ---------- tambah sepeda ----------

    private void dialogSepeda() {
        LinearLayout kotak = new LinearLayout(this);
        kotak.setOrientation(LinearLayout.VERTICAL);
        kotak.setPadding(dp(16), dp(4), dp(16), 0);

        final EditText nama = input(R.string.garage_bike_name, InputType.TYPE_CLASS_TEXT);
        final EditText jenis = input(R.string.garage_bike_type, InputType.TYPE_CLASS_TEXT);
        final EditText offset = input(R.string.garage_bike_offset,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        offset.setText("0");
        kotak.addView(nama);
        kotak.addView(jenis);
        kotak.addView(offset);

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(R.string.garage_add_bike)
                .setView(kotak)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface x) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                String n = nama.getText().toString().trim();
                                if (n.isEmpty()) { toast(R.string.garage_invalid_name); return; }
                                double off = km(offset.getText().toString());
                                if (off < 0) off = 0;
                                long id = GarasiStore.addBike(GarageActivity.this, n,
                                        jenis.getText().toString(), off);
                                if (id <= 0) { toast(R.string.garage_invalid_name); return; }
                                d.dismiss();
                                paint();
                            }
                        });
            }
        });
        d.show();
    }

    // ---------- tambah komponen ----------

    private void dialogKomponen() {
        if (aktif == null) return;
        LinearLayout kotak = new LinearLayout(this);
        kotak.setOrientation(LinearLayout.VERTICAL);
        kotak.setPadding(dp(16), dp(4), dp(16), 0);

        final EditText nama = input(R.string.garage_comp_name, InputType.TYPE_CLASS_TEXT);
        final EditText batas = input(R.string.garage_comp_limit,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText mulai = input(R.string.garage_comp_start,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mulai.setText(String.format(Locale.US, "%.0f", odoM / 1000.0));
        kotak.addView(nama);
        kotak.addView(batas);
        kotak.addView(mulai);

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(R.string.garage_add_component)
                .setView(kotak)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface x) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                String n = nama.getText().toString().trim();
                                if (n.isEmpty()) { toast(R.string.garage_invalid_name); return; }
                                double limit = km(batas.getText().toString());
                                if (limit <= 0) { toast(R.string.garage_invalid_limit); return; }
                                double start = km(mulai.getText().toString());
                                if (start < 0) start = 0;
                                long id = GarasiStore.addComponent(GarageActivity.this,
                                        aktif.id, n, limit, start);
                                if (id <= 0) { toast(R.string.garage_invalid_limit); return; }
                                d.dismiss();
                                paint();
                            }
                        });
            }
        });
        d.show();
    }

    private EditText input(int hint, int tipe) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(tipe);
        e.setSingleLine(true);
        e.setPadding(0, dp(10), 0, dp(10));
        Ui.fonts(e, this);
        return e;
    }

    /** "12,5" atau "12.5" -> meter; -1 kalau kosong/rusak. */
    private static double km(String teks) {
        try {
            double v = Double.parseDouble(teks.trim().replace(',', '.'));
            return Double.isNaN(v) || v < 0 ? -1 : v * 1000.0;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------- hapus ----------

    private void konfirmasiHapusSepeda(final Bike b) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.garage_delete_bike)
                .setMessage(R.string.garage_delete_bike_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        GarasiStore.deleteBike(GarageActivity.this, b.id);
                        paint();
                    }
                })
                .show();
    }

    private void konfirmasiHapusKomponen(final Komponen k) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.garage_delete_comp)
                .setMessage(R.string.garage_delete_comp_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        GarasiStore.deleteComponent(GarageActivity.this, k.id);
                        paint();
                    }
                })
                .show();
    }

    // ---------- komponen kecil ----------

    private LinearLayout kartu() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView judulSeksi(int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_label));
        t.setTextColor(getResources().getColor(R.color.label));
        t.setLetterSpacing(0.2f);
        t.setPadding(dp(4), dp(6), 0, dp(8));
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView labelKecil(int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_label));
        t.setTextColor(getResources().getColor(R.color.label));
        t.setLetterSpacing(0.2f);
        t.setIncludeFontPadding(false);
        return t;
    }

    private Button tombol(int res, boolean utama) {
        Button b = new Button(this);
        b.setText(res);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_body));
        b.setTextColor(getResources().getColor(utama ? R.color.on_accent : R.color.value));
        b.setBackgroundResource(utama ? R.drawable.bg_btn_primary : R.drawable.bg_btn_secondary);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setStateListAnimator(null);
        return b;
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
