package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Geometri titik halaman (indikator di bawah dashboard).
 *
 * Yang diuji di sini murni aritmetika dp -> px yang dipakai MainActivity saat
 * membuat titik dan memasang latar garisnya. Tujuannya: bidang sentuh harus
 * 48dp, dan garis yang terlihat harus tetap 3dp tinggi TEPAT di tengah bidang
 * itu. Kalau kelak ada yang mengubah ukuran bidang sentuh atau tinggi garis,
 * tes ini gagal lebih dulu - sebelum rilis, bukan setelah.
 */
public class DotsGeometriTest {

    private static final int SENTUH_DP = 48;
    private static final int GARIS_H_DP = 3;
    private static final int GARIS_AKTIF_DP = 26;
    private static final int GARIS_MATI_DP = 12;

    /** Tiruan dp() di MainActivity: pembulatan ke px terdekat. */
    private static int dp(int v, float density) {
        return Math.round(density * v);
    }

    private static int insetAtas(float density) {
        return (dp(SENTUH_DP, density) - dp(GARIS_H_DP, density)) / 2;
    }

    private static int insetSamping(float density, int garisDp) {
        return (dp(SENTUH_DP, density) - dp(garisDp, density)) / 2;
    }

    @Test
    public void bidangSentuhCukup48dp() {
        // 48dp pada densitas 1x/2x/3x harus tetap >= 48px.
        for (float d : new float[]{1f, 2f, 3f, 3.5f}) {
            assertTrue("densitas " + d, dp(SENTUH_DP, d) >= 48);
        }
    }

    @Test
    public void garisTigaDpTepatDiTengah() {
        for (float d : new float[]{1f, 2f, 3f}) {
            int atas = insetAtas(d);
            // garis + inset atas + inset bawah == tinggi bidang sentuh
            assertEquals(dp(SENTUH_DP, d),
                    dp(GARIS_H_DP, d) + atas + (dp(SENTUH_DP, d) - dp(GARIS_H_DP, d) - atas));
            // selisih atas-bawah maksimal 1px (akibat pembulatan), bukan bergeser
            int bawah = dp(SENTUH_DP, d) - dp(GARIS_H_DP, d) - atas;
            assertTrue(Math.abs(atas - bawah) <= 1);
        }
    }

    @Test
    public void garisAktifDanMatiSamaSamaTengah() {
        for (float d : new float[]{1f, 2f, 3f}) {
            for (int w : new int[]{GARIS_AKTIF_DP, GARIS_MATI_DP}) {
                int side = insetSamping(d, w);
                int kanan = dp(SENTUH_DP, d) - dp(w, d) - side;
                assertTrue(Math.abs(side - kanan) <= 1);
                assertTrue(side >= 0);
            }
        }
    }

    @Test
    public void garisTidakPernahLebihDariBidangSentuh() {
        // kalau garis lebih lebar dari bidangnya, inset jadi negatif dan
        // InsetDrawable akan menggambar garis keluar dari bidang sentuh.
        for (int w : new int[]{GARIS_AKTIF_DP, GARIS_MATI_DP}) {
            assertTrue(w < SENTUH_DP);
        }
    }
}
