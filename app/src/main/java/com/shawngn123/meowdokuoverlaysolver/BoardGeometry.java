package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.RectF;

final class BoardGeometry {
    final int rows;
    final int columns;
    final float[] xLines;
    final float[] yLines;
    final RectF bounds;
    final float confidence;

    BoardGeometry(int rows, int columns, float[] xLines, float[] yLines, float confidence) {
        this.rows = rows;
        this.columns = columns;
        this.xLines = xLines;
        this.yLines = yLines;
        this.bounds = new RectF(xLines[0], yLines[0], xLines[xLines.length - 1], yLines[yLines.length - 1]);
        this.confidence = confidence;
    }

    float centerX(int column) { return (xLines[column] + xLines[column + 1]) * 0.5f; }
    float centerY(int row) { return (yLines[row] + yLines[row + 1]) * 0.5f; }
    RectF cellRect(int row, int column) { return new RectF(xLines[column], yLines[row], xLines[column + 1], yLines[row + 1]); }
    long cellCount() { return (long) rows * columns; }
    float averageCellSize() { return (bounds.width() / columns + bounds.height() / rows) * 0.5f; }
}
