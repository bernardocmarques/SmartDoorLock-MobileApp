package com.example.smartlockclient;

import androidx.annotation.NonNull;

import java.security.SecureRandom;
import java.util.Date;


public class BLEMessage {
    public static final int MAX_NONCE = 2147483647;

    private static final long ONE_SECOND_IN_MILLIS=1000; //millisecs

    String message;
    int nonce;
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

    BLEMessage(String message, int nonce, long t1, long t2) {
        this.message = message;
        this.nonce = nonce;
        this.t1 = new Date(t1);
        this.t2 = new Date(t2);
    }


    @NonNull
    @Override
    public String toString() {
        return this.message + " " + (int)(this.t1.getTime()/ONE_SECOND_IN_MILLIS) + " " + (int)(this.t2.getTime()/ONE_SECOND_IN_MILLIS) + " " + this.nonce;
    }
}
