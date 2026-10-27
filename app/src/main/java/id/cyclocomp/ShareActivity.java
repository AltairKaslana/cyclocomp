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
    private static final int PAGE_COUNT = 5;

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
    }

    private void buatTitik() {
        dots.removeAllViews();
        float d = 8f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < PAGE_COUNT; i++) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Math.round(d), Math.round(d));
            lp.setMargins(Math.round(6f * d / 8f), 0, Math.round(6f * d / 8f), 0);
            v.setLayoutParams(lp);
            dots.addView(v);
            dotViews[i] = v;
        }
        perbaruiTitik();
    }

    private void perbaruiTitik() {
        int pos = pager.getCurrentPage();
        if (pos < 0 || pos >= PAGE_COUNT) return;
        for (int i = 0; i < PAGE_COUNT; i++) {
            dotViews[i].setBackgroundColor(i == pos ? 0xFFFC4C02 : 0xFF3A4048);
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

    /**
     * Muat bitmap untuk satu halaman saja.
     *
     * Kenapa tidak dibangun semua sekaligus: lima bitmap 1080x1350/1920
     * habiskan ~34 MB. Di HP ber-RAM kecil, bitmap terakhir gagal dialokasi
     * (OutOfMemoryError) sehingga slide tampil kosong dan tombol simpan
     * tidak punya apa-apa untuk ditulis. Karena itu hanya halaman aktif
     * yang dipegang; halaman lain dibuang dan dibangun ulang saat dibutuhkan.
     *
     * URUTANNYA PENTING: lepas gambar dari ImageView DULU (di thread UI),
     * baru recycle bitmap-nya (di thread latar). Kalau dibalik, pager yang
     * sedang scroll masih menggambar bitmap bekas dan aplikasi crash dengan
     * "Canvas: trying to use a recycled bitmap".
     */
    private void muatHalaman(final int page) {
        if (page < 0 || page >= PAGE_COUNT) return;
        halamanAktif = page;
        final int req = ++muatId;
        if (cards[page] != null) {
            imgs[page].setImageBitmap(cards[page]);
            return;
        }
        tvGenerating.setVisibility(View.VISIBLE);

        // 1) Lepas semua gambar yang bukan halaman aktif - WAJIB sebelum recycle.
        for (int i = 0; i < PAGE_COUNT; i++) {
            if (i != page) imgs[i].setImageDrawable(null);
        }

        new Thread(new Runnable() {
            @Override public void run() {
                // Geseran cepat bisa memicu beberapa permintaan sekaligus;
                // yang sudah tidak berlaku dibatalkan di sini.
                if (req != muatId) return;
                // 2) Baru aman untuk membuang bitmap lama.
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
                        // Pengguna sudah geser ke halaman lain selagi render
                        // berjalan: jangan pasang gambar yang tak lagi diminta.
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

    /** Bangun bitmap satu varian; null kalau gagal. */
    private Bitmap bitmapUntuk(int page) {
        try {
            switch (page) {
                case 0: return ShareCard.cardBitmap(this, row, trackCache, false);
                case 1: return ShareCard.cardBitmap(this, row, trackCache, true);
                case 2: return ShareCard.storyBitmap(this, row, false);
                case 3: return ShareCard.storyBitmap(this, row, true);
                case 4: return ShareCard.stickerBitmap(this, row, trackCache);
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
            default: return base + "_card.png";
        }
    }

    /** Bitmap halaman ini; dibangun dulu kalau belum ada (mis. tombol ditekan sebelum render selesai). */
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

    /** Pesan gagal + sebabnya kalau ada, supaya bisa ditindaklanjuti. */
    private String pesanGagal(int resId) {
        String why = ShareCard.lastErrorReason;
        return getString(resId) + ((why == null || why.length() == 0) ? "" : "\n" + why);
    }

    private void tulisGaleri(Bitmap bmp, int page) {
        // Bitmap sudah jadi di memori, jadi cukup tulis ulang ke berkas PNG.
        Uri uri = ShareCard.writePngToGallery(this, bmp, namaKartu(page));
        // Sebutkan sebabnya kalau gagal: "gagal" tanpa alasan tidak bisa
        // ditindaklanjuti, dan tanpa ini EACCES scoped storage terlihat
        // seperti bug misterius.
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
                            // Android 13+ sudah menampilkan preview clipboard sendiri.
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
        // Lepas dulu dari ImageView, baru recycle - urutan sama seperti
        // muatHalaman(), supaya tidak ada sisa gambar yang masih dirujuk.
        for (int i = 0; i < PAGE_COUNT; i++) {
            if (imgs[i] != null) imgs[i].setImageDrawable(null);
            if (cards[i] != null && !cards[i].isRecycled()) cards[i].recycle();
            cards[i] = null;
        }
    }
}
