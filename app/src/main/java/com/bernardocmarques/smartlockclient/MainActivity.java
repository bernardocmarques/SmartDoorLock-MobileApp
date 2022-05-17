package com.bernardocmarques.smartlockclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SmartLock@MainActivity";

    Sidebar sidebar;


    ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts
                            .RequestMultiplePermissions(), result -> {
                        Boolean fineLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_FINE_LOCATION, false);
                        Boolean coarseLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_COARSE_LOCATION,false);
                        if ((fineLocationGranted != null && fineLocationGranted) ||
                                (coarseLocationGranted != null && coarseLocationGranted)) {
                            createUIListeners();
                        } else {
                            Log.e(TAG, "Location Permission Denied. Cant continue");
                            requestLocationPermission();
                        }
                    }
            );

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
        if (result != null) {
            Log.i(TAG, "onActivityResult: " + result.getContents());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.forceLightModeOn();

        setContentView(R.layout.activity_main);

        // Set sidebar
        sidebar = new Sidebar(this);

//        IntentIntegrator intentIntegrator = new IntentIntegrator(this);
//        intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
//        intentIntegrator.initiateScan();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Utils.getUsernameFromDatabase(username -> GlobalValues.getInstance().setCurrentUsername(username));
        }

        if (!gotLocationPermission()) {
            requestLocationPermission();
        } else {
            createUIListeners();
        }
    }


    boolean gotLocationPermission() {
        return ContextCompat.checkSelfPermission(
                getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                        getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    void requestLocationPermission() {
        locationPermissionRequest.launch(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    void createUIListeners() {
        findViewById(R.id.btn_setup_lock).setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), SetupLockActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btn_test_messages).setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), MessagesTestActivity.class);
            startActivity(intent);
        });

        // Set menu click listener for sidebar opening/closing
        MaterialToolbar toolbar = findViewById(R.id.map_toolbar).findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> sidebar.toggleSidebar());

    }

    @Override
    protected void onStart() {
        super.onStart();
        sidebar.changeUserUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sidebar.changeUserUI();
    }
}