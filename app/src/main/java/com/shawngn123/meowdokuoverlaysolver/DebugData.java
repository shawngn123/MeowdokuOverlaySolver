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

    static DebugData from(AnalysisResult result) {
        if (result == null) return new DebugData(null, null, null, null);
        return new DebugData(
                result.board,
                result.regions,
                result.model == null ? null : result.model.occupied,
                result.solution == null ? null : result.solution.columns
        );
    }
}
