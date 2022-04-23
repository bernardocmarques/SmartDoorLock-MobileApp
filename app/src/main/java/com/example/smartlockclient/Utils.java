package com.example.smartlockclient;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.os.AsyncTask;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;


import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Utils {

    /* Testing variables */ // todo remove

    public static String rsaPubKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAl4iRt8ORglI2tv0U3Dp2" +
            "3Zyoc4bY0l414bNCK6TN1AXKXx6iQaiugnsFK84BhVtd6uNX/hMxsat+aZoJvPdM" +
            "aY48U1DgAqBtFhSbXakyfghdk6VDVV6chQzrYzyvZ1eR7q0qfmf5w3Z02fSfI66E" +
            "a8BAT1UpAEWdSU+xFlbRb9qsZYGV99+JjPC4PGhbHMOSsO+We4ZsP8UosNyF8A62" +
            "FheFXimCujiPmOBIOablN9TuWXAUtNHhWf4EyYDQvEo/NfY2mleiYjKqHoJpkIu+" +
            "sMcJJ3ry5Z4HEZi+SUbCjL7I5ZYF8aZq3YRxS4n2ZO7/w7n7B5621HMsahRNUi76" +
            "1wIDAQAB";

    public static String userId = "0vn3kfl3n";
    public static String masterKey = "SoLXxAJHi1Z3NKGHNnS5n4SRLv5UmTB4EssASi0MmoI=";

    /* Testing variables (end) */



    public static final long ONE_SECOND_IN_MILLIS = 1000; //millisecs

    public static String SERVER_URL = "http://192.168.1.7:5000";


    public static enum UserType {
        ADMIN,
        OWNER,
        TENANT,
        PERIODIC_USER,
        ONETIME_USER
    }

    static String TAG = "SmartLock@Utils";

    public static String hmacBase64(String dataBase64, String keyBase64)
            throws NoSuchAlgorithmException, InvalidKeyException {


        SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.decode(keyBase64, Base64.NO_WRAP), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacResult = mac.doFinal(Base64.decode(dataBase64, Base64.NO_WRAP));

        return Base64.encodeToString(hmacResult, Base64.NO_WRAP);
    }


    public static void createDatePicker(TextInputLayout textInputLayout, AppCompatActivity context) {
        EditText editText = textInputLayout.getEditText();

        assert editText != null;
        editText.setOnFocusChangeListener((view, isFocused) -> {
            if (isFocused) {
                editText.setHint("dd/mm/yyyy");
            } else {
                editText.setHint("");
            }
        });

        textInputLayout.setEndIconOnClickListener(view -> {

            MaterialDatePicker<Long> materialDatePicker = MaterialDatePicker.Builder
                    .datePicker()
                    .setTitleText("Select date")
                    .build();

            materialDatePicker.show(context.getSupportFragmentManager(), "MATERIAL_DATE_PICKER");

            materialDatePicker.addOnPositiveButtonClickListener(selection -> {
                SimpleDateFormat sf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date date = new Date(selection);

                editText.setText(sf.format(date));

            });
        });

        editText.addTextChangedListener(new DateInputValidator(textInputLayout, context));
    }

    public static Date getDateFromDateInput(TextInputLayout textInputLayout) throws ParseException {

        String dateStr = Objects.requireNonNull(textInputLayout.getEditText()).getText().toString();

        SimpleDateFormat sf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return sf.parse(dateStr);

    }

    /*** -------------------------------------------- ***/
    /*** --------------- HTTP REQUESTS -------------- ***/
    /*** -------------------------------------------- ***/


    public interface OnTaskCompleted<T> {
        void onTaskCompleted(T obj);
    }

//    public static class httpRequestJson extends AsyncTask<String, Void, JsonObject> {
//
//        private final OnTaskCompleted<JsonObject> callback;
//        private final Cache cache;
//
//        public httpRequestJson(OnTaskCompleted<JsonObject> callback) {
//            this.callback = callback;
//            this.cache = Cache.getInstanceSmallFiles();
//        }
//
//        @Override
//        protected void onPreExecute() {
//        }
//
//        @Override
//        protected JsonObject doInBackground(String[] urls) {
//            URL url;
//
//
//
//            try {
//                url = new URL(urls[0]);
//                HttpURLConnection connection;
//                connection = (HttpURLConnection) url.openConnection();
//                connection.setDoInput(true);
//                connection.connect();
//                InputStream input = connection.getInputStream();
//
//                JsonObject jsonObject = JsonParser.parseReader( new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
//
//                cache.save(urls[0], jsonObject.toString().getBytes(StandardCharsets.UTF_8));
//                return jsonObject;
//
//            } catch (IOException e) {
//                Log.e(TAG, e.getMessage());
//
//                byte[] cachedValue = cache.get(urls[0]);
//
//                if (cachedValue != null && cachedValue.length > 0) {
//                    return JsonParser.parseString(new String(cachedValue, StandardCharsets.UTF_8)).getAsJsonObject();
//                }
//            }
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(JsonObject result) {
//            callback.onTaskCompleted(result);
//        }
//    }

    public static class httpPostRequestJson extends AsyncTask<String, Void, JsonObject> {

        private final OnTaskCompleted<JsonObject> callback;
        private final String jsonString;

        public httpPostRequestJson(OnTaskCompleted<JsonObject> callback, String jsonString) {
            this.callback = callback;
            this.jsonString = jsonString;
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected JsonObject doInBackground(String[] urls) {
            URL url;

            try {
                url = new URL(urls[0]);
                HttpURLConnection connection;
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                connection.setDoInput(true);

                OutputStream outputStream = connection.getOutputStream();
                byte[] inputJson = jsonString.getBytes(StandardCharsets.UTF_8);
                outputStream.write(inputJson, 0, inputJson.length);

                InputStream input = connection.getInputStream();

                return JsonParser.parseReader( new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();

            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(JsonObject result) {
            callback.onTaskCompleted(result);
        }
    }

    /*** -------------------------------------------- ***/
    /*** ----------------- KeyStore ----------------- ***/
    /*** -------------------------------------------- ***/

    public static class KeyStoreUtil {

        private static KeyStoreUtil INSTANCE = null;
        KeyStore keyStore;


        private KeyStoreUtil() {
            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
            } catch (Exception e) {
                Log.e(TAG, "onCreate: Error getting KeyStore", e);
            }
        }


        public static KeyStoreUtil getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new KeyStoreUtil();
            }
            return(INSTANCE);
        }

        public String generateMasterKey(String keyID) {

            try {
                keyStore.deleteEntry(keyID);

                if (!keyStore.containsAlias(keyID)) {

                    KeyGenerator keygen = KeyGenerator.getInstance("HmacSHA256");
                    keygen.init(256);

                    SecretKey masterKey = keygen.generateKey();

                    keyStore.setEntry(
                            keyID,
                            new KeyStore.SecretKeyEntry(masterKey),
                            new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build());

                    return (new RSAUtil()).encrypt(Base64.encodeToString(masterKey.getEncoded(), Base64.NO_WRAP), rsaPubKey);

                } else {
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "storeKeychainKey: Error generating Master Key", e);
                return null;
            }
        }

        public String hmacBase64WithMasterKey(String dataBase64, String keyID) {

            try {
                SecretKey masterKey = (SecretKey) keyStore.getKey(keyID, null);
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(masterKey);



                byte[] hmacResult = mac.doFinal(Base64.decode(dataBase64, Base64.NO_WRAP));
                return Base64.encodeToString(hmacResult, Base64.NO_WRAP);


            } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException | InvalidKeyException e) {
                Log.e(TAG, "hmacBase64WithMasterKey: Error getting masterKey", e);
                return null;
            }
        }

    }

}
