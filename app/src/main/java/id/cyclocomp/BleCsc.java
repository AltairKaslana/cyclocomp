package id.cyclocomp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pembaca sensor kecepatan/kadensi BLE standar (Cycling Speed and Cadence).
 *
 * Layanan 0x1816 dan karakteristik 0x2A5B adalah profil resmi Bluetooth SIG,
 * dipakai bersama oleh sensor roda, sensor engkol, dan banyak sensor gabungan.
 * Aritmetikanya ada di {@link CscParser}; kelas ini hanya mengurus pemindaian,
 * koneksi GATT, notifikasi, dan pembacaan baterai.
 *
 * Alur: pilih perangkat (atau sambung ke MAC tersimpan) -> discoverServices ->
 * aktifkan notifikasi 0x2A5B -> setiap notifikasi diteruskan ke parser.
 * Tidak butuh internet maupun Play Services.
 */
public class BleCsc {

    private static final String TAG = "BleCsc";

    public static final UUID SVC_CSC = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    public static final UUID CH_CSC = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb");
    public static final UUID CH_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID SVC_BATTERY = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID CH_BATTERY = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private static final long RETRY_MS = 5000L;
    private static final int MAX_RETRY = 24;

    public interface Callback {
        /**
         * Satu notifikasi CSC. Kecepatan dalam m/s, kadensi dalam rpm; nilai
         * negatif berarti kanal itu tidak ada di paket ini.
         */
        void onData(float speedMps, int cadenceRpm);

        /** Baterai sensor dalam persen; -1 kalau tidak bisa dibaca. */
        void onBattery(int pct);

        /** Teks status untuk ditampilkan. */
        void onStatus(String text);
    }

    public interface ScanListener {
        void onFound(String name, String mac, boolean hasCscService);

        void onDone(String error);
    }

    private final Context ctx;
    private final Callback cb;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CscParser parser;

    private String mac;
    private BluetoothGatt gatt;
    private boolean wanted;
    private int retry;
    private int battery = -1;
    /** true hanya setelah layanan CSC ditemukan dan notifikasi dinyalakan. */
    private boolean siap;

    public BleCsc(Context c, Callback cb) {
        this.ctx = c.getApplicationContext();
        this.cb = cb;
        this.parser = new CscParser(Roda.BAWAAN_MM);
    }

    public void setWheelMm(int mm) {
        parser.setKelilingMm(mm);
    }

    // ---------- pencarian perangkat ----------

