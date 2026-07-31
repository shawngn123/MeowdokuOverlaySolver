package com.shawngn123.meowdokuoverlaysolver;

import java.util.Arrays;

public final class RegionDetector {
    private static final float CENTER_SAMPLE_FRACTION = 0.32f;
    private static final int CENTER_SAMPLE_STEPS = 9;
    private static final int KMEANS_ITERATIONS = 50;

    public Result detect(ArgbImage image, BoardGeometry board) {
        String inputError = inputValidationError(image, board);
        if (inputError != null) {
            return new Result(null, inputError);
        }

        int size = board.rows;
        int[][] sampledColors = sampleColors(image, board);
        KMeansResult clusters = cluster(sampledColors, size);
        RegionMap regions = new RegionMap(size, clusters.cells, size, sampledColors, clusters.clusterColors);
        String error = regions.validationError(board.rows, board.columns);
        return new Result(regions, error);
    }

    public int[][] sampleColors(ArgbImage image, BoardGeometry board) {
        int[][] colors = new int[board.rows][board.columns];
        for (int row = 0; row < board.rows; row++) {
            for (int column = 0; column < board.columns; column++) {
                colors[row][column] = sampleCellCenterColor(image, board.cellRect(row, column));
            }
        }
        return colors;
    }

    private int sampleCellCenterColor(ArgbImage image, FloatRect cell) {
        int sampleCount = CENTER_SAMPLE_STEPS * CENTER_SAMPLE_STEPS;
        int[] red = new int[sampleCount];
        int[] green = new int[sampleCount];
        int[] blue = new int[sampleCount];
        int index = 0;
        float start = 0.5f - CENTER_SAMPLE_FRACTION * 0.5f;
        float step = CENTER_SAMPLE_STEPS == 1 ? 0f : CENTER_SAMPLE_FRACTION / (CENTER_SAMPLE_STEPS - 1f);
        for (int sy = 0; sy < CENTER_SAMPLE_STEPS; sy++) {
            float fy = start + sy * step;
            int y = clamp(Math.round(cell.top + cell.height() * fy), 0, image.height - 1);
            for (int sx = 0; sx < CENTER_SAMPLE_STEPS; sx++) {
                float fx = start + sx * step;
                int x = clamp(Math.round(cell.left + cell.width() * fx), 0, image.width - 1);
                int pixel = image.pixel(x, y);
                red[index] = ColorMath.red(pixel);
                green[index] = ColorMath.green(pixel);
                blue[index] = ColorMath.blue(pixel);
                index++;
            }
        }
        Arrays.sort(red);
        Arrays.sort(green);
        Arrays.sort(blue);
        return ColorMath.rgb(red[red.length / 2], green[green.length / 2], blue[blue.length / 2]);
    }

