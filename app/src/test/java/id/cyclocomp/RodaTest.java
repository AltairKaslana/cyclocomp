package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Uji tabel ukuran roda dan penjepitan nilai di luar batas wajar. */
public class RodaTest {

    @Test public void bawaanAdalahSalahSatuPreset() {
        assertEquals("700×25C", Roda.label(Roda.BAWAAN_MM));
    }

    @Test public void semuaPresetPunyaLabel() {
        for (int i = 0; i < Roda.PRESET_MM.length; i++) {
            assertEquals(Roda.PRESET_LABEL[i], Roda.label(Roda.PRESET_MM[i]));
        }
    }

    @Test public void nilaiBukanPresetTidakPunyaLabel() {
        assertNull(Roda.label(1234));
    }

    @Test public void nilaiTerlaluKecilDijepitKeBatasBawah() {
        assertEquals(Roda.MIN_MM, Roda.normalisasi(0));
        assertEquals(Roda.MIN_MM, Roda.normalisasi(-500));
        assertEquals(Roda.MIN_MM, Roda.normalisasi(Roda.MIN_MM - 1));
    }

    @Test public void nilaiTerlaluBesarDijepitKeBatasAtas() {
        assertEquals(Roda.MAX_MM, Roda.normalisasi(99_999));
        assertEquals(Roda.MAX_MM, Roda.normalisasi(Roda.MAX_MM + 1));
    }

    @Test public void nilaiDalamBatasTidakBerubah() {
        assertEquals(2105, Roda.normalisasi(2105));
        assertEquals(Roda.MIN_MM, Roda.normalisasi(Roda.MIN_MM));
        assertEquals(Roda.MAX_MM, Roda.normalisasi(Roda.MAX_MM));
    }

    @Test public void meterAdalahMilimeterDibagiSeribu() {
        assertEquals(2.105f, Roda.meter(2105), 0.0001f);
        assertEquals(2.0f, Roda.meter(2000), 0.0001f);
    }

    @Test public void meterIkutMenjepit() {
        assertEquals(Roda.MIN_MM / 1000f, Roda.meter(0), 0.0001f);
        assertEquals(Roda.MAX_MM / 1000f, Roda.meter(10_000), 0.0001f);
    }
}
