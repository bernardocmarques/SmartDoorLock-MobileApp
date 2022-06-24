package com.bernardocmarques.smartlockclient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.os.AsyncTask;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;


import com.google.android.gms.common.util.IOUtils;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;


public class Utils {

    public static final long ONE_SECOND_IN_MILLIS = 1000; //millisecs

    public static int THUMBNAIL_SIZE_SMALL = 128;
    public static int THUMBNAIL_SIZE_MEDIUM = 256;

    public static String SERVER_URL = "https://server.smartlocks.ga";
//    public static String SERVER_URL = "http://192.168.1.7:5000";


    public enum UserType {
        ADMIN,
        OWNER,
        TENANT,
        PERIODIC_USER,
        ONETIME_USER
    }

    static String TAG = "SmartLock@Utils";


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
                sf.setTimeZone(TimeZone.getTimeZone("GMT"));

                editText.setText(sf.format(date));

            });
        });

        editText.addTextChangedListener(new DateInputValidator(textInputLayout, context));
    }

    public static Date getDateFromDateInput(TextInputLayout textInputLayout) throws ParseException {

        String dateStr = Objects.requireNonNull(textInputLayout.getEditText()).getText().toString();

        SimpleDateFormat sf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        sf.setTimeZone(TimeZone.getTimeZone("GMT"));

        return sf.parse(dateStr);

    }


    /*** -------------------------------------------- ***/
    /*** ----------------- VALIDITY ----------------- ***/
    /*** -------------------------------------------- ***/

    public static boolean isValidEmail(CharSequence target) {
        return Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    public static boolean areAllTrue(Collection<Boolean> booleans) {
        for (boolean b : booleans) {
            if (!b) return false;
        }
        return true;
    }

    /*** -------------------------------------------- ***/
    /*** -------------- USER INTERFACE -------------- ***/
    /*** -------------------------------------------- ***/

    static void forceLightModeOn() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    /*** -------------------------------------------- ***/
    /*** ------------------ STRINGS ----------------- ***/
    /*** -------------------------------------------- ***/

    static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static boolean searchInString(String query, String target) {
        Pattern pattern = Pattern.compile(query.replace(" ", "(.)*"), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(target);
        return matcher.find();
    }

    /*** -------------------------------------------- ***/
    /*** --------------- HTTP REQUESTS -------------- ***/
    /*** -------------------------------------------- ***/


    public interface OnTaskCompleted<T> {
        void onTaskCompleted(T obj);
    }

    public static class httpRequestJson extends AsyncTask<String, Void, JsonObject> {

        private final OnTaskCompleted<JsonObject> callback;

        public httpRequestJson(OnTaskCompleted<JsonObject> callback) {
            this.callback = callback;
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
                connection.setDoInput(true);
                connection.connect();
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


    public static class httpRequestImage extends AsyncTask<String, Void, Bitmap> {

        private final OnTaskCompleted<Bitmap> callback;
        private final Cache cache;


        public httpRequestImage(OnTaskCompleted<Bitmap> callback) {
            this.callback = callback;
            this.cache = Cache.getInstanceBigFiles();
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Bitmap doInBackground(String[] urls) {
            URL url;

            byte[] cachedValue = cache.get(urls[0]);

            if (cachedValue != null && cachedValue.length > 0) {
                return BitmapFactory.decodeByteArray(cachedValue, 0, cachedValue.length);
            }

            try {
                url = new URL(urls[0]);
                HttpURLConnection connection;
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();

                byte[] bytes = IOUtils.toByteArray(input);

                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                cache.save(urls[0], bytes);


                return bitmap;

            } catch (IOException e) {
                Log.e(TAG, e.getMessage());

                cachedValue = cache.get(urls[0]);

                if (cachedValue != null && cachedValue.length > 0) {
                    return BitmapFactory.decodeByteArray(cachedValue, 0, cachedValue.length);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            callback.onTaskCompleted(result);
        }
    }


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
    /*** --------------- Server Utils --------------- ***/
    /*** -------------------------------------------- ***/

    public static void finishUserCreation(OnTaskCompleted<Boolean> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);


            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                    callback.onTaskCompleted(false);
                }
            }, data.toString())).execute(SERVER_URL + "/finish-user-creation");
        });
    }

    public static void getUsernameFromDatabase(OnTaskCompleted<String> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {
            String tokenId = result.getToken();
            (new httpRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    String username = response.get("username").getAsString();
                    callback.onTaskCompleted(username);
                } else {
                    Log.e(TAG, "Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            })).execute(SERVER_URL + "/get-username?id_token=" + tokenId);
        });
    }

    public static void getCertificateFromDatabase(String lock_id, OnTaskCompleted<X509Certificate> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {
            String tokenId = result.getToken();
            (new httpRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    byte[] decoded = Base64.decode(response.get("certificate").getAsString(), Base64.NO_WRAP);
                    InputStream inputStream = new ByteArrayInputStream(decoded);

                    try {
                        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(inputStream);
                        callback.onTaskCompleted(certificate);

                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.onTaskCompleted(null);
                    }
                } else {
                    Log.e(TAG, "Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            })).execute(SERVER_URL + "/get-door-certificate?smart_lock_mac=" + lock_id + "&id_token=" + tokenId);
        });
    }

    public static void getPublicKeyBase64FromDatabase(String lock_id, Context context, OnTaskCompleted<String> callback) {
        getCertificateFromDatabase(lock_id, certificate -> {
            callback.onTaskCompleted(getPublicKeyBase64FromCertificate(certificate, context));
        });
    }


    public static void redeemInvite(String lockMAC, String inviteID, Context context, OnTaskCompleted<Boolean> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {
            String tokenId = result.getToken();
            String username = GlobalValues.getInstance().getCurrentUsername();

            Utils.getPublicKeyBase64FromDatabase(lockMAC, context, keyRSA -> {
                String masterKeyEncryptedLock =  KeyStoreUtil.getInstance().generateMasterKey(lockMAC + username, keyRSA);
                Log.i(TAG, "redeemInvite: token " + tokenId);
                JsonObject data = new JsonObject();
                data.addProperty("id_token", tokenId);
                data.addProperty("invite_id", inviteID);
                data.addProperty("master_key_encrypted_lock", masterKeyEncryptedLock);

                (new Utils.httpPostRequestJson(response -> {
                    if (response.get("success").getAsBoolean()) {
                        callback.onTaskCompleted(true);
                    } else {
                        Log.e(TAG, "Error code " +
                                response.get("code").getAsString() +
                                ": " +
                                response.get("msg").getAsString());
                        callback.onTaskCompleted(false);
                    }
                }, data.toString())).execute(SERVER_URL + "/redeem-invite");
            });
        });

    }

    public static void getUserLocks(OnTaskCompleted<ArrayList<Lock>> callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onTaskCompleted(new ArrayList<>());
            return;
        }
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {
            String tokenId = result.getToken();
            (new httpRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    JsonArray jsonArray = response.get("locks").getAsJsonArray();

                    ArrayList<Lock> locks = new ArrayList<>();

                    for (JsonElement json : jsonArray) {
                        locks.add(Lock.fromJson(json.getAsJsonObject()));
                    }


                    callback.onTaskCompleted(locks);
                } else {
                    Log.e(TAG, "Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            })).execute(SERVER_URL + "/get-user-locks?id_token=" + tokenId);
        });
    }

    public static void setUserLock(Lock lock, OnTaskCompleted<Boolean> callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onTaskCompleted(false);
            return;
        }

        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);
            data.add("lock", lock.toJson());

            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            }, data.toString())).execute(SERVER_URL + "/set-user-locks");
        });
    }

    public static void deleteUserLock(String lockId, OnTaskCompleted<Boolean> callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onTaskCompleted(false);
            return;
        }

        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);
            data.addProperty("lock_id", lockId);

            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            }, data.toString())).execute(SERVER_URL + "/delete-user-lock");
        });
    }

    public static void getAllIcons(OnTaskCompleted<HashMap<String, Bitmap>> callback) {
        (new httpRequestJson(response -> {
            if (response.get("success").getAsBoolean()) {
                JsonArray jsonArray = response.get("icons").getAsJsonArray();
                HashMap<String, Bitmap> icons = new HashMap<>();

                for (JsonElement json : jsonArray) {
                    String iconId = json.getAsString();

                    (new httpRequestImage(bitmap -> {
                        icons.put(iconId, bitmap);

                        if (icons.size() == jsonArray.size()) {
                            callback.onTaskCompleted(icons);
                        }
                    })).execute(SERVER_URL + "/get-icon?icon_id=" + iconId);
                }

            } else {
                Log.e(TAG, "Error code " +
                        response.get("code").getAsString() +
                        ": " +
                        response.get("msg").getAsString());
            }
        })).execute(SERVER_URL + "/get-all-icons");
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

        public String generateMasterKey(String keyID, String rsaPubKey) {

            Log.i(TAG, "save key with id: " + keyID);

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
            Log.i(TAG, "hmac with key id: " + keyID);

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

    /*** -------------------------------------------- ***/
    /*** ----------- CA and Certificates ------------ ***/
    /*** -------------------------------------------- ***/
    public static String getPublicKeyBase64FromCertificate(X509Certificate cert, Context context) {
        try {
            cert.checkValidity();
            validateCertificate(cert, context);

            return Base64.encodeToString(cert.getPublicKey().getEncoded(), Base64.NO_WRAP);
        } catch (CertificateExpiredException e) {
            Log.e(TAG,"(Certificate expired)" + e.getMessage());
            e.printStackTrace();
        } catch (CertificateNotYetValidException e) {
            Log.e(TAG,"(Certificate not yet valid)" + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void validateCertificate(X509Certificate c1, Context context) throws Exception {
        try {
            InputStream  issuerCertInoutStream = context.getResources().openRawResource(R.raw.my_ca);
            X509Certificate issuerCert = getCertFromFile(issuerCertInoutStream);
            TrustAnchor anchor = new TrustAnchor(issuerCert, null);
            Set<TrustAnchor> anchors = Collections.singleton(anchor);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            List<Certificate> list = Collections.singletonList(c1);
            CertPath path = cf.generateCertPath(list);
            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult) validator
                    .validate(path, params);
        } catch (Exception e) {
            Log.e(TAG,"EXCEPTION (Certificate not valid) " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private static X509Certificate getCertFromFile(InputStream inputStream) throws Exception {

        InputStream caInput = new BufferedInputStream(inputStream);
        X509Certificate cert = null;
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        cert = (X509Certificate) cf.generateCertificate(caInput);
        cert.getSerialNumber();
        return cert;
    }

}
