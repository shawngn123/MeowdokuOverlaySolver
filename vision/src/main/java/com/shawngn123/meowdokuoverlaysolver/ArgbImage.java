package com.shawngn123.meowdokuoverlaysolver;

import java.util.Arrays;

public final class ArgbImage {
    public final int width;
    public final int height;
    private final int[] pixels;

    public ArgbImage(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive.");
        }
        if (pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException("Pixel buffer size does not match image dimensions.");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    public static ArgbImage wrapCopy(int width, int height, int[] pixels) {
        return new ArgbImage(width, height, pixels);
    }

    public int pixel(int x, int y) {
        int clampedX = Math.max(0, Math.min(width - 1, x));
        int clampedY = Math.max(0, Math.min(height - 1, y));
        return pixels[clampedY * width + clampedX];
    }

    public int[] copyPixels() {
        return pixels.clone();
    }

    public ArgbImage scaledToFit(int maxDimension) {
        if (maxDimension <= 0 || Math.max(width, height) <= maxDimension) {
            return this;
        }
        float scale = maxDimension / (float) Math.max(width, height);
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        int[] scaled = new int[scaledWidth * scaledHeight];
        for (int y = 0; y < scaledHeight; y++) {
            int sourceY = Math.min(height - 1, Math.round(y / scale));
            for (int x = 0; x < scaledWidth; x++) {
                int sourceX = Math.min(width - 1, Math.round(x / scale));
                scaled[y * scaledWidth + x] = pixels[sourceY * width + sourceX];
            }
        }
        return new ArgbImage(scaledWidth, scaledHeight, scaled);
    }

    public boolean sameInstancePixels(ArgbImage other) {
        return other != null && pixels == other.pixels;
    }

    @Override public String toString() {
        return width + "x" + height;
    }

    @Override public boolean equals(Object object) {
        if (!(object instanceof ArgbImage)) return false;
        ArgbImage other = (ArgbImage) object;
        return width == other.width && height == other.height && Arrays.equals(pixels, other.pixels);
    }

    @Override public int hashCode() {
        int result = width * 31 + height;
        result = result * 31 + Arrays.hashCode(pixels);
        return result;
    }
}
