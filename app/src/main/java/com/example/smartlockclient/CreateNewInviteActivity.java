package com.example.smartlockclient;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Toast;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class CreateNewInviteActivity extends AppCompatActivity {


    /* UI */

    AutoCompleteTextView userTypeSelect;

    HashMap<String, Integer> userTypeMap = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_invite);
        userTypeSelect = findViewById(R.id.user_type_select);
        userTypeSelect.setEnabled(false);
        createUserTypeSelect();

//        MaterialDatePicker<> datePicker =
//                MaterialDatePicker.Builder.datePicker()
//                        .setTitleText("Select date")
//                        .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
//                        .build()
//
//        datePicker.show()
    }

    private void createUserTypeSelect() {
        userTypeMap.put("Admin", 0);
        userTypeMap.put("Owner", 1);
        userTypeMap.put("Tenant", 2);
        userTypeMap.put("Periodic User", 3);
        userTypeMap.put("One-time User", 4);

        String[] userTypeKeys = {"Admin", "Owner", "Tenant", "Periodic User", "One-time User"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_item, userTypeKeys);


        userTypeSelect.setAdapter(adapter);
        userTypeSelect.setEnabled(true);

        userTypeSelect.setOnItemClickListener((parent, view, position, id) -> {
            int userType = Objects.requireNonNull(userTypeMap.get(userTypeKeys[(int) id]));
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), userType, Toast.LENGTH_LONG).show());
        });

    }
}