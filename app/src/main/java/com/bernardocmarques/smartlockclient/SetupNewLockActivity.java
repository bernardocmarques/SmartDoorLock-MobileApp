package com.bernardocmarques.smartlockclient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class SetupNewLockActivity extends AppCompatActivity {

    private static final String TAG = "SmartLock@SetupNewLockActivity";

    static final int QR_CODE_REQUEST_CODE = 1;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == QR_CODE_REQUEST_CODE) {
            IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);

            Uri uri = Uri.parse(result.getContents());
            if (uri != null) {
                String lockId = uri.getQueryParameter("id");
                String bleAddress = uri.getQueryParameter("ble_addr");

                Intent intent = new Intent(getApplicationContext(), SetupLockActivity.class);
                intent.putExtra("lockId", lockId);
                intent.putExtra("lockBleAddress", bleAddress);
                startActivity(intent);
            }

        }


    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_new_lock);

        setActionBarTitle();
        createUIListeners();


    }

    void setActionBarTitle() {
        View actionBarInclude = findViewById(R.id.action_bar_include);
        MaterialToolbar actionBar = actionBarInclude.findViewById(R.id.backBar);
        actionBar.setTitle(R.string.add_new_smart_lock_title);
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


    }


}