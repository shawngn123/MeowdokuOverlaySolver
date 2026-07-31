package com.shawngn123.meowdokuoverlaysolver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DebugImageWriter {
    private static final String TAG = "MeowdokuSolver";
    private static final String DEBUG_DIR = "meowdoku-debug";

    private DebugImageWriter() { }

    static File saveAll(Context context, Bitmap screenshot, AnalysisResult result, String label) {
        if (context == null || screenshot == null || screenshot.isRecycled() || result == null) {
            return null;
        }
        File parent = debugDirectory(context);
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            Log.i(TAG, "Could not create debug image directory.");
            return null;
        }
        File directory = new File(parent, timestamp() + "-" + sanitize(label));
        if (!directory.exists() && !directory.mkdirs()) {
            Log.i(TAG, "Could not create debug image run directory.");
            return null;
        }

        saveHudCrop(screenshot, result, directory);

        for (DebugStage stage : DebugStage.values()) {
            Bitmap bitmap = screenshot.copy(Bitmap.Config.ARGB_8888, true);
            try {
                drawStage(new Canvas(bitmap), result, stage);
                File output = new File(directory, stage.fileName);
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        Log.i(TAG, "Could not encode debug stage " + stage.fileName);
                    }
                }
            } catch (IOException error) {
                Log.e(TAG, "Could not save debug stage " + stage.fileName, error);
            } finally {
                bitmap.recycle();
            }
        }
        Log.i(TAG, "Saved debug images: " + directory.getAbsolutePath());
        return directory;
    }

    private static void saveHudCrop(Bitmap screenshot, AnalysisResult result, File directory) {
        if (result.hudDetection == null || result.hudDetection.cropBounds == null) return;
        FloatRect bounds = result.hudDetection.cropBounds;
        int left = clamp(Math.round(bounds.left), 0, screenshot.getWidth() - 1);
        int top = clamp(Math.round(bounds.top), 0, screenshot.getHeight() - 1);
        int right = clamp(Math.round(bounds.right), left + 1, screenshot.getWidth());
        int bottom = clamp(Math.round(bounds.bottom), top + 1, screenshot.getHeight());
        Bitmap crop = null;
        try {
            crop = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top);
            File output = new File(directory, "Debug_HUD.png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!crop.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    Log.i(TAG, "Could not encode Debug_HUD.png");
                }
            }
        } catch (IOException error) {
            Log.e(TAG, "Could not save Debug_HUD.png", error);
        } finally {
            if (crop != null) crop.recycle();
        }
    }

    private static void drawStage(Canvas canvas, AnalysisResult result, DebugStage stage) {
        if (stage == DebugStage.RAW) return;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BoardGeometry board = result.board;
        if (board == null) {
            drawMessage(canvas, paint, result.failureReason == null ? "Board not detected" : result.failureReason);
            return;
        }

        switch (stage) {
            case BOARD_BOUNDS:
                drawBoardBounds(canvas, paint, board);
                break;
            case GRID_LINES:
                drawGridLines(canvas, paint, board);
                break;
            case CELL_CENTERS:
                drawGridLines(canvas, paint, board);
                drawCenters(canvas, paint, board);
                break;
            case COLOR_CLUSTERS:
                if (result.regions != null) drawSampledColors(canvas, paint, board, result.regions);
                drawGridLines(canvas, paint, board);
                break;
            case REGIONS:
                if (result.regions != null) drawRegions(canvas, paint, board, result.regions);
                drawGridLines(canvas, paint, board);
                break;
            case SOLUTION:
                if (result.regions != null) drawRegions(canvas, paint, board, result.regions);
                drawSolution(canvas, paint, board, result.model, result.solution);
                drawGridLines(canvas, paint, board);
                break;
            case TOUCH_TARGETS:
                if (result.regions != null) drawRegions(canvas, paint, board, result.regions);
                drawTouchTargets(canvas, paint, result);
                drawGridLines(canvas, paint, board);
                break;
            case RAW:
                break;
        }
        if (result.failureReason != null) {
            drawMessage(canvas, paint, result.failureReason);
        }
    }

    private static void drawBoardBounds(Canvas canvas, Paint paint, BoardGeometry board) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4f, board.averageCellSize() * 0.055f));
        paint.setColor(Color.argb(245, 0, 210, 90));
        drawRect(canvas, board.bounds, paint);
    }

    private static void drawGridLines(Canvas canvas, Paint paint, BoardGeometry board) {
        drawBoardBounds(canvas, paint, board);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, board.averageCellSize() * 0.022f));
        paint.setColor(Color.argb(235, 0, 180, 255));
        for (float x : board.xLines) canvas.drawLine(x, board.bounds.top, x, board.bounds.bottom, paint);
        for (float y : board.yLines) canvas.drawLine(board.bounds.left, y, board.bounds.right, y, paint);
    }

    private static void drawCenters(Canvas canvas, Paint paint, BoardGeometry board) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(245, 255, 50, 50));
        float radius = Math.max(3f, board.averageCellSize() * 0.055f);
        for (int row = 0; row < board.rows; row++) {
            for (int column = 0; column < board.columns; column++) {
                canvas.drawCircle(board.centerX(column), board.centerY(row), radius, paint);
            }
        }
    }

    private static void drawSampledColors(Canvas canvas, Paint paint, BoardGeometry board, RegionMap regions) {
        paint.setStyle(Paint.Style.FILL);
        for (int row = 0; row < regions.size; row++) {
            for (int column = 0; column < regions.size; column++) {
                int color = regions.sampledColor(row, column);
                paint.setColor(Color.argb(218, Color.red(color), Color.green(color), Color.blue(color)));
                drawRect(canvas, board.cellRect(row, column), paint);
            }
        }
    }

    private static void drawRegions(Canvas canvas, Paint paint, BoardGeometry board, RegionMap regions) {
        paint.setStyle(Paint.Style.FILL);
        for (int row = 0; row < regions.size; row++) {
            for (int column = 0; column < regions.size; column++) {
                int region = regions.regionId(row, column);
                int color = debugRegionColor(region);
                paint.setColor(Color.argb(130, Color.red(color), Color.green(color), Color.blue(color)));
                drawRect(canvas, board.cellRect(row, column), paint);
            }
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(14f, board.averageCellSize() * 0.23f));
        paint.setColor(Color.argb(245, 20, 20, 20));
        for (int row = 0; row < regions.size; row++) {
            for (int column = 0; column < regions.size; column++) {
                float y = board.centerY(row) - (paint.ascent() + paint.descent()) * 0.5f;
                canvas.drawText(Integer.toString(regions.regionId(row, column)), board.centerX(column), y, paint);
            }
        }
    }

    private static void drawSolution(Canvas canvas, Paint paint, BoardGeometry board, PuzzleModel model, PuzzleSolver.Result solution) {
        if (solution == null || solution.columns == null) return;
        paint.setStyle(Paint.Style.FILL);
        float radius = Math.max(8f, board.averageCellSize() * 0.24f);
        for (int row = 0; row < solution.columns.length; row++) {
            int column = solution.columns[row];
            if (column < 0 || column >= board.columns) continue;
            boolean existing = model != null && model.occupied[row][column];
            paint.setColor(existing ? Color.argb(230, 60, 60, 60) : Color.argb(230, 255, 45, 190));
            canvas.drawCircle(board.centerX(column), board.centerY(row), radius, paint);
        }
    }

    private static void drawTouchTargets(Canvas canvas, Paint paint, AnalysisResult result) {
        if (result.touchTargets == null) return;
        BoardGeometry board = result.board;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4f, board.averageCellSize() * 0.06f));
        paint.setColor(Color.argb(245, 255, 45, 190));
        float radius = Math.max(10f, board.averageCellSize() * 0.28f);
        for (TouchTarget target : result.touchTargets) {
            canvas.drawCircle(target.x, target.y, radius, paint);
            canvas.drawLine(target.x - radius, target.y, target.x + radius, target.y, paint);
            canvas.drawLine(target.x, target.y - radius, target.x, target.y + radius, paint);
        }
    }

    private static int debugRegionColor(int region) {
        float[] hsv = new float[]{(region * 137.508f) % 360f, 0.68f, 1f};
        return Color.HSVToColor(hsv);
    }

    private static void drawMessage(Canvas canvas, Paint paint, String message) {
        if (message == null || message.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(220, 30, 30, 30));
        float textSize = Math.max(24f, canvas.getWidth() * 0.032f);
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.LEFT);
        String[] lines = message.split("\\n");
        float lineHeight = textSize * 1.25f;
        float boxWidth = canvas.getWidth() - 32f;
        float boxHeight = lineHeight * lines.length + 28f;
        canvas.drawRoundRect(16f, 16f, boxWidth, 16f + boxHeight, 18f, 18f, paint);
        paint.setColor(Color.WHITE);
        float y = 34f - paint.ascent();
        for (String line : lines) {
            canvas.drawText(line, 30f, y, paint);
            y += lineHeight;
        }
    }

    private static void drawRect(Canvas canvas, FloatRect rect, Paint paint) {
        canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint);
    }

    private static File debugDirectory(Context context) {
        File root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (root == null) root = context.getFilesDir();
        return root == null ? null : new File(root, DEBUG_DIR);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
    }

    private static String sanitize(String label) {
        if (label == null || label.isEmpty()) return "debug";
        return label.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
