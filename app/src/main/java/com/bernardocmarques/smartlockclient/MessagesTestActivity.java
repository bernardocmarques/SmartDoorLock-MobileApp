package com.bernardocmarques.smartlockclient;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import static java.lang.Long.parseLong;

public class MessagesTestActivity extends AppCompatActivity implements BLEManager.BLEActivity {

    private static final String TAG = "SmartLock@MessagesTest";



    BLEManager bleManager;

    RSAUtil rsaUtil;

    Lock lock;

    /* UI */

    Button openLockBtn;
    Button closeLockBtn;
    Button createNewInviteBtn;
    Button redeemInviteBtn;
    SwitchMaterial bleConnectedSwitch;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages_test);
        Utils.forceLightModeOn();

        Bundle bundle = getIntent().getExtras();
        String lockId = bundle.getString("lockId");
        this.lock = GlobalValues.getInstance().getUserLockById(lockId);

        bleManager = BLEManager.getInstance();


        openLockBtn = findViewById(R.id.btn_open_lock);
        closeLockBtn = findViewById(R.id.btn_close_lock);
        createNewInviteBtn = findViewById(R.id.btn_create_new_invite);
        redeemInviteBtn = findViewById(R.id.btn_redeem_invite);
        bleConnectedSwitch = findViewById(R.id.switch_connected);

        findViewById(R.id.btn_edit_lock).setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), EditDoorInformationActivity.class);
            lock.clearIcon();
            intent.putExtra("lock", lock);
            startActivity(intent);
        });

        updateUIOnBLEDisconnected();
        bleConnectedSwitch.setText(R.string.BLE_CONNECTING);

        bleManager.bindToBLEService(this);
        createUIListeners();

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
        bleManager.scanDevices();

    }

    public Activity getActivity() {
        return this;
    }

    @Override
    public RSAUtil getRSAUtil() {
        return rsaUtil;
    }

    @Override
    public String getLockId() {
        return lock.getId();
    }

    @Override
    public String getLockBLE() {
        return lock.getBleAddress();
    }


    @Override
    protected void onResume() {
        super.onResume();
        bleManager.onResume(this, mGattUpdateReceiver);
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


    void createUIListeners() {

        openLockBtn.setOnClickListener(view -> {
            openDoorCommunication();
        });

        closeLockBtn.setOnClickListener(view -> {
            closeDoorCommunication();
        });

        createNewInviteBtn.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), CreateNewInviteActivity.class);
            intent.putExtra("lockId", lock.getId());
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
                Utils.getPublicKeyBase64FromDatabase(lock.getId(), getActivity(), rsaKey -> {
                    rsaUtil = new RSAUtil(rsaKey);
                    updateUIOnBLEConnected();
                });
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