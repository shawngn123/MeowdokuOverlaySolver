package com.shawngn123.meowdokuoverlaysolver;

final class RegionMap {
    private static final int MIN_SIZE = 4;
    private static final int MAX_SIZE = 12;
    private static final int[] ROW_OFFSETS = {-1, 1, 0, 0};
    private static final int[] COLUMN_OFFSETS = {0, 0, -1, 1};

    final int size;
    final int[][] cells;
    final int regionCount;
    final int[][] backgroundColors;
    final int[] regionCellCounts;

    RegionMap(int size, int[][] cells, int regionCount, int[][] backgroundColors) {
        this.size = size;
        this.regionCount = regionCount;
        this.cells = copy(cells);
        this.backgroundColors = copy(backgroundColors);
        this.regionCellCounts = countRegions(this.cells, regionCount);
    }

    boolean isValid() {
        return validationError() == null;
    }

    String validationError() {
        return validationError(size, size);
    }

    String validationError(int expectedRows, int expectedColumns) {
        if (expectedRows != expectedColumns) {
            return "Board must be square; rows=" + expectedRows + ", columns=" + expectedColumns + ".";
        }
        if (size != expectedRows) {
            return "Region map size " + size + " does not match board size " + expectedRows + ".";
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            return "Unsupported board size " + size + ".";
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
        int[] counts = new int[regionCount];
        for (int row = 0; row < size; row++) {
            if (cells[row] == null) {
                return "Region row " + row + " is missing.";
            }
            if (cells[row].length != expectedColumns) {
                return "Expected " + expectedColumns + " region columns in row " + row + ", found " + cells[row].length + ".";
            }
            for (int column = 0; column < size; column++) {
                int region = cells[row][column];
                if (region < 0) {
                    return "Cell (" + row + "," + column + ") has no region.";
                }
                if (region >= regionCount) {
                    return "Cell (" + row + "," + column + ") has invalid RegionID " + region + ".";
                }
                counts[region]++;
            }
        }
        for (int region = 0; region < regionCount; region++) {
            if (counts[region] == 0) {
                return "Region " + region + " contains 0 cells.";
            }
        }
        return disconnectedRegionError(counts);
    }

    int regionId(int row, int column) {
        return cells[row][column];
    }

    int backgroundColor(int row, int column) {
        return backgroundColors[row][column];
    }

    boolean hasBackgroundColors() {
        return backgroundColorsError(size, size) == null;
    }

    String backgroundColorsError(int expectedRows, int expectedColumns) {
        if (backgroundColors == null) {
            return "Sampled background colors are missing.";
        }
        if (backgroundColors.length != expectedRows) {
            return "Expected " + expectedRows + " sampled background rows, found " + backgroundColors.length + ".";
        }
        for (int row = 0; row < expectedRows; row++) {
            if (backgroundColors[row] == null) {
                return "Sampled background row " + row + " is missing.";
            }
            if (backgroundColors[row].length != expectedColumns) {
                return "Expected " + expectedColumns + " sampled background columns in row " + row + ", found " + backgroundColors[row].length + ".";
            }
        }
        return null;
    }

    private String disconnectedRegionError(int[] counts) {
        boolean[][] visited = new boolean[size][size];
        int[] queueRows = new int[size * size];
        int[] queueColumns = new int[size * size];

        for (int region = 0; region < regionCount; region++) {
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

            int head = 0;
            int tail = 0;
            int visitedCount = 0;
            visited[startRow][startColumn] = true;
            queueRows[tail] = startRow;
            queueColumns[tail] = startColumn;
            tail++;

            while (head < tail) {
                int row = queueRows[head];
                int column = queueColumns[head];
                head++;
                visitedCount++;

                for (int i = 0; i < ROW_OFFSETS.length; i++) {
                    int nextRow = row + ROW_OFFSETS[i];
                    int nextColumn = column + COLUMN_OFFSETS[i];
                    if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                        continue;
                    }
                    if (visited[nextRow][nextColumn] || cells[nextRow][nextColumn] != region) {
                        continue;
                    }
                    visited[nextRow][nextColumn] = true;
                    queueRows[tail] = nextRow;
                    queueColumns[tail] = nextColumn;
                    tail++;
                }
            }

            if (visitedCount != counts[region]) {
                return "Duplicate RegionID detected: Region " + region + " appears in disconnected groups.";
            }
        }
        return null;
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
}
