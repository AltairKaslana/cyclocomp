package id.cyclocomp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Layar Bagikan - pola yang dipakai Strava: gambar kartu tampil besar,
 * digeser kiri-kanan untuk memilih varian, dan aksi (simpan ke galeri,
 * bagikan gambar, bagikan GPX) diletakkan tepat di bawah gambar.
 */
public class ShareActivity extends Activity {

    static final String EXTRA_ID = SummaryActivity.EXTRA_ID;
    private static final int REQ_WRITE_STORAGE = 78;
    private static final int PAGE_COUNT = 8;

    private RideDb.Row row;
    private CardPager pager;
    private TextView tvCardName;
    private TextView tvGenerating;
    private LinearLayout dots;
    private LinearLayout pagerContent;
    /** Titik GPX yang sudah dibaca sekali; dipakai ulang tiap ganti halaman. */
    private List<GpxReader.P> trackCache = java.util.Collections.emptyList();
    /** Halaman yang sedang diminta; dipakai membuang hasil render yang telat. */
    private int halamanAktif = 0;
    /** Nomor urut permintaan muat; yang telat dibatalkan. */
    private int muatId = 0;
    private ImageView[] imgs = new ImageView[PAGE_COUNT];
    private View[] dotViews = new View[PAGE_COUNT];
    private Bitmap[] cards = new Bitmap[PAGE_COUNT];
    private int[] labelIds = {
            R.string.card_variant_map,           // 0: 4:5 Opak
            R.string.card_variant_overlay,       // 1: 4:5 Transparan (overlay)
            R.string.card_variant_story,         // 2: Story Opak
            R.string.card_variant_story_overlay, // 3: Story Transparan (overlay)
            R.string.card_variant_sticker,       // 4: Stempel Transparan
            R.string.card_variant_receipt,       // 5: Struk
            R.string.card_variant_capsules,      // 6: Kapsul
            R.string.card_variant_wallpaper,     // 7: Polyline besar (latar HP)
    };
    /**
     * Rasio lebar:tinggi tiap kartu; 0 = tanpa bingkai.
     *
     * T-15: bingkai hanya pada kartu 4:5, sesuai spesifikasi. Kartu Story dan
     * Kapsul (9:16) sudah hampir mengisi tinggi pager, jadi bingkai di sana
     * cuma menambah garis di tepi yang tidak memberi informasi baru.
     */
    private static final float[] RASIO = {
            4f / 5f,    // 0: 4:5 Opak
            4f / 5f,    // 1: 4:5 Transparan (overlay)
            0f,         // 2: Story Opak
            0f,         // 3: Story Transparan (overlay)
            4f / 5f,    // 4: Stempel Transparan
            4f / 5f,    // 5: Struk
            0f,         // 6: Kapsul
            0f,         // 7: Polyline besar (latar HP)
    };

