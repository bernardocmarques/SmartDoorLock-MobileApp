package com.example.smartlockclient;


import android.os.Build;
import androidx.annotation.RequiresApi;
import android.util.Base64;
import android.util.Log;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
class RSAUtil {

    private static final String TAG = "SmartLock@RSAUtils";

    String encrypt(String data, String rsaPublicKeyString) {

        byte[] encryptedBytes = null;

        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.decode(rsaPublicKeyString, Base64.DEFAULT));
            RSAPublicKey rsaPublicKey = (RSAPublicKey) kf.generatePublic(keySpecX509);

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA256AndMGF1Padding");

            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            Log.w(TAG, encryptedBytes.length + "");

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        String result = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);

        if (result.charAt(0) == '+') {
            result = result.replaceFirst("[+]", "-"); // replace '+' with '-', this is used to prevent base64 string started with '+' to be handled as an AT command response
        }

        return result;
    }
}