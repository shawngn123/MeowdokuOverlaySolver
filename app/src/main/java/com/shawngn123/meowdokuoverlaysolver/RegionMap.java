package com.shawngn123.meowdokuoverlaysolver;

final class RegionMap {
    final int size;
    final int[][] cells;
    final int regionCount;
    final int[][] sampledColors;
    final int[] regionCellCounts;

    RegionMap(int size, int[][] cells, int regionCount, int[][] sampledColors) {
        this.size = size;
        this.regionCount = regionCount;
        this.cells = copy(cells);
        this.sampledColors = copy(sampledColors);
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
        if (size <= 0) {
            return "BoardSize must be greater than 0; found " + size + ".";
        }
        long totalCells = (long) expectedRows * expectedColumns;
        long expectedCells = (long) size * size;
        if (totalCells != expectedCells) {
            return "Total cells " + totalCells + " does not equal BoardSize squared " + expectedCells + ".";
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
            if (counts[region] != expectedRows) {
                return "Region " + region + " contains " + counts[region] + " cells; expected " + expectedRows + ".";
            }
        }
        return null;
    }

    String regionCountsDiagnostic() {
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

    int regionId(int row, int column) {
        return cells[row][column];
    }

    int sampledColor(int row, int column) {
        return sampledColors[row][column];
    }

    boolean hasSampledColors() {
        return sampledColorsError(size, size) == null;
    }

    String sampledColorsError(int expectedRows, int expectedColumns) {
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
