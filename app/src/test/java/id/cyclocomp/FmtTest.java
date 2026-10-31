package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tes untuk {@link Fmt} - satu-satunya tempat angka diubah menjadi teks.
 *
 * Kenapa ini penting: kalau format berubah diam-diam, semibun tahun lalu
 * "0:10:56" bisa jadi "10:56" di satu layar dan tetap "0:10:56" di layar lain,
 * dan tidak ada yang menyadari sampai pengguna mengeluh. Tes ini mengunci
 * formatnya.
 *
 * Catatan locale: tes mengasumsikan locale default JVM punya titik sebagai
 * pemisah desimal dan koma sebagai pemisah ribuan. CI GitHub Actions memakai
 * locale POSIX/C, jadi aman.
 */
public class FmtTest {

    private static final double EPS = 1e-6;

    // ---------- duration ----------

    @Test
    public void durationNolAdalahNol() {
        assertEquals("0:00:00", Fmt.duration(0));
    }

    @Test
    public void durationDiBawahSemenit() {
        assertEquals("0:00:09", Fmt.duration(9_400));   // dibulatkan ke bawah
    }

    @Test
    public void durationPersisSemenit() {
        assertEquals("0:01:00", Fmt.duration(60_000));
    }

    @Test
    public void durationDiAtasSemenit() {
        // kasus nyata dari handoff: 8,19 km dalam 10 menit 56 detik
        assertEquals("0:10:56", Fmt.duration(656_000));
    }

    @Test
    public void durationDiAtasSatuJam() {
        assertEquals("1:02:03", Fmt.duration(3_723_000));
    }

    @Test
    public void durationNegatifDianggapNol() {
        assertEquals("0:00:00", Fmt.duration(-5_000));
    }

    // ---------- speed ----------

    @Test
    public void speedNol() {
        assertEquals("0.0", Fmt.speed(0f));
    }

    @Test
    public void speedKonversiMsKeKmPerJam() {
        // 10 m/s = 36 km/jam
        assertEquals("36.0", Fmt.speed(10f));
    }

    @Test
    public void speedSatuDesimal() {
        assertEquals("45.0", Fmt.speed(12.5f));
    }

    @Test
    public void speedPemisahDesimalTitikBukanKoma() {
        // Locale.US: titik desimal, konsisten di semua perangkat
        assertEquals("12.5", Fmt.speed(3.4722f));
    }

    // ---------- km ----------

    @Test
    public void kmNol() {
        assertEquals("0.00", Fmt.km(0));
    }

    @Test
    public void kmKasusHandoff() {
        // 8,19 km - nilai yang dipakai mockup di dokumen handoff
        assertEquals("8.19", Fmt.km(8190));
    }

    @Test
    public void kmDuaDesimal() {
        assertEquals("1.50", Fmt.km(1500));
    }

    // ---------- meters ----------

    @Test
    public void metersBulat() {
        assertEquals("123", Fmt.meters(123.4));
        assertEquals("124", Fmt.meters(123.6));
    }

    // ---------- lap ----------

    @Test
    public void lapFormatLengkapSesuaiHandoff() {
        // "Lap 1 • 8,19 km • 0:10:56 • 45,0 km/jam"
        assertEquals("Lap 1 • 8.19 km • 0:10:56 • 45.0 km/jam",
                Fmt.lap(1, 8190, 656_000, 12.5f));
    }

    @Test
    public void lapMemakaiFungsiYangSama() {
        // lap() harus konsisten dengan km() + duration() + speed()
        String a = Fmt.lap(3, 2500, 90_000, 5f);
        String b = "Lap 3 • " + Fmt.km(2500) + " km • " + Fmt.duration(90_000)
                + " • " + Fmt.speed(5f) + " km/jam";
        assertEquals(b, a);
    }

    @Test
    public void lapTanpaKmPerJamSaatTidakBergerak() {
        // movingMs = 0 -> kecepatan 0, bukan NaN/Infinity
        String s = Fmt.lap(2, 0, 60_000, 0f);
        assertTrue(s.contains("0.0 km/jam"));
        assertTrue(!s.contains("NaN"));
        assertTrue(!s.contains("Infinity"));
    }

    // ---------- kcal ----------

    @Test
    public void kcalNol() {
        assertEquals("0", Fmt.kcal(0));
    }

    @Test
    public void kcalNegatifDianggapNol() {
        assertEquals("0", Fmt.kcal(-50));
    }

    // ---------- grade ----------

    @Test
    public void gradeSatuDesimal() {
        assertEquals("3.5", Fmt.grade(3.46));
    }

    // ---------- size (cache peta) ----------

    @Test
    public void sizeNolDanNegatif() {
        assertEquals("0 B", Fmt.size(0));
        assertEquals("0 B", Fmt.size(-10));
    }

    @Test
    public void sizeByte() {
        assertEquals("999 B", Fmt.size(999));
    }

    @Test
    public void sizeKilobyte() {
        assertEquals("1 KB", Fmt.size(1024));
        assertEquals("512 KB", Fmt.size(512L * 1024));
    }

    @Test
    public void sizeMegabyte() {
        assertEquals("12.3 MB", Fmt.size((long) (12.3 * 1024 * 1024)));
        assertEquals("20.0 MB", Fmt.size(20L * 1024 * 1024));
    }
}
