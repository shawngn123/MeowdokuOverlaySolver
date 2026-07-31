package com.shawngn123.meowdokuoverlaysolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnalysisResult {
    public final int imageWidth;
    public final int imageHeight;
    public final BoardGeometry board;
    public final RegionMap regions;
    public final PuzzleModel model;
    public final PuzzleSolver.Result solution;
    public final List<TouchTarget> touchTargets;
    public final String failureReason;

    AnalysisResult(
            int imageWidth,
            int imageHeight,
            BoardGeometry board,
            RegionMap regions,
            PuzzleModel model,
            PuzzleSolver.Result solution,
            List<TouchTarget> touchTargets,
            String failureReason
    ) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.board = board;
        this.regions = regions;
        this.model = model;
        this.solution = solution;
        this.touchTargets = touchTargets == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(touchTargets));
        this.failureReason = failureReason;
    }

    public boolean isSuccess() {
        return failureReason == null && solution != null && solution.hasUniqueSolution();
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Image: ").append(imageWidth).append('x').append(imageHeight);
        if (board != null) {
            builder.append("\nBoard: ").append(board.rows).append('x').append(board.columns)
                    .append(" confidence=").append(String.format(java.util.Locale.US, "%.3f", board.confidence))
                    .append(" bounds=").append(board.bounds);
        }
        if (regions != null) {
            builder.append("\nRegions: ").append(regions.regionCount)
                    .append(' ').append(java.util.Arrays.toString(regions.regionCellCounts));
        }
        if (model != null) {
            builder.append("\nExisting cats: ").append(model.occupiedCount());
        }
        if (solution != null) {
            builder.append("\nSolutions: ").append(solution.solutionCount);
            if (solution.columns != null) {
                builder.append("\nSolution columns: ").append(java.util.Arrays.toString(solution.columns));
            }
        }
        builder.append("\nTouch targets: ").append(touchTargets.size());
        if (failureReason != null) {
            builder.append("\nFailure: ").append(failureReason);
        }
        return builder.toString();
    }
}
