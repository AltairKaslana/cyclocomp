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
    final String label, tag, dist, sub;

    RowItem(String label, String tag, String dist, String sub) {
        this.label = label;
        this.tag = tag;
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
        TextView label, tag, dist, sub;
    }

    @Override
    public View getView(int pos, View convert, ViewGroup parent) {
        Holder h;
        if (convert == null) {
            convert = inf.inflate(R.layout.item_ride, parent, false);
            h = new Holder();
            h.label = (TextView) convert.findViewById(R.id.tvLabel);
            h.tag = (TextView) convert.findViewById(R.id.tvTag);
            h.dist = (TextView) convert.findViewById(R.id.tvDist);
            h.sub = (TextView) convert.findViewById(R.id.tvSub);

            Context c = getContext();
            h.label.setTypeface(Ui.body(c));
            h.tag.setTypeface(Ui.body(c));
            h.dist.setTypeface(Ui.condBold(c));   // angka utama: condensed tebal
            h.sub.setTypeface(Ui.body(c));

            convert.setTag(h);
        } else {
            h = (Holder) convert.getTag();
        }

        RowItem it = getItem(pos);
        if (it == null) return convert;

        h.label.setText(it.label);
        h.dist.setText(it.dist);
        h.sub.setText(it.sub);

        // Tag (jumlah lap) tidak selalu ada. Kalau tidak ada, disembunyikan — bukan
        // dikosongkan, supaya ruangnya ikut hilang dan baris tidak punya celah aneh.
        if (it.tag == null || it.tag.length() == 0) {
            h.tag.setVisibility(View.GONE);
        } else {
            h.tag.setVisibility(View.VISIBLE);
            h.tag.setText(it.tag);
        }
        return convert;
    }
}
