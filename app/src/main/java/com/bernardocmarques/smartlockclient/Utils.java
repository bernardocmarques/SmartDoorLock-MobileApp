package com.bernardocmarques.smartlockclient;

import static java.lang.Long.parseLong;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.location.Location;
import android.os.AsyncTask;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

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
import java.util.Random;
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
//    public static String SERVER_URL = "http://192.168.1.104:5000";


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
    /*** ----------------- CYPHERS ----------------- ***/ // fixme change name
    /*** -------------------------------------------- ***/


    private static final int KEY_SIZE = 256;


    public static String generateSessionCredentials(CommActivity commActivity) {
        commActivity.setAESUtil(new AESUtil(KEY_SIZE));
        String key = commActivity.getAESUtil().generateNewSessionKey();

        return commActivity.getRSAUtil().encrypt("SSC " + key);
    }

    public static void generateAuthCredentials(CommActivity commActivity, String seed, OnTaskCompleted<String> callback) {
        try {
            String phoneId = Utils.getPhoneId(commActivity.getContext());
            String authCode = KeyStoreUtil.getInstance().hmacBase64WithMasterKey(seed, commActivity.getLockId() + phoneId);
            callback.onTaskCompleted("SAC " + phoneId + " " + authCode);
        } catch (KeyStoreUtil.NonExistingMasterKey nonExistingMasterKey) {

            redeemUserInvite(commActivity.getLockId(), commActivity.getContext().getApplicationContext(), success -> {
                if (success) {
                    generateAuthCredentials(commActivity, seed, callback);
                }
            });

        }

    }

    /*** -------------------------------------------- ***/
    /*** -------------- USER INTERFACE -------------- ***/
    /*** -------------------------------------------- ***/

    static void forceLightModeOn() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    /*** -------------------------------------------- ***/
    /*** -------------- Location Utils -------------- ***/
    /*** -------------------------------------------- ***/

    public static String locationToString(Location location) {
        if (location == null) {
            return "";
        }
        return location.getLatitude() + "," + location.getLongitude();
    }

    public static Location locationFromString(String locationString) {
        String[] locationStringSpliced = locationString.split(",");

        if (locationStringSpliced.length != 2) {
            return null;
        }

        Location location = new Location("");

        location.setLatitude(Double.parseDouble(locationStringSpliced[0]));
        location.setLongitude(Double.parseDouble(locationStringSpliced[1]));

        return location;
    }

    public static double distanceBetweenLocationsInMeters(Location l1, Location l2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(l2.getLatitude() - l1.getLatitude());
        double lonDistance = Math.toRadians(l2.getLongitude() - l1.getLongitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(l1.getLatitude())) * Math.cos(Math.toRadians(l2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = 0;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
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

    private static final String ALLOWED_CHARACTERS = "0123456789qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";

    private static String getRandomString(final int sizeOfRandomString) {
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder(sizeOfRandomString);
        for (int i = 0; i < sizeOfRandomString; ++i)
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        return sb.toString();
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

                return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();

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

                return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();

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
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);


            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Request \"/finish-user-creation\" - Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                    callback.onTaskCompleted(false);
                }
            }, data.toString())).execute(SERVER_URL + "/finish-user-creation");
        });
    }

    public static void registerPhoneId(Context context, OnTaskCompleted<Boolean> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);
            data.addProperty("phone_id", getPhoneId(context));


            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Request \"/register-phone-id\" - Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                    callback.onTaskCompleted(false);
                }
            }, data.toString())).execute(SERVER_URL + "/register-phone-id");
        });
    }

    public static void getCertificateFromDatabase(String lock_id, OnTaskCompleted<X509Certificate> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
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
                    Log.e(TAG, "Request \"/get-door-certificate\" - Error code " +
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
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();
            String phoneId = Utils.getPhoneId(context);

            Utils.getPublicKeyBase64FromDatabase(lockMAC, context, keyRSA -> {
                String masterKeyEncryptedLock = KeyStoreUtil.getInstance().generateMasterKey(lockMAC + phoneId, keyRSA);
                Log.i(TAG, "redeemInvite: token " + tokenId);
                JsonObject data = new JsonObject();
                data.addProperty("id_token", tokenId);
                data.addProperty("invite_id", inviteID);
                data.addProperty("phone_id", phoneId);
                data.addProperty("master_key_encrypted_lock", masterKeyEncryptedLock);

                (new httpPostRequestJson(response -> {
                    Log.e(TAG, "redeemInvite: " + response);
                    if (response.get("success").getAsBoolean()) {
                        callback.onTaskCompleted(true);
                    } else {
                        Log.e(TAG, "Request \"/redeem-invite\" - Error code " +
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
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
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
                    Log.e(TAG, "Request \"/get-user-locks\" - Error code " +
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

        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);
            data.add("lock", lock.toJson());

            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Request \"/set-user-locks\" - Error code " +
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

        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);
            data.addProperty("lock_id", lockId);

            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Request \"/delete-user-lock\" - Error code " +
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
                Log.e(TAG, "Request \"/get-all-icons\" - Error code " +
                        response.get("code").getAsString() +
                        ": " +
                        response.get("msg").getAsString());
            }
        })).execute(SERVER_URL + "/get-all-icons");
    }

    public static void saveUserInvite(String userInviteId, String lockId, OnTaskCompleted<Boolean> callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onTaskCompleted(false);
            return;
        }

        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);
            data.addProperty("lock_id", lockId);
            data.addProperty("invite_id", userInviteId);

            (new httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(true);
                } else {
                    Log.e(TAG, "Request \"/save-user-invite\" - Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            }, data.toString())).execute(SERVER_URL + "/save-user-invite");
        });
    }

    public static void checkUserSavedInvite(String lockId, OnTaskCompleted<Boolean> callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onTaskCompleted(false);
            return;
        }
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();

            (new httpRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    callback.onTaskCompleted(response.get("got_invite").getAsBoolean());
                } else {
                    Log.e(TAG, "Request \"/check-user-invite\" - Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            })).execute(SERVER_URL + "/check-user-invite?id_token=" + tokenId + "&lock_id=" + lockId);
        });
    }

    public static void redeemUserInvite(String lockMAC, Context context, OnTaskCompleted<Boolean> callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result -> {
            String tokenId = result.getToken();
            String phoneId = Utils.getPhoneId(context);

            Utils.getPublicKeyBase64FromDatabase(lockMAC, context, keyRSA -> {
                String masterKeyEncryptedLock = KeyStoreUtil.getInstance().generateMasterKey(lockMAC + phoneId, keyRSA);
                Log.i(TAG, "redeemInvite: token " + tokenId);
                JsonObject data = new JsonObject();
                data.addProperty("id_token", tokenId);
                data.addProperty("lock_id", lockMAC);
                data.addProperty("phone_id", phoneId);
                data.addProperty("master_key_encrypted_lock", masterKeyEncryptedLock);

                (new Utils.httpPostRequestJson(response -> {
                    if (response.get("success").getAsBoolean()) {
                        callback.onTaskCompleted(true);
                    } else {
                        Log.e(TAG, "Request \"/redeem-user-invite\" - Error code " +
                                response.get("code").getAsString() +
                                ": " +
                                response.get("msg").getAsString());
                        callback.onTaskCompleted(false);
                    }
                }, data.toString())).execute(SERVER_URL + "/redeem-user-invite");
            });
        });
    }

    /*** -------------------------------------------- ***/
    /*** --------- Remote Connection Helper --------- ***/
    /*** -------------------------------------------- ***/

    public interface OnResponseReceived {
        void onResponseReceived(String[] responseSplit);
    }


    public interface CommActivity {

        void updateUIOnBLEDisconnected();

        void updateUIOnBLEConnected();

        Context getContext();

        RSAUtil getRSAUtil();

        AESUtil getAESUtil();

        String getLockId();

        String getLockBLE();

        void setAESUtil(AESUtil aes);
    }


    public static void sendRemoteCommandWithAuthentication(CommActivity commActivity, String cmd, OnResponseReceived callback) {

        String sessionCredentialsRequest = generateSessionCredentials(commActivity);

        remoteConnection(commActivity, sessionCredentialsRequest, false, false,
                responseSplitSSC -> {
                    if (responseSplitSSC[0].equals("RAC")) {
                        Utils.generateAuthCredentials(commActivity, responseSplitSSC[1], authCredential -> {
                            remoteConnection(commActivity, authCredential, true, false,
                                    responseSplitSAC -> {
                                        if (responseSplitSAC[0].equals("ACK")) {

                                            remoteConnection(commActivity, cmd, true, true, callback);

                                        } else { // command not ACK
                                            Log.e(TAG, "Error: Should have received ACK command. (After RAC)");
                                            callback.onResponseReceived(new String[]{"NAK"});
                                        }
                                    });
                        });
                    }
                });
    }
    public static void remoteConnection(CommActivity commActivity, String cmd, boolean encrypt, boolean close, OnResponseReceived callback) {
        remoteConnection(commActivity, cmd, encrypt, false, close, callback);
    }
    public static void remoteConnection(CommActivity commActivity, String cmd, boolean encrypt, boolean decrypt, boolean close, OnResponseReceived callback) {
        Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getIdToken(false).addOnSuccessListener(result  -> {

            String msgEnc;
            if (encrypt)
                msgEnc = commActivity.getAESUtil().encrypt(new BLEMessage(cmd).toString());
            else
                msgEnc = cmd;


            String tokenId = result.getToken();

            JsonObject data = new JsonObject();
            data.addProperty("id_token", tokenId);
            data.addProperty("lock_id", commActivity.getLockId());
            data.addProperty("msg", msgEnc);
            data.addProperty("close", close);

            (new Utils.httpPostRequestJson(response -> {
                if (response != null && response.has("success") && response.get("success").getAsBoolean()) {

                    Context context = commActivity.getContext();

                    String responseEnc = response.get("response").getAsString();

                    String[] msgEncSplit = responseEnc.split(" ");

                    if (decrypt) {
                        if (msgEncSplit.length < 2) {
                            Log.e(TAG, "Less then 2");
                            return;
                        }
                        String msg = commActivity.getAESUtil().decrypt(msgEncSplit[0], msgEncSplit[1]);
                        if (msg == null) {
                            Log.e(TAG, "Error decrypting message! Operation Canceled.");
                            if (context instanceof Activity)
                                ((Activity) context).runOnUiThread(() -> Toast.makeText(context.getApplicationContext(), "Error decrypting message! Operation Canceled.", Toast.LENGTH_LONG).show());
                            return;
                        }

//                    Log.e(TAG, "Received: "  + msg);


                        String[] msgSplit = msg.split(" ");
                        int sizeCmdSplit = msgSplit.length;

                        BLEMessage bleMessage = new BLEMessage(String.join(" ", Arrays.copyOfRange(msgSplit, 0, sizeCmdSplit - 3)), parseLong(msgSplit[sizeCmdSplit - 3]), parseLong(msgSplit[sizeCmdSplit - 2]), parseLong(msgSplit[sizeCmdSplit - 1]));

                        if (bleMessage.isValid()) {
                            String[] cmdSplit = bleMessage.message.split(" ");
                            callback.onResponseReceived(cmdSplit);

                        } else {
                            Log.e(TAG, "Message not valid! Operation Canceled.");
                            if (context instanceof Activity)
                                ((Activity) context).runOnUiThread(() -> Toast.makeText(context.getApplicationContext(), "Message not valid! Operation Canceled.", Toast.LENGTH_LONG).show());
                            callback.onResponseReceived(new String[]{"NAK"});
                        }

                    } else {
                            String[] cmdSplit = responseEnc.split(" ");
                            callback.onResponseReceived(cmdSplit);
                    }

                } else {
                    Log.e(TAG, response != null ? "Request \"/remote-connection\" - Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString() : "Request \"/remote-connection\" - Null Response");
                    callback.onResponseReceived(new String[]{"NAK"});
                }
            }, data.toString())).execute(SERVER_URL + "/remote-connection");

        });

    }



    /*** -------------------------------------------- ***/
    /*** --------------- Shared Prefs --------------- ***/
    /*** -------------------------------------------- ***/

    public static String getPhoneId(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(String.valueOf(R.string.phone_id_preference_file_key), Context.MODE_PRIVATE);
        String phoneId = sharedPref.getString("phoneId", null);

        if (phoneId == null) {
            SharedPreferences.Editor editor = sharedPref.edit();
            phoneId = getRandomString(15);
            editor.putString("phoneId", phoneId);
            editor.apply();
        }

        return phoneId;
    }


    /*** -------------------------------------------- ***/
    /*** ----------------- KeyStore ----------------- ***/
    /*** -------------------------------------------- ***/

    public static class KeyStoreUtil {

        private static KeyStoreUtil INSTANCE = null;
        KeyStore keyStore;

        public static class NonExistingMasterKey extends Exception {
            public NonExistingMasterKey(String id) {
                super("The key with id \"" + id + "\" does not exist.");
            }
        }


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

        public String hmacBase64WithMasterKey(String dataBase64, String keyID) throws NonExistingMasterKey {
//            Log.i(TAG, "hmac with key id: " + keyID);


            try {

                if (!keyStore.containsAlias(keyID)) {
                    throw new NonExistingMasterKey(keyID);
                }

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
