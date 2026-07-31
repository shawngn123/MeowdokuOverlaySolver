package com.shawngn123.meowdokuoverlaysolver;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;

import java.nio.ByteBuffer;

public class OverlayService extends Service {
    private static final String TAG = "MeowdokuSolver";
    private static final String CHANNEL_ID = "meowdoku_overlay";
    private static final int NOTIFICATION_ID = 1;

    private static Intent permissionData;
    private static int permissionResult = Activity.RESULT_CANCELED;
    private static boolean permissionConsumed;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private Button solveButton;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Bitmap screenshot;
    private boolean capturing;
    private int displayWidth;
    private int displayHeight;
    private int displayDensity;
    private Runnable timeout;

    public static synchronized void setProjectionPermission(int resultCode, Intent data) {
        permissionResult = resultCode;
        permissionData = new Intent(data);
        permissionConsumed = false;
    }

    public static synchronized boolean hasProjectionPermission() {
        return permissionResult == Activity.RESULT_OK && permissionData != null;
    }

    private static synchronized Intent consumePermission() {
        if (!hasProjectionPermission() || permissionConsumed) return null;
        permissionConsumed = true;
        return new Intent(permissionData);
    }

    private static synchronized int permissionResult() {
        return permissionResult;
    }

    private static synchronized void clearPermission() {
        permissionData = null;
        permissionResult = Activity.RESULT_CANCELED;
        permissionConsumed = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForegroundMode(false);
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        showSolveButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (solveButton == null && Settings.canDrawOverlays(this)) showSolveButton();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelTimeout();
        closeReader();
        if (windowManager != null && solveButton != null) windowManager.removeView(solveButton);
        solveButton = null;
        if (display != null) display.release();
        display = null;
        MediaProjection oldProjection = projection;
        projection = null;
        if (oldProjection != null) oldProjection.stop();
        if (screenshot != null) screenshot.recycle();
        screenshot = null;
        clearPermission();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showSolveButton() {
        if (solveButton != null) return;

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
        solveButton.setOnClickListener(v -> captureOnce());

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

    private void captureOnce() {
        if (capturing) return;
        if (!hasProjectionPermission()) {
            fail("MediaProjection permission is not available", null);
            return;
        }

        capturing = true;
        try {
            int[] size = screenSize();
            reader = ImageReader.newInstance(size[0], size[1], PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(this::imageAvailable, handler);

            if (projection == null) {
                startForegroundMode(true);
                Intent token = consumePermission();
                if (token == null) throw new IllegalStateException("MediaProjection permission token was already used");
                MediaProjectionManager manager = (MediaProjectionManager)
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                projection = manager.getMediaProjection(permissionResult(), token);
                if (projection == null) throw new IllegalStateException("MediaProjectionManager returned null");
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        projectionStopped();
                    }
                }, handler);
                displayWidth = size[0];
                displayHeight = size[1];
                displayDensity = size[2];
                display = projection.createVirtualDisplay(
                        "MeowdokuSingleCapture",
                        displayWidth,
                        displayHeight,
                        displayDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.getSurface(),
                        null,
                        handler
                );
                if (display == null) throw new IllegalStateException("Could not create virtual display");
            } else {
                if (display == null) throw new IllegalStateException("Virtual display is unavailable");
                if (displayWidth != size[0] || displayHeight != size[1] || displayDensity != size[2]) {
                    display.resize(size[0], size[1], size[2]);
                    displayWidth = size[0];
                    displayHeight = size[1];
                    displayDensity = size[2];
                }
                display.setSurface(reader.getSurface());
            }

            timeout = () -> fail("Timed out waiting for a screen image", null);
            handler.postDelayed(timeout, 3000);
        } catch (Exception error) {
            fail(error.getMessage(), error);
        }
    }

    private void imageAvailable(ImageReader source) {
        if (!capturing || source != reader) return;
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            detachSurface();
            cancelTimeout();
            Bitmap bitmap = toBitmap(image);
            if (screenshot != null) screenshot.recycle();
            screenshot = bitmap;
            capturing = false;
            Log.i(TAG, "Screen captured successfully");
        } catch (Exception error) {
            fail(error.getMessage(), error);
        } finally {
            if (image != null) {
                image.close();
                closeReader();
            }
        }
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int paddedWidth = width + (rowStride - pixelStride * width) / pixelStride;
        Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
        buffer.rewind();
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == width) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private void fail(String reason, Throwable error) {
        String message = reason == null || reason.isEmpty() ? "Unknown error" : reason;
        if (error == null) Log.e(TAG, "Screen capture failed: " + message);
        else Log.e(TAG, "Screen capture failed: " + message, error);
        detachSurface();
        cancelTimeout();
        closeReader();
        capturing = false;
    }

    private void projectionStopped() {
        if (capturing) fail("MediaProjection session was stopped", null);
        else {
            cancelTimeout();
            closeReader();
        }
        if (display != null) display.release();
        display = null;
        projection = null;
        clearPermission();
    }

    private void detachSurface() {
        if (display == null) return;
        try {
            display.setSurface(null);
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not detach capture surface", error);
        }
    }

    private void closeReader() {
        if (reader == null) return;
        reader.setOnImageAvailableListener(null, null);
        reader.close();
        reader = null;
    }

    private void cancelTimeout() {
        if (timeout == null) return;
        handler.removeCallbacks(timeout);
        timeout = null;
    }

    private int[] screenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windowManager.getMaximumWindowMetrics();
            Rect bounds = metrics.getBounds();
            return new int[]{bounds.width(), bounds.height(), getResources().getConfiguration().densityDpi};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels, metrics.densityDpi};
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the floating SOLVE button available.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void startForegroundMode(boolean includeProjection) {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
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
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            if (includeProjection) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            startForeground(NOTIFICATION_ID, notification, types);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && includeProjection) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
