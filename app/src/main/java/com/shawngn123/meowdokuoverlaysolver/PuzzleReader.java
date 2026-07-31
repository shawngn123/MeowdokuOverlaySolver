package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

import java.util.Arrays;

final class PuzzleReader {
    PuzzleModel read(Bitmap bitmap, BoardGeometry board, RegionMap regions) {
        if (bitmap == null || board == null || regions == null || !regions.isValid()) return null;
        int n = board.rows;
        float[][] scores = new float[n][n];
        float[][] badges = new float[n][n];
        float[] flat = new float[n * n];
        int index = 0;
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                CellScore score = scoreCell(bitmap, board.cellRect(row, column), regions.sampledColors[row][column]);
                scores[row][column] = score.cat;
                badges[row][column] = score.badge;
                flat[index++] = score.cat;
            }
        }

        float[] means = clusterMeans(flat);
        float low = Math.min(means[0], means[1]);
        float high = Math.max(means[0], means[1]);
        float threshold = (low + high) * 0.5f;
        boolean separated = high >= 0.070f && high - low >= 0.030f;
        boolean[][] occupied = new boolean[n][n];
        boolean[][] locked = new boolean[n][n];
        int occupiedCount = 0;
        if (separated) {
            for (int row = 0; row < n; row++) {
                for (int column = 0; column < n; column++) {
                    boolean cat = scores[row][column] >= threshold;
                    occupied[row][column] = cat;
                    locked[row][column] = cat && badges[row][column] >= 0.085f;
                    if (cat) occupiedCount++;
                }
            }
        }
        if (occupiedCount > n) return null;
        PuzzleModel result = new PuzzleModel(n, regions, occupied, locked, scores);
        return result.isValid() ? result : null;
    }

    private CellScore scoreCell(Bitmap bitmap, RectF cell, int background) {
        int steps = 22;
        int foreground = 0, dark = 0, edges = 0, badge = 0, badgeTotal = 0, total = 0;
        float bgLum = luminance(background);
        for (int sy = 0; sy < steps; sy++) {
            float fy = 0.16f + sy / (steps - 1f) * 0.68f;
            int y = clamp(Math.round(cell.top + cell.height() * fy), 0, bitmap.getHeight() - 1);
            for (int sx = 0; sx < steps; sx++) {
                float fx = 0.16f + sx / (steps - 1f) * 0.68f;
                int x = clamp(Math.round(cell.left + cell.width() * fx), 0, bitmap.getWidth() - 1);
                int pixel = bitmap.getPixel(x, y);
                float difference = colorDistance(pixel, background);
                float lum = luminance(pixel);
                if (difference > 27f) foreground++;
                if (difference > 18f && lum < bgLum - 0.075f) dark++;
                int nx = clamp(x + Math.max(1, Math.round(cell.width() / 44f)), 0, bitmap.getWidth() - 1);
                int ny = clamp(y + Math.max(1, Math.round(cell.height() / 44f)), 0, bitmap.getHeight() - 1);
                if (colorDistance(pixel, bitmap.getPixel(nx, y)) > 25f || colorDistance(pixel, bitmap.getPixel(x, ny)) > 25f) edges++;
                if (fx > 0.58f && fy < 0.44f) {
                    badgeTotal++;
                    if (difference > 31f) badge++;
                }
                total++;
            }
        }
        float foregroundRatio = foreground / (float) total;
        float darkRatio = dark / (float) total;
        float edgeRatio = edges / (float) total;
        float cat = foregroundRatio * 0.48f + darkRatio * 0.34f + edgeRatio * 0.18f;
        return new CellScore(cat, badgeTotal == 0 ? 0f : badge / (float) badgeTotal);
    }

    private float[] clusterMeans(float[] values) {
        float[] sorted = values.clone();
        Arrays.sort(sorted);
        float a = sorted[Math.max(0, sorted.length / 5)];
        float b = sorted[Math.min(sorted.length - 1, sorted.length * 4 / 5)];
        for (int iteration = 0; iteration < 10; iteration++) {
            float sumA = 0f, sumB = 0f;
            int countA = 0, countB = 0;
            for (float value : values) {
                if (Math.abs(value - a) <= Math.abs(value - b)) { sumA += value; countA++; }
                else { sumB += value; countB++; }
            }
            if (countA > 0) a = sumA / countA;
            if (countB > 0) b = sumB / countB;
        }
        return new float[]{a, b};
    }

    private float colorDistance(int first, int second) {
        float dr = Color.red(first) - Color.red(second);
        float dg = Color.green(first) - Color.green(second);
        float db = Color.blue(first) - Color.blue(second);
        return (float) Math.sqrt(dr * dr * 0.24f + dg * dg * 0.55f + db * db * 0.21f);
    }

    private float luminance(int color) {
        return (Color.red(color) * 0.2126f + Color.green(color) * 0.7152f + Color.blue(color) * 0.0722f) / 255f;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class CellScore {
        final float cat;
        final float badge;
        CellScore(float cat, float badge) { this.cat = cat; this.badge = badge; }
    }
}
