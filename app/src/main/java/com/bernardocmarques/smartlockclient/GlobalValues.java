package com.bernardocmarques.smartlockclient;

import java.util.HashMap;

public class GlobalValues {

    static String TAG = "SmartLock@GlobalValues";

    private static GlobalValues INSTANCE = null;

    private HashMap<String, Lock> userLocksMap = new HashMap<>();

    private boolean phoneIdRegistered = false;

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
}
