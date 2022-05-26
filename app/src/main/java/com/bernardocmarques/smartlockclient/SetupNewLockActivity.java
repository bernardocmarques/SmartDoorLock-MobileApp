package com.bernardocmarques.smartlockclient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Objects;

public class SetupNewLockActivity extends AppCompatActivity {

    private static final String TAG = "SmartLock@SetupNewLockActivity";

    static final int QR_CODE_REQUEST_CODE = 1;


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
        createUIListeners();


    }

    void setActionBar() {
        View actionBarInclude = findViewById(R.id.action_bar_include);
        MaterialToolbar actionBar = actionBarInclude.findViewById(R.id.backBar);
        actionBar.setTitle(R.string.add_new_smart_lock_title);

        actionBar.setNavigationOnClickListener(view -> finish());
    }

    void createUIListeners() {
        findViewById(R.id.btn_read_qr_code).setOnClickListener(v -> {
            IntentIntegrator intentIntegrator = new IntentIntegrator(this);
            intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            intentIntegrator.setRequestCode(QR_CODE_REQUEST_CODE);
            intentIntegrator.initiateScan();
        });


        findViewById(R.id.btn_scan_ble_devices).setOnClickListener(v -> {
            Log.i(TAG, "Clicked to scan ble devices - NOT DONE YET!"); // TODO do this...
        });


        TextInputLayout inviteCodeTextInputLayout = findViewById(R.id.text_input_invite_code);

        findViewById(R.id.btn_redeem_invite_code).setOnClickListener(view -> {
            String inviteCodeB64 = Objects.requireNonNull(inviteCodeTextInputLayout.getEditText()).getText().toString();

            if (inviteCodeB64.isEmpty()) return;

            redeemInvite(inviteCodeB64);
        });


    }

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
                lock.clearIcon();
                intent.putExtra("lock", lock);
                startActivity(intent);
            }
        });
    }


}