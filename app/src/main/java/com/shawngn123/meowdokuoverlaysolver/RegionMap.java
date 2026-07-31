package com.shawngn123.meowdokuoverlaysolver;

final class RegionMap {
    final int size;
    final int[][] cells;
    final int regionCount;
    final float confidence;
    final int[][] sampledColors;

    RegionMap(int size, int[][] cells, int regionCount, float confidence, int[][] sampledColors) {
        this.size = size;
        this.cells = cells;
        this.regionCount = regionCount;
        this.confidence = confidence;
        this.sampledColors = sampledColors;
    }

    boolean isValid() {
        if (size < 4 || size > 12 || regionCount != size || cells.length != size) return false;
        boolean[] seen = new boolean[regionCount];
        for (int row = 0; row < size; row++) {
            if (cells[row] == null || cells[row].length != size) return false;
            for (int column = 0; column < size; column++) {
                int region = cells[row][column];
                if (region < 0 || region >= regionCount) return false;
                seen[region] = true;
            }
        }
        for (boolean value : seen) if (!value) return false;
        return true;
    }
}
