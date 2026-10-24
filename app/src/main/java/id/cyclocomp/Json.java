package id.cyclocomp;

import java.util.Locale;

/**
 * Pembaca JSON minimal - hanya membaca nilai, tidak pernah menulis.
 *
 * Kenapa ada: aplikasi ini tanpa dependency, sedangkan jawaban Strava (token, status
 * unggahan) perlu dibaca. Kebutuhan sempit: ambil satu field bernama tertentu dari
 * objek JSON datar. Pencarian kunci dicocokkan **persis** lewat tanda kutip, jadi
 * "id" tidak akan tertukar dengan "id_str" atau "external_id".
 *
 * Kalau suatu saat perlu menulis JSON atau membaca struktur bersarang, ganti kelas
 * ini dengan parser sungguhan - jangan diperluas setengah hati.
 */
final class Json {

    private Json() {}

    /** Nilai string untuk key, atau null kalau tidak ada / bernilai null. */
    static String str(String json, String key) {
        if (json == null) return null;
        int i = indexOfKey(json, key);
        if (i < 0) return null;
        int p = skipWs(json, i);
        if (p >= json.length() || json.charAt(p) != ':') return null;
        p = skipWs(json, p + 1);
        if (p >= json.length()) return null;

        if (json.charAt(p) == '"') {
            StringBuilder sb = new StringBuilder();
            for (int j = p + 1; j < json.length(); j++) {
                char d = json.charAt(j);
                if (d == '\\' && j + 1 < json.length()) {
                    char e = json.charAt(++j);
                    if (e == 'n') sb.append('\n');
                    else if (e == 't') sb.append('\t');
                    else if (e == 'r') sb.append('\r');
                    else if (e == 'u' && j + 4 < json.length()) {
                        try {
                            sb.append((char) Integer.parseInt(json.substring(j + 1, j + 5), 16));
                            j += 4;
                        } catch (NumberFormatException ignored) { }
                    } else sb.append(e);
                    continue;
                }
                if (d == '"') return sb.toString();
                sb.append(d);
            }
            return null;                       // string tidak ditutup
        }

        int end = p;
        while (end < json.length() && ",}] \t\r\n".indexOf(json.charAt(end)) < 0) end++;
        String raw = json.substring(p, end).trim();
        return "null".equals(raw) ? null : raw;
    }

    /** Nilai angka untuk key, atau NaN kalau tidak ada / bukan angka. */
    static double num(String json, String key) {
        String raw = str(json, key);
        if (raw == null) return Double.NaN;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** true kalau key ada dan nilainya true. */
    static boolean bool(String json, String key) {
        return "true".equals(str(json, key));
    }

    /**
     * Posisi karakter setelah nama kunci (di depan tanda ':'), atau -1.
     *
     * Pencocokan memakai tanda kutip pembuka dan penutup, jadi kunci yang namanya
     * lanjutan kunci lain (mis. "id" vs "id_str") tidak akan tertukar.
     */
    private static int indexOfKey(String json, String key) {
        String needle = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int i = json.indexOf(needle, from);
            if (i < 0) return -1;
            int after = skipWs(json, i + needle.length());
            if (after < json.length() && json.charAt(after) == ':') return after;
            from = i + needle.length();
        }
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') i++;
            else break;
        }
        return i;
    }

    /** Untuk pemeriksaan di log: ringkas JSON supaya tidak memenuhi buffer. */
    static String brief(String json) {
        if (json == null) return "null";
        String s = json.replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /** Format angka tanpa notasi ilmiah, untuk pesan ke pengguna. */
    static String numStr(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "-";
        return String.format(Locale.US, "%.0f", v);
    }
}
