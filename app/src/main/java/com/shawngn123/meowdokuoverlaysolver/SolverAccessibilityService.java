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
        List<PointF> points = new ArrayList<>();
        for (int row = 0; row < solutionColumns.length; row++) {
            int column = solutionColumns[row];
            if (!occupied[row][column]) points.add(new PointF(board.centerX(column), board.centerY(row)));
        }
        service.startSequence(points, completion);
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
        main.post(() -> dispatchNext(id, points, 0, completion));
    }

    private void dispatchNext(int id, List<PointF> points, int index, Completion completion) {
        if (id != sequenceId || instance != this) {
            completion.complete(false, "Accessibility gesture sequence was interrupted");
            return;
        }
        if (index >= points.size()) {
            completion.complete(true, null);
            return;
        }

        PointF point = points.get(index);
        Path first = new Path();
        first.moveTo(point.x, point.y);
        Path second = new Path();
        second.moveTo(point.x, point.y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(first, 0, 24))
                .addStroke(new GestureDescription.StrokeDescription(second, 78, 24))
                .build();

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                dispatchNext(id, points, index + 1, completion);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                completion.complete(false, "Accessibility gesture was cancelled");
            }
        }, main);
        if (!accepted) completion.complete(false, "Accessibility rejected the gesture");
    }
}
