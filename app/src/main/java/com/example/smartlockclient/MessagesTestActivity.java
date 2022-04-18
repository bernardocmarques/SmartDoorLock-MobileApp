package com.example.smartlockclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static com.example.smartlockclient.BluetoothLeService.EXTRA_DATA;
import static com.example.smartlockclient.Utils.hmacBase64;
import static java.lang.Long.parseLong;

public class MessagesTestActivity extends AppCompatActivity {

    private static final String TAG = "SmartLock@MessagesTest";

    /* Testing variables */

    String rsaPubKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAl4iRt8ORglI2tv0U3Dp2" +
            "3Zyoc4bY0l414bNCK6TN1AXKXx6iQaiugnsFK84BhVtd6uNX/hMxsat+aZoJvPdM" +
            "aY48U1DgAqBtFhSbXakyfghdk6VDVV6chQzrYzyvZ1eR7q0qfmf5w3Z02fSfI66E" +
            "a8BAT1UpAEWdSU+xFlbRb9qsZYGV99+JjPC4PGhbHMOSsO+We4ZsP8UosNyF8A62" +
            "FheFXimCujiPmOBIOablN9TuWXAUtNHhWf4EyYDQvEo/NfY2mleiYjKqHoJpkIu+" +
            "sMcJJ3ry5Z4HEZi+SUbCjL7I5ZYF8aZq3YRxS4n2ZO7/w7n7B5621HMsahRNUi76" +
            "1wIDAQAB";

    String userId = "0vn3kfl3n";
    String masterKey = "SoLXxAJHi1Z3NKGHNnS5n4SRLv5UmTB4EssASi0MmoI=";

    /* Testing variables (end) */

    private static final int KEY_SIZE = 256;

    private BluetoothLeService mBluetoothLeService;
    private final String mDeviceAddress = "01:B6:EC:2A:C0:D9"; // fixme hardcoded while testing

    private AESUtil aes;
    private RSAUtil rsa;


    /* UI */

    Button openLockBtn;
    Button closeLockBtn;
    Button createNewInviteBtn;
    SwitchMaterial bleConnectedSwitch;

    ActivityResultLauncher<String[]> bluetoothPermissionRequest =
            registerForActivityResult(new ActivityResultContracts
                            .RequestMultiplePermissions(), result -> {
                Boolean granted;
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    granted = result.getOrDefault(
                            Manifest.permission.BLUETOOTH_CONNECT,false);
                } else {
                    granted = true;
                }

                if (granted != null && granted) {
                    createUIListeners();
                } else {
                    Log.e(TAG, "Bluetooth Permission Denied. Cant continue");
                    requestBluetoothPermission();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages_test);

        if (!gotBluetoothPermission()) {

            requestBluetoothPermission();
        } else {
            openLockBtn = findViewById(R.id.btn_open_lock);
            closeLockBtn = findViewById(R.id.btn_close_lock);
            createNewInviteBtn = findViewById(R.id.btn_create_new_invite);
            bleConnectedSwitch = findViewById(R.id.switch_connected);

            updateUIOnBLEDisconnected();
            bleConnectedSwitch.setText(R.string.BLE_CONNECTING);

            bindToBLEService();
            createUIListeners();
        }


    }

    void updateUIOnBLEDisconnected() {
        bleConnectedSwitch.setChecked(false);
        bleConnectedSwitch.setText(R.string.BLE_DISCONNECTED);

        openLockBtn.setEnabled(false);
        closeLockBtn.setEnabled(false);
        createNewInviteBtn.setEnabled(false);
    }

    void updateUIOnBLEConnected() {
        bleConnectedSwitch.setChecked(true);
        bleConnectedSwitch.setText(R.string.BLE_CONNECTED);

        openLockBtn.setEnabled(true);
        closeLockBtn.setEnabled(true);
        createNewInviteBtn.setEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null && !mBluetoothLeService.isConnected()) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }



    void bindToBLEService() {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    void createUIListeners() {
        openLockBtn.setOnClickListener(view -> {
            openDoorCommunication();
        });

        closeLockBtn.setOnClickListener(view -> {
            closeDoorCommunication();
        });

        createNewInviteBtn.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), CreateNewInviteActivity.class);
            startActivity(intent);
        });

        bleConnectedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!mBluetoothLeService.isConnected()) {
                    mBluetoothLeService.connect(mDeviceAddress);
                    bleConnectedSwitch.setText(R.string.BLE_CONNECTING);
                }
            } else {
                if (mBluetoothLeService.isConnected()) mBluetoothLeService.disconnect();
            }
        });
    }

    boolean gotBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(
                    getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            bluetoothPermissionRequest.launch(new String[] {
                    Manifest.permission.BLUETOOTH_CONNECT
            });
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

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateUIOnBLEDisconnected();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
//                generateAndSendCredentials();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                Log.d(TAG, "Teste: " + intent.getData());
            } else if (BluetoothLeService.ACTION_GATT_MTU_SIZE_CHANGED.equals(action)) {
                updateUIOnBLEConnected();

            }
        }
    };


    private String generateSessionCredentials() {
        this.aes = new AESUtil(KEY_SIZE);
        this.rsa = new RSAUtil();

        String key = aes.generateNewSessionKey();

        return rsa.encrypt("SSC " + key, rsaPubKey);
    }

    public String generateAuthCredentials(String seed) {
        try {
            String authCode = hmacBase64(seed, masterKey);

            return "SAC " + userId + " " + authCode;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            Log.e(TAG, "Exception!");
            return null;
        }
    }


    private void sendCommandWithAuthentication(String cmd, OnResponseReceived callback) {
        sendCommandAndReceiveResponse(generateSessionCredentials(), false,
                responseSplitSSC -> {
                    if (responseSplitSSC[0].equals("RAC")) {

                        sendCommandAndReceiveResponse(generateAuthCredentials(responseSplitSSC[1]),
                                responseSplitSAC -> {
                                    if (responseSplitSAC[0].equals("ACK")) {

                                        sendCommandAndReceiveResponse(cmd, callback);

                                    } else { // command not ACK
                                        Log.e(TAG, "Error: Should have received ACK command. (After RAC)");
                                    }
                                });

                    } else { // command not RAC
                        Log.e(TAG, "Error: Should have received RAC command");
                    }
                });
    }

    private void openDoorCommunication() {
        sendCommandWithAuthentication("RUD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Opened", Toast.LENGTH_LONG).show());
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

    private void closeDoorCommunication() {
        sendCommandWithAuthentication("RLD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Locked", Toast.LENGTH_LONG).show());
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }


    public interface OnResponseReceived {
        void onResponseReceived(String[] responseSplit);
    }


    void sendCommandAndReceiveResponse(String cmd, OnResponseReceived callback) {
        sendCommandAndReceiveResponse(cmd,true, callback);
    }


    void sendCommandAndReceiveResponse(String cmd, boolean encrypt, OnResponseReceived callback) {
        String msgEnc;
        if (encrypt)
            msgEnc = aes.encrypt(new BLEMessage(cmd).toString());
        else
            msgEnc = cmd;

        boolean success = mBluetoothLeService.sendString(msgEnc);

        if (!success) {
            if (!mBluetoothLeService.isConnected()) {
                updateUIOnBLEDisconnected();
            }
            return;
        }

        registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        unregisterReceiver(this);

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
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Error decrypting message! Operation Canceled.", Toast.LENGTH_LONG).show());
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
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Message not valid! Operation Canceled.", Toast.LENGTH_LONG).show());
                            }


                        }
                    }
                },
                new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE)
        );
    }


}