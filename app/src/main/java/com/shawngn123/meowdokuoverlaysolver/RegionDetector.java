package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class RegionDetector {
    private static final String TAG = "MeowdokuSolver";
    private static final float REGION_COLOR_DISTANCE_TOLERANCE = 22f;
    private static final float MIN_COLOR_DISTANCE_TOLERANCE = 6f;
    private static final float MAX_COLOR_DISTANCE_TOLERANCE = 48f;
    private static final float COLOR_DISTANCE_TOLERANCE_STEP = 2f;
    private static final int CENTER_SAMPLE_STEPS = 5;
    private static final float CENTER_SAMPLE_SPAN = 0.18f;

    static final class Result {
        final RegionMap regions;
        final String failureReason;

        private Result(RegionMap regions, String failureReason) {
            this.regions = regions;
            this.failureReason = failureReason;
        }

        boolean isSuccess() {
            return regions != null && failureReason == null;
        }
    }

    Result detect(Bitmap bitmap, BoardGeometry board) {
        String inputError = inputValidationError(bitmap, board);
        if (inputError != null) {
            return failure(null, inputError);
        }

        int size = board.rows;
        int[][] sampledColors = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                sampledColors[row][column] = sampleCellCenterColor(bitmap, board.cellRect(row, column));
            }
        }

        DetectionAttempt best = null;
        for (float tolerance : candidateTolerances()) {
            RegionMap regions = buildRegionMap(size, sampledColors, tolerance);
            String validationError = regions.validationError(board.rows, board.columns);
            DetectionAttempt attempt = new DetectionAttempt(regions, validationError, tolerance, score(regions, size));
            if (validationError == null) {
                Log.i(TAG, "Detected " + regions.regionCount + " regions from center-cell color samples with tolerance " + tolerance + ".");
                return new Result(regions, null);
            }
            if (best == null || attempt.score < best.score) {
                best = attempt;
            }
        }

        String reason = best == null ? "Could not sample region colors." : best.validationError
                + "\nColor tolerance used: " + best.tolerance
                + "\n" + best.regions.regionCountsDiagnostic();
        return failure(best == null ? null : best.regions, reason);
    }

    private RegionMap buildRegionMap(int size, int[][] sampledColors, float tolerance) {
        int[][] regionIds = new int[size][size];
        List<ColorCluster> clusters = new ArrayList<>(size);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int color = sampledColors[row][column];
                int region = nearestCluster(clusters, color, tolerance);
                if (region < 0) {
                    region = clusters.size();
                    clusters.add(new ColorCluster(color));
                } else {
                    clusters.get(region).add(color);
                }
                regionIds[row][column] = region;
            }
        }
        return new RegionMap(size, regionIds, clusters.size(), sampledColors);
    }

    private int nearestCluster(List<ColorCluster> clusters, int color, float tolerance) {
        int best = -1;
        float bestDistance = tolerance;
        for (int i = 0; i < clusters.size(); i++) {
            float distance = colorDistance(color, clusters.get(i).averageColor());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private float[] candidateTolerances() {
        int count = 1 + Math.round((MAX_COLOR_DISTANCE_TOLERANCE - MIN_COLOR_DISTANCE_TOLERANCE) / COLOR_DISTANCE_TOLERANCE_STEP);
        float[] tolerances = new float[count];
        tolerances[0] = REGION_COLOR_DISTANCE_TOLERANCE;
        int index = 1;
        for (float delta = COLOR_DISTANCE_TOLERANCE_STEP; index < count; delta += COLOR_DISTANCE_TOLERANCE_STEP) {
            float lower = REGION_COLOR_DISTANCE_TOLERANCE - delta;
            if (lower >= MIN_COLOR_DISTANCE_TOLERANCE) {
                tolerances[index++] = lower;
            }
            float higher = REGION_COLOR_DISTANCE_TOLERANCE + delta;
            if (higher <= MAX_COLOR_DISTANCE_TOLERANCE && index < count) {
                tolerances[index++] = higher;
            }
            if (lower < MIN_COLOR_DISTANCE_TOLERANCE && higher > MAX_COLOR_DISTANCE_TOLERANCE) {
                break;
            }
        }
        return index == tolerances.length ? tolerances : Arrays.copyOf(tolerances, index);
    }

    private int score(RegionMap regions, int expectedSize) {
        int score = Math.abs(regions.regionCount - expectedSize) * expectedSize * expectedSize;
        for (int count : regions.regionCellCounts) {
            score += Math.abs(count - expectedSize);
        }
        if (regions.regionCellCounts.length < expectedSize) {
            score += (expectedSize - regions.regionCellCounts.length) * expectedSize;
        }
        return score;
    }

    private int sampleCellCenterColor(Bitmap bitmap, RectF cell) {
        int sampleCount = CENTER_SAMPLE_STEPS * CENTER_SAMPLE_STEPS;
        int[] red = new int[sampleCount];
        int[] green = new int[sampleCount];
        int[] blue = new int[sampleCount];
        int index = 0;
        float start = 0.5f - CENTER_SAMPLE_SPAN * 0.5f;
        float step = CENTER_SAMPLE_STEPS == 1 ? 0f : CENTER_SAMPLE_SPAN / (CENTER_SAMPLE_STEPS - 1f);
        for (int sy = 0; sy < CENTER_SAMPLE_STEPS; sy++) {
            float fy = start + sy * step;
            int y = clamp(Math.round(cell.top + cell.height() * fy), 0, bitmap.getHeight() - 1);
            for (int sx = 0; sx < CENTER_SAMPLE_STEPS; sx++) {
                float fx = start + sx * step;
                int x = clamp(Math.round(cell.left + cell.width() * fx), 0, bitmap.getWidth() - 1);
                int pixel = bitmap.getPixel(x, y);
                red[index] = Color.red(pixel);
                green[index] = Color.green(pixel);
                blue[index] = Color.blue(pixel);
                index++;
            }
        }
        Arrays.sort(red);
        Arrays.sort(green);
        Arrays.sort(blue);
        return Color.rgb(median(red), median(green), median(blue));
    }

    private int median(int[] values) {
        return values[values.length / 2];
    }

    private String inputValidationError(Bitmap bitmap, BoardGeometry board) {
        if (bitmap == null || bitmap.isRecycled()) {
            return "Screenshot is unavailable.";
        }
        if (board == null) {
            return "Board is missing.";
        }
        if (board.rows != board.columns) {
            return "Board must be square; rows=" + board.rows + ", columns=" + board.columns + ".";
        }
        if (board.rows <= 0) {
            return "BoardSize must be greater than 0; found " + board.rows + ".";
        }
        return null;
    }

    private Result failure(RegionMap regions, String reason) {
        Log.i(TAG, "Region detection validation failed: " + reason);
        return new Result(regions, reason);
    }

    private float colorDistance(int first, int second) {
        float r1 = Color.red(first) / 255f, g1 = Color.green(first) / 255f, b1 = Color.blue(first) / 255f;
        float r2 = Color.red(second) / 255f, g2 = Color.green(second) / 255f, b2 = Color.blue(second) / 255f;
        float max1 = Math.max(r1, Math.max(g1, b1)), min1 = Math.min(r1, Math.min(g1, b1));
        float max2 = Math.max(r2, Math.max(g2, b2)), min2 = Math.min(r2, Math.min(g2, b2));
        float chroma = Math.abs((max1 - min1) - (max2 - min2));
        float luminance = Math.abs((r1 * 0.2126f + g1 * 0.7152f + b1 * 0.0722f) - (r2 * 0.2126f + g2 * 0.7152f + b2 * 0.0722f));
        float rgb = (Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)) / 3f;
        return (rgb * 0.58f + luminance * 0.27f + chroma * 0.15f) * 255f;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ColorCluster {
        private int count;
        private int red;
        private int green;
        private int blue;

        ColorCluster(int color) {
            add(color);
        }

        void add(int color) {
            count++;
            red += Color.red(color);
            green += Color.green(color);
            blue += Color.blue(color);
        }

        int averageColor() {
            int divisor = Math.max(1, count);
            return Color.rgb(red / divisor, green / divisor, blue / divisor);
        }
    }

    private static final class DetectionAttempt {
        final RegionMap regions;
        final String validationError;
        final float tolerance;
        final int score;

        DetectionAttempt(RegionMap regions, String validationError, float tolerance, int score) {
            this.regions = regions;
            this.validationError = validationError;
            this.tolerance = tolerance;
            this.score = score;
        }
    }
}
