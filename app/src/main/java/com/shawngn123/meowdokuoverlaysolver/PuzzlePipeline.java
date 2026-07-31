package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.util.Log;

final class PuzzlePipeline {
    private static final String TAG = "MeowdokuSolver";
    private final BoardDetector boardDetector = new BoardDetector();
    private final RegionDetector regionDetector = new RegionDetector();

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

        listener.onDebug(new DebugData(board, regions, null, null));
        listener.onFinished(null);
    }
}
