package id.cyclocomp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Registri bidang (Atur layar). Yang dijaga di sini adalah aturan, bukan angka:
 *
 *  - jumlah slot tiap halaman harus tetap (3, 10, 4) supaya layout yang sudah
 *    ada tidak pernah kelebihan atau kekurangan sel;
 *  - susunan bawaan harus sah dan tidak boleh ada bidang yang tidak dikenal;
 *  - {@link Bidang#sah} menolak panjang yang salah - kalau tidak, menyimpan
 *    susunan rusak akan membuat dashboard kosong.
 */
public class BidangTest {

    @Test
    public void jumlahSlotPerHalamanTetap() {
        assertEquals(3, Bidang.batas(Bidang.SEKILAS));
        assertEquals(10, Bidang.batas(Bidang.DETAIL));
        assertEquals(4, Bidang.batas(Bidang.LAP));
    }

    @Test
    public void semuaBidangPunyaLabelDanSatuanYangDikenal() {
        for (int f = 0; f < Bidang.JUMLAH; f++) {
            assertTrue("bidang " + f, Bidang.dikenal(f));
            assertTrue("label " + f, Bidang.label(f) != 0);
            // satuan boleh 0 (tanpa satuan terpisah), tapi kalau ada harus
            // berbeda dari label - kalau sama, UI akan menulis "km km".
            int u = Bidang.unit(f);
            if (u != 0) assertTrue("satuan " + f, u != Bidang.label(f));
        }
        assertFalse(Bidang.dikenal(-1));
        assertFalse(Bidang.dikenal(Bidang.JUMLAH));
    }

    @Test
    public void susunanBawaanSahDanTidakAdaBidangGanda() {
        for (int h = 0; h < Bidang.HALAMAN_JUMLAH; h++) {
            int[] b = Bidang.bawaan(h);
            assertTrue("halaman " + h, Bidang.sah(h, b));
            assertEquals("halaman " + h, Bidang.batas(h), b.length);
            boolean[] dipakai = new boolean[Bidang.JUMLAH];
            for (int f : b) {
                assertFalse("bidang " + f + " muncul dua kali di halaman " + h, dipakai[f]);
                dipakai[f] = true;
            }
        }
    }

    @Test
    public void sahMenolakYangTidakBolehDisimpan() {
        int[] ok = Bidang.bawaan(Bidang.DETAIL);
        assertTrue(Bidang.sah(Bidang.DETAIL, ok));
        // terlalu pendek / terlalu panjang
        assertFalse(Bidang.sah(Bidang.DETAIL, new int[9]));
        assertFalse(Bidang.sah(Bidang.DETAIL, new int[11]));
        // bidang di luar daftar
        int[] rusak = ok.clone();
        rusak[3] = Bidang.JUMLAH;
        assertFalse(Bidang.sah(Bidang.DETAIL, rusak));
        rusak[3] = -1;
        assertFalse(Bidang.sah(Bidang.DETAIL, rusak));
        // null
        assertFalse(Bidang.sah(Bidang.DETAIL, null));
        // panjang yang benar untuk halaman lain tetap ditolak di halaman ini
        assertFalse(Bidang.sah(Bidang.DETAIL, Bidang.bawaan(Bidang.LAP)));
    }

    @Test
    public void fieldSensorBenarDitandai() {
        assertEquals(Bidang.SENSOR_HR, Bidang.sensor(Bidang.DETAK));
        assertEquals(Bidang.SENSOR_CSC, Bidang.sensor(Bidang.KADENSI));
        assertEquals(Bidang.SENSOR_DAYA, Bidang.sensor(Bidang.DAYA));
        for (int f = 0; f < Bidang.JUMLAH; f++) {
            if (f != Bidang.DETAK && f != Bidang.KADENSI && f != Bidang.DAYA) {
                assertEquals("bidang " + f, Bidang.SENSOR_TANPA, Bidang.sensor(f));
            }
        }
    }

    @Test
    public void hanyaKaloriDanDayaYangPerkiraan() {
        assertTrue(Bidang.perkiraan(Bidang.KALORI));
        assertTrue(Bidang.perkiraan(Bidang.DAYA));
        assertFalse(Bidang.perkiraan(Bidang.JARAK));
        assertFalse(Bidang.perkiraan(Bidang.KECEPATAN));
        assertFalse(Bidang.perkiraan(Bidang.KETINGGIAN));
    }

    @Test
    public void susunanBawaanDetailSamaDenganKisiYangSudahAda() {
        // Urutan ini penting: kalau berubah, dashboard pengguna lama bertukar
        // tempat sendiri saat versi baru dipasang.
        assertArrayEquals(new int[]{
                Bidang.KECEPATAN, Bidang.RATA_RATA, Bidang.MAKS, Bidang.JARAK,
                Bidang.WAKTU_GERAK, Bidang.WAKTU_TOTAL, Bidang.TANJAKAN,
                Bidang.KETINGGIAN, Bidang.KALORI, Bidang.DETAK},
                Bidang.bawaan(Bidang.DETAIL));
    }
}
