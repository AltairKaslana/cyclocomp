package id.cyclocomp;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Atur layar (rancangan UI/UX 5.3): pilih bidang apa yang tampil di SEKILAS,
 * DETAIL, dan LAP.
 *
 * Cara kerjanya sengaja sesederhana mungkin: tiap halaman punya sejumlah slot
 * tetap (3, 10, 4). Ketuk slot -> terpilih (garis aksen 2dp). Ketuk chip di
 * panel bawah -> slot itu berisi. SIMPAN menulis susunannya ke pengaturan.
 *
 * Pratinjau memakai {@link BidangNilai} yang SAMA dengan dashboard, jadi apa
 * yang dilihat di sini (termasuk tanda "-" untuk sensor yang belum ada) persis
 * yang akan muncul di dashboard. Kalau pratinjau memakai kode sendirinya, dua
 * tempat bisa berbeda pendapat dan itulah bug yang paling sulit dilacak.
 *
 * Tidak ada konfirmasi saat keluar tanpa menyimpan: perubahan yang belum
 * disimpan memang harus hilang, dan dialog "yakin?" untuk itu hanya menambah
 * satu ketukan. Yang jelas: tombol SIMPAN selalu terlihat.
 */
public class AturLayarActivity extends Activity {

    private LinearLayout preview;
    private LinearLayout chips;
    private TextView hint;

    private int halaman = Bidang.SEKILAS;
    /**
     * Susunan KETIGA halaman, bukan hanya yang sedang dibuka. Perubahan
     * di satu tab tetap ada saat berpindah tab, dan SIMPAN menulis semuanya
     * sekaligus - satu tombol simpan yang hanya menyimpan sebagian layar
     * adalah janji yang tidak ditepati.
     */
    private final int[][] urutanHalaman = new int[Bidang.HALAMAN_JUMLAH][];
    /** Selalu menunjuk urutanHalaman[halaman]; disingkat karena dipakai di banyak tempat. */
    private int[] urutan;
    private int slotTerpilih = -1;
    private boolean berubah = false;

