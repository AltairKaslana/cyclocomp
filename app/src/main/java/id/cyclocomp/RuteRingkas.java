package id.cyclocomp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pratinjau rute ringkas untuk daftar Riwayat.
 *
 * Thumbnail 64dp hanya butuh BENTUK rute, bukan ribuan titik GPS aslinya.
 * Menyimpan seluruh trek di database berarti menyalin berkas GPX ke SQLite:
 * daftar Riwayat jadi berat dibaca dan basis data membengkak tanpa satu piksel
 * tambahan yang terlihat. Karena itu rute diringkas sekali saat gowes disimpan,
 * lalu hanya angka lat/lon hasil ringkasan yang disimpan.
 *
 * Kelas ini bebas android.* supaya bisa diuji di JVM (lihat RuteRingkasTest).
 */
final class RuteRingkas {
    private RuteRingkas() {
    }

    /** Cukup untuk thumbnail: bentuk masih terbaca, baris tidak menyimpan banyak data. */
    static final int MAKS_TITIK = 48;

    /**
     * Ambil titik-titik yang tersebar merata menurut JARAK, bukan nomor urut.
     *
     * Kenapa jarak: GPS menulis satu titik per detik. Pengendara yang berhenti
     * lama di lampu merah menghasilkan ratusan titik menumpuk di satu tempat;
     * memilih setiap titik ke-n akan menghabiskan kuota thumbnail di sana dan
     * justru membuang belokan yang dilewati cepat. Sampel per jarak menahan
     * bentuk rute di kedua keadaan.
     */
    static List<GpxReader.P> sederhanakan(List<GpxReader.P> titik) {
        return sederhanakan(titik, MAKS_TITIK);
    }

    static List<GpxReader.P> sederhanakan(List<GpxReader.P> titik, int maks) {
        List<GpxReader.P> out = new ArrayList<GpxReader.P>();
        if (titik == null || titik.isEmpty()) return out;

        int batas = Math.max(2, maks);
        if (titik.size() <= batas) {
            out.addAll(titik);
            return out;
        }

        int n = titik.size();
        double[] jarak = new double[n];
        for (int i = 1; i < n; i++) {
            GpxReader.P a = titik.get(i - 1);
            GpxReader.P b = titik.get(i);
            jarak[i] = jarak[i - 1] + RideStats.distanceM(a.lat, a.lon, b.lat, b.lon);
        }

        double total = jarak[n - 1];
        out.add(titik.get(0));
        if (total <= 0) {
            // Semua titik menumpuk: satu titik sudah mewakili seluruh rute.
            return out;
        }

        int j = 1;
        for (int k = 1; k < batas - 1; k++) {
            double target = total * k / (batas - 1);
            while (j < n - 1 && jarak[j] < target) j++;
            tambahKalauBaru(out, titik.get(j));
        }
        tambahKalauBaru(out, titik.get(n - 1));
        return out;
    }

    private static void tambahKalauBaru(List<GpxReader.P> out, GpxReader.P p) {
        if (!sama(out.get(out.size() - 1), p)) out.add(p);
    }

    private static boolean sama(GpxReader.P a, GpxReader.P b) {
        return Math.abs(a.lat - b.lat) < 1e-7 && Math.abs(a.lon - b.lon) < 1e-7;
    }

    /**
     * Simpan sebagai teks "lat,lon;lat,lon;...".
     *
     * Lima angka desimal sekitar satu meter - jauh di bawah ketelitian layar
     * 64dp, tapi cukup untuk membedakan dua jalur yang berdekatan. Locale.US
     * dipakai supaya pemisah desimal tidak berubah di perangkat berkoma.
     */
    static String encode(List<GpxReader.P> titik) {
        if (titik == null || titik.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(titik.size() * 16);
        for (GpxReader.P p : titik) {
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%.5f,%.5f", p.lat, p.lon));
        }
        return sb.toString();
    }

    static List<GpxReader.P> decode(String teks) {
        List<GpxReader.P> out = new ArrayList<GpxReader.P>();
        if (teks == null || teks.length() == 0) return out;
        String[] potong = teks.split(";");
        for (String t : potong) {
            int koma = t.indexOf(',');
            if (koma <= 0) continue;
            try {
                double lat = Double.parseDouble(t.substring(0, koma));
                double lon = Double.parseDouble(t.substring(koma + 1));
                out.add(new GpxReader.P(lat, lon));
            } catch (NumberFormatException ignored) {
                // satu potongan rusak tidak boleh membatalkan seluruh pratinjau
            }
            if (out.size() >= 256) break;   // batas aman untuk data yang tidak dikenal
        }
        return out;
    }

    /** Ringkas langsung dari berkas GPX; null kalau berkas tidak ada/rusak. */
    static String dariGpx(File gpx, int maks) {
        if (gpx == null || !gpx.exists()) return null;
        List<GpxReader.P> titik = GpxReader.read(gpx);
        if (titik.isEmpty()) return null;
        return encode(sederhanakan(titik, maks));
    }
}
