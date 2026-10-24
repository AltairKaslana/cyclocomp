package id.cyclocomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Uji bagian Strava yang bisa diuji di JVM: penguraian query string pengalihan OAuth.
 *
 * Kenapa ini diuji: kalau parameter salah diambil, alur OAuth bisa menyimpan token milik
 * field lain, atau menandai unggahan berhasil padahal Strava menolak. Kesalahan di sini
 * tidak terlihat sampai pengguna benar-benar mencoba menyambungkan akun.
 */
public class StravaParamTest {

    @Test
    public void ambilKodeOtorisasi() {
        String q = "code=75e251e3ff8fff&scope=read,activity:write";
        assertEquals("75e251e3ff8fff", Strava.param(q, "code"));
        assertEquals("read,activity:write", Strava.param(q, "scope"));
    }

    @Test
    public void ambilGalatSaatPenggunaMenolak() {
        assertEquals("access_denied", Strava.param("error=access_denied", "error"));
        assertNull(Strava.param("error=access_denied", "code"));
    }

    @Test
    public void queryKosongAtauNull() {
        assertNull(Strava.param(null, "code"));
        assertNull(Strava.param("", "code"));
        assertNull(Strava.param("tanpa=tanda sama", "code"));
    }

    @Test
    public void decodePercentEncoding() {
        assertEquals("http://127.0.0.1:8741/callback",
                Strava.decode("http%3A%2F%2F127.0.0.1%3A8741%2Fcallback"));
        assertEquals("a b", Strava.decode("a+b"));
        assertEquals("a b", Strava.decode("a%20b"));
    }

    @Test
    public void decodeTanpaPercentTidakBerubah() {
        assertEquals("75e251e3ff8fff", Strava.decode("75e251e3ff8fff"));
        assertNull(Strava.decode(null));
    }

    @Test
    public void kunciUtuhBukanPotongan() {
        // "code" tidak boleh tertangkap dari "grant_type"
        assertNull(Strava.param("grant_type=authorization_code", "code"));
    }
}
