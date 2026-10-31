package id.cyclocomp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/** Isi satu baris daftar: label di atas, jarak sebagai angka utama, keterangan di bawah. */
final class RowItem {
    /** Id rekaman; 0 untuk baris lap (tidak punya pratinjau maupun aksi hapus). */
    final long id;
    final String label, dist, sub;
    final String tag;
    final boolean commute;
    /** Pratinjau rute tersimpan; boleh berubah saat rekaman lama diisi di latar. */
    String preview;

    /** Baris lap di layar utama: tanpa id, pratinjau, maupun chip komuter. */
    RowItem(String label, String tag, String dist, String sub) {
        this(0, label, tag, false, null, dist, sub);
    }

    RowItem(long id, String label, String tag, boolean commute, String preview,
            String dist, String sub) {
        this.id = id;
        this.label = label;
        this.tag = tag;
        this.commute = commute;
        this.preview = preview;
        this.dist = dist;
        this.sub = sub;
    }
}

/**
 * Adaptor untuk {@code item_ride.xml} — dipakai daftar riwayat rekaman DAN daftar lap.
 *
 * Kenapa tidak {@code ArrayAdapter<String>} + {@code android.R.layout.simple_list_item_1}
 * seperti sebelumnya: layout bawaan Android memakai font SISTEM, dan
 * {@link Ui#fonts(View, Context)} hanya menyisir pohon view yang SUDAH ada saat
 * dipanggil. Adaptor membuat baris SETELAH itu, jadi barisnya tidak pernah kena
 * Barlow — daftar riwayat dan daftar lap menjadi satu-satunya bagian aplikasi yang
 * tidak memakai font aplikasi sendiri.
 *
 * Karena itu font dipasang eksplisit di sini, sekali per baris baru. Baris yang
 * didaur ulang (convertView) sudah punya fontnya.
 *
 * Pola ViewHolder juga menghindari findViewById berulang saat daftar digulir.
 */
class RowAdapter extends ArrayAdapter<RowItem> {

    private final LayoutInflater inf;

    RowAdapter(Context c, List<RowItem> items) {
        super(c, 0, items);
        this.inf = LayoutInflater.from(c);
    }

    static class Holder {
        TextView label, tag, commute, dist, unit, sub;
        RuteThumb thumb;
        View chipRow;
    }

    @Override
    public View getView(int pos, View convert, ViewGroup parent) {
        Holder h;
        if (convert == null) {
            convert = inf.inflate(R.layout.item_ride, parent, false);
            h = new Holder();
            h.label = (TextView) convert.findViewById(R.id.tvLabel);
            h.tag = (TextView) convert.findViewById(R.id.tvTag);
            h.commute = (TextView) convert.findViewById(R.id.tvCommute);
            h.dist = (TextView) convert.findViewById(R.id.tvDist);
            h.unit = (TextView) convert.findViewById(R.id.tvUnit);
            h.sub = (TextView) convert.findViewById(R.id.tvSub);
            h.thumb = (RuteThumb) convert.findViewById(R.id.tvThumb);
            h.chipRow = convert.findViewById(R.id.chipRow);

            Context c = getContext();
            h.label.setTypeface(Ui.body(c));
            h.tag.setTypeface(Ui.body(c));
            h.commute.setTypeface(Ui.body(c));
            h.dist.setTypeface(Ui.condBold(c));   // angka utama: condensed tebal
            h.unit.setTypeface(Ui.body(c));
            h.sub.setTypeface(Ui.body(c));
            h.commute.setText(R.string.chip_commute);

            convert.setTag(h);
        } else {
            h = (Holder) convert.getTag();
        }

        RowItem it = getItem(pos);
        if (it == null) return convert;

        h.label.setText(it.label);
        h.dist.setText(it.dist);
        h.sub.setText(it.sub);

        // Pratinjau hanya ada pada rekaman baru/terisi. Baris lap dan rekaman
        // lama tanpa rute memakai lebar penuh - lebih baik daripada kotak kosong.
        boolean adaPreview = it.preview != null && it.preview.length() > 0;
        h.thumb.setVisibility(adaPreview ? View.VISIBLE : View.GONE);
        h.thumb.setPreview(adaPreview ? it.preview : null);

        boolean adaTag = it.tag != null && it.tag.length() > 0;
        h.tag.setVisibility(adaTag ? View.VISIBLE : View.GONE);
        if (adaTag) h.tag.setText(it.tag);
        h.commute.setVisibility(it.commute ? View.VISIBLE : View.GONE);
        h.chipRow.setVisibility(adaTag || it.commute ? View.VISIBLE : View.GONE);
        return convert;
    }
}
