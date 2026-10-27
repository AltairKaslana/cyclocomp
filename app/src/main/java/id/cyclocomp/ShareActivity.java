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
 *
 * Tiga varian kartu dibangun sekali di thread latar lalu disimpan di memori,
 * jadi menggeser preview tidak me-render ulang apa pun. Aksi selalu
 * bekerja pada kartu yang sedang tampil.
 *
 * Tanpa dependency: geseran pakai {@link CardPager} (HorizontalScrollView
 * dengan snap), semua view dari framework.
 */
public class ShareActivity extends Activity {

    static final String EXTRA_ID = SummaryActivity.EXTRA_ID;

    private static final int REQ_WRITE_STORAGE = 78;

    private RideDb.Row row;
    private CardPager pager;
    private TextView tvCardName;
    private TextView tvGenerating;
    private LinearLayout dots;
    private ImageView[] imgs = new ImageView[3];
    private View[] dotViews = new View[3];
    private Bitmap[] cards = new Bitmap[3];
    private int[] labelIds = {
            R.string.card_variant_map,
            R.string.card_variant_story,
            R.string.card_variant_clear,
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
        tvCardName = (TextView) findViewById(R.id.tvCardName);
        tvGenerating = (TextView) findViewById(R.id.tvGenerating);
        dots = (LinearLayout) findViewById(R.id.dots);
        imgs[0] = (ImageView) findViewById(R.id.img0);
        imgs[1] = (ImageView) findViewById(R.id.img1);
        imgs[2] = (ImageView) findViewById(R.id.img2);

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
            }
        });

        bangunKartu();
    }

    private void buatTitik() {
        dots.removeAllViews();
        float d = 8f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < 3; i++) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Math.round(d), Math.round(d));
            lp.setMargins(Math.round(6f * d / 8f), 0,
                    Math.round(6f * d / 8f), 0);
            v.setLayoutParams(lp);
            dots.addView(v);
            dotViews[i] = v;
        }
        perbaruiTitik();
    }

    private void perbaruiTitik() {
        int pos = pager.getCurrentPage();
        for (int i = 0; i < 3; i++) {
            dotViews[i].setBackgroundColor(i == pos ? 0xFFFC4C02 : 0xFF3A4048);
        }
        tvCardName.setText(labelIds[pos]);
    }

    /**
     * Bangun ketiga kartu di thread latar. Membuat bitmap 1080x1350/1920
     * butuh puluhan milidetik - cukup lama untuk thread UI, dan membangun
     * bitmaps di background membuat layar tetap responsif.
     */
    private void bangunKartu() {
        new Thread(new Runnable() {
            @Override public void run() {
                List<GpxReader.P> track = null;
                try {
                    track = GpxReader.read(GpxStore.materialize(
                            ShareActivity.this, row.gpxPath));
                } catch (Throwable t) {
                    track = java.util.Collections.emptyList();
                }
                final List<GpxReader.P> t = track;
                cards[0] = ShareCard.cardBitmap(ShareActivity.this, row, t);
                cards[1] = ShareCard.storyBitmap(ShareActivity.this, row);
                cards[2] = ShareCard.stickerBitmap(ShareActivity.this, row, t);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        tvGenerating.setVisibility(View.GONE);
                        boolean ada = false;
                        for (int i = 0; i < 3; i++) {
                            if (cards[i] != null) {
                                imgs[i].setImageBitmap(cards[i]);
                                ada = true;
                            } else {
                                imgs[i].setImageDrawable(null);
                            }
                        }
                        if (!ada) {
                            Toast.makeText(ShareActivity.this, R.string.card_failed,
                                    Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                });
            }
        }).start();
    }

    /** Nama berkas untuk kartu halaman tertentu. */
    private String namaKartu(int page) {
        String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
        if (gpxName == null) gpxName = "ride";
        int dot = gpxName.lastIndexOf('.');
        String base = dot > 0 ? gpxName.substring(0, dot) : gpxName;
        switch (page) {
            case 1:  return base + "_story.png";
            case 2:  return base + "_sticker.png";
            default: return base + "_card.png";
        }
    }

    /** Simpan kartu yang sedang tampil ke galeri. */
    private void simpanKartu() {
        final int page = pager.getCurrentPage();
        final Bitmap bmp = cards[page];
        if (bmp == null) {
            Toast.makeText(this, R.string.card_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        // Android 9 ke bawah masih butuh izin runtime untuk jalur berkas.
        if (Build.VERSION.SDK_INT < 29) {
            int granted = checkSelfPermission(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (granted != PackageManager.PERMISSION_GRANTED) {
                pendingSavePage = page;
                requestPermissions(
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQ_WRITE_STORAGE);
                return;
            }
        }
        tulisGaleri(bmp, page);
    }

    private void tulisGaleri(Bitmap bmp, int page) {
        String err = ShareCard.saveBitmapToGallery(this, bmp, namaKartu(page));
        Toast.makeText(this,
                err == null ? R.string.save_done : R.string.save_failed_gallery,
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_WRITE_STORAGE) return;
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingSavePage >= 0) {
            Bitmap bmp = cards[pendingSavePage];
            if (bmp != null) tulisGaleri(bmp, pendingSavePage);
        } else {
            Toast.makeText(this, R.string.save_need_permission,
                    Toast.LENGTH_LONG).show();
        }
        pendingSavePage = -1;
    }

    /** Bagikan kartu yang sedang tampil lewat pemilih aplikasi. */
    private void bagikanKartu() {
        int page = pager.getCurrentPage();
        Bitmap bmp = cards[page];
        if (bmp == null) {
            Toast.makeText(this, R.string.card_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        File png = ShareCard.writePngForShare(this, bmp, namaKartu(page));
        if (png == null) {
            Toast.makeText(this, R.string.card_failed, Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Salin kartu yang sedang tampil ke clipboard - pola "Copy to Clipboard"
     * yang dipakai Strava. Gambar ditulis dulu ke folder rides supaya punya
     * content:// URI; aplikasi lain (WhatsApp, chat, editor) bisa menempelnya.
     */
    private void salinKartu() {
        int page = pager.getCurrentPage();
        Bitmap bmp = cards[page];
        if (bmp == null) {
            Toast.makeText(this, R.string.card_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        File png = ShareCard.writePngForShare(this, bmp, namaKartu(page));
        if (png == null) {
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) {
                Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Uri uri = Uri.parse("content://" + RideProvider.AUTHORITY + "/" + png.getName());
            android.content.ClipData clip = android.content.ClipData.newUri(
                    getContentResolver(), "Zylo", uri);
            cm.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Bagikan berkas GPX-nya langsung (mis. untuk diunggah ke Strava). */
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
        // bitmap besar tidak perlu dipegang setelah layar ditutup
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null && !cards[i].isRecycled()) cards[i].recycle();
            cards[i] = null;
        }
    }
}
