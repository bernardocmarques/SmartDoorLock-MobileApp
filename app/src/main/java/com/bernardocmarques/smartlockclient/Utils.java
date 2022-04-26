package com.bernardocmarques.smartlockclient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;


public class Utils {

    /* Testing variables */ // todo remove

    public static String rsaPubKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAl4iRt8ORglI2tv0U3Dp2" +
            "3Zyoc4bY0l414bNCK6TN1AXKXx6iQaiugnsFK84BhVtd6uNX/hMxsat+aZoJvPdM" +
            "aY48U1DgAqBtFhSbXakyfghdk6VDVV6chQzrYzyvZ1eR7q0qfmf5w3Z02fSfI66E" +
            "a8BAT1UpAEWdSU+xFlbRb9qsZYGV99+JjPC4PGhbHMOSsO+We4ZsP8UosNyF8A62" +
            "FheFXimCujiPmOBIOablN9TuWXAUtNHhWf4EyYDQvEo/NfY2mleiYjKqHoJpkIu+" +
            "sMcJJ3ry5Z4HEZi+SUbCjL7I5ZYF8aZq3YRxS4n2ZO7/w7n7B5621HMsahRNUi76" +
            "1wIDAQAB";

//    public static String userId = "user123";

    /* Testing variables (end) */



    public static final long ONE_SECOND_IN_MILLIS = 1000; //millisecs

    public static int THUMBNAIL_SIZE_SMALL = 128;
    public static int THUMBNAIL_SIZE_MEDIUM = 256;

    public static String SERVER_URL = "http://192.168.1.7:5000";


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


        public httpRequestImage(OnTaskCompleted<Bitmap> callback) {
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Bitmap doInBackground(String[] urls) {
            URL url;

            try {
                url = new URL(urls[0]);
                HttpURLConnection connection;
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();

                byte[] bytes = IOUtils.toByteArray(input);

                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                return bitmap;

            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
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

    public static void getCertificateFromDatabase(String lock_id, OnTaskCompleted<X509Certificate> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(true).addOnSuccessListener(result  -> {
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
