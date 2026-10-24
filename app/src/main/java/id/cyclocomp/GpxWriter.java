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
    private int count;

    public GpxWriter(File file, String name) throws IOException {
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write("<gpx version=\"1.1\" creator=\"Cyclocomp\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        w.write("<trk><name>" + name + "</name><trkseg>\n");
    }

    public void add(Location l) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "<trkpt lat=\"%.6f\" lon=\"%.6f\">", l.getLatitude(), l.getLongitude()));
        if (l.hasAltitude()) sb.append(String.format(Locale.US, "<ele>%.1f</ele>", l.getAltitude()));
        sb.append("<time>").append(iso.format(new Date(l.getTime()))).append("</time></trkpt>\n");
        w.write(sb.toString());
        if (++count % 10 == 0) w.flush();
    }

    /**
     * Tutup GPX yang terputus (HP mati / proses dibunuh) dengan menambahkan footer.
     * Tanpa footer, banyak aplikasi peta menolak membuka berkasnya.
     */
    static void closeTruncated(java.io.File f) {
        if (f == null || !f.exists()) return;
        try {
            String isi = baca(f);
            if (isi.contains("</gpx>")) return;      // sudah utuh
            StringBuilder sb = new StringBuilder(isi);
            if (!isi.contains("</trkseg>")) sb.append("</trkseg>\n");
            if (!isi.contains("</trk>")) sb.append("</trk>\n");
            sb.append("</gpx>\n");
            java.io.Writer w = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(f, false), "UTF-8");
            w.write(sb.toString());
            w.close();
        } catch (Throwable ignored) { }
    }

    private static String baca(java.io.File f) throws java.io.IOException {
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    public void close() {
        try {
            w.write("</trkseg></trk>\n</gpx>\n");
            w.close();
        } catch (IOException ignored) {
        }
    }
}
