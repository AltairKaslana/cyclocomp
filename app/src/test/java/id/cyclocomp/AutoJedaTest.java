package id.cyclocomp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tes keputusan jeda otomatis.
 *
 * Latar belakang: auto-resume pernah MATI total di lapangan. Rantainya -
 * pauseRide() mencabut GPS, checkAutoResume() cuma terpanggil dari
 * onLocationChanged(), dan tidak ada lagi yang mendaftarkan GPS kembali.
 * Pengendara berhenti di lampu merah, ride jeda sendiri, GPS mati, pengendara
 * mengayuh lagi - dan ride tetap diam sampai dia sadar dan menekan resume
 * manual. Waktu dan jarak ride under-count tanpa tanda apa pun.
 *
 * Bug itu tidak terlihat dari luar dan tidak bisa ditemukan dengan menguji
 * layar. Karena itu keputusannya dipisah ke AutoJeda dan diuji di sini.
 */
public class AutoJedaTest {

    private static final float AMBANG = 1.4f;   // ~5 km/jam

    // ---------- jeda sendiri ----------

    @Test
    public void belumBerhentiJanganJeda() {
        assertFalse("sejak == 0 berarti belum pernah berhenti",
                AutoJeda.harusJeda(0f, AMBANG, 0, 5000, 60_000));
    }

    @Test
    public void masihGerakJanganJeda() {
        assertFalse("masih mengayuh, jangan jeda walau sudah lama",
                AutoJeda.harusJeda(5f, AMBANG, 10_000, 5000, 60_000));
    }

    @Test
    public void berhentiCukupLamaHarusJeda() {
        assertTrue(AutoJeda.harusJeda(0f, AMBANG, 10_000, 5000, 20_000));
    }

    @Test
    public void berhentiBelumCukupLamaJanganJeda() {
        assertFalse("tunggu sampai tunda selesai, jangan jeda terlalu cepat",
                AutoJeda.harusJeda(0f, AMBANG, 10_000, 5000, 14_000));
    }

    @Test
    public void berhentiTepatPadaBatasHarusJeda() {
        // Batasnya ">=", bukan ">": berhenti tepat selama tunda yang ditentukan
        // sudah cukup lama.
        assertTrue(AutoJeda.harusJeda(0f, AMBANG, 10_000, 5000, 15_000));
    }

    // ---------- lanjut sendiri ----------

    @Test
    public void bergerakLagiHarusLanjut() {
        assertTrue(AutoJeda.harusLanjut(4f, AMBANG));
    }

    @Test
    public void tepatPadaAmbangHarusLanjut() {
        assertTrue("tepat di ambang sudah dianggap bergerak",
                AutoJeda.harusLanjut(AMBANG, AMBANG));
    }

    @Test
    public void masihDiamJanganLanjut() {
        assertFalse(AutoJeda.harusLanjut(0f, AMBANG));
    }

    @Test
    public void melayangPelanJanganLanjut() {
        // Di bawah ambang = belum dianggap bergerak, walau GPS masih
        // melaporkan kecepatan kecil (geser di lampu merah, misalnya).
        assertFalse(AutoJeda.harusLanjut(0.5f, AMBANG));
    }

    // ---------- kecepatan dari fix jeda ----------

    @Test
    public void kecepatanNegatifDianggapDiam() {
        // GPS sesekali melaporkan kecepatan negatif saat baru mencari sinyal.
        // Kalau dibandingkan mentah, nilai negatif akan gagal ambang - itu
        // memang benar, tapi bikin bingung kalau dibaca sebagai kecepatan.
        assertEquals(0f, AutoJeda.kecepatanDari(true, -3f), 0.0001f);
    }

    @Test
    public void tanpaKecepatanDianggapDiam() {
        assertEquals(0f, AutoJeda.kecepatanDari(false, 99f), 0.0001f);
    }

    @Test
    public void kecepatanBagusDipakaiApaAdanya() {
        assertEquals(6.5f, AutoJeda.kecepatanDari(true, 6.5f), 0.0001f);
    }
}
