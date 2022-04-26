package com.bernardocmarques.smartlockclient;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.espressif.iot.esptouch2.provision.EspProvisioner;
import com.espressif.iot.esptouch2.provision.EspProvisioningListener;
import com.espressif.iot.esptouch2.provision.EspProvisioningRequest;
import com.espressif.iot.esptouch2.provision.EspProvisioningResult;
import com.espressif.iot.esptouch2.provision.EspSyncListener;

public class SetupLockActivity extends AppCompatActivity {

    TextView ssidTextView;
    TextView bssidTextView;
    EditText passwordEditText;
    Button connectBtn;

    private static final String TAG = "SmartLock@SetupLockActivity";
    Context context = this;

    EspProvisioner provisioner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_lock);
        Utils.forceLightModeOn();

        ssidTextView = findViewById(R.id.text_view_ssid_value);
        bssidTextView = findViewById(R.id.text_view_bssid_value);
        passwordEditText = findViewById(R.id.edit_text_password);
        connectBtn = findViewById(R.id.btn_connect_wifi_setup);

        provisioner = new EspProvisioner(context);


        syncWithLock();

        connectBtn.setOnClickListener(view -> sendWifiCredentials());


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
                    .setPositiveButton(R.string.OK, (dialog, which) -> {
                        Log.d(TAG, "Pressed OK!");
                        finish();
                    })
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
}