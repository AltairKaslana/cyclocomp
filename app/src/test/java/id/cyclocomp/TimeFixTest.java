package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Uji {@link TimeFix} - penjaga waktu GPX.
 *
 * Latar belakang: ada perangkat yang melaporkan jam GPS dengan jam/menit/detik benar tapi
 * tanggal ngawur (terlihat 2007 alih-alih 2026). Kalau tidak dijaga, berkas GPX tetap terbuka
 * tapi gowesnya mendarat di tahun yang salah saat diunggah ke Strava/Garmin.
 */
public class TimeFixTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void waktuMasukAkalTidakDiubah() {
        long now = System.currentTimeMillis();
        TimeFix t = new TimeFix();
        assertEquals(now, t.sanitize(now));
        assertEquals(now - 60_000, t.sanitize(now - 60_000));   // satu menit lalu
        assertFalse(t.correcting());
    }

    @Test
    public void tanggalNgawurDikoreksiKeSekarang() {
        // Samsung pelapor: jam/menit/detik benar, tanggal 19 tahun terlampaui
        long ngawur = System.currentTimeMillis() - 620L * DAY;
        TimeFix t = new TimeFix();
        long out = t.sanitize(ngawur);

        assertTrue(t.correcting());
        long now = System.currentTimeMillis();
        assertTrue("harus mendekati jam sistem", Math.abs(out - now) < DAY);
    }

    @Test
    public void koreksiMenjagaJarakAntarTitik() {
        // Dua titik ngawur berjarak 5 detik harus tetap berjarak 5 detik setelah dikoreksi,
        // supaya Strava tetap menghitung durasi dan kecepatan yang benar.
        long now = System.currentTimeMillis();
        long a = now - 620L * DAY;
        long b = a + 5_000;

        TimeFix t = new TimeFix();
        long oa = t.sanitize(a);
        long ob = t.sanitize(b);

        assertEquals(5_000L, ob - oa);
        assertTrue(Math.abs(oa - now) < DAY);
    }

    @Test
    public void waktuNgawurDiMasaDepanJugaDikoreksi() {
        long now = System.currentTimeMillis();
        TimeFix t = new TimeFix();
        long out = t.sanitize(now + 400L * DAY);
        assertTrue(t.correcting());
        assertTrue(Math.abs(out - now) < DAY);
    }

    @Test
    public void koreksiHanyaDihitungSekali() {
        long now = System.currentTimeMillis();
        long a = now - 620L * DAY;
        long b = now - 620L * DAY + 1_000;

        TimeFix t = new TimeFix();
        long oa = t.sanitize(a);
        long ob = t.sanitize(b);
        // offset tidak ditimpa oleh titik kedua, jadi selisih tetap utuh
        assertEquals(1_000L, ob - oa);
        assertTrue(Math.abs(oa - now) < DAY);
    }
}
