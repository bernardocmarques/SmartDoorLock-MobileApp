package com.bernardocmarques.smartlockclient;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class EditDoorInformationActivity extends AppCompatActivity {
    private static final String TAG = "SmartLock@EditDoorInfoActivity";

    Lock lock;

    AlertDialog alertDialogIconPicker = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_door_information);

        Bundle bundle = getIntent().getExtras();
        lock = Lock.fromSerializable(bundle.getSerializable("lock"));

        setActionBar();
        createUI();
    }

    void setActionBar() {
        View actionBarInclude = findViewById(R.id.action_bar_include);
        MaterialToolbar actionBar = actionBarInclude.findViewById(R.id.backBar);
        actionBar.setTitle(R.string.edit_smart_lock_title);

        actionBar.setNavigationOnClickListener(view -> finish());
    }


    private void createUI() {

        findViewById(R.id.btn_save).setOnClickListener(view -> {
            Utils.setUserLock(lock, success -> {
                if (success) {

                    // fixme Consider changing to Activity Result API v
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("lock", lock.getSerializable());
                    setResult(Activity.RESULT_OK, resultIntent);
                    // fixme Consider changing to Activity Result API ^

                    finish();
                }
            });
        });



        TextInputLayout lockNameTextInputLayout = findViewById(R.id.text_input_lock_name);
        EditText lockNameEditText = lockNameTextInputLayout.getEditText();
        assert lockNameEditText != null;

        View cardPreviewInclude = findViewById(R.id.card_preview);
        cardPreviewInclude.setClickable(false);

        View iconSelector = findViewById(R.id.icon_selector_icon);
        ImageView iconSelectorIcon = iconSelector.findViewById(R.id.lock_card_image_view);

        ImageView previewIcon = cardPreviewInclude.findViewById(R.id.lock_card_image_view);
        TextView previewName = cardPreviewInclude.findViewById(R.id.lock_card_text_view);

        Log.e(TAG, "createUI: " + lock.getName());
        lockNameEditText.setText(lock.getName());
        previewName.setText(lock.getName());

        new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                Bitmap bitmap = lock.getIcon();
                Log.i(TAG, "run: " + bitmap);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        iconSelectorIcon.setImageBitmap(bitmap);
                        previewIcon.setImageBitmap(bitmap);
                    });
                    this.cancel();
                }
            }
        },0,100);

        iconSelectorIcon.setImageBitmap(lock.getIcon());

        lockNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void afterTextChanged(Editable editable) {
                lock.setName(editable.toString());
                previewName.setText(editable.toString());
            }
        });

        iconSelector.setOnClickListener(v -> {


            LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

            View iconSelection = inflater.inflate(R.layout.icon_selection, null);
            CircularProgressIndicator loadingSpinner = iconSelection.findViewById(R.id.loading_spinner);
            FlexboxLayout flexboxLayout = iconSelection.findViewById(R.id.lock_icon_card_flexbox);
            loadingSpinner.setVisibility(View.VISIBLE);
            final float scale = getResources().getDisplayMetrics().density;

            FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins((int)(8 * scale), (int)(8 * scale),(int)(8 * scale),(int)(8 * scale));

            Utils.getAllIcons(iconsMap -> {
                loadingSpinner.setVisibility(View.GONE);

                for (String iconId: iconsMap.keySet()) {
                    View iconCard = inflater.inflate(R.layout.lock_icon_card, flexboxLayout, false);

                    ImageView imageView = iconCard.findViewById(R.id.lock_card_image_view);
                    imageView.setImageBitmap(iconsMap.get(iconId));

                    iconCard.setOnClickListener(v2 -> {
                        lock.setIconID(iconId);
                        previewIcon.setImageBitmap(iconsMap.get(iconId));
                        iconSelectorIcon.setImageBitmap(iconsMap.get(iconId));
                        if (alertDialogIconPicker != null) alertDialogIconPicker.cancel();
                    });

                    flexboxLayout.addView(iconCard, params);
                    iconCard.setVisibility(View.VISIBLE);
                }

            });

            runOnUiThread(() -> alertDialogIconPicker = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.select_smart_lock_icon)
                    .setView(iconSelection)
                    .setNegativeButton(R.string.CLOSE, (dialog, which) -> {})
                    .show());
        });

    }
}