package id.cyclocomp;

/**
 * Pembaca Measurement Cycling Speed and Cadence (0x2A5B).
 *
 * Formatnya (Bluetooth SIG): satu byte flag, lalu data roda dan/atau engkol
 * bila bitnya menyala.
 *   bit0 = data roda:  jumlah putaran (uint32) + waktu event (uint16, 1/1024 dtk)
 *   bit1 = data engkol: jumlah putaran (uint16) + waktu event (uint16, 1/1024 dtk)
 *
 * Sensor mengirim ANGKA KUMULATIF, bukan kecepatan. Jadi parser harus
 * menyimpan nilai terakhir, menghitung selisih, dan menangani wrap-around:
 * waktu event kembali ke 0 tiap 64 detik, putaran engkol tiap 65.536 putaran,
 * putaran roda tiap 4.294.967.296 putaran.
 *
 * Kelas ini bebas android.* supaya seluruh aritmetikanya bisa diuji di JVM
 * (lihat CscParserTest). Tidak ada thread, tidak ada state global.
 */
public final class CscParser {

    /** Hasil satu notifikasi; nilai < 0 berarti kanal itu tidak ada di paket ini. */
    public static final class Hasil {
        public float speedMps = -1f;
        public int cadenceRpm = -1;

        public boolean adaKecepatan() {
            return speedMps >= 0f;
        }

        public boolean adaKadensi() {
            return cadenceRpm >= 0;
        }
    }

    /**
     * Batas kewajaran. Nilai di luar ini biasanya berarti sensor baru
     * tersambung (selisih dari data lama jadi sangat besar), bukan sepeda
     * melaju 200 km/jam. Paket seperti itu dipakai sebagai titik awal baru.
     */
    private static final float MAX_SPEED_MPS = 40f;      // 144 km/jam
    private static final int MAX_CADENCE_RPM = 250;
    private static final long MAX_GAP_TICKS = 20_000;    // ~19,5 detik

    private float kelilingM;
    private long wheelRev;
    private int wheelTime;
    private int crankRev;
    private int crankTime;
    private boolean wheelPertama = true;
    private boolean crankPertama = true;

    public CscParser(int kelilingMm) {
        this.kelilingM = Roda.meter(kelilingMm);
    }

    public void setKelilingMm(int mm) {
        kelilingM = Roda.meter(mm);
    }

    /** Lupakan titik acuan; panggil saat sensor tersambung ulang. */
    public void reset() {
        wheelPertama = true;
        crankPertama = true;
    }

    /**
     * Baca satu notifikasi 0x2A5B.
     *
     * Paket pertama hanya dipakai sebagai acuan (belum ada selisih), jadi
     * hasilnya kosong. Paket yang rusak atau tidak masuk akal juga
     * mengembalikan hasil kosong, bukan angka palsu.
     */
    public Hasil parse(byte[] v) {
        Hasil h = new Hasil();
        if (v == null || v.length < 1) return h;

        int flags = v[0] & 0xFF;
        int i = 1;

        if ((flags & 0x01) != 0) {             // data roda
            if (v.length < i + 6) return h;
            long rev = u32(v, i);
            int time = u16(v, i + 4);
            i += 6;

            if (!wheelPertama) {
                long dRev = (rev - wheelRev) & 0xFFFFFFFFL;
                int dTick = (time - wheelTime) & 0xFFFF;
                float speed = kecepatan(dRev, dTick);
                if (speed >= 0f) h.speedMps = speed;
            }
            wheelRev = rev;
            wheelTime = time;
            wheelPertama = false;
        }

        if ((flags & 0x02) != 0) {             // data engkol
            if (v.length < i + 4) return h;
            int rev = u16(v, i);
            int time = u16(v, i + 2);

            if (!crankPertama) {
                int dRev = (rev - crankRev) & 0xFFFF;
                int dTick = (time - crankTime) & 0xFFFF;
                int rpm = kadensi(dRev, dTick);
                if (rpm >= 0) h.cadenceRpm = rpm;
            }
            crankRev = rev;
            crankTime = time;
            crankPertama = false;
        }

        return h;
    }

    private float kecepatan(long dRev, int dTick) {
        if (dTick <= 0 || dTick > MAX_GAP_TICKS) return -1f;
        // Putaran yang mustahil banyaknya (mis. setelah reconnect) jangan
        // dihitung: hasilnya kecepatan tak masuk akal.
        if (dRev > 10_000) return -1f;
        float mps = (float) (dRev * kelilingM / (dTick / 1024.0));
        if (mps < 0f || mps > MAX_SPEED_MPS) return -1f;
        return mps;
    }

    private int kadensi(int dRev, int dTick) {
        if (dTick <= 0 || dTick > MAX_GAP_TICKS) return -1;
        if (dRev > 1_000) return -1;
        int rpm = (int) Math.round(dRev * 60.0 * 1024.0 / dTick);
        if (rpm < 0 || rpm > MAX_CADENCE_RPM) return -1;
        return rpm;
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 0xFFL)
                | ((b[at + 1] & 0xFFL) << 8)
                | ((b[at + 2] & 0xFFL) << 16)
                | ((b[at + 3] & 0xFFL) << 24);
    }
}