    /**
     * Kartu yang punya area transparan, jadi butuh pola kotak-kotak di belakang
     * pratinjaunya. T-15.
     *
     * Sengaja TIDAK dipasang di kartu opak: kotak-kotaknya akan muncul di ruang
     * sisa di sekeliling kartu dan menyiratkan transparansi yang tidak ada.
     */
    private static final boolean[] BERKOTAK = {
            false,      // 0: 4:5 Opak
            true,       // 1: 4:5 Transparan (overlay)
            false,      // 2: Story Opak
            true,       // 3: Story Transparan (overlay)
            true,       // 4: Stempel Transparan
            false,      // 5: Struk
            false,      // 6: Kapsul
            false,      // 7: Polyline besar (latar HP)
    };
    private int pendingSavePage = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        row = id > 0 ? new RideDb(this).byId(id) : null;
        if (row == null) {
            Toast.makeText(this, R.string.gpx_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_share);

        pager = (CardPager) findViewById(R.id.pager);
        // T-17: pager kartu adalah wilayah geser tanpa teks. Tanpa deskripsi,
        // pembaca layar tidak memberi tahu pengguna bahwa ada delapan kartu yang
        // bisa dijangkau dengan menggeser - ia hanya melihat satu gambar.
        pager.setContentDescription(getString(R.string.share_pager_desc, PAGE_COUNT));
        pagerContent = (LinearLayout) findViewById(R.id.pagerContent);
        tvCardName = (TextView) findViewById(R.id.tvCardName);
        tvGenerating = (TextView) findViewById(R.id.tvGenerating);
        dots = (LinearLayout) findViewById(R.id.dots);

        // Inisialisasi ImageView secara dinamis
        for (int i = 0; i < PAGE_COUNT; i++) {
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // T-15: latar pratinjau.
            //
            // Kartu transparan (overlay 4:5, overlay Story, stempel) sebelumnya
            // ditampilkan di atas latar hitam pekat aplikasi, jadi tepinya tidak
            // terlihat - pengguna mengira kartunya berlatar hitam, padahal yang
            // tersimpan adalah PNG transparan. Kartu yang ditempel ke foto jadi
            // terkejut besar kecilnya.
            //
            // Sekarang kartu transparan berdiri di atas pola kotak-kotak abu
            // (konvensi "di sini tidak ada warna"), dan kartu 4:5 dapat bingkai
            // rasio supaya batasnya terlihat sebelum disimpan.
            if (RASIO[i] > 0f || BERKOTAK[i]) {
                int sel = getResources().getDimensionPixelSize(R.dimen.checker_cell);
                iv.setBackground(new CardBackdrop(RASIO[i], BERKOTAK[i], sel,
                        getResources().getColor(R.color.card),
                        getResources().getColor(R.color.card_disabled),
                        getResources().getColor(R.color.divider)));
            }
            pagerContent.addView(iv);
            imgs[i] = iv;
        }

        findViewById(R.id.btnShareClose).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnCardSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { simpanKartu(); }
        });
        findViewById(R.id.btnCardShare).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { bagikanKartu(); }
        });
        findViewById(R.id.btnCardCopy).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { salinKartu(); }
        });
        findViewById(R.id.btnCardGpx).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { bagikanGpx(); }
        });

        buatTitik();
        pager.setOnPageSelectedListener(new CardPager.OnPageSelectedListener() {
            @Override public void onPageSelected(int pos) {
                perbaruiTitik();
                muatHalaman(pos);
            }
        });

        bangunKartu();

        Ui.fonts(getWindow().getDecorView(), this);
        Ui.buttonFonts(getWindow().getDecorView(), this);
    }

    private void buatTitik() {
        dots.removeAllViews();
        int d = getResources().getDimensionPixelSize(R.dimen.dot_page);
        int gap = getResources().getDimensionPixelSize(R.dimen.dot_page_gap);
        for (int i = 0; i < PAGE_COUNT; i++) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d, d);
            lp.setMargins(gap, 0, gap, 0);
            v.setLayoutParams(lp);
            // T-17: titik halaman tidak punya teks sama sekali, jadi pembaca
            // layar membacanya sebagai "tombol" tanpa nama - atau melewatkannya
            // karena dianggap hiasan. Padahal di layar Bagikan titik itu SATU-
            // SATUNYA penanda kartu mana yang sedang dibuka.
            //
            // Deskripsinya menyebut posisi DAN nama varian, supaya pengguna
            // tunanetra tahu ada berapa kartu dan sedang di yang mana tanpa
            // harus menggeser ke semua halaman dulu.
            v.setContentDescription(getString(R.string.card_page_fmt, i + 1, PAGE_COUNT,
                    getString(labelIds[i])));
            dots.addView(v);
            dotViews[i] = v;
        }
        perbaruiTitik();
    }

    private void perbaruiTitik() {
        int pos = pager.getCurrentPage();
        if (pos < 0 || pos >= PAGE_COUNT) return;
        int on = getColor(R.color.accent);
        int off = getColor(R.color.dot_off);
        for (int i = 0; i < PAGE_COUNT; i++) {
            dotViews[i].setBackgroundColor(i == pos ? on : off);
        }
        tvCardName.setText(labelIds[pos]);
    }

    private void bangunKartu() {
        new Thread(new Runnable() {
            @Override public void run() {
                List<GpxReader.P> track = null;
                try {
                    track = GpxReader.read(GpxStore.materialize(ShareActivity.this, row.gpxPath));
                } catch (Throwable t) {
                    track = java.util.Collections.emptyList();
                }
                trackCache = track;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        tvGenerating.setVisibility(View.GONE);
                        muatHalaman(pager.getCurrentPage());
                    }
                });
            }
        }).start();
    }

    private void muatHalaman(final int page) {
        if (page < 0 || page >= PAGE_COUNT) return;
        halamanAktif = page;
        final int req = ++muatId;
        if (cards[page] != null) {
            imgs[page].setImageBitmap(cards[page]);
            return;
        }
        tvGenerating.setVisibility(View.VISIBLE);

        for (int i = 0; i < PAGE_COUNT; i++) {
            if (i != page) imgs[i].setImageDrawable(null);
        }

        new Thread(new Runnable() {
            @Override public void run() {
                if (req != muatId) return;
                for (int i = 0; i < PAGE_COUNT; i++) {
                    if (i != page && cards[i] != null) {
                        if (!cards[i].isRecycled()) cards[i].recycle();
                        cards[i] = null;
                    }
                }
                final Bitmap b = bitmapUntuk(page);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        tvGenerating.setVisibility(View.GONE);
                        if (req != muatId || page != halamanAktif) {
                            if (b != null && !b.isRecycled()) b.recycle();
                            return;
                        }
                        if (b == null) {
                            imgs[page].setImageDrawable(null);
                            Toast.makeText(ShareActivity.this, R.string.card_failed,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        cards[page] = b;
                        imgs[page].setImageBitmap(b);
                    }
                });
            }
        }).start();
    }

    private Bitmap bitmapUntuk(int page) {
        try {
            switch (page) {
                case 0: return ShareCard.cardBitmap(this, row, trackCache, false);
                case 1: return ShareCard.cardBitmap(this, row, trackCache, true);
                case 2: return ShareCard.storyBitmap(this, row, false);
                case 3: return ShareCard.storyBitmap(this, row, true);
                case 4: return ShareCard.stickerBitmap(this, row, trackCache);
                case 5: return ShareCard.receiptBitmap(this, row, trackCache);
                case 6: return ShareCard.capsulesBitmap(this, row, trackCache);
                case 7: return ShareCard.wallpaperBitmap(this, row, trackCache);
                default: return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private String namaKartu(int page) {
        String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
        if (gpxName == null) gpxName = "ride";
        int dot = gpxName.lastIndexOf('.');
        String base = dot > 0 ? gpxName.substring(0, dot) : gpxName;
        switch (page) {
            case 0:  return base + "_card.png";
            case 1:  return base + "_overlay.png";
            case 2:  return base + "_story.png";
            case 3:  return base + "_story_overlay.png";
            case 4:  return base + "_sticker.png";
            case 5:  return base + "_receipt.png";
            case 6:  return base + "_capsules.png";
            case 7:  return base + "_wallpaper.png";
            default: return base + "_card.png";
        }
    }

    private Bitmap bitmapSiap(int page) {
        if (page < 0 || page >= PAGE_COUNT) return null;
        if (cards[page] == null || cards[page].isRecycled()) {
            cards[page] = bitmapUntuk(page);
        }
        return cards[page];
    }

    private void simpanKartu() {
        final int page = pager.getCurrentPage();
        if (page < 0 || page >= PAGE_COUNT) return;
        if (Build.VERSION.SDK_INT < 29) {
            int granted = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (granted != PackageManager.PERMISSION_GRANTED) {
                pendingSavePage = page;
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE_STORAGE);
                return;
            }
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = bitmapSiap(page);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (bmp == null) {
                            Toast.makeText(ShareActivity.this, R.string.card_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        tulisGaleri(bmp, page);
                    }
                });
            }
        }).start();
    }

    private String pesanGagal(int resId) {
        String why = ShareCard.lastErrorReason;
        return getString(resId) + ((why == null || why.length() == 0) ? "" : "\n" + why);
    }

    private void tulisGaleri(Bitmap bmp, int page) {
        Uri uri = ShareCard.writePngToGallery(this, bmp, namaKartu(page));
        Toast.makeText(this, uri != null ? getString(R.string.save_done)
                        : pesanGagal(R.string.save_failed_gallery),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_WRITE_STORAGE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingSavePage >= 0) {
            final int pg = pendingSavePage;
            new Thread(new Runnable() {
                @Override public void run() {
                    final Bitmap bmp = bitmapSiap(pg);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (bmp != null) tulisGaleri(bmp, pg);
                        }
                    });
                }
            }).start();
        } else {
            Toast.makeText(this, R.string.save_need_permission, Toast.LENGTH_LONG).show();
        }
        pendingSavePage = -1;
    }

    private void bagikanKartu() {
        final int page = pager.getCurrentPage();
        if (page < 0 || page >= PAGE_COUNT) return;
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = bitmapSiap(page);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (bmp == null) {
                            Toast.makeText(ShareActivity.this, R.string.card_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        File png = ShareCard.writePngForShare(ShareActivity.this, bmp, namaKartu(page));
                        if (png == null) {
                            Toast.makeText(ShareActivity.this,
                                    pesanGagal(R.string.card_failed), Toast.LENGTH_LONG).show();
                            return;
                        }
                        Uri uri = Uri.parse("content://" + RideProvider.AUTHORITY + "/" + png.getName());
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("image/png");
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, png.getName()));
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            startActivity(Intent.createChooser(i, getString(R.string.share_title)));
                        } catch (Exception e) {
                            Toast.makeText(ShareActivity.this, R.string.no_share_app,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void salinKartu() {
        final int page = pager.getCurrentPage();
        if (page < 0 || page >= PAGE_COUNT) return;
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = bitmapSiap(page);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (bmp == null) {
                            Toast.makeText(ShareActivity.this, R.string.copy_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        File png = ShareCard.writePngForShare(ShareActivity.this, bmp, namaKartu(page));
                        if (png == null) {
                            Toast.makeText(ShareActivity.this,
                                    pesanGagal(R.string.copy_failed), Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                    getSystemService(CLIPBOARD_SERVICE);
                            if (cm == null) {
                                Toast.makeText(ShareActivity.this, R.string.copy_failed,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            Uri uri = Uri.parse("content://" + RideProvider.AUTHORITY + "/" + png.getName());
                            android.content.ClipData clip = android.content.ClipData.newUri(
                                    getContentResolver(), "Zylo", uri);
                            cm.setPrimaryClip(clip);
                            if (Build.VERSION.SDK_INT <= 32) {
                                Toast.makeText(ShareActivity.this, R.string.copy_done,
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (Throwable t) {
                            Toast.makeText(ShareActivity.this, R.string.copy_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void bagikanGpx() {
        if (row.gpxPath == null) return;
        if (!GpxStore.exists(this, row.gpxPath)) {
            Toast.makeText(this, R.string.gpx_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        String nama = GpxStore.displayName(row.gpxPath);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/gpx+xml");
        i.putExtra(Intent.EXTRA_STREAM, GpxStore.shareUri(this, row.gpxPath));
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, nama));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_title)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < PAGE_COUNT; i++) {
            if (imgs[i] != null) imgs[i].setImageDrawable(null);
            if (cards[i] != null && !cards[i].isRecycled()) cards[i].recycle();
            cards[i] = null;
        }
    }
}
