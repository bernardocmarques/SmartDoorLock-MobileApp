package com.bernardocmarques.smartlockclient;

import static com.bernardocmarques.smartlockclient.Utils.SERVER_URL;

import com.bernardocmarques.smartlockclient.Utils.KeyStoreUtil;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.JsonObject;

import java.util.Objects;

public class RedeemInviteActivity extends AppCompatActivity {

    private static final String TAG = "SmartLock@RedeemInvite";
    TextInputLayout inviteCodeTextInputLayout;
    Button redeemInviteBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_redeem_invite);
        Utils.forceLightModeOn();

        inviteCodeTextInputLayout = findViewById(R.id.text_input_invite_code);
        redeemInviteBtn = findViewById(R.id.btn_redeem_invite);



        redeemInviteBtn.setOnClickListener(view -> {

            String inviteCodeB64 = Objects.requireNonNull(inviteCodeTextInputLayout.getEditText()).getText().toString();
            Log.i(TAG, "onCreate: inviteCodeB64 = " + inviteCodeB64);

            String[] inviteCode = new String(Base64.decode(inviteCodeB64, Base64.NO_WRAP)).split(" ");

            String lockMAC = inviteCode[0];
            Log.i(TAG, "onCreate: lockMAC = " + lockMAC);

            String inviteID = inviteCode[1];
            Log.i(TAG, "onCreate: invite id = " + inviteID);


            Utils.redeemInvite(lockMAC, inviteID,this , success -> {
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.smart_door_created_title)
                        .setMessage(success ? R.string.smart_door_created_msg : R.string.smart_door_error_created_msg)
                        .setPositiveButton(R.string.OK, (dialog, which) -> {})
                        .show());
            });
        });

    }




}