    /**
     * Pindai perangkat sekitar selama ms milidetik. Perangkat yang
     * mengiklankan layanan CSC ditandai supaya bisa ditaruh paling atas;
     * perangkat bernama tetap ditampilkan karena sebagian sensor hanya
     * mengiklankan layanannya setelah tersambung.
     */
    public static void scan(final Context c, final int ms, final ScanListener l) {
        final BluetoothAdapter a = adapter(c);
        if (a == null) { l.onDone(c.getString(R.string.ble_no_bt)); return; }
        try {
            if (!a.isEnabled()) { l.onDone(c.getString(R.string.ble_off)); return; }
        } catch (SecurityException e) {
            l.onDone(c.getString(R.string.ble_no_perm));
            return;
        }
        if (!BleHr.hasPermission(c)) { l.onDone(c.getString(R.string.ble_no_perm)); return; }

        final BluetoothLeScanner sc;
        try {
            sc = a.getBluetoothLeScanner();
        } catch (SecurityException e) {
            l.onDone(c.getString(R.string.ble_no_perm));
            return;
        }
        if (sc == null) { l.onDone(c.getString(R.string.ble_off)); return; }

        final List<String> seen = new ArrayList<String>();

        final ScanCallback cbp = new ScanCallback() {
            @Override
            public void onScanResult(int type, ScanResult r) {
                try {
                    BluetoothDevice d = r.getDevice();
                    if (d == null) return;
                    String addr = d.getAddress();
                    if (addr == null || seen.contains(addr)) return;
                    boolean hasCsc = false;
                    if (r.getScanRecord() != null) {
                        List<ParcelUuid> uuids = r.getScanRecord().getServiceUuids();
                        if (uuids != null) {
                            for (ParcelUuid p : uuids) {
                                if (SVC_CSC.equals(p.getUuid())) { hasCsc = true; break; }
                            }
                        }
                    }
                    String name = null;
                    try { name = d.getName(); } catch (SecurityException ignored) { }
                    if (!hasCsc && (name == null || name.trim().isEmpty())) return;
                    seen.add(addr);
                    l.onFound(name == null ? c.getString(R.string.ble_no_name) : name, addr, hasCsc);
                } catch (Throwable ignored) { }
            }

            @Override
            public void onScanFailed(int errorCode) {
                l.onDone(c.getString(R.string.ble_scan_failed, errorCode));
            }
        };

        try {
            ScanSettings st = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            sc.startScan(null, st, cbp);
        } catch (Throwable t) {
            l.onDone(c.getString(R.string.ble_cannot_scan, t.getClass().getSimpleName()));
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try { sc.stopScan(cbp); } catch (Throwable ignored) { }
                l.onDone(null);
            }
        }, ms);
    }

    // ---------- koneksi ----------

    public void setMac(String mac) {
        this.mac = mac;
    }

    public boolean connected() {
        return gatt != null && siap;
    }

    public int battery() {
        return battery;
    }

    /** Mulai mencari/menyambung. Aman dipanggil berulang. */
    public void start() {
        wanted = true;
        retry = 0;
        siap = false;
        parser.reset();
        battery = -1;
        connect();
    }

    public void stop() {
        wanted = false;
        handler.removeCallbacksAndMessages(null);
        stopScan();
        closeGatt();
        parser.reset();
        battery = -1;
    }

    private static BluetoothAdapter adapter(Context c) {
        try {
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            return bm == null ? null : bm.getAdapter();
        } catch (Throwable t) {
            return null;
        }
    }

    private void connect() {
        if (!wanted) return;
        if (!BleHr.hasPermission(ctx)) { status(ctx.getString(R.string.ble_no_perm)); return; }
        BluetoothAdapter a = adapter(ctx);
        if (a == null) { status(ctx.getString(R.string.ble_unavailable)); return; }
        try {
            if (!a.isEnabled()) { status(ctx.getString(R.string.ble_off)); scheduleRetry(); return; }
        } catch (SecurityException e) {
            status(ctx.getString(R.string.ble_no_perm));
            return;
        }

        if (mac != null && !mac.isEmpty()) {
            status(ctx.getString(R.string.ble_connecting));
            try {
                BluetoothDevice d = a.getRemoteDevice(mac);
                gatt = d.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE);
            } catch (Throwable t) {
                Log.w(TAG, "connectGatt gagal", t);
                gatt = null;
                scheduleRetry();
            }
        } else {
            status(ctx.getString(R.string.ble_searching_csc));
            scanForCsc(a);
        }
    }

    private BluetoothLeScanner scanner;
    private ScanCallback scanCb;

    private void scanForCsc(BluetoothAdapter a) {
        stopScan();
        try {
            scanner = a.getBluetoothLeScanner();
            if (scanner == null) { scheduleRetry(); return; }
            List<ScanFilter> filters = new ArrayList<ScanFilter>();
            filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SVC_CSC)).build());
            ScanSettings st = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanCb = new ScanCallback() {
                @Override
                public void onScanResult(int type, ScanResult r) {
                    if (!wanted || gatt != null) return;
                    stopScan();
                    try {
                        BluetoothDevice d = r.getDevice();
                        mac = d.getAddress();
                        gatt = d.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE);
                    } catch (Throwable t) {
                        Log.w(TAG, "connect dari scan gagal", t);
                        scheduleRetry();
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.w(TAG, "scan failed " + errorCode);
                    scheduleRetry();
                }
            };
            scanner.startScan(filters, st, scanCb);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (gatt == null) {
                        stopScan();
                        status(ctx.getString(R.string.ble_not_found));
                        scheduleRetry();
                    }
                }
            }, 15000L);
        } catch (Throwable t) {
            Log.w(TAG, "scanForCsc gagal", t);
            scheduleRetry();
        }
    }

    private void stopScan() {
        try {
            if (scanner != null && scanCb != null) scanner.stopScan(scanCb);
        } catch (Throwable ignored) { }
        scanner = null;
        scanCb = null;
    }

    private void scheduleRetry() {
        if (!wanted) return;
        if (++retry > MAX_RETRY) { status(ctx.getString(R.string.ble_not_connected)); return; }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (wanted && gatt == null) connect();
            }
        }, RETRY_MS);
    }

    private void closeGatt() {
        siap = false;
        try {
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
        } catch (Throwable ignored) { }
        gatt = null;
    }

    private void status(String s) {
        if (cb != null) cb.onStatus(s);
    }

    private void data(float speedMps, int cadenceRpm) {
        if (cb != null) cb.onData(speedMps, cadenceRpm);
    }

    private void battery(int pct) {
        battery = pct;
        if (cb != null) cb.onBattery(pct);
    }

    private void readBattery(BluetoothGatt g) {
        try {
            BluetoothGattService svc = g.getService(SVC_BATTERY);
            if (svc == null) return;
            BluetoothGattCharacteristic ch = svc.getCharacteristic(CH_BATTERY);
            if (ch != null) g.readCharacteristic(ch);
        } catch (Throwable t) {
            Log.w(TAG, "baca baterai gagal", t);
        }
    }

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                retry = 0;
                parser.reset();
                try {
                    g.discoverServices();
                } catch (Throwable t) {
                    closeGatt();
                    scheduleRetry();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt();
                battery = -1;
                if (wanted) {
                    status(ctx.getString(R.string.ble_reconnecting));
                    scheduleRetry();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService svc = g.getService(SVC_CSC);
            if (svc == null) {
                status(ctx.getString(R.string.ble_no_csc_service));
                closeGatt();
                wanted = false;
                return;
            }
            BluetoothGattCharacteristic ch = svc.getCharacteristic(CH_CSC);
            if (ch == null) {
                status(ctx.getString(R.string.ble_no_csc_char));
                closeGatt();
                wanted = false;
                return;
            }
            try {
                g.setCharacteristicNotification(ch, true);
                BluetoothGattDescriptor d = ch.getDescriptor(CH_CCCD);
                if (d != null) {
                    d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(d);
                }
                readBattery(g);
                siap = true;
                String name = null;
                try {
                    BluetoothDevice dev = g.getDevice();
                    name = dev == null ? null : dev.getName();
                    if (name == null && dev != null) name = dev.getAddress();
                } catch (SecurityException ignored) { }
                status(ctx.getString(R.string.ble_connected,
                        name == null ? ctx.getString(R.string.ble_sensor_name) : name));
            } catch (Throwable t) {
                Log.w(TAG, "aktifkan notifikasi gagal", t);
            }
        }

        // Android 13+ mengirim overload dengan byte[]; versi lama tetap dipakai
        // perangkat API 23-32. Keduanya diarahkan ke penanganan yang sama.
        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            handle(ch, ch == null ? null : ch.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch,
                                            byte[] value) {
            handle(ch, value);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic ch,
                                         int status) {
            handleBattery(ch, ch == null ? null : ch.getValue());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic ch,
                                         byte[] value, int status) {
            handleBattery(ch, value);
        }
    };

    private void handle(BluetoothGattCharacteristic ch, byte[] value) {
        if (ch == null || !CH_CSC.equals(ch.getUuid()) || value == null) return;
        CscParser.Hasil h = parser.parse(value);
        if (h.adaKecepatan() || h.adaKadensi()) {
            data(h.speedMps, h.cadenceRpm);
        }
    }

    private void handleBattery(BluetoothGattCharacteristic ch, byte[] value) {
        if (ch == null || !CH_BATTERY.equals(ch.getUuid()) || value == null || value.length < 1) {
            return;
        }
        int pct = value[0] & 0xFF;
        battery(pct <= 100 ? pct : -1);
    }
}
