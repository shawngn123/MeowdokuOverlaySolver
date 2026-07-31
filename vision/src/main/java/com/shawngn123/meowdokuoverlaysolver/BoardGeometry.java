package com.shawngn123.meowdokuoverlaysolver;

import java.util.Arrays;

public final class BoardGeometry {
    public final int rows;
    public final int columns;
    public final float[] xLines;
    public final float[] yLines;
    public final float[] xCenters;
    public final float[] yCenters;
    public final FloatRect bounds;
    public final float confidence;

    public BoardGeometry(int rows, int columns, float[] xLines, float[] yLines, float[] xCenters, float[] yCenters, float confidence) {
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("Board dimensions must be positive.");
        }
        if (xLines == null || xLines.length != columns + 1 || yLines == null || yLines.length != rows + 1) {
            throw new IllegalArgumentException("Grid line counts do not match board dimensions.");
        }
        if (xCenters == null || xCenters.length != columns || yCenters == null || yCenters.length != rows) {
            throw new IllegalArgumentException("Cell center counts do not match board dimensions.");
        }
        this.rows = rows;
        this.columns = columns;
        this.xLines = xLines.clone();
        this.yLines = yLines.clone();
        this.xCenters = xCenters.clone();
        this.yCenters = yCenters.clone();
        this.bounds = new FloatRect(xLines[0], yLines[0], xLines[xLines.length - 1], yLines[yLines.length - 1]);
        this.confidence = confidence;
    }

    public float centerX(int column) {
        return xCenters[column];
    }

    public float centerY(int row) {
        return yCenters[row];
    }

    public FloatRect cellRect(int row, int column) {
        return new FloatRect(xLines[column], yLines[row], xLines[column + 1], yLines[row + 1]);
    }

    public long cellCount() {
        return (long) rows * columns;
    }

    public float averageCellSize() {
        return (bounds.width() / columns + bounds.height() / rows) * 0.5f;
    }

    static float[] linesFromCenters(float[] centers) {
        float[] lines = new float[centers.length + 1];
        if (centers.length == 1) {
            lines[0] = centers[0] - 0.5f;
            lines[1] = centers[0] + 0.5f;
            return lines;
        }
        lines[0] = centers[0] - (centers[1] - centers[0]) * 0.5f;
        for (int i = 1; i < centers.length; i++) {
            lines[i] = (centers[i - 1] + centers[i]) * 0.5f;
        }
        int last = centers.length - 1;
        lines[centers.length] = centers[last] + (centers[last] - centers[last - 1]) * 0.5f;
        return lines;
    }

    @Override public String toString() {
        return rows + "x" + columns + " " + bounds + " confidence=" + confidence
                + " xCenters=" + Arrays.toString(xCenters)
                + " yCenters=" + Arrays.toString(yCenters);
    }
}
