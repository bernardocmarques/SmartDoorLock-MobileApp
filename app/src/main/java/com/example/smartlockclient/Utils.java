package com.example.smartlockclient;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Utils {

    static String TAG = "SmartLock@Utils";

    public static String hmacBase64(String dataBase64, String keyBase64)
            throws NoSuchAlgorithmException, InvalidKeyException {


        SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.decode(keyBase64, Base64.NO_WRAP), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacResult = mac.doFinal(Base64.decode(dataBase64, Base64.NO_WRAP));

        return Base64.encodeToString(hmacResult, Base64.NO_WRAP);
    }



}
