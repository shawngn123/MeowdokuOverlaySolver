package com.shawngn123.meowdokuoverlaysolver;

public final class PuzzleModel {
    public final int size;
    public final RegionMap regions;
    public final boolean[][] occupied;
    public final boolean[][] locked;
    public final float[][] catConfidence;

    public PuzzleModel(int size, RegionMap regions, boolean[][] occupied, boolean[][] locked, float[][] catConfidence) {
        this.size = size;
        this.regions = regions;
        this.occupied = copy(occupied);
        this.locked = copy(locked);
        this.catConfidence = copy(catConfidence);
    }

    public boolean isValid() {
        return validationError() == null;
    }

    public String validationError() {
        if (size <= 0) {
            return "BoardSize must be greater than 0; found " + size + ".";
        }
        if (regions == null) {
            return "Regions are missing.";
        }
        String regionError = regions.validationError(size, size);
        if (regionError != null) {
            return regionError;
        }
        if (occupied == null) {
            return "Occupied cell map is missing.";
        }
        if (locked == null) {
            return "Locked cell map is missing.";
        }
        if (catConfidence == null) {
            return "Cat confidence map is missing.";
        }
        if (occupied.length != size) {
            return "Expected " + size + " occupied rows, found " + occupied.length + ".";
        }
        if (locked.length != size) {
            return "Expected " + size + " locked rows, found " + locked.length + ".";
        }
        if (catConfidence.length != size) {
            return "Expected " + size + " cat confidence rows, found " + catConfidence.length + ".";
        }

        boolean[] columns = new boolean[size];
        boolean[] regionUsed = new boolean[size];
        int previousColumn = -99;
        int occupiedCount = 0;
        for (int row = 0; row < size; row++) {
            if (occupied[row] == null) {
                return "Occupied row " + row + " is missing.";
            }
            if (locked[row] == null) {
                return "Locked row " + row + " is missing.";
            }
            if (catConfidence[row] == null) {
                return "Cat confidence row " + row + " is missing.";
            }
            if (occupied[row].length != size) {
                return "Expected " + size + " occupied columns in row " + row + ", found " + occupied[row].length + ".";
            }
            if (locked[row].length != size) {
                return "Expected " + size + " locked columns in row " + row + ", found " + locked[row].length + ".";
            }
            if (catConfidence[row].length != size) {
                return "Expected " + size + " cat confidence columns in row " + row + ", found " + catConfidence[row].length + ".";
            }
            int rowColumn = -1;
            for (int column = 0; column < size; column++) {
                if (locked[row][column] && !occupied[row][column]) {
                    return "Cell (" + row + "," + column + ") is locked but not occupied.";
                }
                if (!occupied[row][column]) continue;
                occupiedCount++;
                if (rowColumn >= 0) {
                    return "Row " + row + " contains more than one cat.";
                }
                if (columns[column]) {
                    return "Column " + column + " contains more than one cat.";
                }
                int region = regions.regionId(row, column);
                if (regionUsed[region]) {
                    return "Region " + region + " contains more than one cat.";
                }
                rowColumn = column;
                columns[column] = true;
                regionUsed[region] = true;
            }
            if (rowColumn >= 0 && previousColumn >= 0 && Math.abs(rowColumn - previousColumn) == 1) {
                return "Cats in adjacent rows touch diagonally.";
            }
            if (rowColumn >= 0) previousColumn = rowColumn;
            else previousColumn = -99;
        }
        if (occupiedCount > size) {
            return "Expected at most " + size + " cats, found " + occupiedCount + ".";
        }
        return null;
    }

    public int occupiedCount() {
        int count = 0;
        for (boolean[] row : occupied) {
            for (boolean value : row) if (value) count++;
        }
        return count;
    }

    private static boolean[][] copy(boolean[][] source) {
        if (source == null) return null;
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private static float[][] copy(float[][] source) {
        if (source == null) return null;
        float[][] copy = new float[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }
}
