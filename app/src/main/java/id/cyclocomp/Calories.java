package id.cyclocomp;

/**
 * Perkiraan kalori yang terbakar saat gowes.
 *
 * Strava menghitung kalori dari DAYA (watt), bukan sekadar dari jarak — itu sebabnya
 * angkanya masuk akal saat tanjakan: 30 km datar dan 30 km menanjak tidak sama.
 * Zylo tidak punya power meter, jadi dayanya DIPERKIRAAN dari fisika: gaya gulung,
 * gaya udara, dan gaya gravitasi tanjakan.
 *
 * Semua metode statis dan tidak menyentuh kelas Android apa pun, sehingga bisa
 * diuji di JVM biasa tanpa Robolectric (pola yang sama dipakai {@link RideStats}).
 *
 * Catatan kejujuran: ini PERKIRAAN, bukan pengukuran. Arah kesalahannya sengaja
 * konservatif (cenderung lebih rendah daripada kebanyakan aplikasi) supaya tidak
 * membesar-besarkan. Tanpa detak jantung sungguan, tidak ada cara lebih tepat.
 */
final class Calories {
    private Calories() {}

    private static final double G = 9.80665;       // gravitasi, m/s^2
    private static final double RHO = 1.2;         // massa jenis udara, kg/m^3
    private static final double CRR = 0.005;       // koefisien gulung ban jalan
    private static final double CDA = 0.36;        // luas hambatan udara, m^2 (posisi hoods)
    private static final double BIKE_KG = 10.0;    // massa sepeda jalan rata-rata
    private static final double ETA = 0.24;        // efisiensi otot manusia
    private static final double DRIVETRAIN = 0.97; // rugi rangkaian gir
    private static final double KCAL_PER_J = 1.0 / 4184.0;

    /** Massa pengendela + sepeda. Dibatasi bawah supaya berat yang salah tetap aman. */
    private static double totalMass(double riderKg) {
        double r = riderKg;
        if (Double.isNaN(r) || r < 20.0) r = 20.0;
        if (r > 200.0) r = 200.0;
        return r + BIKE_KG;
    }

    /**
     * Perkiraan kalori (kcal).
     *
     * @param distanceM jarak BERGERAK, meter (bukan jarak GPS mentah)
     * @param movingMs  waktu BERGERAK, milidetik
     * @param elevGainM total kenaikan, meter — sudah tersaring dari noise ketinggian
     *                   oleh {@link RideStats#updateElevation}
     * @param riderKg   massa pengendara, kg
     * @return kcal bulat, selalu >= 0; 0 kalau masukan tidak masuk akal
     */
    static int estimate(double distanceM, long movingMs, double elevGainM, double riderKg) {
        if (!(distanceM > 0) || movingMs <= 0) return 0;
        double sec = movingMs / 1000.0;
        double v = distanceM / sec;               // kecepatan rata-rata, m/s
        double m = totalMass(riderKg);

        double pRoll = CRR * m * G * v;                     // ban & jalan
        double pAir  = 0.5 * RHO * CDA * v * v * v;         // tumbukan udara (kubik!)

        // Gradien rata-rata = total kenaikan / jarak. Ditahan di >= 0: tubuh tidak
        // mengisi ulang energi saat turunan, jadi jalan turun tidak boleh menghasilkan
        // kalori negatif yang memakan habis hasil jalan datar.
        double grade = Math.max(0.0, elevGainM / distanceM);
        double pClimb = m * G * v * grade;

        // Rugi gir berarti kaki harus menghasilkan LEBIH besar daripada yang sampai ke roda.
        double watts = (pRoll + pAir + pClimb) / DRIVETRAIN;
        double kcal = watts * sec * KCAL_PER_J / ETA;

        if (Double.isNaN(kcal) || Double.isInfinite(kcal) || kcal <= 0) return 0;
        return (int) Math.round(kcal);
    }

    /**
     * Daya rata-rata yang diperkirakan (watt). Dipakai layar Ringkasan untuk
     * menjelaskan DARI MANA angka kalori datang, bukan angka ajaib.
     */
    static int avgWatts(double distanceM, long movingMs, double elevGainM, double riderKg) {
        int kcal = estimate(distanceM, movingMs, elevGainM, riderKg);
        if (kcal <= 0 || movingMs <= 0) return 0;
        double watts = kcal * 4184.0 * ETA / (movingMs / 1000.0);
        if (Double.isNaN(watts) || Double.isInfinite(watts)) return 0;
        return (int) Math.round(watts);
    }
}
