package id.cyclocomp;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Hitungan tujuh hari terakhir untuk kartu di layar Riwayat.
 *
 * Jendelanya hari ini dan enam hari sebelumnya menurut kalender LOKAL, bukan
 * tujuh kali 24 jam. Bedanya terlihat saat gowes dilakukan pagi hari: dengan
 * hitungan 7x24 jam, gowes seminggu yang lalu pukul 06.00 baru keluar dari
 * hitungan besok pukul 06.00, padahal batang hari itu sudah kosong. Batang
 * per hari dan angka total harus memakai batas yang sama, kalau tidak keduanya
 * saling bertentangan di layar yang sama.
 *
 * Ini tetap BUKAN minggu kalender (Senin-Minggu): kalau dibuka hari Minggu,
 * minggu kalender baru saja berganti dan totalnya nol - terlihat seperti data
 * hilang padahal gowes baru saja selesai kemarin.
 *
 * Kelas ini bebas android.* supaya bisa diuji di JVM (lihat MingguTest).
 */
final class Minggu {
    private Minggu() {
    }

    static final int HARI = 7;

    /** Tengah malam lokal pada hari yang memuat waktuMs. */
    static long awalHari(long waktuMs, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(waktuMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * Delapan batas hari, urut lama ke baru:
     * [0] awal hari tertua, [6] awal hari ini, [7] awal hari besok.
     *
     * Memakai Calendar.add(DAY_OF_MONTH) supaya perubahan zona waktu dan
     * daylight saving tetap menghasilkan tengah malam lokal yang benar.
     */
    static long[] batas(long sekarangMs, TimeZone tz) {
        long[] b = new long[HARI + 1];
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(awalHari(sekarangMs, tz));
        c.add(Calendar.DAY_OF_MONTH, -(HARI - 1));
        for (int i = 0; i < b.length; i++) {
            b[i] = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return b;
    }

    /**
     * Jarak per hari, urut lama (indeks 0) sampai hari ini (indeks 6).
     *
     * Durasi hari bisa 23 atau 25 jam saat daylight saving; batas yang dibuat
     * Calendar sudah menangani itu, jadi di sini cukup membandingkan waktu.
     */
    static double[] jarak(long sekarangMs, long[] mulaiMs, double[] meter, TimeZone tz) {
        double[] out = new double[HARI];
        long[] b = batas(sekarangMs, tz);
        int n = Math.min(mulaiMs == null ? 0 : mulaiMs.length,
                meter == null ? 0 : meter.length);
        for (int i = 0; i < n; i++) {
            long t = mulaiMs[i];
            if (t < b[0] || t >= b[HARI]) continue;
            for (int k = HARI - 1; k >= 0; k--) {
                if (t >= b[k]) {
                    out[k] += meter[i];
                    break;
                }
            }
        }
        return out;
    }
}
