package com.shawngn123.meowdokuoverlaysolver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SolverAccessibilityService extends AccessibilityService {
    interface Completion {
        void complete(boolean success, String reason);
    }

    private static final int CELLS_PER_BATCH = 4;
    private static final long CELL_WINDOW_MS = 68;
    private static final Pattern CAT_COUNTER_PATTERN = Pattern.compile("(?<!\\d)(\\d+)\\s*/\\s*(\\d+)(?!\\d)");
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

    static Integer currentCatTarget() {
        SolverAccessibilityService service = instance;
        return service == null ? null : service.readCatTarget();
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

    private Integer readCatTarget() {
        CounterCandidate best = null;
        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null) {
            try {
                best = bestCandidate(activeRoot, best);
            } finally {
                activeRoot.recycle();
            }
        }

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                try {
                    best = bestCandidate(root, best);
                } finally {
                    root.recycle();
                }
            }
        }
        return best == null ? null : best.required;
    }

    private CounterCandidate bestCandidate(AccessibilityNodeInfo node, CounterCandidate best) {
        if (node == null) return best;
        best = bestCandidate(node.getText(), best);
        best = bestCandidate(node.getContentDescription(), best);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                best = bestCandidate(child, best);
            } finally {
                child.recycle();
            }
        }
        return best;
    }

    private CounterCandidate bestCandidate(CharSequence text, CounterCandidate best) {
        if (text == null) return best;
        Matcher matcher = CAT_COUNTER_PATTERN.matcher(text);
        while (matcher.find()) {
            int current = parseCounterPart(matcher.group(1));
            int required = parseCounterPart(matcher.group(2));
            if (required <= 0 || current < 0 || current > required) continue;
            CounterCandidate candidate = new CounterCandidate(required);
            if (best == null || candidate.required > best.required) best = candidate;
        }
        return best;
    }

    private int parseCounterPart(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

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

    private static final class CounterCandidate {
        final int required;

        CounterCandidate(int required) {
            this.required = required;
        }
    }
}
