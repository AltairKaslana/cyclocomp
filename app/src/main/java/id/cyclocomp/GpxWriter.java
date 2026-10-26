package id.cyclocomp;

import android.location.Location;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Menulis GPX secara bertahap, jadi data tidak hilang total kalau HP mati di tengah jalan. */
public class GpxWriter {
    private final BufferedWriter w;
    private final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private final TimeFix timeFix = new TimeFix();
    private int count;

    public GpxWriter(File file, String name) throws IOException {
        this(new FileOutputStream(file), name);
    }

    /** Versi stream: dipakai waktu berkas tujuannya MediaStore (content://). */
    public GpxWriter(OutputStream os, String name) throws IOException {
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        w = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write("<gpx version=\"1.1\" creator=\"Cyclocomp\""
                + " xmlns=\"http://www.topografix.com/GPX/1/1\""
                + " xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n");
        w.write("<trk><name>" + name + "</name><trkseg>\n");
    }

    public void add(Location l) throws IOException {
        add(l, -1);
    }

    /**
     * @param hr detak jantung saat titik ini (bpm); <= 0 berarti tidak ada data.
     *           Ditulis sebagai TrackPointExtension, format yang dibaca Strava/Garmin.
     */
    public void add(Location l, int hr) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "<trkpt lat=\"%.6f\" lon=\"%.6f\">", l.getLatitude(), l.getLongitude()));
        if (l.hasAltitude()) sb.append(String.format(Locale.US, "<ele>%.1f</ele>", l.getAltitude()));
        sb.append("<time>").append(iso.format(new Date(timeFix.sanitize(l.getTime())))).append("</time>");
        if (hr > 0) {
            sb.append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>")
              .append(hr)
              .append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>");
        }
        sb.append("</trkpt>\n");
        w.write(sb.toString());
        if (++count % 10 == 0) w.flush();
    }

    /**
     * Tutup GPX yang terputus (HP mati / proses dibunuh) dengan menambahkan footer.
     * Tanpa footer, banyak aplikasi peta menolak membuka berkasnya.
     *
     * @param loc locator dari {@link GpxStore} (path biasa atau content://)
     */
    static void closeTruncated(android.content.Context c, String loc) {
        if (loc == null) return;
        try {
            String isi = GpxStore.readAll(c, loc);
            if (isi.contains("</gpx>")) return;      // sudah utuh
            StringBuilder sb = new StringBuilder(isi);
            if (!isi.contains("</trkseg>")) sb.append("</trkseg>\n");
            if (!isi.contains("</trk>")) sb.append("</trk>\n");
            sb.append("</gpx>\n");
            GpxStore.writeAll(c, loc, sb.toString());
        } catch (Throwable ignored) { }
    }

    public void close() {
        try {
            w.write("</trkseg></trk>\n</gpx>\n");
            w.close();
        } catch (IOException ignored) {
        }
    }
}
