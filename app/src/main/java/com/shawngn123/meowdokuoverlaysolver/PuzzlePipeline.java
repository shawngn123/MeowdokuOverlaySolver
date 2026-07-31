package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.util.Log;

final class PuzzlePipeline {
    private static final String TAG = "MeowdokuSolver";
    private final BoardDetector boardDetector = new BoardDetector();
    private final RegionDetector regionDetector = new RegionDetector();
    private final PuzzleReader puzzleReader = new PuzzleReader();
    private final PuzzleSolver puzzleSolver = new PuzzleSolver();

    interface Listener {
        boolean isCancelled();
        void onDebug(DebugData data);
        void onBeforeGestures();
        void onFinished(String failureReason);
    }

    void run(Bitmap bitmap, Listener listener) {
        if (listener.isCancelled()) return;
        BoardGeometry board;
        try {
            board = boardDetector.detect(bitmap);
        } catch (RuntimeException error) {
            Log.e(TAG, "Board detection failed", error);
            listener.onFinished("Board detection failed");
            return;
        }
        if (board == null) {
            Log.i(TAG, "Puzzle not found");
            listener.onFinished("Puzzle not found");
            return;
        }
        String boardError = boardValidationError(board);
        if (boardError != null) {
            Log.i(TAG, "Board validation failed: " + boardError);
            listener.onFinished("Puzzle board is invalid: " + boardError);
            return;
        }
        if (listener.isCancelled()) return;
        listener.onDebug(new DebugData(board, null, null, null));

        RegionDetector.Result detectedRegions;
        try {
            detectedRegions = regionDetector.detect(bitmap, board);
        } catch (RuntimeException error) {
            Log.e(TAG, "Region detector crashed before producing RegionIDs", error);
            listener.onFinished("Puzzle regions could not be read.");
            return;
        }
        if (!detectedRegions.isSuccess()) {
            listener.onFinished("Puzzle regions could not be read: " + detectedRegions.failureReason);
            return;
        }
        RegionMap regions = detectedRegions.regions;
        if (listener.isCancelled()) return;
        listener.onDebug(new DebugData(board, regions, null, null));

        String regionError = regions.validationError(board.rows, board.columns);
        if (regionError != null) {
            Log.i(TAG, "Region validation failed: " + regionError);
            listener.onFinished("Puzzle regions are invalid: " + regionError);
            return;
        }
        String backgroundError = regions.backgroundColorsError(board.rows, board.columns);
        if (backgroundError != null) {
            Log.i(TAG, "Cell background validation failed: " + backgroundError);
            listener.onFinished("Puzzle cell backgrounds could not be read: " + backgroundError);
            return;
        }

        PuzzleModel model;
        try {
            model = puzzleReader.read(bitmap, board, regions);
        } catch (RuntimeException error) {
            Log.e(TAG, "Puzzle reader failed", error);
            listener.onFinished("Puzzle reader failed");
            return;
        }
        String solveInputError = solveInputValidationError(board, regions, model);
        if (solveInputError != null) {
            Log.i(TAG, "Solve validation failed: " + solveInputError);
            listener.onFinished("Puzzle is not ready to solve: " + solveInputError);
            return;
        }
        if (listener.isCancelled()) return;
        listener.onDebug(new DebugData(board, regions, model.occupied, null));

        PuzzleSolver.Result solved;
        try {
            solved = puzzleSolver.solve(model);
        } catch (RuntimeException error) {
            Log.e(TAG, "Solver failed", error);
            listener.onFinished("Solver failed before it could finish.");
            return;
        }
        if (listener.isCancelled()) return;
        if (solved.solutionCount == 0) {
            listener.onFinished("No solution");
            return;
        }
        if (solved.solutionCount > 1) {
            listener.onFinished("Multiple solutions");
            return;
        }

        listener.onDebug(new DebugData(board, regions, model.occupied, solved.columns));
        listener.onBeforeGestures();
        if (listener.isCancelled()) return;
        SolverAccessibilityService.tapMissing(
                board,
                model.occupied,
                solved.columns,
                (success, reason) -> {
                    if (!listener.isCancelled()) {
                        listener.onFinished(success ? null : reason);
                    }
                }
        );
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

    private String solveInputValidationError(BoardGeometry board, RegionMap regions, PuzzleModel model) {
        String boardError = boardValidationError(board);
        if (boardError != null) {
            return boardError;
        }
        if (regions == null) {
            return "Regions are missing.";
        }
        String regionError = regions.validationError(board.rows, board.columns);
        if (regionError != null) {
            return regionError;
        }
        if (model == null) {
            return "Puzzle state is missing.";
        }
        return model.validationError();
    }
}