    private final List<View> slotViews = new ArrayList<View>();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Tema.bungkus(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tema.pasangPada(this);   // palet benar SEBELUM layout dibaca
        setContentView(R.layout.activity_atur_layar);

        preview = (LinearLayout) findViewById(R.id.preview);
        chips = (LinearLayout) findViewById(R.id.chips);
        hint = (TextView) findViewById(R.id.tvHint);

        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        ((Button) findViewById(R.id.tabGlance)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pilihHalaman(Bidang.SEKILAS); }
        });
        ((Button) findViewById(R.id.tabDetail)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pilihHalaman(Bidang.DETAIL); }
        });
        ((Button) findViewById(R.id.tabLap)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pilihHalaman(Bidang.LAP); }
        });
        ((Button) findViewById(R.id.btnReset)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // ATUR ULANG hanya mengembalikan halaman yang sedang dibuka,
                // bukan ketiganya: orang datang ke sini untuk memperbaiki satu
                // halaman, dan mereset yang tidak diminta justru mengejutkan.
                //
                // Hasilnya hanya DIPASANG di layar, belum ditulis: atur ulang
                // adalah perubahan seperti perubahan lain, jadi ia baru berlaku
                // saat SIMPAN ditekan - dan bisa dibatalkan dengan keluar.
                urutanHalaman[halaman] = Bidang.bawaan(halaman);
                urutan = urutanHalaman[halaman];
                slotTerpilih = -1;
                berubah = true;
                gambar();
            }
        });
        ((Button) findViewById(R.id.btnSave)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // Ketiga halaman disimpan, bukan hanya tab yang terlihat.
                for (int h = 0; h < Bidang.HALAMAN_JUMLAH; h++) {
                    Settings.setPageFields(AturLayarActivity.this, h, urutanHalaman[h]);
                }
                berubah = false;
                gambar();   // tombol SIMPAN kembali mati
                android.widget.Toast.makeText(AturLayarActivity.this,
                        R.string.atur_saved, android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        for (int h = 0; h < Bidang.HALAMAN_JUMLAH; h++) {
            urutanHalaman[h] = Settings.pageFields(this, h);
        }
        urutan = urutanHalaman[halaman];
        gambar();
    }

    // ---------- menggambar ----------

    private void pilihHalaman(int h) {
        if (h == halaman) return;
        halaman = h;
        // Yang ditampilkan adalah susunan di layar, bukan yang tersimpan:
        // perubahan di tab sebelumnya tidak boleh hilang hanya karena tab
        // lain dibuka. Semuanya baru ditulis oleh SIMPAN.
        urutan = urutanHalaman[halaman];
        slotTerpilih = -1;
        gambar();
    }

    private void gambar() {
        preview.removeAllViews();
        slotViews.clear();
        tandaiTab();
        hint.setText(urutan.length + " " + getString(R.string.atur_slot_count) + " · "
                + getString(R.string.atur_hint));

        // SEKILAS: satu baris 3 slot. LAP: 2 kolom. DETAIL: 2 kolom.
        int kolom = halaman == Bidang.SEKILAS ? 1 : 2;
        LinearLayout baris = null;
        for (int i = 0; i < urutan.length; i++) {
            if (baris == null) {
                baris = new LinearLayout(this);
                baris.setOrientation(LinearLayout.HORIZONTAL);
                baris.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                preview.addView(baris);
            }
            baris.addView(buatSlot(i));
            if (baris.getChildCount() >= kolom) baris = null;
        }
        // Font bundel diterapkan setelah semua view ada: satu kali jalan untuk
        // seluruh pratinjau, sama seperti MainActivity melakukannya di onCreate.
        Ui.fonts(preview, this);
        Ui.buttonFonts(preview, this);
        gambarChips();
        Ui.fonts(chips, this);
        // SIMPAN mati kalau tidak ada perubahan: tombol yang selalu aktif
        // membuat orang menekannya dan bertanya-tanya apakah sesuatu terjadi.
        findViewById(R.id.btnSave).setEnabled(berubah);
    }

    private void tandaiTab() {
        // Tab aktif memakai pil AKSEN, bukan card2: dengan latar card2 dan
        // teks on_accent, teks putih di atas abu-abu muda hanya 1,2:1 -
        // praktis tak terbaca di tema siang. Aksen + on_accent tetap
        // berkontras tinggi di kedua tema.
        int aktif = R.drawable.bg_btn_primary;
        int pasif = R.drawable.bg_btn_secondary;
        ((Button) findViewById(R.id.tabGlance)).setBackgroundResource(
                halaman == Bidang.SEKILAS ? aktif : pasif);
        ((Button) findViewById(R.id.tabDetail)).setBackgroundResource(
                halaman == Bidang.DETAIL ? aktif : pasif);
        ((Button) findViewById(R.id.tabLap)).setBackgroundResource(
                halaman == Bidang.LAP ? aktif : pasif);
        ((Button) findViewById(R.id.tabGlance)).setTextColor(getResources().getColor(
                halaman == Bidang.SEKILAS ? R.color.on_accent : R.color.label));
        ((Button) findViewById(R.id.tabDetail)).setTextColor(getResources().getColor(
                halaman == Bidang.DETAIL ? R.color.on_accent : R.color.label));
        ((Button) findViewById(R.id.tabLap)).setTextColor(getResources().getColor(
                halaman == Bidang.LAP ? R.color.on_accent : R.color.label));
    }

    /** Satu slot: kartu berisi label, nilai contoh, dan satuan. */
    private View buatSlot(final int index) {
        int f = urutan[index];
        LinearLayout kartu = new LinearLayout(this);
        kartu.setOrientation(LinearLayout.VERTICAL);
        kartu.setGravity(Gravity.CENTER);
        kartu.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        kartu.setLayoutParams(lp);

        TextView label = new TextView(this);
        label.setText(Bidang.label(f));
        label.setTextColor(getResources().getColor(R.color.label));
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_label));
        label.setGravity(Gravity.CENTER);
        label.setLetterSpacing(0.14f);
        kartu.addView(label);

        TextView nilai = new TextView(this);
        nilai.setText(BidangNilai.contoh(this, f));
        nilai.setTextColor(getResources().getColor(R.color.value));
        nilai.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_value_s));
        nilai.setGravity(Gravity.CENTER);
        // tag "num" = angka memakai Barlow Condensed (konvensi Ui.fonts)
        nilai.setTag("num");
        kartu.addView(nilai);

        int unit = Bidang.unit(f);
        if (unit != 0) {
            TextView u = new TextView(this);
            u.setText(unit);
            u.setTextColor(getResources().getColor(R.color.label));
            u.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.text_caption));
            u.setGravity(Gravity.CENTER);
            kartu.addView(u);
        }

        // Garis tepi: aksen 2dp kalau terpilih, pemisah kalau tidak. Dua
        // keadaan yang harus bisa dibedakan tanpa mengandalkan warna saja -
        // slot terpilih juga diberi contentDescription.
        kartu.setBackground(latarSlot(index == slotTerpilih));
        kartu.setContentDescription(getString(R.string.atur_slot_desc,
                index + 1, getString(Bidang.label(f)),
                getString(index == slotTerpilih ? R.string.atur_selected : R.string.atur_not_selected)));
        kartu.setFocusable(true);
        kartu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                slotTerpilih = index;
                gambar();
            }
        });
        slotViews.add(kartu);
        return kartu;
    }

    private GradientDrawable latarSlot(boolean terpilih) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(16));
        d.setColor(getResources().getColor(R.color.card));
        d.setStroke(dp(terpilih ? 2 : 1),
                getResources().getColor(terpilih ? R.color.accent : R.color.divider));
        return d;
    }

    private void gambarChips() {
        chips.removeAllViews();
        for (int f = 0; f < Bidang.JUMLAH; f++) {
            final int field = f;
            TextView chip = new TextView(this);
            chip.setText(Bidang.label(f));
            // Chip adalah daftar pilihan, bukan tombol aksen: latar card2
            // dengan teks value. accent_soft + on_accent membuat teks putih
            // di atas biru muda (siang) hanya ~1,2:1.
            chip.setTextColor(getResources().getColor(R.color.value));
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.text_caption));
            chip.setPadding(dp(14), dp(10), dp(14), dp(10));
            chip.setLetterSpacing(0.08f);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(22));
            bg.setColor(getResources().getColor(R.color.card2));
            bg.setStroke(dp(1), getResources().getColor(R.color.stroke_ctrl));
            chip.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            chip.setLayoutParams(lp);
            chip.setContentDescription(getString(Bidang.label(f)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // Belum ada slot terpilih: pilih slot pertama supaya ketukan
                    // chip tetap melakukan sesuatu yang berguna, bukan buntu.
                    if (slotTerpilih < 0) slotTerpilih = 0;
                    urutan[slotTerpilih] = field;
                    berubah = true;
                    // Maju ke slot berikutnya: mengisi lima slot berarti lima
                    // ketukan, bukan lima kali memilih slot lalu chip.
                    if (slotTerpilih < urutan.length - 1) slotTerpilih++;
                    gambar();
                }
            });
            chips.addView(chip);
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
