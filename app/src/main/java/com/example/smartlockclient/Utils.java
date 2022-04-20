package com.example.smartlockclient;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.icu.text.SimpleDateFormat;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Utils {
    public static final long ONE_SECOND_IN_MILLIS = 1000; //millisecs


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

}
