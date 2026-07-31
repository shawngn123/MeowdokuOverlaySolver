package com.shawngn123.meowdokuoverlaysolver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private static final String FILE_PREFIX = "region-debug-";
    private static final int SAMPLE_SWATCH_ALPHA = 210;

    private DebugImageWriter() { }

    static File save(Context context, Bitmap screenshot, DebugData data, String label) {
        if (context == null || screenshot == null || screenshot.isRecycled() || data == null || data.board == null) {
            return null;
        }
        File directory = debugDirectory(context);
        if (directory == null || (!directory.exists() && !directory.mkdirs())) {
            Log.i(TAG, "Could not create debug image directory.");
            return null;
        }

        Bitmap annotated = screenshot.copy(Bitmap.Config.ARGB_8888, true);
        try {
            drawOverlay(new Canvas(annotated), data);
            File output = new File(directory, FILE_PREFIX + timestamp() + "-" + sanitize(label) + ".png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!annotated.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    Log.i(TAG, "Could not encode debug image.");
                    return null;
                }
            }
            Log.i(TAG, "Saved region debug image: " + output.getAbsolutePath());
            return output;
        } catch (IOException error) {
            Log.e(TAG, "Could not save region debug image", error);
            return null;
        } finally {
            annotated.recycle();
        }
    }

    private static void drawOverlay(Canvas canvas, DebugData data) {
        BoardGeometry board = data.board;
        RegionMap regions = data.regions;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (regions != null && regions.hasSampledColors()) {
            paint.setStyle(Paint.Style.FILL);
            for (int row = 0; row < regions.size; row++) {
                for (int column = 0; column < regions.size; column++) {
                    RectF cell = board.cellRect(row, column);
                    int sampled = regions.sampledColor(row, column);
                    paint.setColor(Color.argb(SAMPLE_SWATCH_ALPHA, Color.red(sampled), Color.green(sampled), Color.blue(sampled)));
                    canvas.drawRect(cell, paint);
                }
            }
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, board.averageCellSize() * 0.035f));
        paint.setColor(Color.argb(245, 0, 255, 80));
        canvas.drawRect(board.bounds, paint);

        paint.setStrokeWidth(Math.max(1.5f, board.averageCellSize() * 0.018f));
        paint.setColor(Color.argb(235, 0, 210, 255));
        for (float x : board.xLines) canvas.drawLine(x, board.bounds.top, x, board.bounds.bottom, paint);
        for (float y : board.yLines) canvas.drawLine(board.bounds.left, y, board.bounds.right, y, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(14f, board.averageCellSize() * 0.24f));
        float centerRadius = Math.max(3f, board.averageCellSize() * 0.055f);
        for (int row = 0; row < board.rows; row++) {
            for (int column = 0; column < board.columns; column++) {
                float x = board.centerX(column);
                float y = board.centerY(row);
                paint.setColor(Color.argb(245, 255, 40, 40));
                canvas.drawCircle(x, y, centerRadius, paint);
                if (regions != null && row < regions.size && column < regions.size) {
                    paint.setColor(Color.argb(245, 20, 20, 20));
                    float textY = y - (paint.ascent() + paint.descent()) * 0.5f;
                    canvas.drawText(Integer.toString(regions.regionId(row, column)), x, textY, paint);
                }
            }
        }
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
}
