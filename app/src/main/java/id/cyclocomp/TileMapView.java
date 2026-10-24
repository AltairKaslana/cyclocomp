package id.cyclocomp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rute di atas ubin peta OpenStreetMap.
 *
 * Kenapa ditulis sendiri dan bukan osmdroid: proyek ini tanpa dependency, dan kebutuhan
 * kita cuma satu - tempel ubin di belakang garis rute. Yang dipakai dari peta hanya
 * gambar; tidak ada pencarian tempat, tidak ada navigasi belok, jadi menambah pustaka
 * 2-3 MB tidak sepadan.
 *
 * Kalau peta tidak bisa dimuat (offline, server lambat, izin jaringan dicabut), ubin yang
 * gagal dibiarkan kosong sehingga latar kisi dari {@link RouteView} yang tampak - rute
 * tetap terbaca. Peta adalah hiasan, rute adalah datanya.
 *
 * Ubin disimpan di cache aplikasi, jadi rute yang pernah dibuka tetap tampil tanpa jaringan.
 */
public class TileMapView extends RouteView {

    private static final int TILE = 256;
    private static final int MIN_ZOOM = 3, MAX_ZOOM = 18;
    /** Batas jumlah ubin yang diminta bersamaan; sopan terhadap server publik. */
    private static final int MAX_PARALLEL = 2;

    private static final String URL_TEMPLATE = "https://tile.openstreetmap.org/%d/%d/%d.png";
    /** Kebijakan OSM mewajibkan agen pengguna yang jelas; tanpa ini permintaan ditolak. */
    private static final String UA = "Cyclocomp/0.8.0 (bike computer; Android)";

    private final Paint pTile = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint pAttrib = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Rect srcRect = new Rect(0, 0, TILE, TILE);
    private final Rect dstRect = new Rect();

