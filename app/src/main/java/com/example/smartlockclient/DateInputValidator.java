package com.example.smartlockclient;

import static java.lang.Integer.parseInt;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

public class DateInputValidator implements TextWatcher {

    private static final String TAG = "SmartLock@DateInputValidator";
    private final TextInputLayout inputLayout;
    private Context context;

    public DateInputValidator(TextInputLayout inputLayout, Context context) {
        this.context = context;
        this.inputLayout = inputLayout;
        EditText input = this.inputLayout.getEditText();

        if (input != null) {
            input.addTextChangedListener(this);
        } else {
            Log.e(TAG, "Error could not get Edit Text on " +
                    context.getResources().getResourceEntryName(this.inputLayout.getId()));
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        inputLayout.setErrorEnabled(false);
    }

    @Override
    public void afterTextChanged(Editable s) {
        String dateStr = s.toString();

        if (dateStr.length() > 0) {
            if (!validJavaDate(dateStr)) {
                inputLayout.setError(context.getString(R.string.INVALID_DATE_ERROR));
                inputLayout.setErrorEnabled(true);
            }
        }
    }

    private static boolean validJavaDate(String strDate) {
        int[] monthMaxDay = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

        if (!strDate.trim().equals("")) {
            int day = -1;
            int month = -1;

            String[] dateSplinted = strDate.split("/");

            if (strDate.length() > 0) day = parseInt(dateSplinted[0]);
            if (strDate.length() > 4) month = parseInt(dateSplinted[1]);

            if (day != -1 && (day < 1 || day > 31)) {
                return false;
            } else if (month != -1 && (month < 1 || month > 12 || day > monthMaxDay[month - 1])){
                return false;
            } else if (strDate.length() > 6){
                SimpleDateFormat sdfrmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                sdfrmt.setLenient(false);
                try {
                    Date javaDate = sdfrmt.parse(strDate);
                } catch (ParseException e) {
                    return false;
                }
            }
        }

        return true;
    }
}