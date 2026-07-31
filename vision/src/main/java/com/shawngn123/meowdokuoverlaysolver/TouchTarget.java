package com.shawngn123.meowdokuoverlaysolver;

public final class TouchTarget {
    public final int row;
    public final int column;
    public final float x;
    public final float y;

    public TouchTarget(int row, int column, float x, float y) {
        this.row = row;
        this.column = column;
        this.x = x;
        this.y = y;
    }

    @Override public String toString() {
        return "r" + row + "c" + column + "@(" + x + "," + y + ")";
    }
}
