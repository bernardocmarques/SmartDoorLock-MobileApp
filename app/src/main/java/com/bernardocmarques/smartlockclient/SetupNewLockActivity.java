package com.bernardocmarques.smartlockclient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class SetupNewLockActivity extends AppCompatActivity implements BLEManager.BLEActivity {

    private static final String TAG = "SmartLock@SetupNewLockActivity";

    static final int QR_CODE_REQUEST_CODE = 1;

    BLEManager bleManager;



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == QR_CODE_REQUEST_CODE) {
            IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
            if (result.getContents() != null) {
                Uri uri = Uri.parse(result.getContents());
                if (uri != null) {
                    String inviteCode = uri.getQueryParameter("inviteCode");

                    if (inviteCode != null && !inviteCode.isEmpty()) {
                        redeemInvite(inviteCode);
                    } else {
                        String lockId = uri.getQueryParameter("id");
                        String bleAddress = uri.getQueryParameter("ble_addr");

                        if (lockId == null || lockId.isEmpty() || bleAddress == null || bleAddress.isEmpty())
                            return;

                        Intent intent = new Intent(getApplicationContext(), SetupLockActivity.class);
                        intent.putExtra("lockId", lockId);
                        intent.putExtra("mode", SetupLockActivity.Mode.FIRST_CONFIG);
                        intent.putExtra("lockBleAddress", bleAddress);
                        startActivity(intent);
                        finish();
                    }
                }
            }
        }


    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_new_lock);

        setActionBar();

        // Get invite code
        Intent intent = getIntent();
        Uri data = intent.getData();
        String inviteCode;

        bleManager = BLEManager.getInstance();
        bleManager.bindToBLEService(this);


        if (data != null) {
            inviteCode = data.getQueryParameter("inviteCode");
            Log.i(TAG, "onCreate: " + inviteCode);
            redeemInvite(inviteCode);
        }

        createUIListeners();


    }

    void setActionBar() {
        View actionBarInclude = findViewById(R.id.action_bar_include);
        MaterialToolbar actionBar = actionBarInclude.findViewById(R.id.backBar);
        actionBar.setTitle(R.string.add_new_smart_lock_title);

        actionBar.setNavigationOnClickListener(view -> finish());
    }

    ArrayList<BluetoothDevice> bleDevicesList = new ArrayList<>();
    ArrayList<String> bleDevicesMacList = new ArrayList<>();
    ArrayAdapter<BluetoothDevice> bleDevicesListAdapter;
    CircularProgressIndicator bleScanningSpinner;
    TextView bleScanTextBtn;

    static final int CHECK_INTERVAL = 1000;

    void createUIListeners() {
        findViewById(R.id.btn_read_qr_code).setOnClickListener(v -> {
            IntentIntegrator intentIntegrator = new IntentIntegrator(this);
            intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            intentIntegrator.setRequestCode(QR_CODE_REQUEST_CODE);
            intentIntegrator.initiateScan();
        });


        findViewById(R.id.btn_scan_ble_devices).setOnClickListener(v -> {
            bleDevicesList.clear();
            startScanningDevices();

            LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

            View bleSelection = inflater.inflate(R.layout.ble_device_selection_list, null);
            bleScanningSpinner = bleSelection.findViewById(R.id.loading_spinner);
            bleScanTextBtn = bleSelection.findViewById(R.id.scan_text_btn);

            bleScanTextBtn.setText(bleManager.isScanning() ? R.string.stop : R.string.scan);
            bleScanningSpinner.setVisibility(bleManager.isScanning() ? View.VISIBLE : View.INVISIBLE);

            bleScanTextBtn.setOnClickListener(view -> {
                if (bleManager.isScanning()) {
                    bleManager.stopScanningDevices(leScanCallback);
                } else {
                    startScanningDevices();
                }

                bleScanTextBtn.setText(bleManager.isScanning() ? R.string.stop : R.string.scan);
                bleScanningSpinner.setVisibility(bleManager.isScanning() ? View.VISIBLE : View.INVISIBLE);

            });

            ListView listView = bleSelection.findViewById(R.id.ble_select_device_list_view);

            bleDevicesListAdapter = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_2, android.R.id.text1, bleDevicesList) {
                @SuppressLint("MissingPermission")
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text1 = view.findViewById(android.R.id.text1);
                    TextView text2 = view.findViewById(android.R.id.text2);

                    String name = bleDevicesList.get(position).getName();

                    text1.setText(name != null ? name : "<Unnamed Device>");
                    text2.setText(bleDevicesList.get(position).getAddress());
                    return view;
                }
            };

            listView.setAdapter(bleDevicesListAdapter);

//             String lockId = uri.getQueryParameter("id");
//                        String bleAddress = uri.getQueryParameter("ble_addr");
//
//                        if (lockId == null || lockId.isEmpty() || bleAddress == null || bleAddress.isEmpty())
//                            return;
//
//                        Intent intent = new Intent(getApplicationContext(), SetupLockActivity.class);
//                        intent.putExtra("lockId", lockId);
//                        intent.putExtra("mode", SetupLockActivity.Mode.FIRST_CONFIG);
//                        intent.putExtra("lockBleAddress", bleAddress);
//                        startActivity(intent);
//                        finish();

            listView.setOnItemClickListener((parent, view, position, id) -> {
                bleManager.stopScanningDevices(leScanCallback); // stop scanning

                String bleAddress = bleDevicesList.get(position).getAddress();
                String lockId = bleDevicesMacList.get(position);

                if (lockId == null || lockId.isEmpty() || bleAddress == null || bleAddress.isEmpty())
                    return;

                Intent intent = new Intent(getApplicationContext(), SetupLockActivity.class);
                intent.putExtra("lockId", lockId);
                intent.putExtra("mode", SetupLockActivity.Mode.FIRST_CONFIG);
                intent.putExtra("lockBleAddress", bleAddress);
                startActivity(intent);
                finish();
            });

            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setView(bleSelection)
                    .setNegativeButton(R.string.CLOSE, (dialog, which) -> {})
                    .show());
        });


        TextInputLayout inviteCodeTextInputLayout = findViewById(R.id.text_input_invite_code);

        findViewById(R.id.btn_redeem_invite_code).setOnClickListener(view -> {
            String inviteCodeB64 = Objects.requireNonNull(inviteCodeTextInputLayout.getEditText()).getText().toString();

            if (inviteCodeB64.isEmpty()) return;

            redeemInvite(inviteCodeB64);
        });


    }


    private void startScanningDevices() {
        bleManager.scanDevices(leScanCallback);

        new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run() {
                runOnUiThread(() -> {
                    bleScanTextBtn.setText(bleManager.isScanning() ? R.string.stop : R.string.scan);
                    bleScanningSpinner.setVisibility(bleManager.isScanning() ? View.VISIBLE : View.INVISIBLE);
                });

                if (!bleManager.isScanning()) this.cancel();
            }
        },CHECK_INTERVAL, CHECK_INTERVAL);

    }

    // Device scan callback.
    private final ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);

                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "onScanResult: " + result.getDevice() + " - " + result.getDevice().getName());
                        return;
                    }

                    Log.i(TAG, "onScanResult: " + result.getDevice() + " - " + result.getDevice().getName());


                    if (result.getDevice() != null && !bleDevicesList.contains(result.getDevice())) {
                        bleDevicesList.add(result.getDevice());
                        bleDevicesListAdapter.notifyDataSetChanged();

                        byte[] bytes = result.getScanRecord().getManufacturerSpecificData(65535);

                        StringBuilder sb = new StringBuilder(bytes.length * 2);
                        for(byte b: bytes)
                            sb.append(String.format("%02X:", b));
                        sb.deleteCharAt(sb.length() - 1);
                        bleDevicesMacList.add(sb.toString());
                    }
                }


            };

    private void redeemInvite(String inviteCodeB64) {
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
                    .setPositiveButton(R.string.OK, (dialog, which) -> {})
                    .show());
            return;
        }

        if (inviteCode.length > 0)  inviteID = inviteCode[0];

        if (inviteCode.length > 1) lockMAC = inviteCode[1];

        if (inviteCode.length > 2) lockBLE = inviteCode[2];

        Log.i(TAG, "redeemInvite: " + lockMAC);

        if (lockMAC == null || inviteID == null || lockBLE == null) {
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.smart_door_created_title)
                    .setMessage(R.string.error_creating_smart_lock_invalid_invite_msg)
                    .setPositiveButton(R.string.OK, (dialog, which) -> {})
                    .show());
            return;
        }

        if (GlobalValues.getInstance().getUserLockById(lockMAC.toUpperCase()) != null) {
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.smart_door_created_title)
                    .setMessage(R.string.error_creating_smart_lock_repeated_lock_msg)
                    .setPositiveButton(R.string.OK, (dialog, which) -> {})
                    .show());
            return;
        }

        Lock lock = new Lock(lockMAC, lockMAC, lockBLE, "New Door", "lock-open");

        Utils.redeemInvite(lockMAC, inviteID,this , success -> {
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.smart_door_created_title)
                    .setMessage(success ? R.string.smart_lock_created_msg : R.string.error_creating_smart_lock_invalid_invite_msg)
                    .setPositiveButton(R.string.OK, (dialog, which) -> {
                        if (success) {
                            goToEditSmartLock(lock);
                            finish();
                        }

                    })
                    .show());
        });
    }

    private void goToEditSmartLock(Lock lock) {
        Utils.setUserLock(lock, lockCreated -> {
            if (lockCreated) {
                lock.setName("");
                Intent intent = new Intent(getApplicationContext(), EditDoorInformationActivity.class);
                intent.putExtra("lock", lock.getSerializable());
                startActivity(intent);
            }
        });
    }


    @Override
    public void updateUIOnBLEDisconnected() {

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
        return null;
    }

    @Override
    public String getLockId() {
        return null;
    }

    @Override
    public String getLockBLE() {
        return null;
    }
}