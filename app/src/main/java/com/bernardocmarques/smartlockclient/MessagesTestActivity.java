package com.bernardocmarques.smartlockclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import static java.lang.Long.parseLong;

public class MessagesTestActivity extends AppCompatActivity implements BLEManager.BLEActivity {

    private static final String TAG = "SmartLock@MessagesTest";



    BLEManager bleManager;

    /* UI */

    Button openLockBtn;
    Button closeLockBtn;
    Button createNewInviteBtn;
    Button redeemInviteBtn;
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
        Utils.forceLightModeOn();

        bleManager = BLEManager.getInstance();

        if (!gotBluetoothPermission()) {
            requestBluetoothPermission();
        } else {
            openLockBtn = findViewById(R.id.btn_open_lock);
            closeLockBtn = findViewById(R.id.btn_close_lock);
            createNewInviteBtn = findViewById(R.id.btn_create_new_invite);
            redeemInviteBtn = findViewById(R.id.btn_redeem_invite);
            bleConnectedSwitch = findViewById(R.id.switch_connected);

            updateUIOnBLEDisconnected();
            bleConnectedSwitch.setText(R.string.BLE_CONNECTING);

            bindToBLEService();
            createUIListeners();
        }


    }

    public void updateUIOnBLEDisconnected() {
        bleConnectedSwitch.setChecked(false);
        bleConnectedSwitch.setText(R.string.BLE_DISCONNECTED);

        openLockBtn.setEnabled(false);
        closeLockBtn.setEnabled(false);
        createNewInviteBtn.setEnabled(false);
        redeemInviteBtn.setEnabled(false);
    }

    public void updateUIOnBLEConnected() {
        bleConnectedSwitch.setChecked(true);
        bleConnectedSwitch.setText(R.string.BLE_CONNECTED);

        openLockBtn.setEnabled(true);
        closeLockBtn.setEnabled(true);
        createNewInviteBtn.setEnabled(true);
        redeemInviteBtn.setEnabled(true);

    }

    public Activity getActivity() {
        return this;
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bleManager.mBluetoothLeService != null && !bleManager.mBluetoothLeService.isConnected()) {
            final boolean result = bleManager.mBluetoothLeService.connect(bleManager.mDeviceAddress);
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
        unbindService(bleManager.mServiceConnection);
        bleManager.mBluetoothLeService = null;
    }


    void bindToBLEService() {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, bleManager.mServiceConnection, BIND_AUTO_CREATE);
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

        redeemInviteBtn.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), RedeemInviteActivity.class);
            startActivity(intent);
        });

        bleConnectedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (bleManager != null && bleManager.mBluetoothLeService != null && !bleManager.mBluetoothLeService.isConnected()) {
                    bleManager.mBluetoothLeService.connect(bleManager.mDeviceAddress);
                    bleConnectedSwitch.setText(R.string.BLE_CONNECTING);
                }
            } else {
                if (bleManager.mBluetoothLeService.isConnected()) bleManager.mBluetoothLeService.disconnect();
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



    private void openDoorCommunication() {
        bleManager.sendCommandWithAuthentication(this,"RUD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Opened", Toast.LENGTH_LONG).show());
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

    private void closeDoorCommunication() {
        bleManager.sendCommandWithAuthentication(this,"RLD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Locked", Toast.LENGTH_LONG).show());
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

}