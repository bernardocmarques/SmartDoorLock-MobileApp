package com.bernardocmarques.smartlockclient;

import static com.bernardocmarques.smartlockclient.Utils.SERVER_URL;
import static com.bernardocmarques.smartlockclient.Utils.userId;

import com.bernardocmarques.smartlockclient.Utils.KeyStoreUtil;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;

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

            String inviteCodeB64 = inviteCodeTextInputLayout.getEditText().getText().toString();
            Log.i(TAG, "onCreate: inviteCodeB64 = " + inviteCodeB64);

            String[] inviteCode = new String(Base64.decode(inviteCodeB64, Base64.NO_WRAP)).split(" ");

            String lockMAC = inviteCode[0];
            Log.i(TAG, "onCreate: lockMAC = " + lockMAC);

            String inviteID = inviteCode[1];
            Log.i(TAG, "onCreate: invite id = " + inviteID);



            String masterKeyEncryptedLock =  KeyStoreUtil.getInstance().generateMasterKey(lockMAC + userId);

            JsonObject data = new JsonObject();
            data.addProperty("id_token", userId); //fixme remove hardcode
            data.addProperty("invite_id", inviteID);
            data.addProperty("master_key_encrypted_lock", masterKeyEncryptedLock);

            (new Utils.httpPostRequestJson(response -> {
                if (response.get("success").getAsBoolean()) {
                    Log.i(TAG, "onCreate: YEI!!!");
                } else {
                    Log.e(TAG, "Error code " +
                            response.get("code").getAsString() +
                            ": " +
                            response.get("msg").getAsString());
                }
            }, data.toString())).execute(SERVER_URL + "/redeem-invite");
        });

    }




}