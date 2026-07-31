package com.shawngn123.meowdokuoverlaysolver;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class RegionMap {
    public final int size;
    public final int[][] cells;
    public final int regionCount;
    public final int[][] sampledColors;
    public final int[] clusterColors;
    public final int[] regionCellCounts;

    public RegionMap(int size, int[][] cells, int regionCount, int[][] sampledColors, int[] clusterColors) {
        this.size = size;
        this.regionCount = regionCount;
        this.cells = copy(cells);
        this.sampledColors = copy(sampledColors);
        this.clusterColors = clusterColors == null ? null : clusterColors.clone();
        this.regionCellCounts = countRegions(this.cells, regionCount);
    }

    public boolean isValid() {
        return validationError() == null;
    }

    public String validationError() {
        return validationError(size, size);
    }

    public String validationError(int expectedRows, int expectedColumns) {
        if (expectedRows != expectedColumns) {
            return "Board must be square; rows=" + expectedRows + ", columns=" + expectedColumns + ".";
        }
        if (size != expectedRows) {
            return "Region map size " + size + " does not match board size " + expectedRows + ".";
        }
        if (size <= 0) {
            return "BoardSize must be greater than 0; found " + size + ".";
        }
        if (regionCount != expectedRows) {
            return "Expected " + expectedRows + " regions, found " + regionCount + ".";
        }
        if (cells == null) {
            return "Region map is missing.";
        }
        if (cells.length != expectedRows) {
            return "Expected " + expectedRows + " region rows, found " + cells.length + ".";
        }
        int total = 0;
        int[] counts = new int[regionCount];
        for (int row = 0; row < expectedRows; row++) {
            if (cells[row] == null) {
                return "Region row " + row + " is missing.";
            }
            if (cells[row].length != expectedColumns) {
                return "Expected " + expectedColumns + " region columns in row " + row + ", found " + cells[row].length + ".";
            }
            for (int column = 0; column < expectedColumns; column++) {
                int region = cells[row][column];
                if (region < 0) {
                    return "Cell (" + row + "," + column + ") has no region.";
                }
                if (region >= regionCount) {
                    return "Cell (" + row + "," + column + ") has invalid RegionID " + region + ".";
                }
                counts[region]++;
                total++;
            }
        }
        if (total != expectedRows * expectedColumns) {
            return "Total cells " + total + " does not equal board size squared " + (expectedRows * expectedColumns) + ".";
        }
        for (int region = 0; region < regionCount; region++) {
            if (counts[region] == 0) {
                return "Region " + region + " contains no cells.";
            }
            if (!isContiguous(region, counts[region])) {
                return "Region " + region + " is not contiguous.";
            }
        }
        return sampledColorsError(expectedRows, expectedColumns);
    }

    public String regionCountsDiagnostic() {
        StringBuilder diagnostic = new StringBuilder("Detected region counts:");
        if (regionCellCounts.length == 0) {
            diagnostic.append("\nRegion count matrix is empty.");
            return diagnostic.toString();
        }
        for (int region = 0; region < regionCellCounts.length; region++) {
            diagnostic.append("\nRegion ").append(region).append(": ").append(regionCellCounts[region]).append(" cells");
        }
        return diagnostic.toString();
    }

    public int regionId(int row, int column) {
        return cells[row][column];
    }

    public int sampledColor(int row, int column) {
        return sampledColors[row][column];
    }

    public int clusterColor(int region) {
        if (clusterColors == null || region < 0 || region >= clusterColors.length) {
            return 0xff000000;
        }
        return clusterColors[region];
    }

    public boolean hasSampledColors() {
        return sampledColorsError(size, size) == null;
    }

    public String sampledColorsError(int expectedRows, int expectedColumns) {
        if (sampledColors == null) {
            return "Sampled cell colors are missing.";
        }
        if (sampledColors.length != expectedRows) {
            return "Expected " + expectedRows + " sampled color rows, found " + sampledColors.length + ".";
        }
        for (int row = 0; row < expectedRows; row++) {
            if (sampledColors[row] == null) {
                return "Sampled color row " + row + " is missing.";
            }
            if (sampledColors[row].length != expectedColumns) {
                return "Expected " + expectedColumns + " sampled color columns in row " + row + ", found " + sampledColors[row].length + ".";
            }
        }
        return null;
    }

    public String compactRows() {
        StringBuilder builder = new StringBuilder();
        for (int row = 0; row < size; row++) {
            if (row > 0) builder.append('\n');
            for (int column = 0; column < size; column++) {
                if (column > 0) builder.append(' ');
                builder.append(cells[row][column]);
            }
        }
        return builder.toString();
    }

    private boolean isContiguous(int region, int expectedCount) {
        int startRow = -1;
        int startColumn = -1;
        for (int row = 0; row < size && startRow < 0; row++) {
            for (int column = 0; column < size; column++) {
                if (cells[row][column] == region) {
                    startRow = row;
                    startColumn = column;
                    break;
                }
            }
        }
        if (startRow < 0) return false;

        boolean[][] visited = new boolean[size][size];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startRow * size + startColumn);
        visited[startRow][startColumn] = true;
        int found = 0;
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        while (!queue.isEmpty()) {
            int packed = queue.removeFirst();
            int row = packed / size;
            int column = packed % size;
            found++;
            for (int i = 0; i < dr.length; i++) {
                int nextRow = row + dr[i];
                int nextColumn = column + dc[i];
                if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) continue;
                if (visited[nextRow][nextColumn] || cells[nextRow][nextColumn] != region) continue;
                visited[nextRow][nextColumn] = true;
                queue.add(nextRow * size + nextColumn);
            }
        }
        return found == expectedCount;
    }

    private static int[][] copy(int[][] source) {
        if (source == null) {
            return null;
        }
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private static int[] countRegions(int[][] cells, int regionCount) {
        int[] counts = new int[Math.max(0, regionCount)];
        if (cells == null) {
            return counts;
        }
        for (int[] row : cells) {
            if (row == null) {
                continue;
            }
            for (int region : row) {
                if (region >= 0 && region < counts.length) {
                    counts[region]++;
                }
            }
        }
        return counts;
    }

    @Override public String toString() {
        return "RegionMap{size=" + size
                + ", regionCount=" + regionCount
                + ", counts=" + Arrays.toString(regionCellCounts) + "}";
    }
}
