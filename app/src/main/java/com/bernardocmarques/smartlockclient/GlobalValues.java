package com.bernardocmarques.smartlockclient;

import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class GlobalValues {

    static String TAG = "SmartLock@GlobalValues";

    private static GlobalValues INSTANCE = null;

    private HashMap<String, Lock> userLocksMap = new HashMap<>();
    private boolean phoneIdRegistered = false;
    private boolean proximityServiceRunning = false;
//    private final ArrayList<Lock> proximityUnlockLocks = new ArrayList<>();
    private final HashMap<String, Lock> proximityUnlockLocksMap = new HashMap<>();



    private GlobalValues() { };

    public static GlobalValues getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GlobalValues();
        }
        return(INSTANCE);
    }

    public Lock getUserLockById(String lockId) {
        return userLocksMap.get(lockId);
    }

    public void addToUserLocksMap(Lock lock) {
        if (this.userLocksMap.containsKey(lock.getId())) return;

        this.userLocksMap.put(lock.getId(), lock);
    }

    public void clearUserLocksMap() {
        this.userLocksMap = new HashMap<>();
    }

    public boolean isPhoneIdRegistered() {
        return phoneIdRegistered;
    }

    public void setPhoneIdRegistered(boolean phoneIdRegistered) {
        this.phoneIdRegistered = phoneIdRegistered;
    }

    public ArrayList<Lock> getProximityUnlockLocks() {
        return new ArrayList<>(proximityUnlockLocksMap.values());
    }

    private void addToProximityUnlockLocks(Lock lock) {
        this.proximityUnlockLocksMap.put(lock.getId(), lock);
    }

    private void removeFromProximityUnlockLocks(Lock lock) {
        if (!this.proximityUnlockLocksMap.containsKey(lock.getId())) return;
        this.proximityUnlockLocksMap.remove(lock.getId());
    }

    public void updateProximityUnlockLocks(Lock lock) {
        if (lock.getLocation() != null && (lock.isProximityLockActive() || lock.isProximityUnlockActive())) {
            addToProximityUnlockLocks(lock);
        } else {
            removeFromProximityUnlockLocks(lock);
        }
    }

    public boolean isProximityServiceRunning() {
        return proximityServiceRunning;
    }

    public void setProximityServiceRunning(boolean proximityServiceRunning) {
        this.proximityServiceRunning = proximityServiceRunning;
    }


}
