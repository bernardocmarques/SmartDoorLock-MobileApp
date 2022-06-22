package com.bernardocmarques.smartlockclient;

import static java.lang.Integer.parseInt;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;
import com.ncorti.slidetoact.SlideToActView;

import java.util.Timer;
import java.util.TimerTask;

public class SmartLockActivity extends AppCompatActivity implements BLEManager.BLEActivity {
    private static final String TAG = "SmartLock@SmartLockActivity";

    BLEManager bleManager;
    RSAUtil rsaUtil;
    Lock lock;


    /* UI */
    SlideToActView slideToUnlockView;
    TextView connectedStateTextView;
    ImageView connectedStateIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_lock);

        Bundle bundle = getIntent().getExtras();
        lock = Lock.fromSerializable(bundle.getSerializable("lock"));

        findViews();
        createUI();

        bleManager = BLEManager.getInstance();

        updateUIOnBLEDisconnected();

        connectedStateTextView.setText(R.string.BLE_CONNECTING);  // fixme maybe change

        bleManager.bindToBLEService(this);
        createUIListeners();

        setActionBar();
    }

    void setActionBar() {
        View actionBarInclude = findViewById(R.id.action_bar_include);
        MaterialToolbar actionBar = actionBarInclude.findViewById(R.id.taller_top_bar);


        MaterialTextView actionBarTitle = actionBarInclude.findViewById(R.id.taller_top_bar_name);
        actionBarTitle.setText(lock.getName());

        ImageView actionBarIcon = actionBarInclude.findViewById(R.id.taller_top_bar_icon);
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Bitmap bitmap = lock.getIcon();
                Log.i(TAG, "run: " + bitmap);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        actionBarIcon.setImageBitmap(bitmap);
                    });
                    this.cancel();
                }
            }
        }, 0, 100);

        actionBar.setNavigationOnClickListener(view -> finish());


        // Set share btn click listener
        actionBar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.share) {
                Intent intent = new Intent(getApplicationContext(), CreateNewInviteActivity.class);
                intent.putExtra("lockId", lock.getId());
                startActivity(intent);
            } else if (id == R.id.delete) {

            } else if (id == R.id.settings) {

            }
            return false;
        });


    }

    void findViews() {
        slideToUnlockView = findViewById(R.id.slide_to_unlock);
        connectedStateTextView = findViewById(R.id.connected_state_text);
        connectedStateIcon = findViewById(R.id.connected_state_icon);
    }

    void createUI() {
        slideToUnlockView.setVisibility(View.INVISIBLE);
    }

    void createUIListeners() {

    }


    public void updateUIOnBLEDisconnected() {
        connectedStateTextView.setText(R.string.BLE_DISCONNECTED);
        connectedStateIcon.setImageResource(R.drawable.ic_round_bluetooth_disabled_24);
    }

    public void updateUIOnBLEConnected() {
        connectedStateTextView.setText(R.string.BLE_CONNECTED);
        connectedStateIcon.setImageResource(R.drawable.ic_round_bluetooth_24);

        if (lock.isLocked()) {
            setSlideToUnlockViewToLocked();
        } else {
            setSlideToUnlockViewToUnlocked();
        }


    }


    private void setSlideToUnlockViewToLocked() {
        slideToUnlockView.resetSlider();
        slideToUnlockView.setReversed(false);

        slideToUnlockView.setSliderIcon(R.drawable.ic_font_awesome_lock);
        slideToUnlockView.setText(getString(R.string.slide_to_unlock));

        slideToUnlockView.setCompleteIcon(R.drawable.ic_font_awesome_lock_open);

        slideToUnlockView.setOnSlideCompleteListener(slideToActView -> {
            openLockCommunication();
        });
        slideToUnlockView.setVisibility(View.VISIBLE);

    }

    private void setSlideToUnlockViewToUnlocked() {
        slideToUnlockView.resetSlider();
        slideToUnlockView.setReversed(true);

        slideToUnlockView.setSliderIcon(R.drawable.ic_font_awesome_lock_open_inverted);
        slideToUnlockView.setText(getString(R.string.slide_to_lock));

        slideToUnlockView.setCompleteIcon(R.drawable.ic_font_awesome_lock);

        slideToUnlockView.setOnSlideCompleteListener(slideToActView -> {
            closeLockCommunication();
        });
        slideToUnlockView.setVisibility(View.VISIBLE);
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
                    getLockStateCommunication(ignore -> updateUIOnBLEConnected());
                });
            }
        }
    };


    private void openLockCommunication() {
        bleManager.sendCommandWithAuthentication(this,"RUD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
//                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Opened", Toast.LENGTH_LONG).show());
                setSlideToUnlockViewToUnlocked();
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

    private void closeLockCommunication() {
        bleManager.sendCommandWithAuthentication(this,"RLD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
//                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Locked", Toast.LENGTH_LONG).show());
                setSlideToUnlockViewToLocked();
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

    private void getLockStateCommunication(BLEManager.OnResponseReceived callback) {
        bleManager.sendCommandWithAuthentication(this,"RDS", responseSplit -> {
            if (responseSplit[0].equals("SDS")) {
                lock.setLockState(Lock.LockState.values()[parseInt(responseSplit[1])]);
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door State " + responseSplit[1], Toast.LENGTH_LONG).show());
                callback.onResponseReceived(null);
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
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




}