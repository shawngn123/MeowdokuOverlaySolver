package com.shawngn123.meowdokuoverlaysolver;

import java.util.Arrays;

public final class PuzzleReader {
    private static final int CELL_SCORE_STEPS = 15;
    private static final float CELL_SCORE_FRACTION = 0.62f;

    public PuzzleModel read(ArgbImage image, BoardGeometry board, RegionMap regions) {
        if (image == null || board == null || regions == null || !regions.isValid() || !regions.hasSampledColors()) {
            return null;
        }

        int size = board.rows;
        float[][] scores = new float[size][size];
        float[] flat = new float[size * size];
        int index = 0;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float score = scoreCell(image, board.cellRect(row, column), regions.sampledColor(row, column));
                scores[row][column] = score;
                flat[index++] = score;
            }
        }

        boolean[][] occupied = inferOccupied(size, regions, scores, flat);
        boolean[][] locked = copy(occupied);
        return new PuzzleModel(size, regions, occupied, locked, scores);
    }

    private boolean[][] inferOccupied(int size, RegionMap regions, float[][] scores, float[] flat) {
        boolean[][] empty = new boolean[size][size];
        if (flat.length == 0) return empty;

        ScoreSplit split = splitScores(flat);
        if (!split.twoClusterPreferred) return empty;

        boolean[][] occupied = new boolean[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                occupied[row][column] = scores[row][column] >= split.threshold;
            }
        }
        PuzzleModel candidate = new PuzzleModel(size, regions, occupied, copy(occupied), scores);
        if (!candidate.isValid()) return empty;
        return occupied;
    }

    private ScoreSplit splitScores(float[] values) {
        float[] sorted = values.clone();
        Arrays.sort(sorted);
        float mean = 0f;
        for (float value : sorted) mean += value;
        mean /= sorted.length;

        float singleSse = 0f;
        for (float value : sorted) {
            float delta = value - mean;
            singleSse += delta * delta;
        }

        float low = sorted[Math.max(0, sorted.length / 5)];
        float high = sorted[Math.min(sorted.length - 1, sorted.length * 4 / 5)];
        for (int iteration = 0; iteration < 24; iteration++) {
            float lowSum = 0f, highSum = 0f;
            int lowCount = 0, highCount = 0;
            for (float value : sorted) {
                if (Math.abs(value - low) <= Math.abs(value - high)) {
                    lowSum += value;
                    lowCount++;
                } else {
                    highSum += value;
                    highCount++;
                }
            }
            if (lowCount > 0) low = lowSum / lowCount;
            if (highCount > 0) high = highSum / highCount;
        }
        if (low > high) {
            float swap = low;
            low = high;
            high = swap;
        }

        float twoSse = 0f;
        int highCount = 0;
        for (float value : sorted) {
            if (Math.abs(value - low) <= Math.abs(value - high)) {
                float delta = value - low;
                twoSse += delta * delta;
            } else {
                highCount++;
                float delta = value - high;
                twoSse += delta * delta;
            }
        }

        int sampleCount = sorted.length;
        double singleBic = sampleCount * Math.log(Math.max(1.0e-9, singleSse / sampleCount)) + Math.log(sampleCount);
        double twoBic = sampleCount * Math.log(Math.max(1.0e-9, twoSse / sampleCount)) + 2.0 * Math.log(sampleCount);
        boolean preferred = highCount > 0 && highCount <= Math.sqrt(sampleCount) && twoBic < singleBic;
        return new ScoreSplit((low + high) * 0.5f, preferred);
    }

    private float scoreCell(ArgbImage image, FloatRect cell, int background) {
        double[] backgroundColor = ColorMath.oklab(background);
        float start = 0.5f - CELL_SCORE_FRACTION * 0.5f;
        float step = CELL_SCORE_STEPS == 1 ? 0f : CELL_SCORE_FRACTION / (CELL_SCORE_STEPS - 1f);
        double total = 0.0;
        double totalSquared = 0.0;
        int count = 0;
        for (int sy = 0; sy < CELL_SCORE_STEPS; sy++) {
            float fy = start + sy * step;
            int y = clamp(Math.round(cell.top + cell.height() * fy), 0, image.height - 1);
            for (int sx = 0; sx < CELL_SCORE_STEPS; sx++) {
                float fx = start + sx * step;
                int x = clamp(Math.round(cell.left + cell.width() * fx), 0, image.width - 1);
                double distance = Math.sqrt(ColorMath.oklabDistanceSquared(ColorMath.oklab(image.pixel(x, y)), backgroundColor));
                total += distance;
                totalSquared += distance * distance;
                count++;
            }
        }
        double mean = total / Math.max(1, count);
        double variance = totalSquared / Math.max(1, count) - mean * mean;
        return (float) (mean + Math.sqrt(Math.max(0.0, variance)));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean[][] copy(boolean[][] source) {
        if (source == null) return null;
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private static final class ScoreSplit {
        final float threshold;
        final boolean twoClusterPreferred;

        ScoreSplit(float threshold, boolean twoClusterPreferred) {
            this.threshold = threshold;
            this.twoClusterPreferred = twoClusterPreferred;
        }
    }
}
