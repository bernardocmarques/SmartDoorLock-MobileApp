package com.bernardocmarques.smartlockclient;

public class GlobalValues {

    static String TAG = "SmartLock@GlobalValues";

    private static GlobalValues INSTANCE = null;

    private String currentUsername = null;

    private GlobalValues() { };

    public static GlobalValues getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GlobalValues();
        }
        return(INSTANCE);
    }


    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }
}
