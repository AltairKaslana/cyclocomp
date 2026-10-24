package id.cyclocomp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP kecil untuk dua kebutuhan Strava: GET dengan token, dan POST multipart
 * (bertoken, boleh membawa satu berkas).
 *
 * Sengaja ditulis sendiri, bukan memakai OkHttp, karena proyek ini tanpa dependency dan
 * kebutuhan kita sempit. Semua yang dilewatkan ke jaringan harus lewat sini supaya batas
 * waktu dan penanganan galat hanya ada di satu tempat.
 */
final class Http {

    private Http() {}

    private static final int TIMEOUT_MS = 20000;

    /**
     * Galat HTTP yang tetap membawa isi jawaban.
     *
     * Strava menjelaskan penyebab galat di badan jawaban (mis. "Rate Limit Exceeded" atau
     * "Aplication does not have permission..."), jadi badan itu tidak boleh dibuang -
     * pengguna perlu melihat pesan yang sebenarnya, bukan "gagal".
     */
    static final class HttpError extends IOException {
        final int code;
        final String body;
        HttpError(int code, String body) {
            super("HTTP " + code);
            this.code = code;
            this.body = body;
        }
    }

    /** GET dengan header Authorization: Bearer. Mengembalikan isi jawaban. */
    static String get(String urlStr, String bearer) throws IOException {
        return read(open(urlStr, "GET", bearer));
    }

    /**
     * POST multipart/form-data.
     *
     * @param fields kolom teks biasa
     * @param fileField nama kolom untuk berkas, atau null kalau tidak ada berkas
     */
    static String postMultipart(String urlStr, Map<String, String> fields,
                                String fileField, File file, String fileType,
                                String bearer) throws IOException {
        String boundary = "----cyclocomp" + Long.toHexString(System.currentTimeMillis());
        HttpURLConnection c = open(urlStr, "POST", bearer);
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream out = c.getOutputStream();
        try {
            if (fields != null) {
                for (Map.Entry<String, String> e : fields.entrySet()) {
                    out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                    out.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n")
                            .getBytes("UTF-8"));
                    out.write((e.getValue() + "\r\n").getBytes("UTF-8"));
                }
            }
            if (fileField != null && file != null) {
                out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                out.write(("Content-Disposition: form-data; name=\"" + fileField
                        + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes("UTF-8"));
                out.write(("Content-Type: " + fileType + "\r\n\r\n").getBytes("UTF-8"));
                FileInputStream in = new FileInputStream(file);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    in.close();
                }
                out.write("\r\n".getBytes("UTF-8"));
            }
            out.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
        return read(c);
    }

    /** Bentuk peta sederhana supaya pemanggil tidak perlu mengimpor LinkedHashMap. */
    static Map<String, String> fields(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (IOException e) {
            return s;                       // UTF-8 selalu ada di Android
        }
    }

    private static HttpURLConnection open(String urlStr, String method, String bearer)
            throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(false);
        if (bearer != null && !bearer.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        return c;
    }

    /** Baca jawaban; galat HTTP dilempar sebagai {@link HttpError} beserta badannya. */
    private static String read(HttpURLConnection c) throws IOException {
        int code;
        try {
            code = c.getResponseCode();
        } catch (IOException e) {
            throw new HttpError(-1, e.getMessage() == null ? "" : e.getMessage());
        }
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = "";
        if (in != null) {
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                body = new String(bo.toByteArray(), "UTF-8");
            } finally {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
        if (code >= 400) throw new HttpError(code, body);
        return body;
    }
}