    private LruCache<String, Bitmap> mem;
    // Diminta dari thread UI (saat menggambar) DAN dari thread unduhan. Tanpa kunci,
    // HashSet bisa rusak di tengah jalan -> ConcurrentModificationException -> crash.
    private final Object tileLock = new Object();
    private final Set<String> gagal = new HashSet<String>();
    private final Set<String> diminta = new HashSet<String>();
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_PARALLEL);

    private double centerLat, centerLon;
    private int zoom = 14;
    private boolean fitted;
    private boolean enabled = true;

    private GestureDetector gesture;
    private ScaleGestureDetector pinch;
    private float pinchAccum = 1f;

    public TileMapView(Context c) { super(c); initMap(); }
    public TileMapView(Context c, AttributeSet a) { super(c, a); initMap(); }
    public TileMapView(Context c, AttributeSet a, int s) { super(c, a, s); initMap(); }

    private void initMap() {
        int cacheKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        mem = new LruCache<String, Bitmap>(cacheKb) {
            @Override protected int sizeOf(String k, Bitmap b) {
                return Math.max(1, b.getByteCount() / 1024);
            }
        };

        pAttrib.setColor(0xB0FFFFFF);
        pAttrib.setTextSize(22f);
        pAttrib.setTextAlign(Paint.Align.RIGHT);

        gesture = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                // geser peta: pusat bergerak berlawanan arah jari
                double world = worldSize();
                centerLon -= dx / world * 360.0;
                centerLat = yToLat(yToWorld(centerLat) - dy, world);
                centerLat = Math.max(-85, Math.min(85, centerLat));
                invalidate();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                zoom = Math.min(MAX_ZOOM, zoom + 1);
                invalidate();
                return true;
            }
        });

        pinch = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                pinchAccum *= d.getScaleFactor();
                if (pinchAccum > 1.6f && zoom < MAX_ZOOM) { zoom++; pinchAccum = 1f; invalidate(); }
                else if (pinchAccum < 0.62f && zoom > MIN_ZOOM) { zoom--; pinchAccum = 1f; invalidate(); }
                return true;
            }
        });
    }

    /** Peta bisa dimatikan pengguna; kalau mati, tampilannya sama dengan {@link RouteView}. */
    public void setMapEnabled(boolean on) {
        enabled = on;
        invalidate();
    }

    @Override public void setTrack(List<GpxReader.P> track) {
        super.setTrack(track);
        fitted = false;
        gagal.clear();
        invalidate();
    }

    // ---------- proyeksi Web Mercator ----------

    private double worldSize() { return TILE * Math.pow(2, zoom); }

    private double lonToWorldX(double lon, double world) {
        return (lon + 180.0) / 360.0 * world;
    }

    private double latToWorldY(double lat, double world) {
        double r = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, lat)));
        double y = Math.log(Math.tan(r) + 1.0 / Math.cos(r));
        return (1.0 - y / Math.PI) / 2.0 * world;
    }

    private double yToWorld(double lat) { return latToWorldY(lat, worldSize()); }

    private double yToLat(double worldY, double world) {
        double n = Math.PI - 2.0 * Math.PI * worldY / world;
        return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
    }

    /** Pilih zoom dan pusat supaya seluruh rute masuk tampilan. */
    private void fit(int w, int h) {
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (GpxReader.P p : pts) {
            if (p.lat < minLat) minLat = p.lat;
            if (p.lat > maxLat) maxLat = p.lat;
            if (p.lon < minLon) minLon = p.lon;
            if (p.lon > maxLon) maxLon = p.lon;
        }
        centerLat = (minLat + maxLat) / 2.0;
        centerLon = (minLon + maxLon) / 2.0;

        float pad = 40f;
        int best = MIN_ZOOM;
        for (int z = MAX_ZOOM; z >= MIN_ZOOM; z--) {
            double world = TILE * Math.pow(2, z);
            double dx = Math.abs(lonToWorldX(maxLon, world) - lonToWorldX(minLon, world));
            double dy = Math.abs(latToWorldY(minLat, world) - latToWorldY(maxLat, world));
            if (dx <= w - 2 * pad && dy <= h - 2 * pad) { best = z; break; }
        }
        zoom = best;
        fitted = true;
    }

    @Override protected void drawBackground(Canvas cv, int w, int h) {
        // latar kisi dulu: ini yang tampak kalau ubin belum/tidak bisa dimuat
        super.drawBackground(cv, w, h);
        if (!enabled || !fitted || pts.size() < 2) return;

        double world = worldSize();
        double left = lonToWorldX(centerLon, world) - w / 2.0;
        double top = latToWorldY(centerLat, world) - h / 2.0;

        int n = 1 << zoom;
        int tx0 = (int) Math.floor(left / TILE);
        int ty0 = (int) Math.floor(top / TILE);
        int tx1 = (int) Math.floor((left + w) / TILE);
        int ty1 = (int) Math.floor((top + h) / TILE);

        // Bitmap rusak/kelelahan memori tidak boleh menjatuhkan aplikasi:
        // kalau ada ubin yang gagal, ubin itu dilewati dan rute tetap tergambar.
        for (int ty = ty0; ty <= ty1; ty++) {
            if (ty < 0 || ty >= n) continue;               // kutub: tidak ada ubin
            for (int tx = tx0; tx <= tx1; tx++) {
                int wx = ((tx % n) + n) % n;               // bumi berputar: bungkus mendatar
                Bitmap b = mem.get(key(wx, ty));
                if (b == null) { request(wx, ty); continue; }

                try {
                    dstRect.set(
                            (int) Math.round(tx * TILE - left),
                            (int) Math.round(ty * TILE - top),
                            (int) Math.round(tx * TILE - left) + TILE,
                            (int) Math.round(ty * TILE - top) + TILE);
                    cv.drawBitmap(b, srcRect, dstRect, pTile);
                } catch (Throwable ignored) { }
            }
        }

        // atribusi wajib menurut ketentuan OpenStreetMap
        cv.drawText("© OpenStreetMap", w - 8f, h - 8f, pAttrib);
    }

    @Override protected boolean prepareProjection(int w, int h) {
        if (pts.size() < 2) return false;
        if (!fitted) fit(w, h);
        return true;
    }

    @Override protected void project(double lat, double lon, float[] out) {
        double world = worldSize();
        out[0] = (float) (lonToWorldX(lon, world) - (lonToWorldX(centerLon, world) - getWidth() / 2.0));
        out[1] = (float) (latToWorldY(lat, world) - (latToWorldY(centerLat, world) - getHeight() / 2.0));
    }

    // ---------- ubin: memori, disk, jaringan ----------

    private String key(int x, int y) { return zoom + "/" + x + "/" + y; }

    private File diskFile(int z, int x, int y) {
        File dir = new File(getContext().getCacheDir(), "tiles/" + z + "/" + x);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, y + ".png");
    }

    private void request(final int x, final int y) {
        final int z = zoom;
        final String k = key(x, y);
        synchronized (tileLock) {
            if (diminta.contains(k) || gagal.contains(k)) return;
            diminta.add(k);
        }

        pool.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                File f = diskFile(z, x, y);

                // 1. cache disk
                if (f.exists() && f.length() > 0) {
                    b = BitmapFactory.decodeFile(f.getAbsolutePath());
                }

                // 2. jaringan, hanya kalau belum ada di disk
                if (b == null) b = download(z, x, y, f);

                synchronized (tileLock) {
                    diminta.remove(k);
                    if (b == null) {
                        gagal.add(k);
                        return;
                    }
                }
                final Bitmap got = b;
                post(new Runnable() {
                    @Override public void run() {
                        mem.put(k, got);
                        invalidate();
                    }
                });
            }
        });
    }

    private Bitmap download(int z, int x, int y, File save) {
        HttpURLConnection c = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL u = new URL(String.format(java.util.Locale.US, URL_TEMPLATE, z, x, y));
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", UA);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;

            in = c.getInputStream();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(16 * 1024);
            byte[] tmp = new byte[8 * 1024];
            int n;
            while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
            byte[] data = buf.toByteArray();
            if (data.length == 0) return null;

            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (b == null) return null;

            // simpan ke cache aplikasi supaya rute ini tetap tampil saat offline
            try {
                out = new FileOutputStream(save);
                out.write(data);
                out.flush();
            } catch (Throwable ignored) { }

            return b;
        } catch (Throwable t) {
            return null;
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
            if (in != null) try { in.close(); } catch (Exception ignored) { }
            if (c != null) c.disconnect();
        }
    }

    /** Peta adalah hiasan: kegagalan di sini tidak boleh menjatuhkan aplikasi. */
    @Override protected void onDraw(Canvas cv) {
        try {
            super.onDraw(cv);
        } catch (Throwable ignored) { }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        gesture.onTouchEvent(e);
        return true;
    }
}
