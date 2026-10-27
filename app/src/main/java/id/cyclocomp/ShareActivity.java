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

    private RideDb.Row row;
    private CardPager pager;
    private TextView tvCardName;
    private TextView tvGenerating;
    private LinearLayout dots;
    private LinearLayout pagerContent;
    private ImageView[] imgs = new ImageView[5];
    private View[] dotViews = new View[5];
    private Bitmap[] cards = new Bitmap[5];
    private int[] labelIds = {
            R.string.card_variant_map,     // 0: 4:5 Opak
            R.string.card_variant_clear,    // 1: 4:5 Transparan (overlay)
            R.string.card_variant_story,    // 2: Story Opak
            R.string.card_variant_clear,   // 3: Story Transparan (overlay)
            R.string.card_variant_clear,    // 4: Stempel Transparan
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
        for (int i = 0; i < 5; i++) {
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
            }
        });

        bangunKartu();
    }

    private void buatTitik() {
        dots.removeAllViews();
        float d = 8f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < 5; i++) {
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
        if (pos < 0 || pos >= 5) return;
        for (int i = 0; i < 5; i++) {
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
                final List<GpxReader.P> t = track;
                
                // Bangun 5 varian
                cards[0] = ShareCard.cardBitmap(ShareActivity.this, row, t, false); // 4:5 Opak
                cards[1] = ShareCard.cardBitmap(ShareActivity.this, row, t, true);  // 4:5 Transparan
                cards[2] = ShareCard.storyBitmap(ShareActivity.this, row, false);   // Story Opak
                cards[3] = ShareCard.storyBitmap(ShareActivity.this, row, true);    // Story Transparan
                cards[4] = ShareCard.stickerBitmap(ShareActivity.this, row, t);     // Stempel (Transparan)

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        tvGenerating.setVisibility(View.GONE);
                        boolean ada = false;
                        for (int i = 0; i < 5; i++) {
                            if (cards[i] != null) {
                                imgs[i].setImageBitmap(cards[i]);
                                ada = true;
                            } else {
                                imgs[i].setImageDrawable(null);
                            }
                        }
                        if (!ada) {
                            Toast.makeText(ShareActivity.this, R.string.card_failed, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                });
            }
        }).start();
    }

    private String namaKartu(int page) {
        String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
        if (gpxName == null) gpxName = \"ride\";
        int dot = gpxName.lastIndexOf('.');
        String base = dot > 0 ? gpxName.substring(0, dot) : gpxName;
        switch (page) {
            case 0:  return base + \"_card.png\";
            case 1:  return base + \"_overlay.png\";
            case 2:  return base + \"_story.png\";
            case 3:  return base + \"_story_overlay.png\";
            case 4:  return base + \"_sticker.png\";
            default: return base + \"_card.png\";
        }
    }

    private void simpanKartu() {
        final int page = pager.getCurrentPage();
        if (page < 0 || page >= 5) return;
        final Bitmap bmp = cards[page];
        if (bmp == null) {
            Toast.makeText(this, R.string.card_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29) {
            int granted = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (granted != PackageManager.PERMISSION_GRANTED) {
                pendingSavePage = page;
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE_STORAGE);
                return;
            }
        }
        tulisGaleri(bmp, page);
    }

    private void tulisGaleri(Bitmap bmp, int page) {
        // Gunakan metode build yang benar sesuai varian untuk memastikan nama berkas konsisten
        String gpxName = row.gpxPath == null ? null : GpxStore.displayName(row.gpxPath);
        File file = null;
        List<GpxReader.P> track = java.util.Collections.emptyList(); // Tidak perlu track untuk build jika sudah punya bitmap, tapi ShareCard.build butuh track. 
        // Lebih aman panggil ShareCard.writePngForShare langsung.
        file = ShareCard.writePngForShare(this, bmp, namaKartu(page));
        
        Toast.makeText(this, file != null ? R.string.save_done : R.string.save_failed_gallery, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_WRITE_STORAGE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingSavePage >= 0) {
            Bitmap bmp = cards[pendingSavePage];
            if (bmp != null) tulisGaleri(bmp, pendingSavePage);
        } else {
            Toast.makeText(this, R.string.save_need_permission, Toast.LENGTH_LONG).show();
        }
        pendingSavePage = -1;
    }

    private void bagikanKartu() {
        int page = pager.getCurrentPage();
        if (page < 0 || page >= 5) return;
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
        Uri uri = Uri.parse(\"content://\" + RideProvider.AUTHORITY + \"/\" + png.getName());
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(\"image/png\");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, png.getName()));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_title)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_LONG).show();
        }
    }

    private void salinKartu() {
        int page = pager.getCurrentPage();
        if (page < 0 || page >= 5) return;
        Bitmap bmp = cards[page];
        if (bmp == null) {
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        File png = ShareCard.writePngForShare(this, bmp, namaKartu(page));
        if (png == null) {
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) {
                Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Uri uri = Uri.parse(\"content://\" + RideProvider.AUTHORITY + \"/\" + png.getName());
            android.content.ClipData clip = android.content.ClipData.newUri(getContentResolver(), \"Zylo\", uri);
            cm.setPrimaryClip(clip);
            if (Build.VERSION.SDK_INT <= 32) {
                Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void bagikanGpx() {
        if (row.gpxPath == null) return;
        if (!GpxStore.exists(this, row.gpxPath)) {
            Toast.makeText(this, R.string.gpx_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        String nama = GpxStore.displayName(row.gpxPath);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(\"application/gpx+xml\");
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
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null && !cards[i].isRecycled()) cards[i].recycle();
            cards[i] = null;
        }
    }
}
