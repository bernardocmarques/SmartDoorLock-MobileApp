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

import com.google.firebase.auth.OAuthCredential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class BLEManager {

    static String TAG = "SmartLock@BLEManager";

    private static BLEManager INSTANCE = null;

    public BluetoothLeService mBluetoothLeService;
    public String mDeviceAddress;
    private boolean connectToDevice = true;

    private boolean initialized = false;

    public static final ReentrantLock  scanningLock = new ReentrantLock(true);



    private BLEManager() { }

    public static BLEManager getOneUseInstance(Utils.CommActivity commActivity) {
        BLEManager bleManager = new BLEManager();
        bleManager.connectToDevice = false;
        bleManager.bindToBLEService(commActivity);
        return bleManager;
    }


    public static BLEManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BLEManager();
        }
        return(INSTANCE);
    }


    void bindToBLEService(Utils.CommActivity commActivity) {
        mDeviceAddress = commActivity.getLockBLE();
        Intent gattServiceIntent = new Intent(commActivity.getContext(), BluetoothLeService.class);
        commActivity.getContext().bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    public void onResume(Utils.CommActivity commActivity, BroadcastReceiver mGattUpdateReceiver) {
        commActivity.getContext().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null && !mBluetoothLeService.isConnected()) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
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

    public interface OnBluetoothLeServiceInitialized {
        void onBluetoothLeServiceInitialized();
    }

    private ArrayList<OnBluetoothLeServiceInitialized> btServiceInitializedCallbackList = new ArrayList<>();

    public final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }

            initialized = true;
            // Automatically connects to the device upon successful start-up initialization.
            if (connectToDevice) mBluetoothLeService.connect(mDeviceAddress);

            for (OnBluetoothLeServiceInitialized btServiceInitializedCallback: btServiceInitializedCallbackList) {
                btServiceInitializedCallback.onBluetoothLeServiceInitialized();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    public void connectToDevice() {
        connectToDevice = true;
        mBluetoothLeService.connect(mDeviceAddress);
    }

    public void disconnectFromDevice() {
        mBluetoothLeService.disconnect();
    }


    public void sendRequestFirstInvite(Utils.CommActivity commActivity, Utils.OnResponseReceived callback) {
        sendCommandAndReceiveResponse(commActivity, Utils.generateSessionCredentials(commActivity), false,
                responseSplitSSC -> {
                    if (responseSplitSSC[0].equals("ACK")) {

                        sendCommandAndReceiveResponse(commActivity, "RFI " + Utils.getPhoneId(commActivity.getContext()), callback);

                    } else { // command not ACK
                        Log.e(TAG, "Error: Should have received ACK command");

                        String[] res = {"NAK"};
                        callback.onResponseReceived(res);
                    }
                });
    }


    public synchronized void sendCommandWithAuthentication(Utils.CommActivity commActivity, String cmd, Utils.OnResponseReceived callback) {
        sendCommandAndReceiveResponse(commActivity, Utils.generateSessionCredentials(commActivity), false,
                responseSplitSSC -> {
                    if (responseSplitSSC[0].equals("RAC")) {

                        Utils.generateAuthCredentials(commActivity, responseSplitSSC[1], authCredential -> {
                            sendCommandAndReceiveResponse(commActivity, authCredential,
                                    responseSplitSAC -> {
                                        if (responseSplitSAC[0].equals("ACK")) {

                                            sendCommandAndReceiveResponse(commActivity, cmd, callback);

                                        } else { // command not ACK
                                            Log.e(TAG, "Error: Should have received ACK command. (After RAC)");
                                        }
                                    });
                        });

                    } else { // command not RAC
                        Log.e(TAG, "Error: Should have received RAC command");
                    }
                });
    }

    void sendCommandAndReceiveResponse(Utils.CommActivity commActivity, String cmd, Utils.OnResponseReceived callback) {
        sendCommandAndReceiveResponse(commActivity, cmd,true, callback);
    }

    public void sendCommandAndReceiveResponse(Utils.CommActivity commActivity, String cmd, boolean encrypt, Utils.OnResponseReceived callback) {
        sendCommandAndReceiveResponse(commActivity, cmd,encrypt, true, callback);
    }

    public void sendCommandAndReceiveResponse(Utils.CommActivity commActivity, String cmd, boolean encrypt, boolean decrypt, Utils.OnResponseReceived callback) {
        String msgEnc;
        Context context = commActivity.getContext();
        if (encrypt)
            msgEnc = commActivity.getAESUtil().encrypt(new BLEMessage(cmd).toString());
        else
            msgEnc = cmd;

        boolean success = mBluetoothLeService.sendString(msgEnc);

        if (!success) {
            if (!mBluetoothLeService.isConnected()) {
                commActivity.updateUIOnBLEDisconnected();
            }
            return;
        }

        context.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        context.unregisterReceiver(this);

                        final String action = intent.getAction();
                        if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                            String msgEnc = intent.getStringExtra(EXTRA_DATA);

                            if (decrypt) {
                                String[] msgEncSplit = msgEnc.split(" ");

                                Log.w(TAG, "onReceive: " + msgEnc);
                                if (msgEncSplit.length < 2) {
                                    Log.e(TAG, "Less then 2");
                                    return;
                                }
                                String msg = commActivity.getAESUtil().decrypt(msgEncSplit[0], msgEncSplit[1]);
                                if (msg == null) {
                                    Log.e(TAG, "Error decrypting message! Operation Canceled.");
                                    if (context instanceof Activity) ((Activity) context).runOnUiThread(() -> Toast.makeText(context.getApplicationContext(), "Error decrypting message! Operation Canceled.", Toast.LENGTH_LONG).show());
                                    return;
                                }

                                String[] msgSplit = msg.split(" ");
                                int sizeCmdSplit = msgSplit.length;

                                BLEMessage bleMessage = new BLEMessage(String.join(" ", Arrays.copyOfRange(msgSplit, 0, sizeCmdSplit-3)),  parseLong(msgSplit[sizeCmdSplit-3]), parseLong(msgSplit[sizeCmdSplit-2]), parseLong(msgSplit[sizeCmdSplit-1]));
//                            BLEMessage bleMessage = new BLEMessage(cmd);
                                Log.e(TAG, "onReceive: " + bleMessage.message);
                                if (bleMessage.isValid()) {
                                    String[] cmdSplit = bleMessage.message.split(" ");
                                    callback.onResponseReceived(cmdSplit);

                                } else {
                                    Log.e(TAG, "Message not valid! Operation Canceled.");
                                    if (context instanceof Activity) ((Activity) context).runOnUiThread(() -> Toast.makeText(context.getApplicationContext(), "Message not valid! Operation Canceled.", Toast.LENGTH_LONG).show());
                                }
                            } else {
                                String[] cmdSplit = msgEnc.split(" ");
                                callback.onResponseReceived(cmdSplit);
                            }

                        }
                    }
                },
                new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE)
        );
    }

    public void waitForReadyMessage(Utils.CommActivity commActivity, Utils.OnResponseReceived callback) {

        sendCommandAndReceiveResponse(commActivity,"PNG", false, false,
                responseSplit -> {
                    Log.e(TAG, "waitForReadyMessage: " + responseSplit[0]);
                    if (responseSplit[0].equals("LOK")) {
                        Log.e(TAG, "waitForReadyMessage: nao esperar");
                        callback.onResponseReceived(responseSplit);
                    } else {
                        Log.e(TAG, "waitForReadyMessage: esperar");
                        Context context = commActivity.getContext();

                        context.registerReceiver(
                                new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        context.unregisterReceiver(this);

                                        final String action = intent.getAction();
                                        if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                                            String data = intent.getStringExtra(EXTRA_DATA);
                                            Log.e(TAG, "onReceive2: " + data);
                                            if (data.equals("LOK")) {  // Lock OK
                                                callback.onResponseReceived(new String[]{"LOK"});
                                            } else {
                                                callback.onResponseReceived(new String[]{"LNO"}); // Lock Not OK
                                            }
                                        }
                                    }
                                },
                                new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE)
                        );
                    }
                });
    }


    public void waitForInitialization(OnBluetoothLeServiceInitialized callback) {
        if (initialized) {
            callback.onBluetoothLeServiceInitialized();
        } else {
            btServiceInitializedCallbackList.add(callback);
        }
    }

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
