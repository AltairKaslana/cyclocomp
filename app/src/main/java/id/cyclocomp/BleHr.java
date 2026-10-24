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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pembaca detak jantung lewat BLE GATT standar.
 *
 * Sengaja memakai Heart Rate Service (0x180D) dan bukan Wearable API: aplikasi ini
 * tidak memakai Play Services, jadi jalur Wear OS tertutup. 0x180D adalah profil
 * resmi Bluetooth SIG, tersedia sejak API 18 (minSdk kita 23), dan dipakai bersama
 * oleh chest strap, jam Garmin/COROS/Huawei, dan band yang punya mode "siarkan HR".
 *
 * Alur: cari perangkat (atau sambung langsung ke MAC tersimpan) -> discoverServices
 * -> aktifkan notifikasi pada karakteristik 0x2A37 -> baca tiap notifikasi.
 * Tidak butuh internet.
 */
public class BleHr {

    private static final String TAG = "BleHr";

    public static final UUID SVC_HR = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID CH_HR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static final UUID CH_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long RETRY_MS = 5000L;
    private static final int MAX_RETRY = 24;      // ~2 menit mencoba, lalu menyerah diam-diam

    public interface Callback {
        void onHr(int bpm);

        /** Teks status untuk ditampilkan, mis. "Mencari sensor", "Tersambung: H10". */
        void onStatus(String text);
    }

    public interface ScanListener {
        void onFound(String name, String mac, boolean hasHrService);

        void onDone(String error);
    }

    private final Context ctx;
    private final Callback cb;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String mac;
    private BluetoothGatt gatt;
    private boolean wanted;         // true selama kita masih ingin tersambung
    private int retry;
    private int lastBpm = -1;

    public BleHr(Context c, Callback cb) {
        this.ctx = c.getApplicationContext();
        this.cb = cb;
    }

    // ---------- izin ----------

    static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            return c.checkSelfPermission("android.permission.BLUETOOTH_SCAN") == PackageManager.PERMISSION_GRANTED
                    && c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED;
        }
        // API 23-30: pemindaian BLE ikut izin lokasi
        return c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"};
        }
        return new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private static BluetoothAdapter adapter(Context c) {
        try {
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            return bm == null ? null : bm.getAdapter();
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean available(Context c) {
        BluetoothAdapter a = adapter(c);
        return a != null;
    }

    // ---------- pencarian perangkat (dipakai dialog pengaturan) ----------

    /**
     * Cari perangkat sekitar selama ms milidetik. Yang dilaporkan hanya perangkat yang
     * mengiklankan Heart Rate Service atau punya nama; sisanya derau (ratusan MAC acak).
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
        if (!hasPermission(c)) { l.onDone(c.getString(R.string.ble_no_perm)); return; }

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
                    String mac = d.getAddress();
                    if (mac == null || seen.contains(mac)) return;
                    boolean hasHr = false;
                    if (r.getScanRecord() != null) {
                        List<ParcelUuid> uuids = r.getScanRecord().getServiceUuids();
                        if (uuids != null) {
                            for (ParcelUuid p : uuids) {
                                if (SVC_HR.equals(p.getUuid())) { hasHr = true; break; }
                            }
                        }
                    }
                    String name = null;
                    try { name = d.getName(); } catch (SecurityException ignored) { }
                    if (!hasHr && (name == null || name.trim().isEmpty())) return;
                    seen.add(mac);
                    l.onFound(name == null ? "(tanpa nama)" : name, mac, hasHr);
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

    public int lastBpm() {
        return lastBpm;
    }

    public boolean connected() {
        return gatt != null;
    }

    /** Mulai mencari/menyambung. Aman dipanggil berulang. */
    public void start() {
        wanted = true;
        retry = 0;
        connect();
    }

    public void stop() {
        wanted = false;
        handler.removeCallbacksAndMessages(null);
        stopScan();
        closeGatt();
        lastBpm = -1;
    }

    private void connect() {
        if (!wanted) return;
        if (!hasPermission(ctx)) { status(ctx.getString(R.string.ble_no_perm)); return; }
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
            // Belum ada pilihan tersimpan: cari yang mengiklankan Heart Rate Service
            status(ctx.getString(R.string.ble_searching));
            scanForHr(a);
        }
    }

    private BluetoothLeScanner scanner;
    private ScanCallback scanCb;

    private void scanForHr(BluetoothAdapter a) {
        stopScan();
        try {
            scanner = a.getBluetoothLeScanner();
            if (scanner == null) { scheduleRetry(); return; }
            List<ScanFilter> filters = new ArrayList<ScanFilter>();
            filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SVC_HR)).build());
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
            // batasi pencarian supaya tidak menyedot baterai kalau sensor tidak ada
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
            Log.w(TAG, "scanForHr gagal", t);
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

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                retry = 0;
                try {
                    g.discoverServices();
                } catch (Throwable t) {
                    closeGatt();
                    scheduleRetry();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt();
                lastBpm = -1;
                if (wanted) {
                    status(ctx.getString(R.string.ble_reconnecting));
                    scheduleRetry();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService svc = g.getService(SVC_HR);
            if (svc == null) {
                status(ctx.getString(R.string.ble_no_hr_service));
                closeGatt();
                wanted = false;      // jangan berputar terus pada perangkat yang salah
                return;
            }
            BluetoothGattCharacteristic ch = svc.getCharacteristic(CH_HR);
            if (ch == null) {
                status(ctx.getString(R.string.ble_no_hr_char));
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

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            if (ch == null || !CH_HR.equals(ch.getUuid())) return;
            byte[] v = ch.getValue();
            if (v == null || v.length < 2) return;
            int flags = v[0] & 0xFF;
            int bpm;
            if ((flags & 0x01) != 0) {                 // bit0: nilai 16-bit
                if (v.length < 3) return;
                bpm = (v[1] & 0xFF) | ((v[2] & 0xFF) << 8);
            } else {
                bpm = v[1] & 0xFF;
            }
            if (bpm <= 0 || bpm > 250) return;         // 0 = sensor belum kontak kulit
            lastBpm = bpm;
            if (cb != null) cb.onHr(bpm);
        }
    };
}
