package com.shawngn123.meowdokuoverlaysolver;

import java.util.ArrayList;
import java.util.List;

public final class PuzzlePipeline {
    private final HudBoardSizeDetector hudBoardSizeDetector = new HudBoardSizeDetector();
    private final BoardDetector boardDetector = new BoardDetector();
    private final RegionDetector regionDetector = new RegionDetector();
    private final PuzzleReader puzzleReader = new PuzzleReader();
    private final PuzzleSolver puzzleSolver = new PuzzleSolver();

    public AnalysisResult analyze(ArgbImage image) {
        if (image == null) {
            return result(0, 0, null, null, null, null, null, null, "Screenshot is unavailable.");
        }

        HudBoardSizeDetector.Result hudDetection = hudBoardSizeDetector.detect(image);
        if (hudDetection == null || !hudDetection.isSuccess()) {
            return result(image.width, image.height, hudDetection, null, null, null, null, null, "Unable to determine board size.");
        }

        return analyze(image, hudDetection, hudDetection.detectedValue);
    }

    public AnalysisResult analyze(ArgbImage image, int boardSize) {
        if (image == null) {
            return result(0, 0, null, null, null, null, null, null, "Screenshot is unavailable.");
        }
        if (!HudBoardSizeDetector.isSupportedBoardSize(boardSize)) {
            return result(image.width, image.height, null, null, null, null, null, null, "Unsupported forced board size " + boardSize + ".");
        }
        return analyze(image, null, boardSize);
    }

    private AnalysisResult analyze(ArgbImage image, HudBoardSizeDetector.Result hudDetection, int boardSize) {
        BoardGeometry board = boardDetector.detect(image, boardSize);
        if (board == null) {
            return result(image.width, image.height, hudDetection, null, null, null, null, null, "Puzzle board was not found.");
        }
        String boardError = boardValidationError(board);
        if (boardError != null) {
            return result(image.width, image.height, hudDetection, board, null, null, null, null, "Puzzle board is invalid: " + boardError);
        }

        RegionDetector.Result detectedRegions = regionDetector.detect(image, board);
        RegionMap regions = detectedRegions.regions;
        if (!detectedRegions.isSuccess()) {
            String reason = detectedRegions.failureReason == null ? "Puzzle regions could not be read." : detectedRegions.failureReason;
            return result(image.width, image.height, hudDetection, board, regions, null, null, null, "Puzzle regions could not be read: " + reason);
        }

        PuzzleModel model = puzzleReader.read(image, board, regions);
        if (model == null) {
            return result(image.width, image.height, hudDetection, board, regions, null, null, null, "Puzzle reader failed.");
        }
        String modelError = model.validationError();
        if (modelError != null) {
            return result(image.width, image.height, hudDetection, board, regions, model, null, null, "Puzzle is not ready to solve: " + modelError);
        }

        PuzzleSolver.Result solved = puzzleSolver.solve(model);
        if (solved.solutionCount == 0) {
            return result(image.width, image.height, hudDetection, board, regions, model, solved, null, "No solution.");
        }
        if (solved.solutionCount > 1) {
            return result(image.width, image.height, hudDetection, board, regions, model, solved, null, "Multiple solutions.");
        }

        List<TouchTarget> targets = touchTargets(board, model.occupied, solved.columns);
        return result(image.width, image.height, hudDetection, board, regions, model, solved, targets, null);
    }

    private List<TouchTarget> touchTargets(BoardGeometry board, boolean[][] occupied, int[] solutionColumns) {
        List<TouchTarget> targets = new ArrayList<>();
        if (board == null || occupied == null || solutionColumns == null) return targets;
        for (int row = 0; row < solutionColumns.length; row++) {
            int column = solutionColumns[row];
            if (column < 0 || column >= board.columns) continue;
            if (!occupied[row][column]) {
                targets.add(new TouchTarget(row, column, board.centerX(column), board.centerY(row)));
            }
        }
        return targets;
    }

    private String boardValidationError(BoardGeometry board) {
        if (board == null) {
            return "Board is missing.";
        }
        if (board.rows <= 0) {
            return "Row count must be positive; found " + board.rows + ".";
        }
        if (board.columns <= 0) {
            return "Column count must be positive; found " + board.columns + ".";
        }
        if (board.rows != board.columns) {
            return "Board must be square; rows=" + board.rows + ", columns=" + board.columns + ".";
        }
        if (!HudBoardSizeDetector.isSupportedBoardSize(board.rows)) {
            return "Detected unsupported board size " + board.rows + ".";
        }
        long expectedCells = (long) board.rows * board.rows;
        if (board.cellCount() != expectedCells) {
            return "CellCount " + board.cellCount() + " does not equal BoardSize squared " + expectedCells + ".";
        }
        if (board.xLines == null || board.xLines.length != board.columns + 1) {
            int found = board.xLines == null ? 0 : board.xLines.length;
            return "Expected " + (board.columns + 1) + " vertical grid lines, found " + found + ".";
        }
        if (board.yLines == null || board.yLines.length != board.rows + 1) {
            int found = board.yLines == null ? 0 : board.yLines.length;
            return "Expected " + (board.rows + 1) + " horizontal grid lines, found " + found + ".";
        }
        return null;
    }

    private AnalysisResult result(
            int imageWidth,
            int imageHeight,
            HudBoardSizeDetector.Result hudDetection,
            BoardGeometry board,
            RegionMap regions,
            PuzzleModel model,
            PuzzleSolver.Result solution,
            List<TouchTarget> touchTargets,
            String failureReason
    ) {
        return new AnalysisResult(imageWidth, imageHeight, hudDetection, board, regions, model, solution, touchTargets, failureReason);
    }
}
