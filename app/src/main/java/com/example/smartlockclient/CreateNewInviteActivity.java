package com.example.smartlockclient;


import androidx.appcompat.app.AppCompatActivity;

import com.dpro.widgets.OnWeekdaysChangeListener;
import com.dpro.widgets.WeekdaysPicker;
import com.example.smartlockclient.Utils.UserType;

import android.annotation.SuppressLint;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class CreateNewInviteActivity extends AppCompatActivity {
    private static final String TAG = "SmartLock@CreateNewInvite";


    /* UI */
    AutoCompleteTextView userTypeSelect;
    TextInputLayout validFromTextInputLayout;
    TextInputLayout validUntilTextInputLayout;
    WeekdaysPicker weekdaysPicker;
    TextInputLayout oneDayTextInputLayout;
    Button createInviteBtn;

    HashMap<Integer, UserType> userTypeMap = new HashMap<>();

    UserType selectedUserType;

    MessagesTestActivity messagesTestActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_invite);

        createUI();
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

        createUserTypeSelect();
        createValidityDatePickers();
        createWeekDaySelect();
        createOneDayDatePickers();

        createInviteBtn.setOnClickListener(view -> {
            Log.i(TAG, getInviteRequestCommand());
        });
    }

    private void createUserTypeSelect() {
        userTypeMap.put(0, UserType.ADMIN);
        userTypeMap.put(1, UserType.OWNER);
        userTypeMap.put(2, UserType.TENANT);
        userTypeMap.put(3, UserType.PERIODIC_USER);
        userTypeMap.put(4, UserType.ONETIME_USER);

        String[] userTypeKeys = {"Admin", "Owner", "Tenant", "Periodic User", "One-time User"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_item, userTypeKeys);


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
}