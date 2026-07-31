package com.shawngn123.meowdokuoverlaysolver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;

public class OverlayService extends Service {
    private static final String CHANNEL_ID = "meowdoku_overlay";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;
    private Button solveButton;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startAsForegroundService();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        showSolveButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (solveButton == null && Settings.canDrawOverlays(this)) {
            showSolveButton();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (windowManager != null && solveButton != null) {
            windowManager.removeView(solveButton);
            solveButton = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showSolveButton() {
        if (solveButton != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        solveButton = new Button(this);
        solveButton.setText("SOLVE");
        solveButton.setTextColor(Color.WHITE);
        solveButton.setTextSize(17);
        solveButton.setAllCaps(false);
        solveButton.setPadding(dp(18), dp(8), dp(18), dp(8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(229, 106, 33));
        background.setCornerRadius(dp(28));
        background.setStroke(dp(2), Color.rgb(168, 68, 15));
        solveButton.setBackground(background);
        solveButton.setElevation(dp(8));

        // Phase 1 intentionally has no solve behavior yet.
        solveButton.setOnClickListener(v -> { });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(180);

        windowManager.addView(solveButton, params);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the floating SOLVE button available.");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void startAsForegroundService() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
