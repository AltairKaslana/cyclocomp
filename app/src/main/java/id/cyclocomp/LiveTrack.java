package id.cyclocomp;

import android.content.Context;
import java.io.File;
import java.util.List;

/**
 * Rute yang sudah dilalui pada gowes yang SEDANG berjalan.
 *
 * Saat merekam, RideService tidak menahan titik di memori - tiap fix GPS langsung
 * ditulis ke berkas GPX (GpxWriter). Jadi sumber kebenaran untuk peta live adalah
 * berkas itu, dibaca ulang sesekali. GpxReader sengaja berbasis baris dan toleran,
 * sehingga berkas yang belum ditutup pun bisa dibaca: yang sudah ter-flush terbaca,
 * sisanya menunggu flush berikutnya.
 */
public final class LiveTrack {
    private LiveTrack() {}

    /**
     * Baca rute dari locator GPX yang sedang ditulis.
     *
     * @return daftar titik (bisa kosong), atau null kalau locator belum ada /
     *         berkas belum bisa dibaca.
     */
    public static List<GpxReader.P> read(Context c, String loc) {
        if (loc == null) return null;
        try {
            File f = GpxStore.materialize(c, loc);
            if (f == null || !f.exists() || f.length() == 0) return null;
            return GpxReader.read(f);
        } catch (Throwable t) {
            return null;
        }
    }
}
