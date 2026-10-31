package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Uji aritmetika Cycling Speed and Cadence (0x2A5B).
 *
 * Semua angka di sini diturunkan tangan dari format Bluetooth SIG: waktu event
 * dalam 1/1024 detik, putaran roda uint32, putaran engkol uint16. Parser bebas
 * Android supaya bisa diuji di JVM.
 */
public class CscParserTest {

    private static final float TOL = 0.02f;

    /** Paket hanya data roda (flag bit0). */
    private static byte[] roda(long rev, int waktu) {
        return new byte[]{
                0x01,
                (byte) rev, (byte) (rev >> 8), (byte) (rev >> 16), (byte) (rev >> 24),
                (byte) waktu, (byte) (waktu >> 8)
        };
    }

    /** Paket hanya data engkol (flag bit1). */
    private static byte[] engkol(int rev, int waktu) {
        return new byte[]{
                0x02,
                (byte) rev, (byte) (rev >> 8),
                (byte) waktu, (byte) (waktu >> 8)
        };
    }

    /** Paket dengan kedua kanal (flag bit0+bit1). */
    private static byte[] dua(long revRoda, int waktuRoda, int revEngkol, int waktuEngkol) {
        return new byte[]{
                0x03,
                (byte) revRoda, (byte) (revRoda >> 8), (byte) (revRoda >> 16), (byte) (revRoda >> 24),
                (byte) waktuRoda, (byte) (waktuRoda >> 8),
                (byte) revEngkol, (byte) (revEngkol >> 8),
                (byte) waktuEngkol, (byte) (waktuEngkol >> 8)
        };
    }

    @Test public void paketPertamaHanyaJadiAcuan() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        CscParser.Hasil h = p.parse(roda(100, 10_000));
        assertFalse("belum ada selisih, jadi belum ada kecepatan", h.adaKecepatan());
        assertFalse(h.adaKadensi());
    }

    @Test public void kecepatanDariDuaPutaranRoda() {
        CscParser p = new CscParser(2105);          // keliling 2,105 m
        p.parse(roda(100, 0));
        // 2 putaran dalam 2 detik -> 4,21 m/s (15,2 km/jam)
        CscParser.Hasil h = p.parse(roda(102, 2048));
        assertEquals(2 * 2.105f / 2f, h.speedMps, TOL);
    }

    @Test public void kadensiDariSatuPutaranEngkol() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        p.parse(engkol(500, 0));
        // 1 putaran dalam 1024 tick (1 detik) -> 60 rpm
        CscParser.Hasil h = p.parse(engkol(501, 1024));
        assertEquals(60, h.cadenceRpm);
        assertFalse("paket ini tidak punya data roda", h.adaKecepatan());
    }

    @Test public void keduaKanalDalamSatuPaket() {
        CscParser p = new CscParser(2000);
        p.parse(dua(0, 0, 0, 0));
        CscParser.Hasil h = p.parse(dua(1, 1024, 1, 1024));
        assertEquals(2f, h.speedMps, TOL);
        assertEquals(60, h.cadenceRpm);
    }

    @Test public void waktuRodaMelingkar64Detik() {
        CscParser p = new CscParser(2105);
        p.parse(roda(10, 65_024));                  // 0xFE00
        // waktu kembali ke 0: selisih = 512 tick = 0,5 detik
        CscParser.Hasil h = p.parse(roda(11, 0));
        assertEquals(2.105f / 0.5f, h.speedMps, 0.2f);
    }

    @Test public void putaranEngkolMelingkar65536() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        p.parse(engkol(65_535, 0));
        // 65535 -> 1 = 2 putaran dalam 1024 tick -> 120 rpm
        CscParser.Hasil h = p.parse(engkol(1, 1024));
        assertEquals(120, h.cadenceRpm);
    }

    @Test public void putaranRodaMelingkar32Bit() {
        CscParser p = new CscParser(2000);
        p.parse(roda(0xFFFFFFFFL, 0));
        // 0xFFFFFFFF -> 1 = 2 putaran dalam 1 detik -> 4 m/s
        CscParser.Hasil h = p.parse(roda(1, 1024));
        assertEquals(4f, h.speedMps, TOL);
    }

    @Test public void waktuSamaTidakMenghasilkanAngka() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        p.parse(roda(10, 4096));
        CscParser.Hasil h = p.parse(roda(11, 4096));
        assertFalse("pembagi nol tidak boleh jadi kecepatan", h.adaKecepatan());
    }

    @Test public void kecepatanMustahilDitolak() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        p.parse(roda(0, 0));
        // 1000 putaran dalam 1 tick = ratusan km/jam
        CscParser.Hasil h = p.parse(roda(1000, 1));
        assertFalse(h.adaKecepatan());
    }

    @Test public void kadensiMustahilDitolak() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        p.parse(engkol(0, 0));
        CscParser.Hasil h = p.parse(engkol(1000, 1));
        assertFalse(h.adaKadensi());
    }

    @Test public void celahTerlaluLamaDitolak() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        p.parse(roda(0, 0));
        // 30.000 tick (~29 detik) berarti sensor sempat mati; angka tidak dipercaya
        CscParser.Hasil h = p.parse(roda(10, 30_000));
        assertFalse(h.adaKecepatan());
    }

    @Test public void resetMelupakanAcuan() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        p.parse(roda(100, 0));
        p.reset();
        CscParser.Hasil h = p.parse(roda(101, 1024));
        assertFalse("setelah reset paket pertama jadi acuan baru", h.adaKecepatan());
    }

    @Test public void ukuranRodaBaruIkutDipakai() {
        CscParser p = new CscParser(2000);
        p.parse(roda(0, 0));
        CscParser.Hasil h = p.parse(roda(1, 1024));
        assertEquals(2f, h.speedMps, TOL);

        p.setKelilingMm(2105);
        p.parse(roda(2, 2048));                      // acuan baru di 2048
        h = p.parse(roda(3, 3072));
        assertEquals(2.105f, h.speedMps, TOL);
    }

    @Test public void paketPendekTidakDibaca() {
        CscParser p = new CscParser(Roda.BAWAAN_MM);
        assertFalse(p.parse(new byte[]{0x01}).adaKecepatan());
        assertFalse(p.parse(new byte[]{0x02, 1}).adaKadensi());
        assertFalse(p.parse(null).adaKecepatan());
        assertFalse(p.parse(new byte[0]).adaKecepatan());
    }

    @Test public void flagKosongTidakMengubahAcuan() {
        CscParser p = new CscParser(2000);
        p.parse(roda(0, 0));
        p.parse(new byte[]{0x00});                   // tanpa data apa pun
        CscParser.Hasil h = p.parse(roda(1, 1024));
        assertTrue(h.adaKecepatan());
        assertEquals(2f, h.speedMps, TOL);
    }
}
