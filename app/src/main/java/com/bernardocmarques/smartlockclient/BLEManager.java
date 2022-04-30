package com.bernardocmarques.smartlockclient;

import static com.bernardocmarques.smartlockclient.BluetoothLeService.EXTRA_DATA;
import static java.lang.Long.parseLong;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Arrays;
import java.util.Objects;

public class BLEManager {

    static String TAG = "SmartLock@BLEManager";

    private static BLEManager INSTANCE = null;

    /* Testing variables */  // todo remove hardcode

    String rsaPubKey = Utils.rsaPubKey;
    String keyID = "7C:DF:A1:1A:0E:5A";

    /* Testing variables (end) */

    private static final int KEY_SIZE = 256;


    private AESUtil aes;
    private RSAUtil rsa;



    public BluetoothLeService mBluetoothLeService;
    public final String mDeviceAddress = "01:B6:EC:2A:C0:D9"; // fixme hardcoded while testing

    private BLEManager() { };


    public static BLEManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BLEManager();
        }
        return(INSTANCE);
    }


    public final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    public interface OnResponseReceived {
        void onResponseReceived(String[] responseSplit);
    }


    public String generateSessionCredentials() {
        this.aes = new AESUtil(KEY_SIZE);
        this.rsa = new RSAUtil();

        String key = aes.generateNewSessionKey();

        return rsa.encrypt("SSC " + key, rsaPubKey);
    }

    public String generateAuthCredentials(String seed) {
        String username = GlobalValues.getInstance().getCurrentUsername();
        String authCode = Utils.KeyStoreUtil.getInstance().hmacBase64WithMasterKey(seed, keyID + username);
        return "SAC " + username + " " + authCode;
    }


    public void sendCommandWithAuthentication(BLEActivity bleActivity,String cmd, BLEManager.OnResponseReceived callback) {
        sendCommandAndReceiveResponse(bleActivity, generateSessionCredentials(), false,
                responseSplitSSC -> {
                    if (responseSplitSSC[0].equals("RAC")) {

                        sendCommandAndReceiveResponse(bleActivity, generateAuthCredentials(responseSplitSSC[1]),
                                responseSplitSAC -> {
                                    if (responseSplitSAC[0].equals("ACK")) {

                                        sendCommandAndReceiveResponse(bleActivity, cmd, callback);

                                    } else { // command not ACK
                                        Log.e(TAG, "Error: Should have received ACK command. (After RAC)");
                                    }
                                });

                    } else { // command not RAC
                        Log.e(TAG, "Error: Should have received RAC command");
                    }
                });
    }

    void sendCommandAndReceiveResponse(BLEActivity bleActivity, String cmd, BLEManager.OnResponseReceived callback) {
        sendCommandAndReceiveResponse(bleActivity, cmd,true, callback);
    }

    void sendCommandAndReceiveResponse(BLEActivity bleActivity, String cmd, boolean encrypt, OnResponseReceived callback) {
        String msgEnc;
        Activity activity = bleActivity.getActivity();

        if (encrypt)
            msgEnc = aes.encrypt(new BLEMessage(cmd).toString());
        else
            msgEnc = cmd;

        boolean success = mBluetoothLeService.sendString(msgEnc);

        if (!success) {
            if (!mBluetoothLeService.isConnected()) {
                bleActivity.updateUIOnBLEDisconnected();
            }
            return;
        }

        activity.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        activity.unregisterReceiver(this);

                        final String action = intent.getAction();
                        if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                            String msgEnc = intent.getStringExtra(EXTRA_DATA);

                            String[] msgEncSplit = msgEnc.split(" ");

                            Log.w(TAG, "onReceive: " + msgEnc);
                            if (msgEncSplit.length < 2) {
                                Log.e(TAG, "Less then 2");
                                return;
                            }
                            String msg = aes.decrypt(msgEncSplit[0], msgEncSplit[1]);
                            if (msg == null) {
                                Log.e(TAG, "Error decrypting message! Operation Canceled.");
                                activity.runOnUiThread(() -> Toast.makeText(activity.getApplicationContext(), "Error decrypting message! Operation Canceled.", Toast.LENGTH_LONG).show());
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
                                activity.runOnUiThread(() -> Toast.makeText(activity.getApplicationContext(), "Message not valid! Operation Canceled.", Toast.LENGTH_LONG).show());
                            }


                        }
                    }
                },
                new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE)
        );
    }

    public interface BLEActivity {
        void updateUIOnBLEDisconnected();
        void updateUIOnBLEConnected();

        Activity getActivity();
    }

}
