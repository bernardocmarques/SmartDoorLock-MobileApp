package com.bernardocmarques.smartlockclient;

import static android.content.Context.BIND_AUTO_CREATE;
import static com.bernardocmarques.smartlockclient.BluetoothLeService.EXTRA_DATA;
import static java.lang.Long.parseLong;

import android.app.Activity;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public class BLEScanningManager {

    static String TAG = "SmartLock@BLEScanningManager";

    private static BLEScanningManager INSTANCE = null;

    public BluetoothLeService mBluetoothLeService;


    private BLEScanningManager() { }


    public static BLEScanningManager getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new BLEScanningManager();
            INSTANCE.bindToBLEService(context);
        }
        return(INSTANCE);
    }


    void bindToBLEService(Context context) {
        Intent gattServiceIntent = new Intent(context, BluetoothLeService.class);
        context.bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    public void onResume(Utils.CommActivity commActivity, BroadcastReceiver mGattUpdateReceiver) {
        commActivity.getContext().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_MTU_SIZE_CHANGED);
        return intentFilter;
    }


    public final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    public boolean isScanning() {
        return mBluetoothLeService.isScanning();
    }

    public void scanDevices(ScanCallback leScanCallback) {
        mBluetoothLeService.scanLeDevice(leScanCallback);
    }

    public void scanLeDevice(ScanCallback leScanCallback, List<ScanFilter> filters, ScanSettings scanSettings, long scanPeriod) {
        mBluetoothLeService.scanLeDevice(leScanCallback, filters, scanSettings, scanPeriod);
    }

    public void stopScanningDevices(ScanCallback leScanCallback) {
        mBluetoothLeService.stopScanning(leScanCallback);
    }
}
