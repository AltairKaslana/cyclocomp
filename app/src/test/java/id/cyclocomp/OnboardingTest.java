package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Aturan langkah onboarding - lihat {@link Onboarding} untuk alasannya.
 *
 * Yang dijaga di sini: (1) izin lokasi yang sudah ada tidak memunculkan lagi
 * langkah lokasi, (2) tombol utama tiap langkah sesuai keadaan, (3) onboarding
 * yang semuanya sudah beres tidak muncul lagi.
 */
public class OnboardingTest {

    @Test
    public void majuMundurDiDalamBatas() {
        assertEquals(1, Onboarding.berikutnya(0));
        assertEquals(2, Onboarding.berikutnya(1));
        assertEquals(-1, Onboarding.berikutnya(2));   // langkah terakhir: tidak ada sesudahnya
        assertEquals(0, Onboarding.sebelumnya(1));
        assertEquals(1, Onboarding.sebelumnya(2));
        assertEquals(-1, Onboarding.sebelumnya(0));   // langkah pertama: tidak ada sebelumnya
    }

    @Test
    public void izinLokasiSudahAdaMakaMulaiDariBaterai() {
        assertEquals(Onboarding.LOKASI, Onboarding.langkahAwal(false));
        assertEquals(Onboarding.BATERAI, Onboarding.langkahAwal(true));
    }

    @Test
    public void tombolUtamaMengikutiKeadaan() {
        // lokasi: minta izin kalau belum ada, lanjut kalau sudah
        assertEquals(R.string.onb_izinkan_lokasi,
                Onboarding.tombolUtama(Onboarding.LOKASI, false, false));
        assertEquals(R.string.onb_lanjut,
                Onboarding.tombolUtama(Onboarding.LOKASI, true, false));
        // baterai: buka pengaturan kalau masih kena optimasi, lanjut kalau sudah dikecualikan
        assertEquals(R.string.onb_buka_pengaturan,
                Onboarding.tombolUtama(Onboarding.BATERAI, true, false));
        assertEquals(R.string.onb_lanjut,
                Onboarding.tombolUtama(Onboarding.BATERAI, true, true));
        // sensor: selalu "Mulai gowes", apa pun keadaannya
        assertEquals(R.string.onb_mulai, Onboarding.tombolUtama(Onboarding.SENSOR, false, false));
        assertEquals(R.string.onb_mulai, Onboarding.tombolUtama(Onboarding.SENSOR, true, true));
    }

    @Test
    public void tidakDitampilkanKalauSudahSelesaiAtauSemuaBeres() {
        assertFalse(Onboarding.layakDitampilkan(true, false, false));   // sudah ditutup pengguna
        assertFalse(Onboarding.layakDitampilkan(false, true, true));    // tidak ada yang perlu ditanya
        assertTrue(Onboarding.layakDitampilkan(false, false, true));    // izin lokasi belum
        assertTrue(Onboarding.layakDitampilkan(false, true, false));    // baterai belum dikecualikan
    }
}
