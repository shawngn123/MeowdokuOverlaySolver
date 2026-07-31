package com.shawngn123.meowdokuoverlaysolver;

final class PuzzleModel {
    final int size;
    final RegionMap regions;
    final boolean[][] occupied;
    final boolean[][] locked;
    final float[][] catConfidence;

    PuzzleModel(int size, RegionMap regions, boolean[][] occupied, boolean[][] locked, float[][] catConfidence) {
        this.size = size;
        this.regions = regions;
        this.occupied = occupied;
        this.locked = locked;
        this.catConfidence = catConfidence;
    }

    boolean isValid() {
        if (size < 4 || size > 12 || regions == null || !regions.isValid()) return false;
        if (occupied.length != size || locked.length != size || catConfidence.length != size) return false;
        boolean[] columns = new boolean[size];
        boolean[] regionUsed = new boolean[size];
        int previousColumn = -99;
        for (int row = 0; row < size; row++) {
            if (occupied[row].length != size || locked[row].length != size || catConfidence[row].length != size) return false;
            int rowColumn = -1;
            for (int column = 0; column < size; column++) {
                if (locked[row][column] && !occupied[row][column]) return false;
                if (!occupied[row][column]) continue;
                if (rowColumn >= 0 || columns[column]) return false;
                int region = regions.cells[row][column];
                if (regionUsed[region]) return false;
                rowColumn = column;
                columns[column] = true;
                regionUsed[region] = true;
            }
            if (rowColumn >= 0 && previousColumn >= 0 && Math.abs(rowColumn - previousColumn) == 1) return false;
            if (rowColumn >= 0) previousColumn = rowColumn;
            else previousColumn = -99;
        }
        return true;
    }

    int occupiedCount() {
        int count = 0;
        for (boolean[] row : occupied) for (boolean value : row) if (value) count++;
        return count;
    }
}
