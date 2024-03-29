package com.bernardocmarques.smartlockclient;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;

public class CreateAccountActivity extends AppCompatActivity {
    static String TAG = "SmartLock@CreateAccount";
    private FirebaseAuth mAuth;

    TextInputLayout emailInputLayout;
    TextInputLayout passwordInputLayout;
    TextInputLayout confirmPasswordInputLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.forceLightModeOn();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        mAuth = FirebaseAuth.getInstance();

        emailInputLayout = findViewById(R.id.sign_up_email);
        passwordInputLayout = findViewById(R.id.sign_up_password);
        confirmPasswordInputLayout = findViewById(R.id.sign_up_confirm_password);

        Button loginBtn = findViewById(R.id.btn_sign_up);
        loginBtn.setOnClickListener(this::createAccount);

        Button signInBtn = findViewById(R.id.btn_sign_in);

        signInBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_left_enter, R.anim.slide_left_leave);
            finish();
        });

        Objects.requireNonNull(emailInputLayout.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                emailInputLayout.setError(null);
            }
        });

        Objects.requireNonNull(passwordInputLayout.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                passwordInputLayout.setError(null);
            }
        });

        Objects.requireNonNull(confirmPasswordInputLayout.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                confirmPasswordInputLayout.setError(null);
            }
        });
    }


    void createAccount(View v) {
        boolean error = false;

        String email = Objects.requireNonNull(emailInputLayout.getEditText()).getText().toString();
        String password = Objects.requireNonNull(passwordInputLayout.getEditText()).getText().toString();
        String confirmPassword = Objects.requireNonNull(confirmPasswordInputLayout.getEditText()).getText().toString();

        if (email.isEmpty()) {
            emailInputLayout.setError(getString(R.string.email_required_error));
            error = true;
        } else if (!Utils.isValidEmail(email)) {
            emailInputLayout.setError(getString(R.string.email_format_error));
            error = true;
        }

        if (password.isEmpty()) {
            passwordInputLayout.setError(getString(R.string.password_required_error));
            error = true;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setError(getString(R.string.passwords_not_match));
            error = true;
        }

        if (!error) {
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            FirebaseUser user = mAuth.getCurrentUser();

                            GlobalValues.getInstance().setPhoneIdRegistered(false);
                            if (FirebaseAuth.getInstance().getCurrentUser() != null && !GlobalValues.getInstance().isPhoneIdRegistered()) {
                                Utils.registerPhoneId(getApplicationContext(), ignored -> GlobalValues.getInstance().setPhoneIdRegistered(true));
                            }
                            finish();
                        } else {
                            Exception e = task.getException();

                            if (e instanceof FirebaseAuthUserCollisionException) {
                                String errorCode = ((FirebaseAuthException) e).getErrorCode();
                                emailInputLayout.setError(getString(R.string.email_duplicated));
                            }

                            // If sign in fails, display a message to the user.
                            Toast.makeText(this, R.string.create_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(this, R.string.create_failed,
                    Toast.LENGTH_SHORT).show();
        }

    }
}