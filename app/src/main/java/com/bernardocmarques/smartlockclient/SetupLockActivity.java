package com.bernardocmarques.smartlockclient;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.espressif.iot.esptouch2.provision.EspProvisioner;
import com.espressif.iot.esptouch2.provision.EspProvisioningListener;
import com.espressif.iot.esptouch2.provision.EspProvisioningRequest;
import com.espressif.iot.esptouch2.provision.EspProvisioningResult;
import com.espressif.iot.esptouch2.provision.EspSyncListener;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class SetupLockActivity extends AppCompatActivity implements BLEManager.BLEActivity {

    TextView ssidTextView;
    TextView bssidTextView;
    EditText passwordEditText;
    ExtendedFloatingActionButton connectBtn;
    BLEManager bleManager;

    private static final String TAG = "SmartLock@SetupLockActivity";
    Context context = this;

    EspProvisioner provisioner;

    RSAUtil rsaUtil;

    String lockId;
    String lockBleAddress;

    public enum Mode {
        FIRST_CONFIG,
        CHANGE_WIFI
    }

    Mode mode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_lock);
        Utils.forceLightModeOn();

        setActionBar();

        Bundle bundle = getIntent().getExtras();
        lockId = bundle.getString("lockId");
        mode = (Mode) bundle.getSerializable("mode");

        lockBleAddress = bundle.getString("lockBleAddress");

        bleManager = BLEManager.getInstance();


        ssidTextView = findViewById(R.id.text_view_ssid_value);
        bssidTextView = findViewById(R.id.text_view_bssid_value);
        passwordEditText = findViewById(R.id.edit_text_password);
        connectBtn = findViewById(R.id.btn_connect_wifi_setup);

        provisioner = new EspProvisioner(context);


        syncWithLock();

        connectBtn.setOnClickListener(view -> sendWifiCredentials());

        findViewById(R.id.btn_skip_wifi_set_up).setOnClickListener(v-> afterWifiConfig());


        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo;

        String ssid;
        String bssid;


        wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
            ssid = wifiInfo.getSSID().replace("\"", "");
            bssid = wifiInfo.getBSSID();
            Log.d(TAG, "SSID = " + ssid);
            Log.d(TAG, "BSSID = " + bssid);
        } else {
            Log.e(TAG, "Could not get SSID from wifi connection.");
            ssid = "Invalid SSID"; // Fixme set to text resource
            bssid = "Invalid BSSID"; // Fixme set to text resource
        }

        ssidTextView.setText(ssid);
        bssidTextView.setText(bssid);
    }

    void setActionBar() {
        View actionBarInclude = findViewById(R.id.action_bar_include);
        MaterialToolbar actionBar = actionBarInclude.findViewById(R.id.backBar);
        actionBar.setTitle(R.string.setup_wifi_title);

        actionBar.setNavigationOnClickListener(view -> finish());
    }

    private void afterWifiConfig() {
        if (mode == Mode.FIRST_CONFIG) {
            connectBLE();
        } else if (mode == Mode.CHANGE_WIFI) {
            finish();
        }
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

    void connectBLE() {
        bleManager.bindToBLEService(this);
    }


    void requestFirstInvite() {
        bleManager.sendRequestFirstInvite(this, (responseSplit -> {

            if (responseSplit[0].equals("SFI")) {
                String inviteCodeB64 = responseSplit[1];
                Log.i(TAG, "Invite: " + inviteCodeB64);

                String inviteID = null;
                String lockMAC = null;
                String lockBLE = null;

                String[] inviteCode = {};

                try {
                    inviteCode = new String(Base64.decode(inviteCodeB64, Base64.NO_WRAP)).split(" ");
                } catch (IllegalArgumentException e) {
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.smart_door_created_title)
                            .setMessage(R.string.error_creating_smart_lock_invalid_invite_msg)
                            .setPositiveButton(R.string.OK, (dialog, which) -> {
                            })
                            .show());
                    return;
                }

                if (inviteCode.length > 0)  inviteID = inviteCode[0];

                if (inviteCode.length > 1) lockMAC = inviteCode[1];

                if (inviteCode.length > 2) lockBLE = inviteCode[2];

                if (lockMAC == null || inviteID == null) {
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.smart_door_created_title)
                            .setMessage(R.string.error_creating_smart_lock_invalid_invite_msg)
                            .setPositiveButton(R.string.OK, (dialog, which) -> {
                            })
                            .show());
                    return;
                }

                if (GlobalValues.getInstance().getUserLockById(lockMAC.toUpperCase()) != null) {
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.smart_door_created_title)
                            .setMessage(R.string.error_creating_smart_lock_repeated_lock_msg)
                            .setPositiveButton(R.string.OK, (dialog, which) -> {
                            })
                            .show());
                    return;
                }

                Utils.redeemInvite(lockMAC, inviteID, this, success -> {
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.smart_door_created_title)
                            .setMessage(success ? R.string.smart_lock_created_msg : R.string.error_creating_smart_lock_invalid_invite_msg)
                            .setPositiveButton(R.string.OK, (dialog, which) -> {
                                if (success) {
                                    Lock lock = new Lock(lockId, lockId, lockBleAddress, "New Door", "lock-open");
                                    Utils.setUserLock(lock, lockCreated -> {
                                        if (lockCreated) {
                                            lock.setName("");
                                            Intent intent = new Intent(getApplicationContext(), EditDoorInformationActivity.class);
                                            lock.clearIcon();
                                            intent.putExtra("lock", lock);
                                            startActivity(intent);
                                        }
                                    });
                                }
                                finish();
                            })
                            .show());
                });
            } else if (responseSplit[0].equals("NAK")) {
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.smart_door_created_title)
                        .setMessage(R.string.error_creating_smart_lock_registered_lock_msg)
                        .setPositiveButton(R.string.OK, (dialog, which) -> {finish();})
                        .show());


            } else { // command not SNI
                Log.e(TAG, "Error: Should have received SNI command. (After RNI)");
            }

        }));
    }

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

                Utils.getPublicKeyBase64FromDatabase(getLockId(), getActivity(), rsaKey -> {
                    rsaUtil = new RSAUtil(rsaKey);
                    updateUIOnBLEConnected();
                    requestFirstInvite();
                });
            }
        }
    };


    void syncWithLock() {
        EspSyncListener listener = new EspSyncListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onStop() {
            }

            @Override
            public void onError(Exception e) {
            }
        };
        provisioner.startSync(listener); // listener is nullable
    }



    void sendWifiCredentials() {
        connectBtn.setEnabled(false);

        provisioner.stopSync();

        byte[] ssid = ssidTextView.getText().toString().getBytes();
        byte[] bssid = bssidTextView.getText().toString().getBytes();
        byte[] password = passwordEditText.getText().toString().getBytes();


        EspProvisioningRequest request = new EspProvisioningRequest.Builder(context)
                .setSSID(ssid) // AP's SSID, nullable
//                .setBSSID(bssid) // AP's BSSID, nonnull
                .setPassword(password) // AP's password, nullable if the AP is open
//                .setReservedData(null) // User's custom data, nullable. If not null, the max length is 127
//                .setAESKey(null) // nullable, if not null, it must be 16 bytes. App developer should negotiate an AES key with Device developer first.
                .build();



        EspProvisioningListener listener = new EspProvisioningListener() {
            @Override
            public void onStart() {
                Log.d(TAG, "Started setup!");
//                Toast.makeText(context, "Started setup!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onResponse(EspProvisioningResult result) {
                Log.d(TAG, "Done setup!");
                runOnUiThread(() -> Toast.makeText(context, "Done setup!", Toast.LENGTH_LONG).show());

                provisioner.stopProvisioning();

                runOnUiThread(() -> new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.SETUP_DONE)
                    .setMessage(R.string.SETUP_DONE_DIALOG_MESSAGE)
                    .setPositiveButton(R.string.OK, (dialog, which) -> afterWifiConfig())
                    .show());


            }

            @Override
            public void onStop() {
                Log.d(TAG, "Stopped setup!");
                runOnUiThread(() -> Toast.makeText(context, "Stopped setup!", Toast.LENGTH_LONG).show());
                provisioner.close();
                connectBtn.setEnabled(true);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error setting up!");
                runOnUiThread(() -> Toast.makeText(context, "Error setting up!", Toast.LENGTH_LONG).show());
                e.printStackTrace();
            }
        };

        provisioner.startProvisioning(request, listener); // request is nonnull, listener is nullable
    }

    @Override
    public void updateUIOnBLEDisconnected() {
        // NOP
    }

    @Override
    public void updateUIOnBLEConnected() {
        
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public RSAUtil getRSAUtil() {
        return rsaUtil;
    }

    @Override
    public String getLockId() {
        return lockId;
    }

    @Override
    public String getLockBLE() {
        return lockBleAddress;
    }
}