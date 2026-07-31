package com.shawngn123.meowdokuoverlaysolver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    private TextView statusText;
    private Button permissionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionState();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            updatePermissionState();
        }
    }

    private LinearLayout createContentView() {
        int padding = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(255, 248, 242));

        TextView title = new TextView(this);
        title.setText("Meowdoku Overlay Solver");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(40, 32, 28));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = new TextView(this);
        description.setText("Grant the required permissions. The floating SOLVE button will then appear over other apps.");
        description.setTextSize(17);
        description.setTextColor(Color.rgb(80, 67, 60));
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = dp(24);
        root.addView(description, descriptionParams);

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setTextColor(Color.rgb(40, 32, 28));
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(32);
        root.addView(statusText, statusParams);

        permissionButton = new Button(this);
        permissionButton.setAllCaps(false);
        permissionButton.setTextSize(17);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(24);
        root.addView(permissionButton, buttonParams);

        return root;
    }

    private void updatePermissionState() {
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean notificationsGranted = notificationsGranted();

        if (!notificationsGranted) {
            statusText.setText("Notification permission is required for the overlay service.");
            permissionButton.setText("Grant notification permission");
            permissionButton.setEnabled(true);
            permissionButton.setOnClickListener(v -> requestNotificationPermission());
            return;
        }

        if (!overlayGranted) {
            statusText.setText("Display-over-other-apps permission is required.");
            permissionButton.setText("Grant overlay permission");
            permissionButton.setEnabled(true);
            permissionButton.setOnClickListener(v -> openOverlayPermissionScreen());
            return;
        }

        statusText.setText("Permissions granted. The SOLVE button is active.");
        permissionButton.setText("Overlay active");
        permissionButton.setEnabled(false);
        permissionButton.setOnClickListener(null);
        startOverlayService();
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        } else {
            updatePermissionState();
        }
    }

    private void openOverlayPermissionScreen() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void startOverlayService() {
        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
