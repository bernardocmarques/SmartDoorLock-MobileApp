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
import android.widget.Toast;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static com.example.smartlockclient.BluetoothLeService.EXTRA_DATA;
import static com.example.smartlockclient.Utils.hmacBase64;

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
    private boolean mConnected = false;

    private AESUtil aes;
    private RSAUtil rsa;


    /* UI */

    Button openLockBtn;
    Button closeLockBtn;

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

            openLockBtn.setEnabled(false);
            closeLockBtn.setEnabled(false);

            bindToBLEService();
            createUIListeners();
        }


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
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
            //TODO
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
                mConnected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
//                generateAndSendCredentials();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                Log.d(TAG, "Teste: " + intent.getData());
            } else if (BluetoothLeService.ACTION_GATT_MTU_SIZE_CHANGED.equals(action)) {
                openLockBtn.setEnabled(true);
                closeLockBtn.setEnabled(true);
            }
        }
    };


    private void generateAndSendSessionCredentials() {
        this.aes = new AESUtil(KEY_SIZE);
        this.rsa = new RSAUtil();

        String key = aes.generateNewSessionKey();
        String keyEncrypted = rsa.encrypt("SSC " + key, rsaPubKey);
        Log.d(TAG, keyEncrypted);
        mBluetoothLeService.sendString(keyEncrypted);
    }

    public void generateAndSendAuthCredentials(String seed) {
        try {
            String authCode = hmacBase64(seed, masterKey);

            String msgEnc = aes.encrypt(new BLEMessage("SAC " + userId + " " + authCode).toString());
            mBluetoothLeService.sendString(msgEnc);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            Log.e(TAG, "Exception!");
        }
    }

    private void openDoorCommunication() {

        generateAndSendSessionCredentials();

        registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        unregisterReceiver(this);

                        final String action = intent.getAction();
                        if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                            String msg = intent.getStringExtra(EXTRA_DATA);
                            String[] msgSplit = msg.split(" ");
                            String cmd = aes.decrypt(msgSplit[0], msgSplit[1]);
                            String[] cmdSplit = cmd.split(" ");

                            if (cmdSplit[0].equals("RAC")) {
                                generateAndSendAuthCredentials(cmdSplit[1]);

                                registerReceiver(
                                        new BroadcastReceiver() {
                                            @Override
                                            public void onReceive(Context context, Intent intent) {
                                                unregisterReceiver(this);

                                                final String action = intent.getAction();
                                                if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                                                    String msg = intent.getStringExtra(EXTRA_DATA);

                                                    String[] msgSplit = msg.split(" ");


                                                    String cmd = aes.decrypt(msgSplit[0], msgSplit[1]);
                                                    String[] cmdSplit = cmd.split(" ");

                                                    Log.e(TAG, "new cmd -> " + cmd);
                                                    if (cmdSplit[0].equals("ACK")) {

                                                        String msgEnc = aes.encrypt(new BLEMessage("RUD").toString());
                                                        mBluetoothLeService.sendString(msgEnc);

                                                        registerReceiver(
                                                                new BroadcastReceiver() {
                                                                    @Override
                                                                    public void onReceive(Context context, Intent intent) {
                                                                        unregisterReceiver(this);

                                                                        final String action = intent.getAction();
                                                                        if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                                                                            String msg = intent.getStringExtra(EXTRA_DATA);

                                                                            String[] msgSplit = msg.split(" ");


                                                                            String cmd = aes.decrypt(msgSplit[0], msgSplit[1]);
                                                                            String[] cmdSplit = cmd.split(" ");

                                                                            Log.e(TAG, "new cmd -> " + cmd);
                                                                            if (cmdSplit[0].equals("ACK")) {

                                                                                runOnUiThread(() -> Toast.makeText(context, "Door Opened", Toast.LENGTH_LONG).show());

                                                                            } else { // command not  ACK
                                                                                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE)
                                                        );

                                                    } else { // command not ACK
                                                        Log.e(TAG, "Error: Should have received ACK command. (After RAC)");
                                                    }
                                                }
                                            }
                                        },
                                        new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE)
                                );

                            } else { // command not RAC
                                Log.e(TAG, "Error: Should have received RAC command");
                            }
                        }
                    }
                },
                new IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE)
        );
    }
}