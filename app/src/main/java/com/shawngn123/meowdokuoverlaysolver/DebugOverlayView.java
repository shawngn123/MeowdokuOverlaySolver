package com.shawngn123.meowdokuoverlaysolver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class DebugOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BoardGeometry board;

    DebugOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    void showBoard(BoardGeometry board) {
        this.board = board;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.argb(235, 0, 255, 80));
        canvas.drawRect(board.bounds, paint);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(210, 0, 220, 255));
        for (float x : board.xLines) canvas.drawLine(x, board.bounds.top, x, board.bounds.bottom, paint);
        for (float y : board.yLines) canvas.drawLine(board.bounds.left, y, board.bounds.right, y, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(240, 255, 40, 40));
        float radius = Math.max(3f, board.averageCellSize() * 0.055f);
        for (int row = 0; row < board.rows; row++) {
            for (int column = 0; column < board.columns; column++) {
                canvas.drawCircle(board.centerX(column), board.centerY(row), radius, paint);
            }
        }
    }
}
