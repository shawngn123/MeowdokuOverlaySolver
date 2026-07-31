package com.shawngn123.meowdokuoverlaysolver;

final class DebugData {
    final BoardGeometry board;
    final RegionMap regions;
    final boolean[][] cats;
    final int[] solutionColumns;

    DebugData(BoardGeometry board, RegionMap regions, boolean[][] cats, int[] solutionColumns) {
        this.board = board;
        this.regions = regions;
        this.cats = cats;
        this.solutionColumns = solutionColumns;
    }
}
