package id.cyclocomp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Map;

/**
 * Koneksi ke Strava: OAuth2 + unggah berkas GPX.
 *
 * ## Kenapa alurnya seperti ini
 *
 * Strava mensyaratkan `redirect_uri` berada di domain callback yang didaftarkan aplikasi,
 * **kecuali localhost dan 127.0.0.1 yang diizinkan langsung**. Karena aplikasi ini tidak
 * punya server sendiri, jalur yang dipakai adalah *loopback*: aplikasi membuka soket di
 * 127.0.0.1, meminta pengguna membuka halaman izin Strava di peramban, lalu menangkap
 * pengalihan kembali ke alamat lokal itu. Tidak perlu intent-filter, tidak perlu domain,
 * dan tidak perlu WebView (WebView dilarang untuk login, lihat catatan Google tentang
 * OAuth di webview).
 *
 * ## Token
 *
 * Token akses berumur pendek; token penyegar dipakai untuk memperbarui. Penyegaran dicoba
 * otomatis sekali kalau jawabannya 401, supaya token kedaluwarsa tidak membuat unggahan
 * gagal tanpa penjelasan.
 *
 * ## Unggahan
 *
 * Strava memproses unggahan secara asynchronous: POST /uploads langsung mengembalikan id,
 * lalu status harus ditanyakan berulang kali sampai aktivitas siap.
 */
final class Strava {

    private Strava() {}

    private static final String TAG = "Strava";
    private static final String AUTHORIZE = "https://www.strava.com/oauth/authorize";
    private static final String TOKEN = "https://www.strava.com/oauth/token";
    private static final String UPLOADS = "https://www.strava.com/api/v3/uploads";

    /** Batas waktu menunggu pengguna selesai login di peramban. */
    private static final int AUTH_TIMEOUT_MS = 180000;
    /** Berapa lama status unggahan ditanyakan sebelum menyerah. */
    private static final int POLL_MAX = 20;
    private static final int POLL_MS = 1500;

    // ---------- OAuth ----------

    /** URL halaman izin. redirect_uri memakai port acak yang baru dibuat. */
    static String authorizeUrl(Context c, String redirectUri) {
        return AUTHORIZE
                + "?client_id=" + Http.encode(StravaStore.clientId(c))
                + "&response_type=code"
                + "&redirect_uri=" + Http.encode(redirectUri)
                + "&approval_prompt=auto"
                + "&scope=activity:write";
    }

