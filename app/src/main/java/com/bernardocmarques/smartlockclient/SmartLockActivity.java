package com.bernardocmarques.smartlockclient;

import static java.lang.Integer.parseInt;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ncorti.slidetoact.SlideToActView;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class SmartLockActivity extends AppCompatActivity implements Utils.CommActivity {
    private static final String TAG = "SmartLock@SmartLockActivity";

    BLEManager bleManager;
    RSAUtil rsaUtil;
    AESUtil aesUtil;
    Lock lock;


    /* UI */
    SlideToActView slideToUnlockView;
    TextView connectedStateTextView;
    ImageView connectedStateIcon;

    boolean isConnected = false;
    boolean remoteConnect = false;
    boolean offline = false;

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

    // fixme Consider changing to Activity Result API v
    private static final int LOCK_EDIT_REQUEST_CODE = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOCK_EDIT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                lock = Lock.fromSerializable(data.getExtras().getSerializable("lock"));
                setActionBar();
            }
        }
    }

    // fixme Consider changing to Activity Result API ^

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
                if ((!isConnected && !remoteConnect) || offline) {
                    Toast.makeText(getApplicationContext(), "Not yet connected to smart lock!", Toast.LENGTH_SHORT).show();
                    return false;
                }

                Intent intent = new Intent(getApplicationContext(), CreateNewInviteActivity.class);
                intent.putExtra("lockId", lock.getId());
                intent.putExtra("connectionMode", remoteConnect ? ConnectionMode.REMOTE : ConnectionMode.BLE);
                startActivity(intent);
            } else if (id == R.id.delete) {
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete_smart_lock_dialog_title)
                        .setMessage(R.string.delete_smart_lock_dialog_msg)
                        .setPositiveButton(R.string.delete_smart_lock_dialog_delete_btn, (dialog, which) -> {
                            Utils.deleteUserLock(getLockId(), deleted -> {
                                finish();
                            });
                        })
                        .setNegativeButton(R.string.delete_smart_lock_dialog_not_delete_btn, (dialog, which) -> {})
                        .show());
            } else if (id == R.id.settings) {
                Intent intent = new Intent(getApplicationContext(), EditDoorInformationActivity.class);
                intent.putExtra("lock", lock.getSerializable());
//                startActivity(intent);
                startActivityForResult(intent, LOCK_EDIT_REQUEST_CODE); // fixme Consider changing to Activity Result API
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
        isConnected = false;
        connectedStateTextView.setText(R.string.BLE_DISCONNECTED);
        connectedStateIcon.setImageResource(R.drawable.ic_round_bluetooth_disabled_24);
    }

    public void updateUIOnBLEConnected() {
        isConnected = true;
        connectedStateTextView.setText(R.string.BLE_CONNECTED);
        connectedStateIcon.setImageResource(R.drawable.ic_round_bluetooth_24);

        connectedStateIcon.setImageTintList(AppCompatResources.getColorStateList(getApplicationContext(), R.color.blue_bluetooth));

        if (lock.isLocked()) {
            setSlideToUnlockViewToLocked();
        } else {
            setSlideToUnlockViewToUnlocked();
        }


    }

    public void updateUIOnRemoteConnected() {
        connectedStateTextView.setText(R.string.REMOTE_CONNECTED);
        connectedStateIcon.setImageResource(R.drawable.ic_round_satellite_alt_24);

        connectedStateIcon.setImageTintList(AppCompatResources.getColorStateList(getApplicationContext(), R.color.black));

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
                remoteConnect = true;
                Utils.getPublicKeyBase64FromDatabase(lock.getId(), getActivity(), rsaKey -> {
                    Log.i(TAG, "onReceive: Entra aqui");
                    rsaUtil = new RSAUtil(rsaKey);
                    getLockStateCommunication(ignore -> Utils.checkUserSavedInvite(gotInvite -> {
                        if (!gotInvite) {
                            requestUserInvite(ignore2 -> updateUIOnRemoteConnected());
                        } else {
                            updateUIOnRemoteConnected();
                        }
                    }));
                });



            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
//                generateAndSendCredentials();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                Log.d(TAG, "Teste: " + intent.getData());
            } else if (BluetoothLeService.ACTION_GATT_MTU_SIZE_CHANGED.equals(action)) {
                Utils.getPublicKeyBase64FromDatabase(lock.getId(), getActivity(), rsaKey -> {
                    rsaUtil = new RSAUtil(rsaKey);
                    getLockStateCommunication(ignore -> Utils.checkUserSavedInvite(gotInvite -> {
                        if (!gotInvite) {
                            requestUserInvite(ignore2 -> updateUIOnBLEConnected());
                        } else {
                            updateUIOnBLEConnected();
                        }
                    }));
                });
            }
        }
    };


    private void sendCommand(String cmd, Utils.OnResponseReceived callback) {
        if (remoteConnect && !offline) {
            Utils.sendRemoteCommandWithAuthentication(this,cmd, callback);
        } else {
            bleManager.sendCommandWithAuthentication(this,cmd, callback);
        }
    }


    private void openLockCommunication() {
        sendCommand("RUD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
//                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Opened", Toast.LENGTH_LONG).show());
                setSlideToUnlockViewToUnlocked();
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

    private void closeLockCommunication() {
        sendCommand("RLD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
//                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door Locked", Toast.LENGTH_LONG).show());
                setSlideToUnlockViewToLocked();
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

    private void getLockStateCommunication(Utils.OnResponseReceived callback) {

        sendCommand("RDS", responseSplit -> {
            if (responseSplit[0].equals("SDS")) {
                lock.setLockState(Lock.LockState.values()[parseInt(responseSplit[1])]);
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door State " + responseSplit[1], Toast.LENGTH_LONG).show());
                callback.onResponseReceived(null);
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
            }
        });
    }

    private void requestUserInvite(Utils.OnTaskCompleted<Boolean> callback) {
        sendCommand("RUI " + Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getEmail(), responseSplit -> {
            if (responseSplit[0].equals("SUI")) {
                String inviteCodeB64 = responseSplit[1];
                Log.i(TAG, "Invite: " + inviteCodeB64);


                String[] inviteCode = {};

                String inviteID = null;
                String lockMAC = null;
                String lockBLE = null;

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


                Utils.saveUserInvite(inviteID, callback);

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
    public AESUtil getAESUtil() {
        return aesUtil;
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
    public void setAESUtil(AESUtil aes) {
        this.aesUtil = aes;
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