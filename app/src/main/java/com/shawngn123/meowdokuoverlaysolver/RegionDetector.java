package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.Log;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

final class RegionDetector {
    private static final String TAG = "MeowdokuSolver";
    private static final float COLOR_WEIGHT = 0.72f;
    private static final float BOUNDARY_WEIGHT = 0.28f;
    private static final int CELL_SAMPLE_STEPS = 13;
    private static final float CELL_SAMPLE_MIN_FRACTION = 0.14f;
    private static final float CELL_SAMPLE_MAX_FRACTION = 0.86f;
    private static final int COLOR_BUCKET_CHANNEL_SHIFT = 4;
    private static final int COLOR_BUCKET_CHANNELS = 1 << COLOR_BUCKET_CHANNEL_SHIFT;
    private static final int COLOR_BUCKET_COUNT = COLOR_BUCKET_CHANNELS * COLOR_BUCKET_CHANNELS * COLOR_BUCKET_CHANNELS;
    private static final int BOUNDARY_SAMPLES = 9;
    private static final float BOUNDARY_SAMPLE_START = 0.16f;
    private static final float BOUNDARY_SAMPLE_SPAN = 0.68f;
    private static final float BOUNDARY_SAMPLE_OFFSET = 0.16f;

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
            return failure(inputError);
        }

        int size = board.rows;
        // Screenshot pixels seed this one-time inference; downstream solving uses only RegionIDs.
        int[][] backgroundColors = new int[size][size];
        ColorBuckets buckets = new ColorBuckets();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                backgroundColors[row][column] = sampleCellBackgroundColor(bitmap, board.cellRect(row, column), buckets);
            }
        }

        Edge[] edges = new Edge[2 * size * (size - 1)];
        int edgeCount = 0;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int cell = row * size + column;
                if (column + 1 < size) {
                    float color = colorDistance(backgroundColors[row][column], backgroundColors[row][column + 1]);
                    float boundary = boundaryDifference(bitmap, board, row, column, true);
                    edges[edgeCount++] = new Edge(cell, cell + 1, color * COLOR_WEIGHT + boundary * BOUNDARY_WEIGHT);
                }
                if (row + 1 < size) {
                    float color = colorDistance(backgroundColors[row][column], backgroundColors[row + 1][column]);
                    float boundary = boundaryDifference(bitmap, board, row, column, false);
                    edges[edgeCount++] = new Edge(cell, cell + size, color * COLOR_WEIGHT + boundary * BOUNDARY_WEIGHT);
                }
            }
        }
        if (edgeCount == 0) {
            return failure("Expected region adjacency edges, found 0.");
        }
        Arrays.sort(edges, 0, edgeCount, Comparator.comparingDouble(edge -> edge.weight));

        UnionFind union = new UnionFind(size * size);
        float lastMergedWeight = 0f;
        int edgeIndex = 0;
        while (union.components > size && edgeIndex < edgeCount) {
            Edge edge = edges[edgeIndex++];
            if (union.union(edge.a, edge.b)) {
                lastMergedWeight = edge.weight;
            }
        }
        if (union.components != size) {
            return failure("Expected " + size + " regions, found " + union.components + ".");
        }

        float nextBoundaryWeight = lastMergedWeight;
        while (edgeIndex < edgeCount) {
            Edge edge = edges[edgeIndex++];
            if (union.find(edge.a) != union.find(edge.b)) {
                nextBoundaryWeight = edge.weight;
                break;
            }
        }

        Map<Integer, Integer> ids = new HashMap<>(size);
        int[][] regionIds = new int[size][size];
        int nextId = 0;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int root = union.find(row * size + column);
                Integer id = ids.get(root);
                if (id == null) {
                    id = nextId++;
                    ids.put(root, id);
                }
                regionIds[row][column] = id;
            }
        }
        if (nextId != size) {
            return failure("Expected " + size + " regions, found " + nextId + ".");
        }

        RegionMap regions = new RegionMap(size, regionIds, nextId, backgroundColors);
        String validationError = regions.validationError(board.rows, board.columns);
        if (validationError != null) {
            return failure(validationError);
        }

        Log.d(TAG, "Detected " + nextId + " deterministic RegionIDs; last merged edge="
                + lastMergedWeight + ", next boundary edge=" + nextBoundaryWeight + ".");
        return new Result(regions, null);
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
        if (board.rows < 4 || board.rows > 12) {
            return "Unsupported board size " + board.rows + ".";
        }
        return null;
    }

    private Result failure(String reason) {
        Log.i(TAG, "Region detection validation failed: " + reason);
        return new Result(null, reason);
    }

    private float boundaryDifference(Bitmap bitmap, BoardGeometry board, int row, int column, boolean vertical) {
        RectF first = board.cellRect(row, column);
        RectF second = vertical ? board.cellRect(row, column + 1) : board.cellRect(row + 1, column);
        float total = 0f;
        for (int i = 0; i < BOUNDARY_SAMPLES; i++) {
            float t = BOUNDARY_SAMPLE_START + i / (BOUNDARY_SAMPLES - 1f) * BOUNDARY_SAMPLE_SPAN;
            int x1;
            int y1;
            int x2;
            int y2;
            if (vertical) {
                float boundary = first.right;
                x1 = Math.round(boundary - first.width() * BOUNDARY_SAMPLE_OFFSET);
                x2 = Math.round(boundary + second.width() * BOUNDARY_SAMPLE_OFFSET);
                y1 = y2 = Math.round(first.top + first.height() * t);
            } else {
                float boundary = first.bottom;
                y1 = Math.round(boundary - first.height() * BOUNDARY_SAMPLE_OFFSET);
                y2 = Math.round(boundary + second.height() * BOUNDARY_SAMPLE_OFFSET);
                x1 = x2 = Math.round(first.left + first.width() * t);
            }
            int a = bitmap.getPixel(clamp(x1, 0, bitmap.getWidth() - 1), clamp(y1, 0, bitmap.getHeight() - 1));
            int b = bitmap.getPixel(clamp(x2, 0, bitmap.getWidth() - 1), clamp(y2, 0, bitmap.getHeight() - 1));
            total += colorDistance(a, b);
        }
        return total / BOUNDARY_SAMPLES;
    }

    private int sampleCellBackgroundColor(Bitmap bitmap, RectF cell, ColorBuckets buckets) {
        buckets.clear();
        int total = 0;
        for (int sy = 0; sy < CELL_SAMPLE_STEPS; sy++) {
            float fy = CELL_SAMPLE_MIN_FRACTION
                    + sy / (CELL_SAMPLE_STEPS - 1f) * (CELL_SAMPLE_MAX_FRACTION - CELL_SAMPLE_MIN_FRACTION);
            int y = clamp(Math.round(cell.top + cell.height() * fy), 0, bitmap.getHeight() - 1);
            for (int sx = 0; sx < CELL_SAMPLE_STEPS; sx++) {
                float fx = CELL_SAMPLE_MIN_FRACTION
                        + sx / (CELL_SAMPLE_STEPS - 1f) * (CELL_SAMPLE_MAX_FRACTION - CELL_SAMPLE_MIN_FRACTION);
                int x = clamp(Math.round(cell.left + cell.width() * fx), 0, bitmap.getWidth() - 1);
                buckets.add(bitmap.getPixel(x, y));
                total++;
            }
        }

        int bestBucket = buckets.bestBucket();
        if (bestBucket >= 0) {
            return buckets.averageColor(bestBucket);
        }
        int center = bitmap.getPixel(
                clamp(Math.round(cell.centerX()), 0, bitmap.getWidth() - 1),
                clamp(Math.round(cell.centerY()), 0, bitmap.getHeight() - 1)
        );
        Log.i(TAG, "Cell background sampling fell back to center pixel after " + total + " samples.");
        return Color.rgb(Color.red(center), Color.green(center), Color.blue(center));
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

    private static final class ColorBuckets {
        final int[] counts = new int[COLOR_BUCKET_COUNT];
        final int[] reds = new int[COLOR_BUCKET_COUNT];
        final int[] greens = new int[COLOR_BUCKET_COUNT];
        final int[] blues = new int[COLOR_BUCKET_COUNT];

        void clear() {
            Arrays.fill(counts, 0);
            Arrays.fill(reds, 0);
            Arrays.fill(greens, 0);
            Arrays.fill(blues, 0);
        }

        void add(int color) {
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            int bucket = ((red >> COLOR_BUCKET_CHANNEL_SHIFT) << 8)
                    | ((green >> COLOR_BUCKET_CHANNEL_SHIFT) << 4)
                    | (blue >> COLOR_BUCKET_CHANNEL_SHIFT);
            counts[bucket]++;
            reds[bucket] += red;
            greens[bucket] += green;
            blues[bucket] += blue;
        }

        int bestBucket() {
            int bestBucket = -1;
            int bestCount = 0;
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] > bestCount) {
                    bestCount = counts[i];
                    bestBucket = i;
                }
            }
            return bestBucket;
        }

        int averageColor(int bucket) {
            int count = Math.max(1, counts[bucket]);
            return Color.rgb(reds[bucket] / count, greens[bucket] / count, blues[bucket] / count);
        }
    }

    private static final class Edge {
        final int a;
        final int b;
        final float weight;

        Edge(int a, int b, float weight) {
            this.a = a;
            this.b = b;
            this.weight = weight;
        }
    }

    private static final class UnionFind {
        final int[] parent;
        final byte[] rank;
        int components;

        UnionFind(int count) {
            parent = new int[count];
            rank = new byte[count];
            components = count;
            for (int i = 0; i < count; i++) {
                parent[i] = i;
            }
        }

        int find(int value) {
            int root = value;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[value] != value) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }

        boolean union(int a, int b) {
            int firstRoot = find(a);
            int secondRoot = find(b);
            if (firstRoot == secondRoot) {
                return false;
            }
            if (rank[firstRoot] < rank[secondRoot]) {
                parent[firstRoot] = secondRoot;
            } else if (rank[firstRoot] > rank[secondRoot]) {
                parent[secondRoot] = firstRoot;
            } else {
                parent[secondRoot] = firstRoot;
                rank[firstRoot]++;
            }
            components--;
            return true;
        }
    }
}
