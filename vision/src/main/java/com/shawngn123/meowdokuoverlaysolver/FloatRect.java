package com.shawngn123.meowdokuoverlaysolver;

public final class FloatRect {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public FloatRect(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) * 0.5f;
    }

    public float centerY() {
        return (top + bottom) * 0.5f;
    }

    @Override public String toString() {
        return "FloatRect(" + left + "," + top + "," + right + "," + bottom + ")";
    }
}
