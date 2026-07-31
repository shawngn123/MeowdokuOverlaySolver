package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.util.Log;

final class PuzzlePipeline {
    private static final String TAG = "MeowdokuSolver";
    private final BoardDetector boardDetector = new BoardDetector();
    private final RegionDetector regionDetector = new RegionDetector();
    private final PuzzleReader puzzleReader = new PuzzleReader();

    interface Listener {
        void onDebug(DebugData data);
        void onFinished(String failureReason);
    }

    void run(Bitmap bitmap, Listener listener) {
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

        RegionMap regions;
        try {
            regions = regionDetector.detect(bitmap, board);
        } catch (RuntimeException error) {
            Log.e(TAG, "Region detection failed", error);
            listener.onFinished("Region detection failed");
            return;
        }
        if (regions == null || !regions.isValid()) {
            listener.onFinished("Region detection failed");
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
        if (model == null || !model.isValid()) {
            listener.onFinished("Puzzle state invalid");
            return;
        }

        listener.onDebug(new DebugData(board, regions, model.occupied, null));
        listener.onFinished(null);
    }
}
