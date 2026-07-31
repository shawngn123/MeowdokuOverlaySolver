package com.shawngn123.meowdokuoverlaysolver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class SolverAccessibilityService extends AccessibilityService {
    interface Completion {
        void complete(boolean success, String reason);
    }

    private static final int CELLS_PER_BATCH = 4;
    private static final long CELL_WINDOW_MS = 68;
    private static volatile SolverAccessibilityService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int sequenceId;

    static boolean isConnected() { return instance != null; }

    static void tapMissing(BoardGeometry board, boolean[][] occupied, int[] solutionColumns, Completion completion) {
        SolverAccessibilityService service = instance;
        if (service == null) {
            completion.complete(false, "Accessibility service is not enabled");
            return;
        }
        if (board == null || occupied == null || solutionColumns == null) {
            completion.complete(false, "Puzzle is not ready for accessibility taps");
            return;
        }
        if (occupied.length != solutionColumns.length || board.rows != solutionColumns.length) {
            completion.complete(false, "Puzzle dimensions changed before tapping");
            return;
        }
        List<PointF> points = new ArrayList<>();
        for (int row = 0; row < solutionColumns.length; row++) {
            int column = solutionColumns[row];
            if (column < 0 || column >= board.columns || occupied[row] == null || occupied[row].length != board.columns) {
                completion.complete(false, "Puzzle dimensions changed before tapping");
                return;
            }
            if (!occupied[row][column]) points.add(new PointF(board.centerX(column), board.centerY(row)));
        }
        service.startSequence(points, completion);
    }

    static void cancelActiveGestures() {
        SolverAccessibilityService service = instance;
        if (service != null) {
            service.sequenceId++;
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        if (instance == this) instance = null;
        sequenceId++;
        return super.onUnbind(intent);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { sequenceId++; }

    private void startSequence(List<PointF> points, Completion completion) {
        int id = ++sequenceId;
        main.post(() -> dispatchBatch(id, points, 0, completion));
    }

    private void dispatchBatch(int id, List<PointF> points, int index, Completion completion) {
        if (id != sequenceId || instance != this) {
            completion.complete(false, "Accessibility gesture sequence was interrupted");
            return;
        }
        if (index >= points.size()) {
            completion.complete(true, null);
            return;
        }

        int end = Math.min(points.size(), index + CELLS_PER_BATCH);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        for (int i = index; i < end; i++) {
            PointF point = points.get(i);
            long start = (i - index) * CELL_WINDOW_MS;
            Path first = new Path();
            first.moveTo(point.x, point.y);
            Path second = new Path();
            second.moveTo(point.x, point.y);
            builder.addStroke(new GestureDescription.StrokeDescription(first, start, 16));
            builder.addStroke(new GestureDescription.StrokeDescription(second, start + 46, 16));
        }

        boolean accepted = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                dispatchBatch(id, points, end, completion);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                if (id == sequenceId) {
                    sequenceId++;
                    completion.complete(false, "Accessibility gesture was cancelled");
                }
            }
        }, main);
        if (!accepted && id == sequenceId) {
            sequenceId++;
            completion.complete(false, "Accessibility rejected the gesture");
        }
    }

}
