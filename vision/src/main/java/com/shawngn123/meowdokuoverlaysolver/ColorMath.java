package com.shawngn123.meowdokuoverlaysolver;

final class ColorMath {
    private ColorMath() { }

    static int red(int color) {
        return (color >> 16) & 0xff;
    }

    static int green(int color) {
        return (color >> 8) & 0xff;
    }

    static int blue(int color) {
        return color & 0xff;
    }

    static int rgb(int red, int green, int blue) {
        return 0xff000000
                | (clamp8(red) << 16)
                | (clamp8(green) << 8)
                | clamp8(blue);
    }

    static double edgeDifference(int first, int second) {
        return Math.abs(red(first) - red(second)) * 0.30
                + Math.abs(green(first) - green(second)) * 0.50
                + Math.abs(blue(first) - blue(second)) * 0.20;
    }

    static double[] oklab(int color) {
        double r = linear(red(color) / 255.0);
        double g = linear(green(color) / 255.0);
        double b = linear(blue(color) / 255.0);

        double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
        double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
        double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

        double lRoot = Math.cbrt(l);
        double mRoot = Math.cbrt(m);
        double sRoot = Math.cbrt(s);

        return new double[]{
                0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
                1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
                0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
        };
    }

    static double oklabDistanceSquared(double[] first, double[] second) {
        double dl = (first[0] - second[0]) * 0.75;
        double da = first[1] - second[1];
        double db = first[2] - second[2];
        return dl * dl + da * da + db * db;
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int clamp8(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
