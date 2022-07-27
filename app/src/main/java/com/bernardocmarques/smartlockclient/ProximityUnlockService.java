package com.bernardocmarques.smartlockclient;

import static java.lang.Integer.parseInt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ProximityUnlockService extends Service {
    private static final String TAG = "SmartLock@ProximityUnlockService";


    static Long LOCATION_UPDATE_INTERVAL_LOW = 1000L;
    static Long LOCATION_UPDATE_MAX_WAIT_INTERVAL_LOW = LOCATION_UPDATE_INTERVAL_LOW;

    static Long LOCATION_UPDATE_INTERVAL_HIGH = 15000L;
    static Long LOCATION_UPDATE_MAX_WAIT_INTERVAL_HIGH = LOCATION_UPDATE_INTERVAL_HIGH * 2;

    private LocationCallback locationCallback;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;


    private static final int ONGOING_NOTIFICATION_ID = 1;



    Handler handler;

    public ArrayList<String> locksInOpenCooldown = new ArrayList<>();
    public ArrayList<String> locksInCloseCooldown = new ArrayList<>();

    public static class OpenDoorRunnable implements Runnable, Utils.CommActivity {

        Lock lock;
        BLEManager bleManager;
        Context context;

        BroadcastReceiver broadcastReceiver;

        RSAUtil rsaUtil;
        AESUtil aesUtil;
        
        volatile boolean done = false;



        public OpenDoorRunnable(Context context, Lock lock) {

            this.context = context;
            this.lock = lock;
            bleManager = BLEManager.getOneUseInstance(this);
            Log.d(TAG, "Created runnable for " + lock.getName());

        }

        public void run() {
            Log.d(TAG, "Run runnable for " + lock.getName());

            if (!((ProximityUnlockService)context).locksInOpenCooldown.contains(lock.getId())) {

                ((ProximityUnlockService)context).locksInOpenCooldown.add(lock.getId());

                new Handler(context.getMainLooper()).postDelayed(() -> ((ProximityUnlockService)context).locksInOpenCooldown.remove(lock.getId()), 10000);

                Log.e(TAG, "Open: " + lock.getName());
                startScanForLock(lock);


                while (!done) {}

            } else {
                Log.i(TAG, "Lock " + lock.getName() + "is in cooldown");
            }
        }

        private void startScanForLock(Lock lock) {

            Log.i(TAG, "Start Scanning For device " + lock.getName() + " - " + lock.getBleAddress());

            bleManager.waitForInitialization(() -> {
                List<ScanFilter> filters = null;
                filters = new ArrayList<>();

                ScanFilter filter = new ScanFilter.Builder()
                        .setDeviceAddress(lock.getBleAddress())
                        .build();
                filters.add(filter);


                ScanSettings scanSettings = new ScanSettings.Builder()
                        .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                        .setLegacy(false)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
                        .setReportDelay(0L)
                        .build();

                bleManager.scanLeDevice(leScanCallback, filters, scanSettings, 5000);

            });
        }


        // Device scan callback.
        private final ScanCallback leScanCallback =
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);

                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        BluetoothDevice device = result.getDevice();
                        Log.i(TAG, "onScanResult: " + lock.getName() + " ---- " + device + " - " + device.getName());
                        bleManager.stopScanningDevices(this);
                        connectToLock();
                    }
                };


        private void connectToLock() {

            this.broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                    } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                        updateUIOnBLEDisconnected();
                    } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                    } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                    } else if (BluetoothLeService.ACTION_GATT_MTU_SIZE_CHANGED.equals(action)) {
                        Log.e(TAG, "onReceive: Chega aqui!!!!!");
                        Utils.getPublicKeyBase64FromDatabase(lock.getId(), getContext(), rsaKey -> {
                            rsaUtil = new RSAUtil(rsaKey);
                            updateUIOnBLEConnected();

                            openLockCommunication();
                        });
                    }
                }
            };

            bleManager.onResume(this, this.broadcastReceiver);
        }

        private void openLockCommunication() {
            bleManager.sendCommandWithAuthentication(this, "RUD", responseSplit -> {
                if (responseSplit[0].equals("ACK")) {
                    Log.i(TAG, "openLockCommunication: Lock opened.");

                } else { // command not  ACK
                    Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
                }

                bleManager.disconnectFromDevice();

                context.unbindService(bleManager.mServiceConnection);
                context.unregisterReceiver(this.broadcastReceiver);
                bleManager.mBluetoothLeService = null;
                bleManager = null;
                done = true;
            });
        }


        /*** -------------------------------------------- ***/
        /*** ---------- Comm Activity Methods ----------- ***/
        /*** -------------------------------------------- ***/


        @Override
        public void updateUIOnBLEDisconnected() {

        }

        @Override
        public void updateUIOnBLEConnected() {

        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public RSAUtil getRSAUtil() {
            return this.rsaUtil;
        }

        @Override
        public AESUtil getAESUtil() {
            return this.aesUtil;
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

    }

    public static class CloseDoorRunnable implements Runnable, Utils.CommActivity {

        Lock lock;
        Context context;

        RSAUtil rsaUtil;
        AESUtil aesUtil;

        public CloseDoorRunnable(Context context, Lock lock) {
            this.context = context;
            this.lock = lock;
        }

        public void run() {
            if (!((ProximityUnlockService)context).locksInCloseCooldown.contains(lock.getId())) {
                ((ProximityUnlockService)context).locksInCloseCooldown.add(lock.getId());
                new Handler(context.getMainLooper()).postDelayed(
                        () -> ((ProximityUnlockService)context).locksInCloseCooldown.remove(lock.getId()), 10000);

                Log.e(TAG, "Close: " + lock.getName());

                Utils.getPublicKeyBase64FromDatabase(lock.getId(), getContext(), rsaKey -> {
                    Log.i(TAG, "onReceive: Entra aqui");
                    rsaUtil = new RSAUtil(rsaKey);
                    closeLockCommunication();

                });
            } else {
                Log.i(TAG, "Lock " + lock.getName() + "is in cooldown");
            }
        }


        private void closeLockCommunication() {
            Utils.sendRemoteCommandWithAuthentication(this, "RLD", responseSplit -> {
                if (responseSplit[0].equals("ACK")) {
                    Log.i(TAG, "openLockCommunication: Lock closed.");
                } else { // command not  ACK
                    Log.e(TAG, "Error: Should have received ACK command. (After RUD)");
                }
            });
        }


        /*** -------------------------------------------- ***/
        /*** ---------- Comm Activity Methods ----------- ***/
        /*** -------------------------------------------- ***/


        @Override
        public void updateUIOnBLEDisconnected() {

        }

        @Override
        public void updateUIOnBLEConnected() {

        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public RSAUtil getRSAUtil() {
            return this.rsaUtil;
        }

        @Override
        public AESUtil getAESUtil() {
            return this.aesUtil;
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

    }


    Location lastLocation;




    private static class CustomThreadPoolExecutor extends ThreadPoolExecutor {

        public CustomThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                        long keepAliveTime, TimeUnit unit,
                                        BlockingQueue<Runnable> workQueue, AbortPolicy abortPolicy) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            Log.i(TAG, "Perform beforeExecute() logic");
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t != null) {
                Log.i(TAG, "Perform exception handler logic");
            }
            Log.i(TAG, "Perform afterExecute() logic");
        }
    }

    BlockingQueue<Runnable> blockingQueueOpen = new LinkedBlockingQueue<>();
    ThreadPoolExecutor executorOpen = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, blockingQueueOpen);

    BlockingQueue<Runnable> blockingQueueClose = new LinkedBlockingQueue<>();
    ThreadPoolExecutor executorClose = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, blockingQueueClose);

    @Override
    public void onCreate() {
        loadLocks();
        handler = new Handler();
        executorOpen.prestartAllCoreThreads();
        executorClose.prestartAllCoreThreads();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {

                Log.i(TAG, "onLocationResult: " + locationResult.getLastLocation());

                Location location = locationResult.getLastLocation();

                if (location == null) return;

                Log.e(TAG, "onLocationResult: " + GlobalValues.getInstance().getProximityUnlockLocks());
                Log.e(TAG, "Location: " + location.getLongitude() + ", " + location.getLatitude());
                Log.e(TAG, "Location Accuracy: " + location.getAccuracy());

                if (location.getAccuracy() > 23) return; // fixme tune this value

                for (Lock lock : GlobalValues.getInstance().getProximityUnlockLocks()) {
                    Location lockLocation = lock.getLocation();

                    double distanceBetweenLockAndLast = lastLocation != null ? Utils.distanceBetweenLocationsInMeters(lockLocation, lastLocation) : 11.0; // considered outside the inner zone if null
                    double distanceBetweenLockAndNow = Utils.distanceBetweenLocationsInMeters(lockLocation, location);

                    Log.i(TAG, "distanceBetweenLockAndLast: " + distanceBetweenLockAndLast);
                    Log.i(TAG, "distanceBetweenLockAndNow: " + distanceBetweenLockAndNow);

                    if (distanceBetweenLockAndLast > 10 && distanceBetweenLockAndNow <= 10 && lock.isProximityUnlockActive()) {
                        OpenDoorRunnable runnable = new OpenDoorRunnable(getContext(), lock);
                        blockingQueueOpen.offer(runnable);
                    } else if (distanceBetweenLockAndLast <= 10 && distanceBetweenLockAndNow > 10) {
                        // todo remove to OpenQueue
                    } else if (distanceBetweenLockAndLast <= 20 && distanceBetweenLockAndNow > 20 && lock.isProximityLockActive()) {
                        CloseDoorRunnable runnable = new CloseDoorRunnable(getContext(), lock);
                        blockingQueueClose.offer(runnable);
                    }
                }

                lastLocation = location;
            }
        };
    }

    private void loadLocks() {
        Utils.getUserLocks(locks -> {
            for (Lock lock: locks) {
                GlobalValues.getInstance().updateProximityUnlockLocks(lock);
            }
        });
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        GlobalValues.getInstance().setProximityServiceRunning(true);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE);

        Notification notification =
                new Notification.Builder(this, getString(R.string.proximity_channel_ID))
                        .setContentTitle(getText(R.string.proximity_notification_title))
                        .setContentText(getText(R.string.proximity_notification_message))
                        .setSmallIcon(R.drawable.ic_logo_horizontal)  // todo change icon
                        .setContentIntent(pendingIntent)
                        .setTicker(getText(R.string.proximity_ticker_text))
                        .build();

        // Notification ID cannot be 0.
        startForeground(ONGOING_NOTIFICATION_ID, notification);


        startLocation();

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        GlobalValues.getInstance().setProximityServiceRunning(false);
    }


    /*** -------------------------------------------- ***/
    /*** ---------------- LOCATION ------------------ ***/
    /*** -------------------------------------------- ***/

    protected void createLocationRequestHighInterval() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(LOCATION_UPDATE_INTERVAL_HIGH);
        locationRequest.setMaxWaitTime(LOCATION_UPDATE_MAX_WAIT_INTERVAL_HIGH);
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);
//        locationRequest.setFastestInterval(5000L); // fixme remove

    }

    protected void createLocationRequestSmallInterval() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(LOCATION_UPDATE_INTERVAL_LOW);
        locationRequest.setMaxWaitTime(LOCATION_UPDATE_MAX_WAIT_INTERVAL_LOW);
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);
//        locationRequest.setFastestInterval(5000L); // fixme remove

    }

    void updateLocationRequestToHighInterval() {
        createLocationRequestHighInterval();
        restartLocationUpdates();
    }

    void updateLocationRequestToSmallInterval() {
        createLocationRequestSmallInterval();
        restartLocationUpdates();
    }

    void restartLocationUpdates() {
        stopLocationUpdates();
        startLocationUpdates();
    }

    void checkIfLocationOn() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(locationSettingsResponse -> {
            Log.i(TAG, "checkIfLocationOn: GPS is ON");
            startLocationUpdates();
        });

        task.addOnFailureListener(e -> {
            if (e instanceof ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                // Show the dialog by calling startResolutionForResult(),
                // and check the result in onActivityResult().
                ResolvableApiException resolvable = (ResolvableApiException) e;
                Log.e(TAG, "checkIfLocationOn: GPS not ON");
            }
        });

    }

    private void startLocation() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationRequest == null) createLocationRequestHighInterval();
            checkIfLocationOn();
        }
    }


    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }


    public Context getContext() {
        return this;
    }
}