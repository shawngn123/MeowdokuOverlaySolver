package com.shawngn123.meowdokuoverlaysolver;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
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
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private static final String TAG = "MeowdokuSolver";
    private static final String CHANNEL = "meowdoku_overlay";
    private static final int NOTIFICATION_ID = 1;
    private static final String SOLVE_BUTTON_ID = "solve";
    private static final String QUIT_BUTTON_ID = "quit";
    private static final String QUIT_BUTTON_TEXT = "I <3 Candace";
    private static final String LOADING_BASE_TEXT = "Loading";
    private static final String KEY_BUTTON_X_PREFIX = "button_position_x_";
    private static final String KEY_BUTTON_Y_PREFIX = "button_position_y_";
    private static final int BUTTON_TEXT_SIZE_SP = 17;
    private static final int QUIT_BUTTON_AUTO_SIZE_MIN_SP = 8;
    private static final int BUTTON_HORIZONTAL_PADDING_DP = 18;
    private static final int BUTTON_VERTICAL_PADDING_DP = 8;
    private static final int BUTTON_CORNER_RADIUS_DP = 28;
    private static final int BUTTON_STROKE_WIDTH_DP = 2;
    private static final int BUTTON_ELEVATION_DP = 8;
    private static final int BUTTON_MIN_WIDTH_DP = 96;
    private static final int BUTTON_MIN_HEIGHT_DP = 48;
    private static final int BOARD_SIZE_BUTTON_ROW_WIDTH_DP = 120;
    private static final int BOARD_SIZE_BUTTON_TEXT_SIZE_SP = 13;
    private static final int BOARD_SIZE_BUTTON_MIN_HEIGHT_DP = 40;
    private static final int BOARD_SIZE_BUTTON_SPACING_DP = 2;
    private static final int DEFAULT_RIGHT_MARGIN_DP = 16;
    private static final int DEFAULT_SOLVE_TOP_DP = 180;
    private static final int DEFAULT_BUTTON_SPACING_DP = 8;
    private static final int SAFE_AREA_MARGIN_DP = 8;
    private static final int STATUS_BOTTOM_MARGIN_DP = 90;
    private static final int STATUS_HORIZONTAL_PADDING_DP = 16;
    private static final int STATUS_VERTICAL_PADDING_DP = 9;
    private static final int STATUS_CORNER_RADIUS_DP = 18;
    private static final int LOADING_TEXT_SIZE_SP = 13;
    private static final int LOADING_TEXT_MIN_HEIGHT_DP = 18;
    private static final int LOADING_LEFT_MARGIN_DP = 8;
    private static final int CAPTURE_MAX_IMAGES = 2;
    private static final long DRAG_HOLD_MS = 300L;
    private static final long CAPTURE_BUTTON_HIDE_DELAY_MS = 90L;
    private static final long CAPTURE_TIMEOUT_MS = 3000L;
    private static final long LOADING_ANIMATION_INTERVAL_MS = 500L;
    private static final long DEBUG_OVERLAY_DURATION_MS = 2500L;
    private static final long STATUS_DURATION_MS = 1500L;
    private static final int LOADING_FRAME_COUNT = 4;
    private static final float HIDDEN_DEBUG_OVERLAY_ALPHA = 0f;
    private static Intent permissionData;
    private static int permissionResult = Activity.RESULT_CANCELED;
    private static boolean permissionConsumed;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final PuzzlePipeline pipeline = new PuzzlePipeline();
    private WindowManager windows;
    private LinearLayout solveButtonRow;
    private Button quitButton;
    private DebugOverlayView debugOverlay;
    private TextView statusBubble;
    private TextView loadingText;
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
    private volatile int operationId;
    private int loadingFrame;
    private final Runnable loadingTick = new Runnable() {
        @Override public void run() {
            if (loadingText == null || !busy) return;
            loadingFrame = (loadingFrame + 1) % LOADING_FRAME_COUNT;
            updateLoadingText();
            positionLoadingIndicator();
            main.postDelayed(this, LOADING_ANIMATION_INTERVAL_MS);
        }
    };

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
        showControlButtons();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (Settings.canDrawOverlays(this)) {
            if (solveButtonRow == null || quitButton == null) showControlButtons();
            else main.post(this::restoreAllButtonPositions);
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        main.post(this::restoreAllButtonPositions);
    }

    @Override public void onDestroy() {
        operationId++;
        SolverAccessibilityService.cancelActiveGestures();
        cancelTimeout();
        closeReader();
        removeDebugOverlay();
        hideLoadingIndicator();
        removeStatusBubble();
        worker.shutdownNow();
        removeOverlayButton(solveButtonRow);
        removeOverlayButton(quitButton);
        solveButtonRow = null;
        quitButton = null;
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

    private void showControlButtons() {
        if (quitButton == null) {
            quitButton = createOverlayButton(QUIT_BUTTON_TEXT, v -> quitPuzzle(), QUIT_BUTTON_ID);
            addOverlayButton(quitButton, dp(BUTTON_MIN_WIDTH_DP), dp(BUTTON_MIN_HEIGHT_DP));
        }
        if (solveButtonRow == null) {
            solveButtonRow = createBoardSizeButtonRow();
            addOverlayButton(solveButtonRow, dp(BOARD_SIZE_BUTTON_ROW_WIDTH_DP), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        main.post(this::restoreAllButtonPositions);
    }

    private LinearLayout createBoardSizeButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setBaselineAligned(false);
        row.setMinimumHeight(dp(BOARD_SIZE_BUTTON_MIN_HEIGHT_DP));
        row.setOnTouchListener(new DraggableButtonTouchListener(SOLVE_BUTTON_ID, row));

        int[] sizes = {8, 9, 10, 11, 12};
        for (int i = 0; i < sizes.length; i++) {
            Button button = createBoardSizeButton(sizes[i], row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(BOARD_SIZE_BUTTON_MIN_HEIGHT_DP), 1f);
            if (i < sizes.length - 1) {
                params.rightMargin = dp(BOARD_SIZE_BUTTON_SPACING_DP);
            }
            row.addView(button, params);
        }
        return row;
    }

    private Button createBoardSizeButton(int boardSize, View dragView) {
        Button button = new Button(this);
        button.setText(Integer.toString(boardSize));
        button.setTextColor(Color.WHITE);
        button.setTextSize(BOARD_SIZE_BUTTON_TEXT_SIZE_SP);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(BOARD_SIZE_BUTTON_MIN_HEIGHT_DP));
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(createButtonBackground());
        button.setElevation(dp(BUTTON_ELEVATION_DP));
        button.setOnClickListener(v -> captureOnce(boardSize));
        button.setOnTouchListener(new DraggableButtonTouchListener(SOLVE_BUTTON_ID, dragView));
        return button;
    }

    private Button createOverlayButton(String text, View.OnClickListener clickListener, String buttonId) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(BUTTON_TEXT_SIZE_SP);
        button.setAutoSizeTextTypeUniformWithConfiguration(QUIT_BUTTON_AUTO_SIZE_MIN_SP, BUTTON_TEXT_SIZE_SP, 1, TypedValue.COMPLEX_UNIT_SP);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(dp(BUTTON_MIN_WIDTH_DP));
        button.setMinHeight(dp(BUTTON_MIN_HEIGHT_DP));
        button.setPadding(dp(BUTTON_HORIZONTAL_PADDING_DP), dp(BUTTON_VERTICAL_PADDING_DP), dp(BUTTON_HORIZONTAL_PADDING_DP), dp(BUTTON_VERTICAL_PADDING_DP));
        button.setBackground(createButtonBackground());
        button.setElevation(dp(BUTTON_ELEVATION_DP));
        button.setOnClickListener(clickListener);
        button.setOnTouchListener(new DraggableButtonTouchListener(buttonId));
        return button;
    }

    private GradientDrawable createButtonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(229, 106, 33));
        background.setCornerRadius(dp(BUTTON_CORNER_RADIUS_DP));
        background.setStroke(dp(BUTTON_STROKE_WIDTH_DP), Color.rgb(168, 68, 15));
        return background;
    }

    private void addOverlayButton(View button) {
        addOverlayButton(button, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void addOverlayButton(View button, int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        windows.addView(button, params);
    }

    private void removeOverlayButton(View button) {
        if (button == null) return;
        try { windows.removeView(button); } catch (RuntimeException ignored) { }
    }

    private void captureOnce(int forcedBoardSize) {
        if (busy) return;
        Log.i(TAG, "Forced board size: " + forcedBoardSize);
        int operation = ++operationId;
        busy = true;
        removeDebugOverlay();
        hideLoadingIndicator();
        removeStatusBubble();
        setControlButtonsVisibility(View.INVISIBLE);
        main.postDelayed(() -> beginCapture(operation, forcedBoardSize), CAPTURE_BUTTON_HIDE_DELAY_MS);
    }

    private void beginCapture(int operation, int forcedBoardSize) {
        if (!isCurrentOperation(operation) || !busy) return;
        if (!hasProjectionPermission()) { fail(operation, "MediaProjection permission is not available", null); return; }
        capturing = true;
        try {
            int[] size = screenSize();
            reader = ImageReader.newInstance(size[0], size[1], PixelFormat.RGBA_8888, CAPTURE_MAX_IMAGES);
            reader.setOnImageAvailableListener(source -> imageAvailable(source, operation, forcedBoardSize), main);
            if (projection == null) createProjection(size);
            else {
                if (virtualDisplay == null) throw new IllegalStateException("Virtual display is unavailable");
                if (displayWidth != size[0] || displayHeight != size[1] || displayDensity != size[2]) {
                    virtualDisplay.resize(size[0], size[1], size[2]);
                    displayWidth = size[0]; displayHeight = size[1]; displayDensity = size[2];
                }
                virtualDisplay.setSurface(reader.getSurface());
            }
            timeout = () -> fail(operation, "Timed out waiting for a screen image", null);
            main.postDelayed(timeout, CAPTURE_TIMEOUT_MS);
        } catch (Exception error) { fail(operation, error.getMessage(), error); }
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

    private void imageAvailable(ImageReader source, int operation, int forcedBoardSize) {
        if (!capturing || source != reader || !isCurrentOperation(operation)) return;
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            detachSurface();
            cancelTimeout();
            Bitmap next = toBitmap(image);
            Bitmap pipelineBitmap = next.copy(Bitmap.Config.ARGB_8888, false);
            capturing = false;
            restoreControlButtons();
            showLoadingIndicator();
            Log.i(TAG, "Screen captured successfully");
            process(pipelineBitmap, operation, forcedBoardSize);
        } catch (Exception error) { fail(operation, error.getMessage(), error); }
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

    private void process(Bitmap bitmap, int operation, int forcedBoardSize) {
        worker.execute(() -> {
            try {
                ArgbImage image = toArgbImage(bitmap);
                AnalysisResult result = pipeline.analyze(image, forcedBoardSize);
                if (result.hudDetection != null) {
                    Log.i(TAG, result.hudDebugSummary());
                }
                DebugImageWriter.saveAll(OverlayService.this, bitmap, result, "operation-" + operation);
                if (!isCurrentOperation(operation)) return;
                main.post(() -> {
                    if (isCurrentOperation(operation)) showDebug(DebugData.from(result));
                });
                if (!result.isSuccess()) {
                    finishOperation(operation, result.failureReason);
                    return;
                }
                main.post(() -> {
                    if (isCurrentOperation(operation) && !DebugFlags.SHOW_OVERLAYS) removeDebugOverlay();
                });
                if (!isCurrentOperation(operation)) return;
                main.post(() -> {
                    if (isCurrentOperation(operation)) hideLoadingIndicator();
                });
                SolverAccessibilityService.tapMissing(
                        result.board,
                        result.model.occupied,
                        result.solution.columns,
                        (success, reason) -> finishOperation(operation, success ? null : reason)
                );
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        });
    }

    private void finishOperation(int operation, String reason) {
        if (!isCurrentOperation(operation)) return;
        main.post(() -> {
            if (!isCurrentOperation(operation)) return;
            if (!DebugFlags.SHOW_OVERLAYS) removeDebugOverlay();
            hideLoadingIndicator();
            if (reason != null) {
                Log.i(TAG, reason);
                showStatus(reason);
            }
            busy = false;
        });
    }

    private ArgbImage toArgbImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return ArgbImage.wrapCopy(width, height, pixels);
    }

    private void quitPuzzle() {
        cancelActiveOperation();
        stopSelf();
    }

    private void cancelActiveOperation() {
        operationId++;
        SolverAccessibilityService.cancelActiveGestures();
        detachSurface();
        cancelTimeout();
        closeReader();
        removeDebugOverlay();
        hideLoadingIndicator();
        removeStatusBubble();
        capturing = false;
        busy = false;
        restoreControlButtons();
    }

    private void showDebug(DebugData data) {
        if (!DebugFlags.SHOW_OVERLAYS) return;
        removeDebugOverlay();
        debugOverlay = new DebugOverlayView(this);
        debugOverlay.show(data);
        debugOverlay.setAlpha(HIDDEN_DEBUG_OVERLAY_ALPHA);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windows.addView(debugOverlay, params);
        main.postDelayed(this::removeDebugOverlay, DEBUG_OVERLAY_DURATION_MS);
    }

    private void removeDebugOverlay() {
        if (debugOverlay == null) return;
        try { windows.removeView(debugOverlay); } catch (RuntimeException ignored) { }
        debugOverlay = null;
    }

    private void showLoadingIndicator() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::showLoadingIndicator);
            return;
        }
        if (loadingText == null) {
            loadingText = new TextView(this);
            loadingText.setTextColor(Color.argb(230, 255, 255, 255));
            loadingText.setTextSize(LOADING_TEXT_SIZE_SP);
            loadingText.setGravity(Gravity.CENTER_VERTICAL);
            loadingText.setSingleLine(true);
            loadingText.setIncludeFontPadding(false);
            loadingText.setShadowLayer(3f, 0f, 1f, Color.argb(190, 0, 0, 0));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            windows.addView(loadingText, params);
        }
        loadingFrame = 0;
        updateLoadingText();
        positionLoadingIndicator();
        main.removeCallbacks(loadingTick);
        main.postDelayed(loadingTick, LOADING_ANIMATION_INTERVAL_MS);
    }

    private void hideLoadingIndicator() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::hideLoadingIndicator);
            return;
        }
        main.removeCallbacks(loadingTick);
        if (loadingText == null) return;
        try { windows.removeView(loadingText); } catch (RuntimeException ignored) { }
        loadingText = null;
        loadingFrame = 0;
    }

    private void updateLoadingText() {
        if (loadingText == null) return;
        StringBuilder text = new StringBuilder(LOADING_BASE_TEXT);
        for (int i = 0; i < loadingFrame; i++) text.append('.');
        loadingText.setText(text.toString());
    }

    private void positionLoadingIndicator() {
        if (loadingText == null || quitButton == null || quitButton.getParent() == null) return;
        WindowManager.LayoutParams quitParams = overlayParams(quitButton);
        WindowManager.LayoutParams loadingParams = overlayParams(loadingText);
        if (quitParams == null || loadingParams == null) return;

        loadingText.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int buttonWidth = Math.max(quitButton.getWidth(), dp(BUTTON_MIN_WIDTH_DP));
        int buttonHeight = Math.max(quitButton.getHeight(), dp(BUTTON_MIN_HEIGHT_DP));
        int loadingWidth = Math.max(Math.max(loadingText.getWidth(), loadingText.getMeasuredWidth()), 1);
        int loadingHeight = Math.max(Math.max(loadingText.getHeight(), loadingText.getMeasuredHeight()), dp(LOADING_TEXT_MIN_HEIGHT_DP));
        Rect safe = safeScreenBounds(quitButton);

        int desiredX = quitParams.x + buttonWidth + dp(LOADING_LEFT_MARGIN_DP);
        if (desiredX + loadingWidth <= safe.right) {
            loadingParams.x = desiredX;
        } else {
            int fallbackX = quitParams.x - loadingWidth - dp(LOADING_LEFT_MARGIN_DP);
            loadingParams.x = clamp(fallbackX, safe.left, Math.max(safe.left, safe.right - loadingWidth));
        }
        loadingParams.y = clamp(quitParams.y + Math.max(0, (buttonHeight - loadingHeight) / 2), safe.top, Math.max(safe.top, safe.bottom - loadingHeight));
        updateButtonLayout(loadingText, loadingParams);
    }

    private void showStatus(String message) {
        removeStatusBubble();
        statusBubble = new TextView(this);
        statusBubble.setText(message);
        statusBubble.setTextColor(Color.WHITE);
        statusBubble.setTextSize(15f);
        statusBubble.setGravity(Gravity.CENTER);
        statusBubble.setPadding(dp(STATUS_HORIZONTAL_PADDING_DP), dp(STATUS_VERTICAL_PADDING_DP), dp(STATUS_HORIZONTAL_PADDING_DP), dp(STATUS_VERTICAL_PADDING_DP));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(225, 45, 45, 45));
        background.setCornerRadius(dp(STATUS_CORNER_RADIUS_DP));
        statusBubble.setBackground(background);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(STATUS_BOTTOM_MARGIN_DP);
        windows.addView(statusBubble, params);
        main.postDelayed(this::removeStatusBubble, STATUS_DURATION_MS);
    }

    private void removeStatusBubble() {
        if (statusBubble == null) return;
        try { windows.removeView(statusBubble); } catch (RuntimeException ignored) { }
        statusBubble = null;
    }

    private void fail(int operation, String reason, Throwable error) {
        if (!isCurrentOperation(operation)) return;
        String message = reason == null || reason.isEmpty() ? "Unknown error" : reason;
        if (error == null) Log.e(TAG, "Screen capture failed: " + message);
        else Log.e(TAG, "Screen capture failed: " + message, error);
        detachSurface();
        cancelTimeout();
        closeReader();
        removeDebugOverlay();
        hideLoadingIndicator();
        showStatus(message);
        restoreControlButtons();
        capturing = false;
        busy = false;
    }

    private void projectionStopped() {
        if (capturing) fail(operationId, "MediaProjection session was stopped", null);
        else { cancelTimeout(); closeReader(); hideLoadingIndicator(); busy = false; }
        if (virtualDisplay != null) virtualDisplay.release();
        virtualDisplay = null;
        projection = null;
        clearPermission();
        restoreControlButtons();
    }

    private void restoreControlButtons() {
        setControlButtonsVisibility(View.VISIBLE);
    }

    private void setControlButtonsVisibility(int visibility) {
        if (solveButtonRow != null) solveButtonRow.setVisibility(visibility);
        if (quitButton != null) quitButton.setVisibility(visibility);
    }

    private boolean isCurrentOperation(int operation) {
        return operation == operationId;
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

    private void restoreAllButtonPositions() {
        restoreButtonPosition(quitButton, QUIT_BUTTON_ID);
        restoreButtonPosition(solveButtonRow, SOLVE_BUTTON_ID);
        positionLoadingIndicator();
    }

    private void restoreButtonPosition(View button, String buttonId) {
        if (button == null || button.getParent() == null) return;
        if (button.getWidth() <= 0 || button.getHeight() <= 0) {
            button.post(() -> restoreButtonPosition(button, buttonId));
            return;
        }
        WindowManager.LayoutParams params = overlayParams(button);
        if (params == null) return;

        Rect safe = safeScreenBounds(button);
        SharedPreferences preferences = OverlayPreferences.preferences(this);
        String xKey = buttonPositionXKey(buttonId);
        String yKey = buttonPositionYKey(buttonId);
        if (preferences.contains(xKey) && preferences.contains(yKey)) {
            float xFraction = clamp(preferences.getFloat(xKey, 1f), 0f, 1f);
            float yFraction = clamp(preferences.getFloat(yKey, 0f), 0f, 1f);
            params.x = safe.left + Math.round(xFraction * Math.max(0, safe.width() - button.getWidth()));
            params.y = safe.top + Math.round(yFraction * Math.max(0, safe.height() - button.getHeight()));
        } else {
            params.x = safe.right - button.getWidth() - dp(DEFAULT_RIGHT_MARGIN_DP);
            params.y = safe.top + defaultButtonTop(button, buttonId);
        }
        clampButtonParams(params, button, safe);
        updateButtonLayout(button, params);
    }

    private void saveButtonPosition(View button, String buttonId) {
        WindowManager.LayoutParams params = overlayParams(button);
        if (params == null) return;
        Rect safe = safeScreenBounds(button);
        clampButtonParams(params, button, safe);
        updateButtonLayout(button, params);

        int xRange = Math.max(1, safe.width() - button.getWidth());
        int yRange = Math.max(1, safe.height() - button.getHeight());
        float xFraction = clamp((params.x - safe.left) / (float) xRange, 0f, 1f);
        float yFraction = clamp((params.y - safe.top) / (float) yRange, 0f, 1f);
        OverlayPreferences.preferences(this)
                .edit()
                .putFloat(buttonPositionXKey(buttonId), xFraction)
                .putFloat(buttonPositionYKey(buttonId), yFraction)
                .apply();
    }

    private int defaultButtonTop(View button, String buttonId) {
        if (QUIT_BUTTON_ID.equals(buttonId)) {
            int buttonHeight = Math.max(button.getHeight(), dp(BUTTON_MIN_HEIGHT_DP));
            return Math.max(0, dp(DEFAULT_SOLVE_TOP_DP) - buttonHeight - dp(DEFAULT_BUTTON_SPACING_DP));
        }
        return dp(DEFAULT_SOLVE_TOP_DP);
    }

    private Rect safeScreenBounds(View attachedView) {
        Rect safe;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windows.getMaximumWindowMetrics();
            safe = new Rect(metrics.getBounds());
            WindowInsets windowInsets = metrics.getWindowInsets();
            Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            safe.left += insets.left;
            safe.top += insets.top;
            safe.right -= insets.right;
            safe.bottom -= insets.bottom;
        } else {
            int[] size = screenSize();
            safe = new Rect(0, 0, size[0], size[1]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && attachedView != null && attachedView.getRootWindowInsets() != null) {
                DisplayCutout cutout = attachedView.getRootWindowInsets().getDisplayCutout();
                if (cutout != null) {
                    safe.left += cutout.getSafeInsetLeft();
                    safe.top += cutout.getSafeInsetTop();
                    safe.right -= cutout.getSafeInsetRight();
                    safe.bottom -= cutout.getSafeInsetBottom();
                }
            }
        }

        int margin = dp(SAFE_AREA_MARGIN_DP);
        safe.inset(margin, margin);
        if (safe.width() <= 0 || safe.height() <= 0) {
            int[] size = screenSize();
            safe.set(0, 0, size[0], size[1]);
        }
        return safe;
    }

    private void clampButtonParams(WindowManager.LayoutParams params, View button, Rect safe) {
        int maxX = Math.max(safe.left, safe.right - Math.max(1, button.getWidth()));
        int maxY = Math.max(safe.top, safe.bottom - Math.max(1, button.getHeight()));
        params.x = clamp(params.x, safe.left, maxX);
        params.y = clamp(params.y, safe.top, maxY);
    }

    private WindowManager.LayoutParams overlayParams(View button) {
        if (!(button.getLayoutParams() instanceof WindowManager.LayoutParams)) {
            return null;
        }
        return (WindowManager.LayoutParams) button.getLayoutParams();
    }

    private void updateButtonLayout(View button, WindowManager.LayoutParams params) {
        try { windows.updateViewLayout(button, params); } catch (RuntimeException ignored) { }
    }

    private String buttonPositionXKey(String buttonId) {
        return KEY_BUTTON_X_PREFIX + buttonId;
    }

    private String buttonPositionYKey(String buttonId) {
        return KEY_BUTTON_Y_PREFIX + buttonId;
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
        channel.setDescription("Keeps the floating SOLVE and QUIT buttons available.");
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class DraggableButtonTouchListener implements View.OnTouchListener {
        private final String buttonId;
        private final View dragView;
        private final int touchSlop;
        private Runnable holdRunnable;
        private boolean dragging;
        private boolean tapCancelled;
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;

        DraggableButtonTouchListener(String buttonId) {
            this(buttonId, null);
        }

        DraggableButtonTouchListener(String buttonId, View dragView) {
            this.buttonId = buttonId;
            this.dragView = dragView;
            touchSlop = ViewConfiguration.get(OverlayService.this).getScaledTouchSlop();
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            if (!OverlayPreferences.allowButtonRepositioning(OverlayService.this)) {
                return false;
            }
            View control = draggableControl(view);
            WindowManager.LayoutParams params = overlayParams(control);
            if (params == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    beginTracking(view, event, params);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateTracking(view, control, event, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    finishTracking(view, control);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelTracking(view, control);
                    return true;
                default:
                    return true;
            }
        }

        private View draggableControl(View touchedView) {
            return dragView == null ? touchedView : dragView;
        }

        private void beginTracking(View view, MotionEvent event, WindowManager.LayoutParams params) {
            cancelHold();
            dragging = false;
            tapCancelled = false;
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            startX = params.x;
            startY = params.y;
            view.setPressed(true);
            holdRunnable = () -> {
                dragging = true;
                tapCancelled = true;
                view.setPressed(false);
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            };
            main.postDelayed(holdRunnable, DRAG_HOLD_MS);
        }

        private void updateTracking(View touchedView, View control, MotionEvent event, WindowManager.LayoutParams params) {
            float deltaX = event.getRawX() - downRawX;
            float deltaY = event.getRawY() - downRawY;
            if (dragging) {
                params.x = startX + Math.round(deltaX);
                params.y = startY + Math.round(deltaY);
                clampButtonParams(params, control, safeScreenBounds(control));
                updateButtonLayout(control, params);
                return;
            }
            if (Math.hypot(deltaX, deltaY) > touchSlop) {
                tapCancelled = true;
                touchedView.setPressed(false);
                cancelHold();
            }
        }

        private void finishTracking(View view, View control) {
            cancelHold();
            view.setPressed(false);
            if (dragging) {
                saveButtonPosition(control, buttonId);
                dragging = false;
                return;
            }
            if (!tapCancelled) {
                view.performClick();
            }
        }

        private void cancelTracking(View view, View control) {
            cancelHold();
            view.setPressed(false);
            if (dragging) {
                saveButtonPosition(control, buttonId);
            }
            dragging = false;
            tapCancelled = true;
        }

        private void cancelHold() {
            if (holdRunnable == null) return;
            main.removeCallbacks(holdRunnable);
            holdRunnable = null;
        }
    }
}