    /**
     * Jalankan seluruh alur OAuth sampai token tersimpan.
     *
     * **Memblokir** - harus dipanggil dari thread latar, bukan thread UI.
     *
     * @return null kalau berhasil, atau pesan galat yang sudah bisa ditampilkan.
     */
    static String runAuth(Context c) {
        if (StravaStore.clientId(c).isEmpty()) return c.getString(R.string.strava_need_id);
        if (StravaStore.clientSecret(c).isEmpty()) return c.getString(R.string.strava_need_secret);

        ServerSocket server = null;
        Socket sock = null;
        try {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            String redirect = "http://127.0.0.1:" + server.getLocalPort() + "/callback";

            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl(c, redirect)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);

            server.setSoTimeout(AUTH_TIMEOUT_MS);
            sock = server.accept();

            BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
            String line = r.readLine();                 // "GET /callback?code=... HTTP/1.1"
            if (line == null) return c.getString(R.string.strava_auth_empty);

            String query = "";
            int sp = line.indexOf(' ');
            int q = line.indexOf('?', sp);
            if (q > 0) {
                int end = line.indexOf(' ', q);
                query = line.substring(q + 1, end < 0 ? line.length() : end);
            }

            String code = param(query, "code");
            String err = param(query, "error");
            reply(sock, c);

            if (err != null) return c.getString(R.string.strava_auth_denied);
            if (code == null || code.isEmpty()) return c.getString(R.string.strava_auth_empty);

            return exchange(c, code);
        } catch (IOException e) {
            Log.w(TAG, "oauth gagal", e);
            return c.getString(R.string.strava_auth_timeout);
        } catch (Throwable t) {
            Log.e(TAG, "oauth error tak terduga", t);
            return c.getString(R.string.strava_failed);
        } finally {
            close(sock);
            close(server);
        }
    }

    /** Kirim halaman kecil ke peramban supaya pengguna tahu proses sudah selesai. */
    private static void reply(Socket sock, Context c) {
        try {
            String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width'>"
                    + "<title>Cyclocomp</title></head>"
                    + "<body style='background:#0E1013;color:#fff;font-family:sans-serif;"
                    + "text-align:center;padding-top:40vh'>"
                    + "<p style='font-size:18px'>" + esc(c.getString(R.string.strava_auth_done)) + "</p>"
                    + "</body></html>";
            byte[] body = html.getBytes("UTF-8");
            OutputStream o = sock.getOutputStream();
            o.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                    + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            o.write(body);
            o.flush();
        } catch (Throwable ignored) {
            // peramban sudah ditutup, tidak masalah
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Tukar kode otorisasi dengan token akses + token penyegar. */
    private static String exchange(Context c, String code) {
        try {
            Map<String, String> f = Http.fields(
                    "client_id", StravaStore.clientId(c),
                    "client_secret", StravaStore.clientSecret(c),
                    "code", code,
                    "grant_type", "authorization_code");
            String body = Http.postMultipart(TOKEN, f, null, null, null, null);
            return save(c, body);
        } catch (Http.HttpError e) {
            Log.w(TAG, "tukar token gagal: " + e.code + " " + Json.brief(e.body));
            return c.getString(R.string.strava_auth_failed);
        } catch (IOException e) {
            Log.w(TAG, "tukar token gagal (jaringan)", e);
            return c.getString(R.string.strava_network);
        }
    }

    /** Perbarui token akses memakai token penyegar. */
    private static String refresh(Context c) {
        if (StravaStore.refreshToken(c).isEmpty()) return c.getString(R.string.strava_reauth);
        try {
            Map<String, String> f = Http.fields(
                    "client_id", StravaStore.clientId(c),
                    "client_secret", StravaStore.clientSecret(c),
                    "refresh_token", StravaStore.refreshToken(c),
                    "grant_type", "refresh_token");
            String body = Http.postMultipart(TOKEN, f, null, null, null, null);
            return save(c, body);
        } catch (Http.HttpError e) {
            Log.w(TAG, "segar token gagal: " + e.code + " " + Json.brief(e.body));
            return c.getString(R.string.strava_reauth);
        } catch (IOException e) {
            Log.w(TAG, "segar token gagal (jaringan)", e);
            return c.getString(R.string.strava_network);
        }
    }

    private static String save(Context c, String body) {
        String at = Json.str(body, "access_token");
        if (at == null || at.isEmpty()) {
            String m = Json.str(body, "message");
            if (m == null) m = Json.str(body, "error");
            Log.w(TAG, "token tidak diterima: " + Json.brief(body));
            return m != null ? m : c.getString(R.string.strava_auth_failed);
        }
        StravaStore.accessToken(c, at);
        String rt = Json.str(body, "refresh_token");
        if (rt != null && !rt.isEmpty()) StravaStore.refreshToken(c, rt);
        double exp = Json.num(body, "expires_at");
        if (!Double.isNaN(exp)) StravaStore.expiresAt(c, (long) exp);

        // nama atlet hanya untuk ditampilkan di Pengaturan; gagal ambil bukan galat
        String first = Json.str(body, "firstname");
        String last = Json.str(body, "lastname");
        if (first != null) {
            String n = (first + (last == null || last.isEmpty() ? "" : " " + last)).trim();
            StravaStore.athlete(c, n);
        }
        return null;
    }

    // ---------- unggah ----------

    /**
     * Unggah berkas GPX ke Strava dan tunggu sampai aktivitasnya siap.
     *
     * **Memblokir** - panggil dari thread latar.
     *
     * @return null kalau berhasil, atau pesan galat yang bisa ditampilkan.
     */
    static String upload(Context c, File gpx) {
        if (gpx == null || !gpx.exists()) return c.getString(R.string.gpx_missing);
        if (!StravaStore.ready(c)) return c.getString(R.string.strava_not_ready);

        String err = ensureToken(c);
        if (err != null) return err;

        String up;
        try {
            Map<String, String> f = Http.fields("data_type", "gpx");
            up = Http.postMultipart(UPLOADS, f, "file", gpx, "application/gpx+xml",
                    StravaStore.accessToken(c));
        } catch (Http.HttpError e) {
            if (e.code == 401) {                       // token kedaluwarsa di tengah jalan
                err = refresh(c);
                if (err != null) return err;
                try {
                    Map<String, String> f = Http.fields("data_type", "gpx");
                    up = Http.postMultipart(UPLOADS, f, "file", gpx, "application/gpx+xml",
                            StravaStore.accessToken(c));
                } catch (Http.HttpError e2) {
                    return fail(c, e2, "unggah");
                } catch (IOException e2) {
                    return c.getString(R.string.strava_network);
                }
            }
            return fail(c, e, "unggah");
        } catch (IOException e) {
            Log.w(TAG, "unggah gagal (jaringan)", e);
            return c.getString(R.string.strava_network);
        }

        double id = Json.num(up, "id");
        if (Double.isNaN(id)) return c.getString(R.string.strava_upload_failed);

        for (int i = 0; i < POLL_MAX; i++) {
            try { Thread.sleep(POLL_MS); } catch (InterruptedException ignored) { }
            String st;
            try {
                st = Http.get(UPLOADS + "/" + Json.numStr(id), StravaStore.accessToken(c));
            } catch (Http.HttpError e) {
                if (e.code == 401) {
                    err = refresh(c);
                    if (err != null) return err;
                    continue;
                }
                return fail(c, e, "tanya status");
            } catch (IOException e) {
                return c.getString(R.string.strava_network);
            }

            String status = Json.str(st, "status");
            double act = Json.num(st, "activity_id");
            if (!Double.isNaN(act) && act > 0) return null;             // siap
            if (status != null && status.toLowerCase(Locale.US).contains("error")) {
                String m = Json.str(st, "error");
                return m != null && !m.isEmpty() ? m : c.getString(R.string.strava_upload_failed);
            }
        }
        return c.getString(R.string.strava_upload_slow);
    }

    /** Pastikan token akses masih berlaku; segarkan kalau perlu. */
    private static String ensureToken(Context c) {
        if (!StravaStore.expired(c)) return null;
        return refresh(c);
    }

    private static String fail(Context c, Http.HttpError e, String where) {
        Log.w(TAG, where + " gagal: HTTP " + e.code + " " + Json.brief(e.body));
        if (e.code == 429) return c.getString(R.string.strava_ratelimit);
        String m = Json.str(e.body, "message");
        if (m == null || m.isEmpty()) m = Json.str(e.body, "error");
        if (m == null || m.isEmpty()) m = c.getString(R.string.strava_upload_failed);
        return m;
    }

    // ---------- util ----------

    /**
     * Ambil nilai parameter dari query string.
     *
     * Dekode percent-encoding ditulis sendiri, bukan lewat {@code android.net.Uri}, supaya
     * fungsi ini bisa diuji di JVM tanpa Robolectric.
     */
    static String param(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            if (!key.equals(pair.substring(0, eq))) continue;
            return decode(pair.substring(eq + 1));
        }
        return null;
    }

    /** Dekode percent-encoding (UTF-8). */
    static String decode(String s) {
        if (s == null || s.indexOf('%') < 0) return s;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                try {
                    bo.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException ignored) { }
            }
            if (c == '+') { bo.write(' '); continue; }
            // karakter non-ASCII ditulis sebagai byte UTF-8
            if (c > 127) {
                byte[] b = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                bo.write(b, 0, b.length);
            } else {
                bo.write((byte) c);
            }
        }
        return new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void close(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) { }
    }
}
