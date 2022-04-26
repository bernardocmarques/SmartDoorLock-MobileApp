package com.bernardocmarques.smartlockclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.internal.NavigationMenuItemView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;

public final class Sidebar {

    static String TAG = "SmartLock@Sidebar";



    private final Activity activity;
    private final NavigationView sidebar;
    private NavigationMenuItemView menuItem = null;
    private boolean isOpen = false;

    public Sidebar(Activity activity) {
        this.activity = activity;
        this.sidebar = activity.findViewById(R.id.sidebar);

        if (sidebar != null) {
            sidebar.setNavigationItemSelectedListener(this::itemClicked);
        }
    }

    public boolean itemClicked(MenuItem item) {

        int id = item.getItemId();
        if (menuItem != null && id == menuItem.getId()) return false;

        if (id == R.id.sidebar_logout) {
            FirebaseAuth.getInstance().signOut();
            changeUserUI();
            return false;

        } else if (id == R.id.sidebar_locks) {
            toggleSidebar();
//            Intent intent = new Intent(activity, RoutesListActivity.class);
//            activity.startActivity(intent);
//            activity.overridePendingTransition(R.anim.fade_out, R.anim.fade_in);
            return true;

        } else if (id == R.id.sidebar_profile) {
            toggleSidebar();
//            Intent intent = new Intent(activity, ProfileActivity.class);
//            activity.startActivity(intent);
//            activity.overridePendingTransition(R.anim.fade_out, R.anim.fade_in);
            return true;

        } else {
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    public void toggleSidebar() {
        RelativeLayout overlay = activity.findViewById(R.id.overlay);


        if (isOpen) {  // close it
            sidebar.animate().translationX(-(sidebar.getWidth()));
            overlay.setVisibility(View.GONE);

        } else {    // open it
            sidebar.animate().translationX(0);
            overlay.setVisibility(View.VISIBLE);



            if (menuItem != null) {
                menuItem.setChecked(true);
                menuItem.getItemData().setChecked(true);
            }

            overlay.setOnClickListener(item -> {
                toggleSidebar();
            });
            LinearLayout sidebarUser = activity.findViewById(R.id.sidebar_user);

            changeUserUI();
            sidebarUser.setOnClickListener(v -> {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    Intent intent = new Intent(activity, LoginActivity.class);
                    activity.startActivity(intent);
                    activity.overridePendingTransition(R.anim.fade_out, R.anim.fade_in);
                }
            });
        }
        isOpen = !isOpen;
    }

    public void changeUserUI() {
        TextView userEmail = activity.findViewById(R.id.logged_user_email);
        TextView userName = activity.findViewById(R.id.logged_user_name);
        ImageView userAvatar = activity.findViewById(R.id.logged_user_avatar);

        if (userEmail == null || userName == null || userAvatar == null)
            return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            userAvatar.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_default_avatar));
            userEmail.setText(R.string.sign_in_msg);
            userName.setText(R.string.sign_in);
            sidebar.getMenu().findItem(R.id.sidebar_logout).setVisible(false);

        } else {
            if (user.getPhotoUrl() != null) {
                (new Utils.httpRequestImage(bitmap -> {
                    Bitmap thumbImage = ThumbnailUtils.extractThumbnail(bitmap, Utils.THUMBNAIL_SIZE_SMALL, Utils.THUMBNAIL_SIZE_SMALL);
                    userAvatar.setImageBitmap(thumbImage);
                })).execute(user.getPhotoUrl().toString());
            } else {
                userAvatar.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_default_avatar));
            }
            userEmail.setText(user.getEmail());
            userName.setText(user.getDisplayName() != null && !user.getDisplayName().isEmpty() ?
                    user.getDisplayName() :
                    Utils.capitalize(Objects.requireNonNull(user.getEmail()).split("@")[0]));
            sidebar.getMenu().findItem(R.id.sidebar_logout).setVisible(true);
        }
    }
}
