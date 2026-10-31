package id.cyclocomp;

/**
 * Sepeda di Garasi.
 *
 * Odometer tidak disimpan sebagai angka yang bertambah: yang disimpan hanya
 * OFFSET awal, lalu jarak semua gowes yang memakai sepeda ini ditambahkan saat
 * dibaca (lihat {@link GarasiStore#ringkas}). Dengan begitu menghapus satu
 * gowes atau mengganti kaitan sepeda tidak pernah membuat odometer basi.
 */
public class Bike {
    public long id;
    public String name = "";
    public String type = "";
    /** Jarak yang sudah ditempuh SEBELUM aplikasi mencatat gowes sepeda ini. */
    public double odoOffsetM;
    public long createdMs;

    /** Nama untuk chip: "Nama · Jenis", atau nama saja kalau jenis kosong. */
    public String label() {
        return type == null || type.trim().isEmpty() ? name : name + " · " + type;
    }
}
