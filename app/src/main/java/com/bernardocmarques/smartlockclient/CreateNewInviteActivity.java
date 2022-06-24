package com.bernardocmarques.smartlockclient;


import androidx.appcompat.app.AppCompatActivity;

import com.dpro.widgets.WeekdaysPicker;
import com.bernardocmarques.smartlockclient.Utils.UserType;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;

public class CreateNewInviteActivity extends AppCompatActivity implements BLEManager.BLEActivity {
    private static final String TAG = "SmartLock@CreateNewInvite";

    BLEManager bleManager;
    RSAUtil rsaUtil;

    /* UI */
    AutoCompleteTextView userTypeSelect;
    TextInputLayout validFromTextInputLayout;
    TextInputLayout validUntilTextInputLayout;
    WeekdaysPicker weekdaysPicker;
    TextInputLayout oneDayTextInputLayout;
    ExtendedFloatingActionButton createInviteBtn;

    HashMap<Integer, UserType> userTypeMap = new HashMap<>();

    UserType selectedUserType;


    Lock lock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_invite);
        Utils.forceLightModeOn();

        Bundle bundle = getIntent().getExtras();
        String lockId = bundle.getString("lockId");
        this.lock = GlobalValues.getInstance().getUserLockById(lockId);


        bleManager = BLEManager.getInstance();

        setActionBar();
        createUI();

        Utils.getPublicKeyBase64FromDatabase(getLockId(), this, keyRSA -> {
            this.rsaUtil = new RSAUtil(keyRSA);
            createUIListeners();
        });
    }

    void setActionBar() {
        View actionBarInclude = findViewById(R.id.action_bar_include);
        MaterialToolbar actionBar = actionBarInclude.findViewById(R.id.backBar);
        actionBar.setTitle(R.string.CREATE_NEW_INVITE);

        actionBar.setNavigationOnClickListener(view -> finish());
    }


    private void createUI() {
        userTypeSelect = findViewById(R.id.user_type_select);
        userTypeSelect.setEnabled(false);

        validFromTextInputLayout = findViewById(R.id.valid_from_date_picker);
        validFromTextInputLayout.setVisibility(View.GONE);
        validUntilTextInputLayout = findViewById(R.id.valid_until_date_picker);
        validUntilTextInputLayout.setVisibility(View.GONE);

        weekdaysPicker = findViewById(R.id.weekdays);
        weekdaysPicker.setVisibility(View.GONE);

        oneDayTextInputLayout = findViewById(R.id.one_day_date_picker);
        oneDayTextInputLayout.setVisibility(View.GONE);

        createInviteBtn = findViewById(R.id.btn_create_new_invite);




    }

    void createUIListeners() {
        createInviteBtn.setOnClickListener(view -> {
            sendInviteRequest();
        });

        createUserTypeSelect();
        createValidityDatePickers();
        createWeekDaySelect();
        createOneDayDatePickers();
    }

    private void createUserTypeSelect() {
        userTypeMap.put(0, UserType.ADMIN);
        userTypeMap.put(1, UserType.OWNER);
        userTypeMap.put(2, UserType.TENANT);
        userTypeMap.put(3, UserType.PERIODIC_USER);
        userTypeMap.put(4, UserType.ONETIME_USER);

        String[] userTypeKeys = {"Admin", "Owner", "Tenant", "Periodic User", "One-time User"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, userTypeKeys);


        userTypeSelect.setAdapter(adapter);
        userTypeSelect.setEnabled(true);

        userTypeSelect.setOnItemClickListener((parent, view, position, id) -> {

            UserType userType = userTypeMap.get((int) id);
            selectedUserType = userType;

            if (userType != null) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), userType.toString(), Toast.LENGTH_LONG).show());


                switch (userType) {
                    case ADMIN:
                    case OWNER:
                        validFromTextInputLayout.setVisibility(View.GONE);
                        validUntilTextInputLayout.setVisibility(View.GONE);
                        weekdaysPicker.setVisibility(View.GONE);
                        oneDayTextInputLayout.setVisibility(View.GONE);
                        break;
                    case TENANT:
                        validFromTextInputLayout.setVisibility(View.VISIBLE);
                        validUntilTextInputLayout.setVisibility(View.VISIBLE);
                        weekdaysPicker.setVisibility(View.GONE);
                        oneDayTextInputLayout.setVisibility(View.GONE);
                        break;
                    case PERIODIC_USER:
                        validFromTextInputLayout.setVisibility(View.VISIBLE);
                        validUntilTextInputLayout.setVisibility(View.VISIBLE);
                        weekdaysPicker.setVisibility(View.VISIBLE);
                        oneDayTextInputLayout.setVisibility(View.GONE);
                        break;
                    case ONETIME_USER:
                        validFromTextInputLayout.setVisibility(View.GONE);
                        validUntilTextInputLayout.setVisibility(View.GONE);
                        weekdaysPicker.setVisibility(View.GONE);
                        oneDayTextInputLayout.setVisibility(View.VISIBLE);
                        break;
                }

                if (userType == UserType.TENANT || userType == UserType.PERIODIC_USER) {
                    createValidityDatePickers();
                }

                if (userType == UserType.PERIODIC_USER) {
                    createWeekDaySelect();
                }

                if (userType == UserType.ONETIME_USER) {
                    createOneDayDatePickers();
                }

            } else {
                Log.e(TAG, "createUserTypeSelect: userType is null");
            }
        });

    }

    private void createValidityDatePickers() {
        Utils.createDatePicker(validFromTextInputLayout, this);
        Utils.createDatePicker(validUntilTextInputLayout, this);
    }

    private void createWeekDaySelect() {
        weekdaysPicker.setSelectedDays(new ArrayList<>());
    }

    private void createOneDayDatePickers() {
        Utils.createDatePicker(oneDayTextInputLayout, this);
    }

    private String getInviteRequestCommand() {
        try {
            switch (selectedUserType) {
                case ADMIN:
                    return "RNI 0";
                case OWNER:
                    return "RNI 1";
                case TENANT:
                    return "RNI 2 " +
                            Utils.getDateFromDateInput(validFromTextInputLayout).getTime() / Utils.ONE_SECOND_IN_MILLIS + " " +
                            Utils.getDateFromDateInput(validUntilTextInputLayout).getTime() / Utils.ONE_SECOND_IN_MILLIS;
                case PERIODIC_USER:
                    return "RNI 3 " +
                            Utils.getDateFromDateInput(validFromTextInputLayout).getTime() / Utils.ONE_SECOND_IN_MILLIS + " " +
                            Utils.getDateFromDateInput(validUntilTextInputLayout).getTime() / Utils.ONE_SECOND_IN_MILLIS + " " +
                            weekdaysPicker.getSelectedDays().stream().map(Object::toString).reduce("", String::concat);
                case ONETIME_USER:
                    return "RNI 4 " +
                            Utils.getDateFromDateInput(oneDayTextInputLayout).getTime() / Utils.ONE_SECOND_IN_MILLIS;
                default:
                    Log.e(TAG, "getInviteRequestCommand: User type not selected");
            }
        } catch (ParseException e) {
            Log.e(TAG, "getInviteRequestCommand: Error parsing date");
        }
        return "" ;
    }

    private void sendInviteRequest() {
        bleManager.sendCommandWithAuthentication(this, getInviteRequestCommand(), responseSplit -> {
            if (responseSplit[0].equals("SNI")) {
                String inviteCode = responseSplit[1];
                Log.i(TAG, "Invite: " + inviteCode);

                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.invite_create_title)
                        .setMessage(inviteCode)
                        .setPositiveButton(R.string.SHARE_INVITE, (dialog, which) -> {

                            QRCodeWriter writer = new QRCodeWriter();
                            Uri bitmapUri = null;
                            try {
                                BitMatrix bitMatrix = writer.encode( "https://smartlocks.ga/new-lock?inviteCode=" + inviteCode, BarcodeFormat.QR_CODE, 512, 512);
                                int width = bitMatrix.getWidth();
                                int height = bitMatrix.getHeight();
                                Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                                for (int x = 0; x < width; x++) {
                                    for (int y = 0; y < height; y++) {
                                        bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                                    }
                                }

                                String bitmapPath = MediaStore.Images.Media.insertImage(getContentResolver(), bmp,"title", null);
                                bitmapUri = Uri.parse(bitmapPath);


                            } catch (WriterException e) {
                                e.printStackTrace();
                            }



                            Intent shareIntent = new Intent();
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Someone wants to share a Smart Lock's Key with you!");
                            shareIntent.putExtra(Intent.EXTRA_TEXT, "You have been invited to use a new Smart Lock.\n\n\n" +
                                    "Use the following link to register the Smart Lock:\n" +
                                    "https://smartlocks.ga/new-lock?inviteCode=" + inviteCode + "\n\n" +
                                    "You can also register the Smart Lock with the QR code sent in this message.\n\n" +
                                    "If none of the above methods work try pasting this code in the app:\n\n"+
                                    inviteCode);
                            if (bitmapUri != null) shareIntent.putExtra(Intent.EXTRA_STREAM, bitmapUri);
                            shareIntent.setType("image/*");
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(shareIntent, "Share Smart Lock's Key"));


                        })
                        .setNegativeButton(R.string.CLOSE, (dialog, which) -> {})
                        .show());
            } else { // command not SNI
                Log.e(TAG, "Error: Should have received SNI command. (After RNI)");
            }
        });
    }



    @Override
    public void updateUIOnBLEDisconnected() {
        finish();
    }

    @Override
    public void updateUIOnBLEConnected() {
        // NOP
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public RSAUtil getRSAUtil() {
        return rsaUtil;
    }

    @Override
    public String getLockId() {
        return lock.getId();
    }

    @Override
    public String getLockBLE() {
        return lock.getBleAddress();
    }
}