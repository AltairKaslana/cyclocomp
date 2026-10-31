package id.cyclocomp;

import android.content.res.Configuration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Uji aturan tema - khususnya bagian yang bikin sakelar tema terasa MACET.
 *
 * Gejalanya di lapangan halus: tidak ada error, tidak ada crash, hanya palet
 * yang tidak ikut berganti. Karena itu yang dikunci di sini adalah aturan
 * bawaannya: palet yang TERPASANG harus selalu menyusul pilihan tersimpan, di
 * kedua arah, dan perbaikannya tidak boleh berulang tanpa akhir.
 *
 * Hanya logika murni yang diuji di sini (tanpa perangkat): sisanya butuh
 * Context/Activity nyata.
 */
public class TemaTest {

    // ---------- penanda malam pada Configuration ----------

    @Test
    public void penandaSiangTerbacaSiang() {
        assertTrue(Tema.terangDariPenanda(
                Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO));
    }

    @Test
    public void penandaMalamTerbacaGelap() {
        assertFalse(Tema.terangDariPenanda(
                Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES));
    }

    @Test
    public void penandaTakJelasBukanSiang() {
        // UI_MODE_NIGHT_UNDEFINED (0x00): jangan dianggap siang.
        assertFalse(Tema.terangDariPenanda(Configuration.UI_MODE_TYPE_NORMAL));
    }

    @Test
    public void bitLainTidakMengubahHasil() {
        // kerapatan (bit 8-15) dan tipe layar tidak boleh ikut menentukan.
        int siangPadat = (2 << 8) | Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
        assertTrue(Tema.terangDariPenanda(siangPadat));
        int malamPadat = (3 << 8) | Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
        assertFalse(Tema.terangDariPenanda(malamPadat));
    }

    @Test
    public void bitMalamDanPembacaannyaSinkron() {
        // Bolak-balik: apa pun uiMode masuk, hasil paksa siang harus terbaca
        // siang, dan hasil paksa gelap harus terbaca gelap. Kalau aritmetika
        // bit-nya meleset, palet yang dipakai ikut salah - dan itu yang bikin
        // sakelar tema terasa macet.
        int[] contoh = {
                0,
                Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO,
                Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES,
                (2 << 8) | Configuration.UI_MODE_TYPE_DESK,
                (4 << 8) | Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_UNDEFINED,
        };
        for (int masuk : contoh) {
            assertTrue("paksa siang harus terbaca siang", Tema.terangDariPenanda(
                    Tema.bitMalam(masuk, true)));
            assertFalse("paksa gelap harus terbaca gelap", Tema.terangDariPenanda(
                    Tema.bitMalam(masuk, false)));
        }
    }

    @Test
    public void bitMalamTidakMenyentuhBitLain() {
        int masuk = (2 << 8) | Configuration.UI_MODE_TYPE_CAR | Configuration.UI_MODE_NIGHT_YES;
        int hasil = Tema.bitMalam(masuk, true);
        // kerapatan dan tipe layar harus utuh; hanya dua bit malam yang berubah
        assertEquals("kerapatan utuh", 2 << 8, hasil & (0xff << 8));
        assertEquals("tipe layar utuh",
                Configuration.UI_MODE_TYPE_CAR, hasil & Configuration.UI_MODE_TYPE_MASK);
    }

    // ---------- keputusan membangun ulang ----------

    @Test
    public void paletBerbedaDariPilihanHarusDibangunUlang() {
        // layar gelap tapi pengguna pilih siang
        assertTrue(Tema.perluPasangUlang(false, true));
        // layar siang tapi pengguna pilih gelap - arah ini yang dilaporkan macet
        assertTrue(Tema.perluPasangUlang(true, false));
    }

    @Test
    public void paletSesuaiPilihanTidakUsahDibangunUlang() {
        assertFalse(Tema.perluPasangUlang(true, true));
        assertFalse(Tema.perluPasangUlang(false, false));
    }

    // ---------- batas pemasangan ulang supaya tidak jadi loop ----------

    @Test
    public void pemasanganUlangTerbatasPerProses() {
        // Percobaan lama memakai kunci objek aktivitas. Itu tidak menahan apa
        // pun: recreate() menghasilkan aktivitas BARU, jadi kuncinya baru dan
        // "sekali" tidak lagi sekali. Batas harus dihitung per proses.
        assertTrue(Tema.bolehPasangUlag(0));
        assertTrue(Tema.bolehPasangUlag(Tema.MAKS_PASANG_ULANG - 1));
        assertFalse("setelah batas, jangan berkedip tanpa berhenti",
                Tema.bolehPasangUlag(Tema.MAKS_PASANG_ULANG));
        assertFalse(Tema.bolehPasangUlag(Tema.MAKS_PASANG_ULANG + 5));
    }
}
