package id.cyclocomp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Uji mekanisme tema: palet yang TERPASANG harus selalu menyusul pilihan
 * tersimpan, dan perbaikannya tidak boleh berulang tanpa akhir.
 *
 * Mengetes aturan ini penting karena gejalanya di lapangan halus: sakelar tema
 * yang "macet" bukan error yang terlihat di log - hanya palet yang tidak ikut.
 */
public class TemaTest {

    /** Aktivitas tiruan dengan palet tertentu. */
    private static class Layar extends Activity {
        Layar(boolean terang) {
            res.configuration.uiMode = Configuration.UI_MODE_TYPE_NORMAL
                    | (terang ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
        }
    }

    private Context ctx() {
        Context c = new Context();
        c.res.configuration.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
        return c;
    }

    /** Palet yang terbaca dari konteks = gelap/terang. */
    private static boolean terang(Context c) {
        return Tema.sedangTerang(c);
    }

    @Test
    public void sedangTerangMembacaPenandaKonfigurasi() {
        Context gelap = ctx();
        Context terangC = new Context();
        terangC.res.configuration.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
        assertFalse(terang(gelap));
        assertTrue(terang(terangC));
    }

    @Test
    public void terapkanLangsungTersimpan() {
        Context c = ctx();
        Tema.terapkan(c, true);
        // pilihan tersimpan harus sudah terbaca sesudah terapkan, tanpa
        // menunggu proses lain atau me-refresh SharedPreferences.
        assertTrue(Settings.theme(c));
    }

    @Test
    public void paletTakCocokDibangunUlangSekali() {
        Context c = ctx();
        Tema.terapkan(c, true);                 // pilihan: siang
        Layar a = new Layar(false);             // tapi layar masih gelap
        assertFalse(Tema.sedangTerang(a));

        Tema.perbaikiKalauPerlu(a);
        assertEquals("palet beda dengan pilihan -> bangun ulang", 1, a.recreateCount);

        // Panggilan berikutnya tidak boleh jadi loop: kalau pemasangan palet
        // gagal total, pengguna tetap bisa memakai aplikasi.
        Tema.perbaikiKalauPerlu(a);
        Tema.perbaikiKalauPerlu(a);
        assertEquals(1, a.recreateCount);
    }

    @Test
    public void paletCocokTidakDibangunUlang() {
        Context c = ctx();
        Tema.terapkan(c, true);
        Layar a = new Layar(true);              // layar sudah siang, sesuai pilihan
        Tema.perbaikiKalauPerlu(a);
        assertEquals(0, a.recreateCount);
    }

    @Test
    public void layarYangSedangDitutupTidakDibangunUlang() {
        Context c = ctx();
        Tema.terapkan(c, true);
        Layar a = new Layar(false);
        a.finishing = true;
        Tema.perbaikiKalauPerlu(a);
        assertEquals(0, a.recreateCount);
    }

    @Test
    public void bungkusMencatatPaletYangDipasang() {
        Context c = ctx();
        Tema.terapkan(c, true);
        // setelah bungkus, palet konteks baru harus siang - dan penanda proses
        // ikut, supaya layar berikutnya tidak membaca nilai lama.
        Context bungkus = Tema.bungkus(c);
        assertTrue(Tema.sedangTerang(bungkus));
        assertTrue(terang(bungkus));
    }

    @Test
    public void gelapSelaluGelapUntukKartuBagikan() {
        Context c = ctx();
        Tema.terapkan(c, true);                 // pengguna pilih siang...
        Context kartu = Tema.gelap(c);          // ...tapi kartu bagikan tetap gelap
        assertFalse(Tema.sedangTerang(kartu));
    }
}
