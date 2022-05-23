package com.bernardocmarques.smartlockclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

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
                            initBluetooth();
                        } else {
                            Log.e(TAG, "Location Permission Denied. Cant continue");
                            requestLocationPermission();
                        }
                    }
            );

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

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Utils.getUsernameFromDatabase(username -> GlobalValues.getInstance().setCurrentUsername(username));
        }

        if (!gotLocationPermission()) {
            requestLocationPermission();
        } else {
            initBluetooth();
        }
    }

    void initBluetooth() {
        if (!gotBluetoothPermission()) {
            requestBluetoothPermission();
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
        loadUserLocks();
        findViewById(R.id.btn_setup_lock).setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), SetupLockActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btn_test_messages).setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), MessagesTestActivity.class);
            startActivity(intent);
        });

        // Set menu click listener for sidebar opening/closing
        MaterialToolbar toolbar = findViewById(R.id.main_toolbar).findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> sidebar.toggleSidebar());

    }

    private void loadUserLocks() {

        Utils.getUserLocks(locks -> {
            FlexboxLayout lockCardsFlexbox = findViewById(R.id.lock_card_flexbox);
            LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

            FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(50, 50,50,50); // fixme set to dp

            for (Lock lock: locks) {

                View lockCard = inflater.inflate(R.layout.lock_card, lockCardsFlexbox, false);

                TextView textView = lockCard.findViewById(R.id.lock_card_text_view);
                textView.setText(lock.getName());

                lockCardsFlexbox.addView(lockCard,  params);

            }

            int remainder = locks.size() % 3;

            if (remainder != 0) {
                for (int i = 0; i < 3 - remainder; i++) {
                    View filler = inflater.inflate(R.layout.lock_card, lockCardsFlexbox, false);
                    filler.setClickable(false);
                    filler.setVisibility(View.INVISIBLE);
                    lockCardsFlexbox.addView(filler,  params);
                }
            }
        });





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