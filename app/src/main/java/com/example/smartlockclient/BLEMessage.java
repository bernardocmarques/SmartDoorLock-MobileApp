package com.example.smartlockclient;

import android.util.Log;

import androidx.annotation.NonNull;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;


public class BLEMessage {
    public static final int MAX_NONCE = 2147483647;

    private static final long ONE_SECOND_IN_MILLIS = Utils.ONE_SECOND_IN_MILLIS; //millisecs

    private static final HashMap<Date, Long> nonceMap = new HashMap<>();
    private static final String TAG = "BLEMessage";

    String message;
    long nonce;
    Date t1;
    Date t2;



    BLEMessage(String message) {
        this.message = message;
        SecureRandom random = new SecureRandom();
        this.nonce = random.nextInt(MAX_NONCE);
        Date now = new Date();
        this.t1 = new Date(now.getTime() - 30 * ONE_SECOND_IN_MILLIS);
        this.t2 = new Date(now.getTime() + 30 * ONE_SECOND_IN_MILLIS);
    }

    BLEMessage(String message, long t1, long t2, long nonce) {
        this.message = message;
        this.nonce = nonce;
        this.t1 = new Date(t1 * ONE_SECOND_IN_MILLIS);
        this.t2 = new Date(t2 * ONE_SECOND_IN_MILLIS);
    }


    @NonNull
    @Override
    public String toString() {
        return this.message + " " + (int)(this.t1.getTime()/ONE_SECOND_IN_MILLIS) + " " + (int)(this.t2.getTime()/ONE_SECOND_IN_MILLIS) + " " + this.nonce;
    }

    public boolean isValid() {
        Date now = new Date();
        purgeNonceMap(now);
        boolean validNonce = !nonceMap.containsValue(this.nonce);

        if (validNonce) {
            nonceMap.put(new Date(this.t2.getTime() + 10), this.nonce);
        } else {
            Log.e(TAG, "nonce not valid...");

        }

        Log.e(TAG, "isValid: " + this.t1.getTime() + " < " + now.getTime() + " < " + this.t2.getTime());
        Log.e(TAG, "isValid: " + this.t1.before(now));
        Log.e(TAG, "isValid: " + this.t2.after(now));

//        return true;
        return validNonce && (this.t1.before(now) && this.t2.after(now));
    }

    private void purgeNonceMap(Date now) {
        for (Date nonceExpire: nonceMap.keySet()) {
            if (nonceExpire.after(now)) {
                nonceMap.remove(nonceExpire);
            }
        }
    }
}


