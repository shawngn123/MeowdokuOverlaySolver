package com.shawngn123.meowdokuoverlaysolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class HudBoardSizeDetector {
    private static final int[] SUPPORTED_SIZES = {8, 9, 10, 11, 12};
    private static final float MIN_CONFIDENCE = 0.58f;
    private static final float CROP_WIDTH_FRACTION = 0.30f;
    private static final float CROP_HEIGHT_FRACTION = 0.12f;
    private static final int MIN_CROP_WIDTH = 160;
    private static final int MAX_CROP_WIDTH = 360;
    private static final int MIN_CROP_HEIGHT = 90;
    private static final int MAX_CROP_HEIGHT = 220;

    public Result detect(ArgbImage image) {
        if (image == null) {
            return new Result(0, 0f, null, null);
        }

        FloatRect cropBounds = cropBounds(image);
        Candidate best = null;
        int width = Math.max(1, Math.round(cropBounds.width()));
        int height = Math.max(1, Math.round(cropBounds.height()));
        int[] luminance = luminance(image, cropBounds, width, height);
        for (boolean brightForeground : new boolean[]{true, false}) {
            LabelMap map = components(mask(luminance, width, height, brightForeground), width, height);
            Candidate candidate = recognize(map);
            if (candidate != null && (best == null || candidate.confidence > best.confidence)) {
                best = candidate;
            }
        }

        if (best == null || best.confidence < MIN_CONFIDENCE) {
            return new Result(0, best == null ? 0f : best.confidence, cropBounds, best == null ? null : best.text);
        }
        return new Result(best.value, best.confidence, cropBounds, best.text);
    }

    public static boolean isSupportedBoardSize(int size) {
        for (int supported : SUPPORTED_SIZES) {
            if (size == supported) return true;
        }
        return false;
    }

    private FloatRect cropBounds(ArgbImage image) {
        int width = clamp(Math.round(image.width * CROP_WIDTH_FRACTION), MIN_CROP_WIDTH, MAX_CROP_WIDTH);
        int height = clamp(Math.round(image.height * CROP_HEIGHT_FRACTION), MIN_CROP_HEIGHT, MAX_CROP_HEIGHT);
        return new FloatRect(0f, 0f, Math.min(image.width, width), Math.min(image.height, height));
    }

    private int[] luminance(ArgbImage image, FloatRect crop, int width, int height) {
        int[] values = new int[width * height];
        int left = Math.round(crop.left);
        int top = Math.round(crop.top);
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.pixel(left + x, top + y);
                values[index++] = Math.round(
                        ColorMath.red(pixel) * 0.299f
                                + ColorMath.green(pixel) * 0.587f
                                + ColorMath.blue(pixel) * 0.114f
                );
            }
        }
        return values;
    }

    private boolean[] mask(int[] luminance, int width, int height, boolean brightForeground) {
        int[] sorted = luminance.clone();
        Arrays.sort(sorted);
        int low = sorted[Math.round((sorted.length - 1) * 0.05f)];
        int median = sorted[sorted.length / 2];
        int high = sorted[Math.round((sorted.length - 1) * 0.95f)];
        int range = Math.max(1, high - low);
        int delta = Math.max(18, Math.round(range * 0.34f));
        int threshold = brightForeground
                ? Math.min(245, median + delta)
                : Math.max(10, median - delta);

        boolean[] mask = new boolean[width * height];
        for (int i = 0; i < luminance.length; i++) {
            mask[i] = brightForeground ? luminance[i] >= threshold : luminance[i] <= threshold;
        }
        return mask;
    }

    private LabelMap components(boolean[] mask, int width, int height) {
        int[] labels = new int[mask.length];
        List<Component> components = new ArrayList<>();
        int[] queue = new int[mask.length];
        int nextLabel = 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int start = y * width + x;
                if (!mask[start] || labels[start] != 0) continue;

                int head = 0;
                int tail = 0;
                queue[tail++] = start;
                labels[start] = nextLabel;
                int left = x;
                int right = x;
                int top = y;
                int bottom = y;
                int area = 0;

                while (head < tail) {
                    int index = queue[head++];
                    int cx = index % width;
                    int cy = index / width;
                    area++;
                    left = Math.min(left, cx);
                    right = Math.max(right, cx);
                    top = Math.min(top, cy);
                    bottom = Math.max(bottom, cy);

                    for (int dy = -1; dy <= 1; dy++) {
                        int ny = cy + dy;
                        if (ny < 0 || ny >= height) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = cx + dx;
                            if (nx < 0 || nx >= width) continue;
                            int neighbor = ny * width + nx;
                            if (!mask[neighbor] || labels[neighbor] != 0) continue;
                            labels[neighbor] = nextLabel;
                            queue[tail++] = neighbor;
                        }
                    }
                }

                components.add(new Component(nextLabel, left, top, right + 1, bottom + 1, area));
                nextLabel++;
            }
        }
        components.sort(Comparator.comparingInt(component -> component.left));
        return new LabelMap(width, height, labels, components);
    }

    private Candidate recognize(LabelMap map) {
        Candidate best = null;
        for (Component slash : map.components) {
            if (!plausibleTextComponent(map, slash)) continue;
            float slashScore = slashScore(map, slash);
            if (slashScore < 0.48f) continue;

            List<Component> afterSlash = digitComponentsAfterSlash(map, slash);
            for (int i = 0; i < afterSlash.size(); i++) {
                Component first = afterSlash.get(i);
                DigitScore firstDigit = classifyDigit(map, first);
                float startGapScore = gapScore(first.left - slash.right, slash.height() * 0.7f);

                if ((firstDigit.digit == 8 || firstDigit.digit == 9)
                        && isSupportedBoardSize(firstDigit.digit)) {
                    float confidence = combine(slashScore, firstDigit.confidence, startGapScore);
                    Candidate candidate = new Candidate(firstDigit.digit, Integer.toString(firstDigit.digit), confidence);
                    if (best == null || candidate.confidence > best.confidence) best = candidate;
                }

                if (firstDigit.digit != 1) continue;
                for (int j = i + 1; j < afterSlash.size(); j++) {
                    Component second = afterSlash.get(j);
                    int gap = second.left - first.right;
                    if (gap < -1) continue;
                    if (gap > Math.max(10, Math.round(slash.height() * 0.65f))) break;
                    DigitScore secondDigit = classifyDigit(map, second);
                    int value = 10 + secondDigit.digit;
                    if (!isSupportedBoardSize(value) || secondDigit.digit > 2) continue;
                    float confidence = combine(
                            slashScore,
                            firstDigit.confidence,
                            secondDigit.confidence,
                            startGapScore,
                            gapScore(gap, slash.height() * 0.45f)
                    );
                    Candidate candidate = new Candidate(value, "1" + secondDigit.digit, confidence);
                    if (best == null || candidate.confidence > best.confidence) best = candidate;
                }
            }
        }
        return best;
    }

    private List<Component> digitComponentsAfterSlash(LabelMap map, Component slash) {
        List<Component> digits = new ArrayList<>();
        float slashCenterY = slash.centerY();
        float maxDistance = Math.max(48f, slash.height() * 4.2f);
        for (Component component : map.components) {
            if (component.left < slash.right - 1) continue;
            if (component.left - slash.right > maxDistance) break;
            if (!plausibleTextComponent(map, component)) continue;
            if (component.id == slash.id) continue;
            if (Math.abs(component.centerY() - slashCenterY) > Math.max(slash.height(), component.height()) * 0.55f) continue;
            float heightRatio = component.height() / (float) Math.max(1, slash.height());
            if (heightRatio < 0.42f || heightRatio > 1.75f) continue;
            digits.add(component);
        }
        digits.sort(Comparator.comparingInt(component -> component.left));
        return digits;
    }

    private boolean plausibleTextComponent(LabelMap map, Component component) {
        int minHeight = Math.max(8, Math.round(map.height * 0.045f));
        int maxHeight = Math.max(minHeight + 1, Math.round(map.height * 0.62f));
        if (component.height() < minHeight || component.height() > maxHeight) return false;
        if (component.width() < 2 || component.width() > Math.max(28, Math.round(map.width * 0.22f))) return false;
        float fill = component.area / (float) (component.width() * component.height());
        return fill >= 0.055f && fill <= 0.88f;
    }

    private float slashScore(LabelMap map, Component component) {
        int width = component.width();
        int height = component.height();
        if (height < 8 || width < 2) return 0f;
        if (height < width * 1.25f) return 0f;

        float topX = componentCenterXInBand(map, component, 0f, 0.38f);
        float bottomX = componentCenterXInBand(map, component, 0.62f, 1f);
        if (Float.isNaN(topX) || Float.isNaN(bottomX)) return 0f;
        float diagonal = (topX - bottomX) / Math.max(1f, width);
        if (diagonal <= 0.18f) return 0f;

        float aspectScore = clamp01((height / (float) Math.max(1, width) - 1.25f) / 4f);
        float diagonalScore = clamp01(diagonal / 0.9f);
        float fill = component.area / (float) (width * height);
        float fillScore = 1f - Math.min(1f, Math.abs(fill - 0.28f) / 0.34f);
        return clamp01(aspectScore * 0.28f + diagonalScore * 0.55f + fillScore * 0.17f);
    }

    private float componentCenterXInBand(LabelMap map, Component component, float fromY, float toY) {
        int startY = component.top + Math.round(component.height() * fromY);
        int endY = component.top + Math.round(component.height() * toY);
        int count = 0;
        int total = 0;
        for (int y = Math.max(component.top, startY); y < Math.min(component.bottom, endY); y++) {
            for (int x = component.left; x < component.right; x++) {
                if (map.labelAt(x, y) == component.id) {
                    total += x;
                    count++;
                }
            }
        }
        return count == 0 ? Float.NaN : total / (float) count;
    }

    private DigitScore classifyDigit(LabelMap map, Component component) {
        float[] segments = segmentDensities(map, component);
        float centerStem = density(map, component, 0.35f, 0.65f, 0.08f, 0.92f);
        int holes = holeCount(map, component);
        float widthRatio = component.width() / (float) Math.max(1, component.height());

        int[] digits = {0, 1, 2, 8, 9};
        DigitScore best = null;
        float second = 0f;
        for (int digit : digits) {
            float score = digitScore(digit, segments, centerStem, holes, widthRatio);
            if (best == null || score > best.score) {
                second = best == null ? 0f : best.score;
                best = new DigitScore(digit, score, 0f);
            } else {
                second = Math.max(second, score);
            }
        }

        float margin = best.score - second;
        float confidence = clamp01(best.score * 0.72f + Math.min(0.28f, margin * 1.7f));
        return new DigitScore(best.digit, best.score, confidence);
    }

    private float digitScore(int digit, float[] segments, float centerStem, int holes, float widthRatio) {
        boolean[] expected;
        switch (digit) {
            case 0:
                expected = new boolean[]{true, false, true, true, true, true, true};
                break;
            case 1:
                expected = new boolean[]{false, false, false, false, true, false, true};
                break;
            case 2:
                expected = new boolean[]{true, true, true, false, true, true, false};
                break;
            case 8:
                expected = new boolean[]{true, true, true, true, true, true, true};
                break;
            case 9:
                expected = new boolean[]{true, true, true, true, true, false, true};
                break;
            default:
                return 0f;
        }

        float score = 0f;
        for (int i = 0; i < segments.length; i++) {
            score += expected[i] ? segments[i] : 1f - segments[i] * 0.82f;
        }
        score /= segments.length;

        float narrow = clamp01((0.52f - widthRatio) / 0.28f);
        float wide = clamp01((widthRatio - 0.32f) / 0.42f);
        if (digit == 1) {
            score = score * 0.34f + narrow * 0.44f + centerStem * 0.22f;
            if (holes > 0) score *= 0.78f;
        } else {
            score = score * 0.82f + wide * 0.18f;
        }

        if (digit == 0) {
            score += holes >= 1 ? 0.09f : -0.06f;
            score += (1f - segments[1]) * 0.06f;
        } else if (digit == 2) {
            score += holes == 0 ? 0.07f : -0.08f;
            score += segments[5] * 0.06f;
        } else if (digit == 8) {
            score += holes >= 2 ? 0.12f : (holes == 1 ? 0.03f : -0.08f);
            score += segments[5] * 0.06f;
        } else if (digit == 9) {
            score += holes >= 1 ? 0.08f : -0.05f;
            score += (1f - segments[5]) * 0.08f;
        }
        return clamp01(score);
    }

    private float[] segmentDensities(LabelMap map, Component component) {
        float[] segments = new float[7];
        segments[0] = density(map, component, 0.20f, 0.80f, 0.00f, 0.25f);
        segments[1] = density(map, component, 0.18f, 0.82f, 0.38f, 0.62f);
        segments[2] = density(map, component, 0.20f, 0.80f, 0.75f, 1.00f);
        segments[3] = density(map, component, 0.00f, 0.38f, 0.10f, 0.48f);
        segments[4] = density(map, component, 0.62f, 1.00f, 0.10f, 0.48f);
        segments[5] = density(map, component, 0.00f, 0.38f, 0.52f, 0.90f);
        segments[6] = density(map, component, 0.62f, 1.00f, 0.52f, 0.90f);
        return segments;
    }

    private float density(LabelMap map, Component component, float fromX, float toX, float fromY, float toY) {
        int left = component.left + Math.round(component.width() * fromX);
        int right = component.left + Math.round(component.width() * toX);
        int top = component.top + Math.round(component.height() * fromY);
        int bottom = component.top + Math.round(component.height() * toY);
        int total = 0;
        int filled = 0;
        for (int y = Math.max(component.top, top); y < Math.min(component.bottom, bottom); y++) {
            for (int x = Math.max(component.left, left); x < Math.min(component.right, right); x++) {
                total++;
                if (map.labelAt(x, y) == component.id) filled++;
            }
        }
        if (total == 0) return 0f;
        return clamp01(filled / (total * 0.18f));
    }

    private int holeCount(LabelMap map, Component component) {
        int width = component.width();
        int height = component.height();
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[width * height];
        int holes = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (visited[index] || map.labelAt(component.left + x, component.top + y) == component.id) continue;

                int head = 0;
                int tail = 0;
                int area = 0;
                boolean touchesBorder = false;
                queue[tail++] = index;
                visited[index] = true;
                while (head < tail) {
                    int current = queue[head++];
                    int cx = current % width;
                    int cy = current / width;
                    area++;
                    if (cx == 0 || cy == 0 || cx == width - 1 || cy == height - 1) touchesBorder = true;
                    int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] neighbor : neighbors) {
                        int nx = cx + neighbor[0];
                        int ny = cy + neighbor[1];
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int next = ny * width + nx;
                        if (visited[next]) continue;
                        if (map.labelAt(component.left + nx, component.top + ny) == component.id) continue;
                        visited[next] = true;
                        queue[tail++] = next;
                    }
                }
                if (!touchesBorder && area >= Math.max(2, width * height / 90)) holes++;
            }
        }
        return holes;
    }

    private float gapScore(int gap, float expectedMax) {
        if (gap < -2) return 0f;
        return 1f - Math.min(1f, Math.max(0f, gap) / Math.max(1f, expectedMax));
    }

    private float combine(float... values) {
        float total = 0f;
        for (float value : values) total += clamp01(value);
        return values.length == 0 ? 0f : total / values.length;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static final class Result {
        public final int detectedValue;
        public final float confidence;
        public final FloatRect cropBounds;
        public final String recognizedText;

        private Result(int detectedValue, float confidence, FloatRect cropBounds, String recognizedText) {
            this.detectedValue = detectedValue;
            this.confidence = confidence;
            this.cropBounds = cropBounds;
            this.recognizedText = recognizedText;
        }

        public boolean isSuccess() {
            return isSupportedBoardSize(detectedValue);
        }

        public String detectedValueText() {
            if (detectedValue > 0) return Integer.toString(detectedValue);
            return recognizedText == null || recognizedText.isEmpty() ? "unavailable" : recognizedText;
        }

        public String confidenceText() {
            return String.format(Locale.US, "%.3f", confidence);
        }
    }

    private static final class Candidate {
        final int value;
        final String text;
        final float confidence;

        Candidate(int value, String text, float confidence) {
            this.value = value;
            this.text = text;
            this.confidence = confidence;
        }
    }

    private static final class DigitScore {
        final int digit;
        final float score;
        final float confidence;

        DigitScore(int digit, float score, float confidence) {
            this.digit = digit;
            this.score = score;
            this.confidence = confidence;
        }
    }

    private static final class Component {
        final int id;
        final int left;
        final int top;
        final int right;
        final int bottom;
        final int area;

        Component(int id, int left, int top, int right, int bottom, int area) {
            this.id = id;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.area = area;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        float centerY() {
            return (top + bottom) * 0.5f;
        }
    }

    private static final class LabelMap {
        final int width;
        final int height;
        final int[] labels;
        final List<Component> components;

        LabelMap(int width, int height, int[] labels, List<Component> components) {
            this.width = width;
            this.height = height;
            this.labels = labels;
            this.components = components;
        }

        int labelAt(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return -1;
            return labels[y * width + x];
        }
    }
}
