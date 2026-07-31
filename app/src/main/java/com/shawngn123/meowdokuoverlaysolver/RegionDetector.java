package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RegionDetector {
    RegionMap detect(Bitmap bitmap, BoardGeometry board) {
        if (bitmap == null || board == null || board.rows != board.columns) return null;
        int n = board.rows;
        int[][] colors = new int[n][n];
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) colors[row][column] = sampleCellColor(bitmap, board.cellRect(row, column));
        }

        List<Edge> edges = new ArrayList<>();
        for (int row = 0; row < n; row++) for (int column = 0; column < n; column++) {
            int cell = row * n + column;
            if (column + 1 < n) edges.add(new Edge(cell, cell + 1, distance(colors[row][column], colors[row][column + 1])));
            if (row + 1 < n) edges.add(new Edge(cell, cell + n, distance(colors[row][column], colors[row + 1][column])));
        }
        edges.sort(Comparator.comparingDouble(edge -> edge.weight));

        UnionFind union = new UnionFind(n * n);
        float lastMerged = 0f;
        float nextWeight = 0f;
        int index = 0;
        while (union.components > n && index < edges.size()) {
            Edge edge = edges.get(index++);
            if (union.union(edge.a, edge.b)) lastMerged = edge.weight;
        }
        if (union.components != n) return null;
        while (index < edges.size()) {
            Edge edge = edges.get(index++);
            if (union.find(edge.a) != union.find(edge.b)) { nextWeight = edge.weight; break; }
        }

        Map<Integer, Integer> ids = new HashMap<>();
        int[][] map = new int[n][n];
        int nextId = 0;
        for (int row = 0; row < n; row++) for (int column = 0; column < n; column++) {
            int root = union.find(row * n + column);
            Integer id = ids.get(root);
            if (id == null) { id = nextId++; ids.put(root, id); }
            map[row][column] = id;
        }
        float confidence = nextWeight <= 0f ? 0f : Math.max(0f, Math.min(1f, (nextWeight - lastMerged) / Math.max(4f, nextWeight)));
        RegionMap result = new RegionMap(n, map, nextId, confidence, colors);
        return result.isValid() ? result : null;
    }

    private int sampleCellColor(Bitmap bitmap, RectF cell) {
        int[] samples = new int[36];
        int count = 0;
        float[] anchors = {0.22f, 0.34f, 0.66f, 0.78f};
        float radiusX = Math.max(1f, cell.width() * 0.035f);
        float radiusY = Math.max(1f, cell.height() * 0.035f);
        for (float ax : anchors) {
            for (float ay : anchors) {
                float cx = cell.left + cell.width() * ax;
                float cy = cell.top + cell.height() * ay;
                for (int sy = -1; sy <= 1; sy++) for (int sx = -1; sx <= 1; sx++) {
                    int x = clamp(Math.round(cx + sx * radiusX), 0, bitmap.getWidth() - 1);
                    int y = clamp(Math.round(cy + sy * radiusY), 0, bitmap.getHeight() - 1);
                    if (count < samples.length) samples[count++] = bitmap.getPixel(x, y);
                }
                if (count >= samples.length) break;
            }
            if (count >= samples.length) break;
        }
        int[] red = new int[count], green = new int[count], blue = new int[count];
        for (int i = 0; i < count; i++) {
            red[i] = Color.red(samples[i]);
            green[i] = Color.green(samples[i]);
            blue[i] = Color.blue(samples[i]);
        }
        java.util.Arrays.sort(red);
        java.util.Arrays.sort(green);
        java.util.Arrays.sort(blue);
        int middle = count / 2;
        return Color.rgb(red[middle], green[middle], blue[middle]);
    }

    private float distance(int first, int second) {
        float r1 = Color.red(first) / 255f, g1 = Color.green(first) / 255f, b1 = Color.blue(first) / 255f;
        float r2 = Color.red(second) / 255f, g2 = Color.green(second) / 255f, b2 = Color.blue(second) / 255f;
        float max1 = Math.max(r1, Math.max(g1, b1)), min1 = Math.min(r1, Math.min(g1, b1));
        float max2 = Math.max(r2, Math.max(g2, b2)), min2 = Math.min(r2, Math.min(g2, b2));
        float chroma = Math.abs((max1 - min1) - (max2 - min2));
        float luminance = Math.abs((r1 * 0.2126f + g1 * 0.7152f + b1 * 0.0722f) - (r2 * 0.2126f + g2 * 0.7152f + b2 * 0.0722f));
        float rgb = (Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)) / 3f;
        return (rgb * 0.58f + luminance * 0.27f + chroma * 0.15f) * 255f;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class Edge {
        final int a, b;
        final float weight;
        Edge(int a, int b, float weight) { this.a = a; this.b = b; this.weight = weight; }
    }

    private static final class UnionFind {
        final int[] parent;
        final byte[] rank;
        int components;
        UnionFind(int count) {
            parent = new int[count]; rank = new byte[count]; components = count;
            for (int i = 0; i < count; i++) parent[i] = i;
        }
        int find(int value) {
            int root = value;
            while (parent[root] != root) root = parent[root];
            while (parent[value] != value) { int next = parent[value]; parent[value] = root; value = next; }
            return root;
        }
        boolean union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra == rb) return false;
            if (rank[ra] < rank[rb]) parent[ra] = rb;
            else if (rank[ra] > rank[rb]) parent[rb] = ra;
            else { parent[rb] = ra; rank[ra]++; }
            components--;
            return true;
        }
    }
}