    private KMeansResult cluster(int[][] sampledColors, int size) {
        int total = size * size;
        int[] colors = new int[total];
        double[][] points = new double[total][];
        int index = 0;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int color = sampledColors[row][column];
                colors[index] = color;
                points[index] = ColorMath.oklab(color);
                index++;
            }
        }

        double[][] centers = initializeCenters(points, size);
        int[] assignments = new int[total];
        Arrays.fill(assignments, -1);
        for (int iteration = 0; iteration < KMEANS_ITERATIONS; iteration++) {
            boolean changed = assign(points, centers, assignments);
            recompute(points, centers, assignments);
            if (!changed) break;
        }

        int[] canonical = canonicalize(assignments, size);
        int[][] cells = new int[size][size];
        int[] sumsR = new int[size], sumsG = new int[size], sumsB = new int[size], counts = new int[size];
        for (int i = 0; i < total; i++) {
            int region = canonical[i];
            cells[i / size][i % size] = region;
            int color = colors[i];
            sumsR[region] += ColorMath.red(color);
            sumsG[region] += ColorMath.green(color);
            sumsB[region] += ColorMath.blue(color);
            counts[region]++;
        }
        int[] clusterColors = new int[size];
        for (int region = 0; region < size; region++) {
            int count = Math.max(1, counts[region]);
            clusterColors[region] = ColorMath.rgb(sumsR[region] / count, sumsG[region] / count, sumsB[region] / count);
        }
        return new KMeansResult(cells, clusterColors);
    }

    private double[][] initializeCenters(double[][] points, int count) {
        double[] mean = new double[3];
        for (double[] point : points) {
            for (int i = 0; i < mean.length; i++) mean[i] += point[i];
        }
        for (int i = 0; i < mean.length; i++) mean[i] /= points.length;

        double[][] centers = new double[count][3];
        boolean[] chosen = new boolean[points.length];
        int first = farthest(points, mean, chosen);
        centers[0] = points[first].clone();
        chosen[first] = true;
        for (int center = 1; center < count; center++) {
            int next = farthestFromCenters(points, centers, center, chosen);
            centers[center] = points[next].clone();
            chosen[next] = true;
        }
        return centers;
    }

    private int farthest(double[][] points, double[] target, boolean[] blocked) {
        int best = 0;
        double bestDistance = -1;
        for (int i = 0; i < points.length; i++) {
            if (blocked[i]) continue;
            double distance = ColorMath.oklabDistanceSquared(points[i], target);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private int farthestFromCenters(double[][] points, double[][] centers, int centerCount, boolean[] blocked) {
        int best = 0;
        double bestDistance = -1;
        for (int i = 0; i < points.length; i++) {
            if (blocked[i]) continue;
            double nearest = Double.MAX_VALUE;
            for (int center = 0; center < centerCount; center++) {
                nearest = Math.min(nearest, ColorMath.oklabDistanceSquared(points[i], centers[center]));
            }
            if (nearest > bestDistance) {
                bestDistance = nearest;
                best = i;
            }
        }
        return best;
    }

    private boolean assign(double[][] points, double[][] centers, int[] assignments) {
        boolean changed = false;
        for (int i = 0; i < points.length; i++) {
            int best = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int center = 0; center < centers.length; center++) {
                double distance = ColorMath.oklabDistanceSquared(points[i], centers[center]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = center;
                }
            }
            if (assignments[i] != best) {
                assignments[i] = best;
                changed = true;
            }
        }
        return changed;
    }

    private void recompute(double[][] points, double[][] centers, int[] assignments) {
        double[][] sums = new double[centers.length][3];
        int[] counts = new int[centers.length];
        for (int i = 0; i < points.length; i++) {
            int assignment = assignments[i];
            counts[assignment]++;
            for (int component = 0; component < 3; component++) {
                sums[assignment][component] += points[i][component];
            }
        }
        for (int center = 0; center < centers.length; center++) {
            if (counts[center] == 0) {
                splitLargestCluster(points, centers, assignments, counts, center);
                continue;
            }
            for (int component = 0; component < 3; component++) {
                centers[center][component] = sums[center][component] / counts[center];
            }
        }
    }

    private void splitLargestCluster(double[][] points, double[][] centers, int[] assignments, int[] counts, int emptyCenter) {
        int largest = 0;
        for (int center = 1; center < counts.length; center++) {
            if (counts[center] > counts[largest]) largest = center;
        }
        int farthest = -1;
        double farthestDistance = -1;
        for (int i = 0; i < points.length; i++) {
            if (assignments[i] != largest) continue;
            double distance = ColorMath.oklabDistanceSquared(points[i], centers[largest]);
            if (distance > farthestDistance) {
                farthestDistance = distance;
                farthest = i;
            }
        }
        if (farthest >= 0) {
            centers[emptyCenter] = points[farthest].clone();
            assignments[farthest] = emptyCenter;
        }
    }

    private int[] canonicalize(int[] assignments, int size) {
        int[] remap = new int[size];
        Arrays.fill(remap, -1);
        int next = 0;
        int[] canonical = new int[assignments.length];
        for (int i = 0; i < assignments.length; i++) {
            int assignment = assignments[i];
            if (remap[assignment] < 0) remap[assignment] = next++;
            canonical[i] = remap[assignment];
        }
        return canonical;
    }

    private String inputValidationError(ArgbImage image, BoardGeometry board) {
        if (image == null) {
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Result {
        public final RegionMap regions;
        public final String failureReason;

        private Result(RegionMap regions, String failureReason) {
            this.regions = regions;
            this.failureReason = failureReason;
        }

        public boolean isSuccess() {
            return regions != null && failureReason == null;
        }
    }

    private static final class KMeansResult {
        final int[][] cells;
        final int[] clusterColors;

        KMeansResult(int[][] cells, int[] clusterColors) {
            this.cells = cells;
            this.clusterColors = clusterColors;
        }
    }
}
