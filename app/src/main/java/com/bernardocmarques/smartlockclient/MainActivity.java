package com.bernardocmarques.smartlockclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

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

                        if (result.isEmpty()) {
                            Log.e(TAG, "Here");
                            return;
                        }

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
        Log.i(TAG, "gotBluetoothPermission: ");
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(
                    getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    void requestBluetoothPermission() {
        Log.i(TAG, "requestBluetoothPermission: ");
        if (Build.VERSION.SDK_INT >= 31) {
            Log.i(TAG, "requestBluetoothPermission: if");

            bluetoothPermissionRequest.launch(new String[] {
                    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
            });
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        CharSequence name = getString(R.string.proximity_channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(getString(R.string.proximity_channel_ID), name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.forceLightModeOn();

        Cache.getInstanceBigFiles(getApplicationContext());
        Cache.getInstanceSmallFiles(getApplicationContext());

        setContentView(R.layout.activity_main);

        createNotificationChannel();

        // Set sidebar
        sidebar = new Sidebar(this);

        if (FirebaseAuth.getInstance().getCurrentUser() != null && !GlobalValues.getInstance().isPhoneIdRegistered()) {
            Utils.registerPhoneId(getApplicationContext(), ignored -> GlobalValues.getInstance().setPhoneIdRegistered(true));
        }

        Log.i(TAG, "onCreate: " + !gotLocationPermission());
        if (!gotLocationPermission()) {
            requestLocationPermission();
        } else {
            initBluetooth();
        }
    }

    void initBluetooth() {
        Log.i(TAG, "initBluetooth: ");
        if (!gotBluetoothPermission()) {
            Log.i(TAG, "initBluetooth: if 1");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            locationPermissionRequest.launch(new String[] {
//                    Manifest.permission.ACCESS_BACKGROUND_LOCATION, // fixme cant use this
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            locationPermissionRequest.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    void createUIListeners() {
        findViewById(R.id.btn_setup_lock).setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), SetupLockActivity.class);
            startActivity(intent);
        });


        findViewById(R.id.btn_add_new_lock).setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), SetupNewLockActivity.class);
            startActivity(intent);
        });

        // Set menu click listener for sidebar opening/closing
        MaterialToolbar toolbar = findViewById(R.id.main_toolbar).findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> sidebar.toggleSidebar());

    }

    private void initProximityUnlockForegroundService() {
        if (!GlobalValues.getInstance().isProximityServiceRunning()) { // fixme uncomment
            Intent intent = new Intent(this, ProximityUnlockService.class);
            getApplicationContext().startForegroundService(intent);
        }
    }

    public void loadUserLocks() {
        FlexboxLayout lockCardsFlexbox = findViewById(R.id.lock_card_flexbox);
        lockCardsFlexbox.removeAllViews();
        GlobalValues.getInstance().clearUserLocksMap();

        Utils.getUserLocks(locks -> {



            LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

            final float scale = this.getResources().getDisplayMetrics().density;

            FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins((int)(10 * scale), (int)(10 * scale),(int)(10 * scale),(int)(10 * scale));

            for (Lock lock: locks) {
                GlobalValues.getInstance().addToUserLocksMap(lock);

                View lockCard = inflater.inflate(R.layout.lock_card, lockCardsFlexbox, false);
                lockCard.setVisibility(View.INVISIBLE);

                TextView textView = lockCard.findViewById(R.id.lock_card_text_view);
                textView.setText(lock.getName());

                lockCardsFlexbox.addView(lockCard, params);

                lockCard.setOnClickListener(view -> {
                    // fixme remove test vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv

                    Intent intent = new Intent(getApplicationContext(), TestActivity.class);
//                    Intent intent = new Intent(getApplicationContext(), SmartLockActivity.class);
                    // fixme remove test ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    intent.putExtra("lock", lock.getSerializable());
                    startActivity(intent);
                });

                ImageView imageView = lockCard.findViewById(R.id.lock_card_image_view);

                new Timer().scheduleAtFixedRate(new TimerTask(){
                    @Override
                    public void run(){

                        Bitmap bitmap = lock.getIcon();
                        if (bitmap != null) {

                            runOnUiThread(() -> {
                                imageView.setImageBitmap(bitmap);
                                lockCard.setVisibility(View.VISIBLE);
                            });

                            this.cancel();
                        }
                    }
                },0,100);
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

            initProximityUnlockForegroundService();
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        sidebar.changeUserUI();
        loadUserLocks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sidebar.changeUserUI();
    }
}