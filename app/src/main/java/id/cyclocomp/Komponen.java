package id.cyclocomp;

/**
 * Satu komponen yang dirawat (rantai, ban, oli) dan batas jaraknya.
 *
 * Semua hitungan status adalah fungsi murni dari odometer sepeda, jadi bisa
 * diuji tanpa basis data. Status memakai batas sesuai rancangan 5.7:
 * BAIK &lt; 80%, SEGERA 80-100%, LEWAT &gt; 100%.
 */
public class Komponen {
    public static final int BAIK = 0, SEGERA = 1, LEWAT = 2;

    /** Ambang SEGERA dalam persen batas. */
    public static final int AMBANG_SEGERA = 80;

    public long id, bikeId;
    public String name = "";
    /** Batas jarak dalam meter; harus > 0. */
    public double limitM;
    /** Odometer sepeda saat komponen ini mulai dipakai. */
    public double startOdoM;

    /** Jarak terpakai sejak komponen dipasang; tidak pernah negatif. */
    public double terpakaiM(double odoM) {
        double m = odoM - startOdoM;
        return m > 0 ? m : 0;
    }

    /**
     * Persentase pemakaian untuk teks dan bar. Tidak dijepit di 100 supaya
     * pengguna bisa melihat seberapa lewat (mis. 112%), tetapi pemanggil yang
     * menggambar bar harus menjepitnya sendiri.
     */
    public int persen(double odoM) {
        if (limitM <= 0) return 0;
        return (int) Math.floor(terpakaiM(odoM) * 100.0 / limitM);
    }

    public int status(double odoM) {
        if (limitM <= 0) return BAIK;
        double t = terpakaiM(odoM);
        // Perbandingan perkalian, bukan pembagian: pembulatan persen tidak
        // boleh memindahkan komponen yang tepat 80% ke BAIK atau yang tepat
        // 100% ke LEWAT.
        if (t > limitM) return LEWAT;
        if (t * 100.0 >= limitM * AMBANG_SEGERA) return SEGERA;
        return BAIK;
    }

    /** Sisa jarak sebelum batas; 0 kalau sudah lewat. */
    public double sisaM(double odoM) {
        double s = limitM - terpakaiM(odoM);
        return s > 0 ? s : 0;
    }

    /** Jarak kelebihan setelah batas; 0 kalau belum lewat. */
    public double lebihM(double odoM) {
        double l = terpakaiM(odoM) - limitM;
        return l > 0 ? l : 0;
    }
}
