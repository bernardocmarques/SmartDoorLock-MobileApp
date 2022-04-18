package com.example.smartlockclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SmartLock@MainActivity";

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(getApplicationContext(), CreateNewInviteActivity.class);
        startActivity(intent);


        setContentView(R.layout.activity_main);

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
    }
}