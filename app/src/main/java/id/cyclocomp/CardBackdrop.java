package id.cyclocomp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Latar belakang pratinjau kartu di layar Bagikan.
 *
 * T-15: dua hal yang dulu tidak terlihat di pratinjau.
 *
 * 1. Kartu transparan (overlay 4:5, overlay Story, stempel) digambar di atas
 *    latar hitam pekat aplikasi, jadi tepinya tidak kelihatan sama sekali.
 *    Pengguna mengira kartunya berlatar hitam, padahal yang tersimpan ke galeri
 *    adalah PNG transparan - dan kartu yang ditempel ke foto terkejut besar
 *    kecilnya. Sekarang kartu transparan berdiri di atas pola kotak-kotak abu
 *    gelap / lebih gelap: konvensi yang dipakai semua editor gambar untuk
 *    mengatakan "di sini tidak ada warna".
 *
 * 2. Kartu 4:5 tidak mengisi seluruh tinggi pager di layar tinggi, jadi tanpa
 *    penanda pengguna tidak tahu di mana kartunya berhenti dan di mana ruang
 *    kosong di sekitarnya. Sekarang ada bingkai pada rasio kartunya.
 *
 * Dibuat sebagai Drawable, bukan View, supaya bisa dipasang sebagai background
 * ImageView tanpa mengubah struktur pager sama sekali.
 */
public class CardBackdrop extends Drawable {

    /** Rasio lebar:tinggi untuk bingkai; <= 0 berarti tanpa bingkai. */
    private final float rasio;
    private final boolean kotak;
    private final int sel;
    private final int gelap;
    private final int lebihGelap;
    private final int garis;

    private final Paint pSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGaris = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CardBackdrop(float rasio, boolean kotak, int sel,
                        int gelap, int lebihGelap, int garis) {
        this.rasio = rasio;
        this.kotak = kotak;
        this.sel = sel;
        this.gelap = gelap;
        this.lebihGelap = lebihGelap;
        this.garis = garis;
        pGaris.setStyle(Paint.Style.STROKE);
        pGaris.setStrokeWidth(2f);
        pGaris.setColor(garis);
    }

    @Override
    public void draw(Canvas cv) {
        Rect b = getBounds();
        if (b.isEmpty()) return;

        // ---- pola kotak-kotak: dua abu yang berselang ----
        // Hanya untuk kartu yang memang punya area transparan. Kalau dipasang
        // di belakang kartu opak, kotak-kotaknya muncul di ruang sisa di
        // sekeliling kartu dan menyiratkan transparansi yang tidak ada.
        if (kotak) {
            for (int y = b.top; y < b.bottom; y += sel) {
                for (int x = b.left; x < b.right; x += sel) {
                    boolean genap = (((x - b.left) / sel) + ((y - b.top) / sel)) % 2 == 0;
                    pSel.setColor(genap ? gelap : lebihGelap);
                    cv.drawRect(x, y, Math.min(x + sel, b.right), Math.min(y + sel, b.bottom), pSel);
                }
            }
        }

        // ---- bingkai rasio kartu ----
        if (rasio <= 0f) return;
        float w = b.width();
        float h = w / rasio;
        if (h > b.height()) {
            h = b.height();
            w = h * rasio;
        }
        float left = b.left + (b.width() - w) / 2f;
        float top = b.top + (b.height() - h) / 2f;
        cv.drawRect(left, top, left + w, top + h, pGaris);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
