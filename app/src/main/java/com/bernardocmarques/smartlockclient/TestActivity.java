package com.bernardocmarques.smartlockclient;

import static com.bernardocmarques.smartlockclient.BluetoothLeService.EXTRA_DATA;
import static java.lang.Integer.parseInt;
import static java.time.temporal.ChronoUnit.SECONDS;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestActivity extends AppCompatActivity implements Utils.CommActivity {

    private static final String TAG = "SmartLock@TestActivity";

    BLEManager bleManager;
    RSAUtil rsaUtil;
    AESUtil aesUtil;
    Lock lock;

    boolean remoteTest = true;

    SwitchMaterial proximityUnlockSwitch;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_lock);

        Bundle bundle = getIntent().getExtras();
        lock = Lock.fromSerializable(bundle.getSerializable("lock"));

        assert lock != null;
        Log.e(TAG, lock.getLocation() != null ? "onCreate: " + lock.getLocation().getLatitude() + ", " + lock.getLocation().getLongitude() : "null location");
        Log.e(TAG, "Using lock with name: " + lock.getName() + " and id: " + lock.getId());

        proximityUnlockSwitch = findViewById(R.id.switch_proximity_unlock);
        proximityUnlockSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            startTest();
        });


        if (!remoteTest) bleManager = BLEManager.getInstance();

        if (!remoteTest) updateUIOnBLEDisconnected();


        if (!remoteTest) bleManager.bindToBLEService(this);

        if (remoteTest) {
            Utils.getPublicKeyBase64FromDatabase(lock.getId(), getContext(), rsaKey -> {
                Log.i(TAG, "onReceive: Entra aqui");
                rsaUtil = new RSAUtil(rsaKey);
                getLockStateCommunication(ignore -> {
                    startTest();
                });
            });
        }
    }

    private static final int LOCK_EDIT_REQUEST_CODE = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOCK_EDIT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                lock = Lock.fromSerializable(data.getExtras().getSerializable("lock"));
            }
        }
    }


    @Override
    public void updateUIOnBLEDisconnected() {
        if (!remoteTest) bleManager.onResume(this, mGattUpdateReceiver);
        if (!remoteTest) bleManager = BLEManager.getInstance();
        if (!remoteTest) bleManager.bindToBLEService(this);
    }

    public void updateUIOnBLEConnected() {


    }

    public void updateUIOnRemoteConnected() {

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
                Log.i(TAG, "onReceive: Connected");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: disconnected");
                updateUIOnBLEDisconnected();


//                Utils.getPublicKeyBase64FromDatabase(lock.getId(), getContext(), rsaKey -> {
//                    Log.i(TAG, "onReceive: Entra aqui");
//                    rsaUtil = new RSAUtil(rsaKey);
//                    getLockStateCommunication(ignore -> {
//                        startTest();
//                    });
//                });



            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
//                generateAndSendCredentials();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                Log.d(TAG, "Teste: " + intent.getData());
            } else if (BluetoothLeService.ACTION_GATT_MTU_SIZE_CHANGED.equals(action)) {
                Utils.getPublicKeyBase64FromDatabase(lock.getId(), getContext(), rsaKey -> {
                    rsaUtil = new RSAUtil(rsaKey);
                    getLockStateCommunication(ignore -> {
                        startTest();
                    });
                });
            }
        }
    };


    private void sendCommand(String cmd, Utils.OnResponseReceived callback) {
        if (remoteTest) {
            Utils.sendRemoteCommandWithAuthentication(this,cmd, callback);
        } else {
            bleManager.sendCommandWithAuthentication(this,cmd, callback);
        }
    }

    ArrayList<Long> testTimes = new ArrayList<>();


    private void printTest() {
        String str = testTimes.toString();

        if (str.length() > 4000) {
            Log.v(TAG, "str.length = " + str.length());
            int chunkCount = str.length() / 4000;     // integer division
            for (int i = 0; i <= chunkCount; i++) {
                int max = 4000 * (i + 1);
                if (max >= str.length()) {
                    Log.v(TAG, "chunk " + i + " of " + chunkCount + ":" + str.substring(4000 * i));
                } else {
                    Log.v(TAG, "chunk " + i + " of " + chunkCount + ":" + str.substring(4000 * i, max));
                }
            }
        } else {
            Log.v(TAG, str);
        }
    }

    private void startTest() {
        Log.i(TAG, "Start Test");
        doTestRTT(2000 - testTimes.size());
    }

    private void doTest(int n) {
        Log.i(TAG, "Test " + n);

        if (n == 0) {
            printTest();
            return;
        }
        long startTime = System.currentTimeMillis();


        sendCommand(n%2==0 ? "RUD": "RLD", responseSplit -> {
            if (responseSplit[0].equals("ACK")) {
                testTimes.add(System.currentTimeMillis() - startTime);
                Log.i(TAG, "ACK");
                new Handler().postDelayed(() ->  doTest(n-1), 100);
            } else { // command not  ACK
                Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
                if (remoteTest) startTest();

            }
        });
    }

    private void doTestRTT(int n) {
        Log.i(TAG, "Test " + n);

        if (n == 0) {
            printTest();
            return;
        }
        long startTime = System.currentTimeMillis();

        if (remoteTest) {
                Utils.remoteConnection(this,"PNG", false, false,  true,
                        responseSplit -> {
                            if (responseSplit[0].equals("LOK")) {
                                testTimes.add(System.currentTimeMillis() - startTime);
                                Log.i(TAG, "LOK");
                                new Handler().postDelayed(() ->  doTestRTT(n-1), 100);
                            } else {
                                Log.e(TAG, "Error: Should have received LOK command. Received " + responseSplit[0]);
                            }
                        });

            } else {
            bleManager.sendCommandAndReceiveResponse(this,"PNG", false, false,
                    responseSplit -> {
                        if (responseSplit[0].equals("LOK")) {
                            testTimes.add(System.currentTimeMillis() - startTime);
                            Log.i(TAG, "LOK");
                            new Handler().postDelayed(() ->  doTestRTT(n-1), 100);
                        } else {
                            Log.e(TAG, "Error: Should have received LOK command. Received " + responseSplit[0]);
                        }
                    });
        }

    }




    private void getLockStateCommunication(Utils.OnResponseReceived callback) {

        sendCommand("RDS", responseSplit -> {
            if (responseSplit[0].equals("SDS")) {
                lock.setLockState(Lock.LockState.values()[parseInt(responseSplit[1])]);
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Door State " + responseSplit[1], Toast.LENGTH_LONG).show());
                callback.onResponseReceived(null);
            } else { // command not  ACK
                if (remoteTest) startTest();
                Log.e(TAG, "Error: Should have received ACK command. (After RDS)");
            }
        });
    }



    @Override
    public Context getContext() {
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
        if (!remoteTest) bleManager.onResume(this, mGattUpdateReceiver);
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