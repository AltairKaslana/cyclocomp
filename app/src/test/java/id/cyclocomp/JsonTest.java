package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Uji {@link Json} - pembaca JSON minimal.
 *
 * Yang paling penting dijaga di sini: pencocokan kunci harus **persis**. Strava
 * mengembalikan "id", "id_str", dan "external_id" dalam satu objek; kalau pencocokan
 * longgar, kode bisa mengambil "id_str" sebagai "id" dan unggahan salah dilaporkan.
 */
public class JsonTest {

    @Test
    public void bacaStringBiasa() {
        String j = "{\"status\":\"Your activity is ready.\",\"error\":null}";
        assertEquals("Your activity is ready.", Json.str(j, "status"));
        assertNull(Json.str(j, "error"));
        assertNull(Json.str(j, "tidak_ada"));
    }

    @Test
    public void kunciHarusPersisBukanAwalan() {
        // "id" tidak boleh tertukar dengan "id_str" atau "external_id"
        String j = "{\"id\":123,\"id_str\":\"123\",\"external_id\":\"ride.gpx\"}";
        assertEquals("123", Json.str(j, "id"));
        assertEquals("123", Json.str(j, "id_str"));
        assertEquals("ride.gpx", Json.str(j, "external_id"));
    }

    @Test
    public void kunciHarusPersisBukanAkhiran() {
        // "id" tidak boleh tertangkap dari "activity_id"
        String j = "{\"activity_id\":987,\"id\":123}";
        assertEquals("987", Json.str(j, "activity_id"));
        assertEquals("123", Json.str(j, "id"));
    }

    @Test
    public void angkaDibaca() {
        String j = "{\"id\":123,\"activity_id\":987,\"expires_at\":1780000000}";
        assertEquals(123.0, Json.num(j, "id"), 0.001);
        assertEquals(987.0, Json.num(j, "activity_id"), 0.001);
        assertTrue(Double.isNaN(Json.num(j, "tidak_ada")));
    }

    @Test
    public void booleanDibaca() {
        assertTrue(Json.bool("{\"ok\":true}", "ok"));
        assertFalse(Json.bool("{\"ok\":false}", "ok"));
        assertFalse(Json.bool("{\"ok\":null}", "ok"));
        assertFalse(Json.bool("{\"lain\":true}", "ok"));
    }

    @Test
    public void escapeDibaca() {
        String j = "{\"msg\":\"baris1\\nbaris2 \\\"kutip\\\" \\u0041\"}";
        assertEquals("baris1\nbaris2 \"kutip\" A", Json.str(j, "msg"));
    }

    @Test
    public void stringTidakDitutupTidakBikinCrash() {
        assertNull(Json.str("{\"a\":\"tidak ditutup", "a"));
        assertNull(Json.str(null, "a"));
        assertNull(Json.str("{}", "a"));
    }

    @Test
    public void spasiDanBarisBaruDiabaikan() {
        String j = "{ \"status\" :\n\t \"sudah siap\" , \"id\" : 5 }";
        assertEquals("sudah siap", Json.str(j, "status"));
        assertEquals("5", Json.str(j, "id"));
    }

    @Test
    public void nilaiNonStringDikembalikanApaAdanya() {
        // null harus jadi null, bukan teks "null"
        assertNull(Json.str("{\"a\":null}", "a"));
        assertEquals("5", Json.str("{\"a\":5}", "a"));
        assertEquals("true", Json.str("{\"a\":true}", "a"));
    }

    @Test
    public void objekBersarangTidakBikinKunciSalahAmbil() {
        // "athlete" adalah objek; mengambil "id" tetap harus dapat id teratas
        String j = "{\"id\":123,\"athlete\":{\"id\":999,\"firstname\":\"Altair\"}}";
        assertEquals("123", Json.str(j, "id"));
    }
}
