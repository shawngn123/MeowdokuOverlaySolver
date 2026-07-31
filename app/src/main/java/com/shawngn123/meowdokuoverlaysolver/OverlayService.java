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
import android.graphics.Canvas;
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
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private static final String TAG = "MeowdokuSolver";
    private static final String CHANNEL = "meowdoku_overlay";
    private static final int NOTIFICATION_ID = 1;
    private static Intent permissionData;
    private static int permissionResult = Activity.RESULT_CANCELED;
    private static boolean permissionConsumed;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final PuzzlePipeline pipeline = new PuzzlePipeline();
    private WindowManager windows;
    private Button solveButton;
    private DebugOverlayView debugOverlay;
    private TextView statusBubble;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private Bitmap screenshot;
    private Bitmap paddedBuffer;
    private boolean capturing;
    private boolean busy;
    private int displayWidth;
    private int displayHeight;
    private int displayDensity;
    private Runnable timeout;

    public static synchronized void setProjectionPermission(int result, Intent data) {
        permissionResult = result;
        permissionData = data == null ? null : new Intent(data);
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

    private static synchronized int permissionResult() { return permissionResult; }

    private static synchronized void clearPermission() {
        permissionData = null;
        permissionResult = Activity.RESULT_CANCELED;
        permissionConsumed = false;
    }

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForegroundMode(false);
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        showSolveButton();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (solveButton == null && Settings.canDrawOverlays(this)) showSolveButton();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        cancelTimeout();
        closeReader();
        removeDebugOverlay();
        removeStatusBubble();
        worker.shutdownNow();
        if (solveButton != null) try { windows.removeView(solveButton); } catch (RuntimeException ignored) { }
        solveButton = null;
        if (virtualDisplay != null) virtualDisplay.release();
        virtualDisplay = null;
        MediaProjection old = projection;
        projection = null;
        if (old != null) old.stop();
        if (screenshot != null) screenshot.recycle();
        if (paddedBuffer != null && paddedBuffer != screenshot) paddedBuffer.recycle();
        screenshot = null;
        paddedBuffer = null;
        clearPermission();
        super.onDestroy();
    }

    private void showSolveButton() {
        if (solveButton != null) return;
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(180);
        windows.addView(solveButton, params);
    }

    private void captureOnce() {
        if (busy) return;
        busy = true;
        removeDebugOverlay();
        removeStatusBubble();
        if (solveButton != null) solveButton.setVisibility(View.INVISIBLE);
        main.postDelayed(this::beginCapture, 90);
    }

    private void beginCapture() {
        if (!busy) return;
        if (!hasProjectionPermission()) { fail("MediaProjection permission is not available", null); return; }
        capturing = true;
        try {
            int[] size = screenSize();
            reader = ImageReader.newInstance(size[0], size[1], PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(this::imageAvailable, main);
            if (projection == null) createProjection(size);
            else {
                if (virtualDisplay == null) throw new IllegalStateException("Virtual display is unavailable");
                if (displayWidth != size[0] || displayHeight != size[1] || displayDensity != size[2]) {
                    virtualDisplay.resize(size[0], size[1], size[2]);
                    displayWidth = size[0]; displayHeight = size[1]; displayDensity = size[2];
                }
                virtualDisplay.setSurface(reader.getSurface());
            }
            timeout = () -> fail("Timed out waiting for a screen image", null);
            main.postDelayed(timeout, 3000);
        } catch (Exception error) { fail(error.getMessage(), error); }
    }

    private void createProjection(int[] size) {
        startForegroundMode(true);
        Intent token = consumePermission();
        if (token == null) throw new IllegalStateException("MediaProjection permission token was already used");
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(permissionResult(), token);
        if (projection == null) throw new IllegalStateException("MediaProjectionManager returned null");
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { projectionStopped(); }
        }, main);
        displayWidth = size[0]; displayHeight = size[1]; displayDensity = size[2];
        virtualDisplay = projection.createVirtualDisplay(
                "MeowdokuSingleCapture", displayWidth, displayHeight, displayDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, main
        );
        if (virtualDisplay == null) throw new IllegalStateException("Could not create virtual display");
    }

    private void imageAvailable(ImageReader source) {
        if (!capturing || source != reader) return;
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            detachSurface();
            cancelTimeout();
            Bitmap next = toBitmap(image);
            screenshot = next;
            capturing = false;
            restoreSolveButton();
            Log.i(TAG, "Screen captured successfully");
            process(next);
        } catch (Exception error) { fail(error.getMessage(), error); }
        finally {
            if (image != null) image.close();
            closeReader();
        }
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth(), height = image.getHeight();
        int paddedWidth = width + (plane.getRowStride() - plane.getPixelStride() * width) / plane.getPixelStride();
        if (paddedWidth == width) {
            screenshot = ensureBitmap(screenshot, width, height);
            buffer.rewind();
            screenshot.copyPixelsFromBuffer(buffer);
            return screenshot;
        }
        paddedBuffer = ensureBitmap(paddedBuffer, paddedWidth, height);
        screenshot = ensureBitmap(screenshot, width, height);
        buffer.rewind();
        paddedBuffer.copyPixelsFromBuffer(buffer);
        Canvas canvas = new Canvas(screenshot);
        canvas.drawBitmap(paddedBuffer, new Rect(0, 0, width, height), new Rect(0, 0, width, height), null);
        return screenshot;
    }

    private Bitmap ensureBitmap(Bitmap bitmap, int width, int height) {
        if (bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() == width && bitmap.getHeight() == height) return bitmap;
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private void process(Bitmap bitmap) {
        worker.execute(() -> pipeline.run(bitmap, new PuzzlePipeline.Listener() {
            @Override public void onDebug(DebugData data) { main.post(() -> showDebug(data)); }
            @Override public void onBeforeGestures() {
                main.post(() -> {
                    if (!DebugFlags.SHOW_OVERLAYS) removeDebugOverlay();
                });
            }
            @Override public void onFinished(String reason) {
                main.post(() -> {
                    if (!DebugFlags.SHOW_OVERLAYS) removeDebugOverlay();
                    if (reason != null) {
                        Log.i(TAG, reason);
                        showStatus(reason);
                    }
                    busy = false;
                });
            }
        }));
    }

    private void showDebug(DebugData data) {
        if (!DebugFlags.SHOW_OVERLAYS) return;
        removeDebugOverlay();
        debugOverlay = new DebugOverlayView(this);
        debugOverlay.show(data);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windows.addView(debugOverlay, params);
        main.postDelayed(this::removeDebugOverlay, 2500);
    }

    private void removeDebugOverlay() {
        if (debugOverlay == null) return;
        try { windows.removeView(debugOverlay); } catch (RuntimeException ignored) { }
        debugOverlay = null;
    }

    private void showStatus(String message) {
        removeStatusBubble();
        statusBubble = new TextView(this);
        statusBubble.setText(message);
        statusBubble.setTextColor(Color.WHITE);
        statusBubble.setTextSize(15f);
        statusBubble.setGravity(Gravity.CENTER);
        statusBubble.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(225, 45, 45, 45));
        background.setCornerRadius(dp(18));
        statusBubble.setBackground(background);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(90);
        windows.addView(statusBubble, params);
        main.postDelayed(this::removeStatusBubble, 1500);
    }

    private void removeStatusBubble() {
        if (statusBubble == null) return;
        try { windows.removeView(statusBubble); } catch (RuntimeException ignored) { }
        statusBubble = null;
    }

    private void fail(String reason, Throwable error) {
        String message = reason == null || reason.isEmpty() ? "Unknown error" : reason;
        if (error == null) Log.e(TAG, "Screen capture failed: " + message);
        else Log.e(TAG, "Screen capture failed: " + message, error);
        detachSurface();
        cancelTimeout();
        closeReader();
        removeDebugOverlay();
        showStatus(message);
        restoreSolveButton();
        capturing = false;
        busy = false;
    }

    private void projectionStopped() {
        if (capturing) fail("MediaProjection session was stopped", null);
        else { cancelTimeout(); closeReader(); busy = false; }
        if (virtualDisplay != null) virtualDisplay.release();
        virtualDisplay = null;
        projection = null;
        clearPermission();
        restoreSolveButton();
    }

    private void restoreSolveButton() {
        if (solveButton != null) solveButton.setVisibility(View.VISIBLE);
    }

    private void detachSurface() {
        if (virtualDisplay == null) return;
        try { virtualDisplay.setSurface(null); } catch (RuntimeException error) { Log.e(TAG, "Could not detach capture surface", error); }
    }

    private void closeReader() {
        if (reader == null) return;
        reader.setOnImageAvailableListener(null, null);
        reader.close();
        reader = null;
    }

    private void cancelTimeout() {
        if (timeout == null) return;
        main.removeCallbacks(timeout);
        timeout = null;
    }

    private int[] screenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windows.getMaximumWindowMetrics();
            Rect bounds = metrics.getBounds();
            return new int[]{bounds.width(), bounds.height(), getResources().getConfiguration().densityDpi};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels, metrics.densityDpi};
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the floating SOLVE button available.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void startForegroundMode(boolean projectionMode) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            if (projectionMode) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            startForeground(NOTIFICATION_ID, notification, types);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projectionMode) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(NOTIFICATION_ID, notification);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
