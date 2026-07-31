package com.shawngn123.meowdokuoverlaysolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BoardDetector {
    private static final int[] SUPPORTED_SIZES = {8, 10, 12};
    private static final int MAX_WORK_DIMENSION = 900;
    private static final int MAX_PEAKS = 180;

    public BoardGeometry detect(ArgbImage source) {
        if (source == null) return null;
        float scale = Math.min(1f, MAX_WORK_DIMENSION / (float) Math.max(source.width, source.height));
        ArgbImage image = scale == 1f ? source : source.scaledToFit(MAX_WORK_DIMENSION);

        float[] verticalProfile = verticalEdgeProfile(image);
        float[] horizontalProfile = horizontalEdgeProfile(image);

        Candidate best = null;
        for (int size : SUPPORTED_SIZES) {
            List<AxisCandidate> xs = axisCandidates(verticalProfile, size, image.width, image.height);
            List<AxisCandidate> ys = axisCandidates(horizontalProfile, size, image.height, image.width);
            for (AxisCandidate x : xs) {
                for (AxisCandidate y : ys) {
                    float pitchRatio = Math.min(x.pitch, y.pitch) / Math.max(x.pitch, y.pitch);
                    if (pitchRatio < 0.93f) continue;
                    float sideRatio = Math.min(x.side(), y.side()) / Math.max(x.side(), y.side());
                    if (sideRatio < 0.93f) continue;
                    float areaRatio = (x.side() * y.side()) / Math.max(1f, image.width * image.height);
                    float score = x.score * y.score * pitchRatio * sideRatio * (0.85f + areaRatio);
                    if (best == null || score > best.score) {
                        best = new Candidate(size, x, y, score);
                    }
                }
            }
        }
        if (best == null) return null;

        float inverseScale = 1f / scale;
        float[] xCenters = scale(best.x.centers, inverseScale);
        float[] yCenters = scale(best.y.centers, inverseScale);
        float[] xLines = BoardGeometry.linesFromCenters(xCenters);
        float[] yLines = BoardGeometry.linesFromCenters(yCenters);
        return new BoardGeometry(best.size, best.size, xLines, yLines, xCenters, yCenters, best.score);
    }

    private float[] verticalEdgeProfile(ArgbImage image) {
        float[] profile = new float[image.width - 1];
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width - 1; x++) {
                profile[x] += ColorMath.edgeDifference(image.pixel(x, y), image.pixel(x + 1, y));
            }
        }
        for (int x = 0; x < profile.length; x++) {
            profile[x] /= image.height;
        }
        return smooth(profile);
    }

    private float[] horizontalEdgeProfile(ArgbImage image) {
        float[] profile = new float[image.height - 1];
        for (int y = 0; y < image.height - 1; y++) {
            float row = 0f;
            for (int x = 0; x < image.width; x++) {
                row += ColorMath.edgeDifference(image.pixel(x, y), image.pixel(x, y + 1));
            }
            profile[y] = row / image.width;
        }
        return smooth(profile);
    }

    private float[] smooth(float[] source) {
        float[] smoothed = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            float total = 0f;
            int count = 0;
            for (int offset = -1; offset <= 1; offset++) {
                int index = i + offset;
                if (index < 0 || index >= source.length) continue;
                total += source[index];
                count++;
            }
            smoothed[i] = total / Math.max(1, count);
        }
        return smoothed;
    }

    private List<AxisCandidate> axisCandidates(float[] profile, int size, int axisLength, int crossLength) {
        List<Peak> peaks = peaks(profile);
        List<AxisCandidate> candidates = new ArrayList<>();
        float strongestPeak = 1f;
        for (Peak peak : peaks) strongestPeak = Math.max(strongestPeak, peak.value);
        float shortAxis = Math.min(axisLength, crossLength);

        for (int startIndex = 0; startIndex < peaks.size(); startIndex++) {
            Peak start = peaks.get(startIndex);
            for (int endIndex = startIndex + 1; endIndex < peaks.size(); endIndex++) {
                Peak last = peaks.get(endIndex);
                float pitch = (last.position - start.position) / (size - 1f);
                float side = pitch * size;
                if (pitch <= 0f || side < shortAxis * 0.35f || side > axisLength * 1.04f) continue;
                float tolerance = Math.max(1.75f, pitch * 0.055f);

                Peak[] leadingEdges = matchSequence(peaks, start.position, pitch, size, tolerance);
                if (leadingEdges == null) continue;

                for (Peak firstTrailing : peaks) {
                    float cellWidth = firstTrailing.position - start.position;
                    if (cellWidth <= pitch * 0.45f || cellWidth >= pitch * 0.98f) continue;
                    Peak[] trailingEdges = matchSequence(peaks, start.position + cellWidth, pitch, size, tolerance);
                    if (trailingEdges == null) continue;

                    float[] centers = new float[size];
                    float strength = 0f;
                    float error = 0f;
                    for (int i = 0; i < size; i++) {
                        centers[i] = (leadingEdges[i].position + trailingEdges[i].position) * 0.5f;
                        strength += (leadingEdges[i].value + trailingEdges[i].value) / (2f * strongestPeak);
                        error += Math.abs(leadingEdges[i].position - (start.position + i * pitch)) / pitch;
                        error += Math.abs(trailingEdges[i].position - (start.position + cellWidth + i * pitch)) / pitch;
                    }

                    float regularity = regularity(centers);
                    float geometry = 1f - Math.min(1f, error / (size * 2f) * 4f);
                    float fill = Math.min(1f, Math.max(0f, cellWidth / pitch));
                    float score = (strength / size) * regularity * geometry * (0.75f + fill * 0.25f) * (side / shortAxis);
                    if (score > 0f) {
                        candidates.add(new AxisCandidate(size, centers, pitch, score));
                    }
                }
            }
        }

        candidates.sort((a, b) -> Float.compare(b.score, a.score));
        if (candidates.size() > 24) {
            return new ArrayList<>(candidates.subList(0, 24));
        }
        return candidates;
    }

    private List<Peak> peaks(float[] profile) {
        float[] sorted = profile.clone();
        java.util.Arrays.sort(sorted);
        float median = sorted[sorted.length / 2];
        float p85 = sorted[Math.min(sorted.length - 1, Math.round((sorted.length - 1) * 0.85f))];
        float[] deviations = new float[sorted.length];
        for (int i = 0; i < profile.length; i++) deviations[i] = Math.abs(profile[i] - median);
        java.util.Arrays.sort(deviations);
        float mad = deviations[deviations.length / 2];
        float threshold = Math.max(p85, median + Math.max(0.001f, mad) * 2.2f);

        List<Peak> all = new ArrayList<>();
        for (int i = 2; i < profile.length - 2; i++) {
            if (profile[i] < threshold) continue;
            if (profile[i] >= profile[i - 1]
                    && profile[i] >= profile[i + 1]
                    && profile[i] >= profile[i - 2]
                    && profile[i] >= profile[i + 2]) {
                all.add(new Peak(i, profile[i]));
            }
        }
        all.sort((a, b) -> Float.compare(b.value, a.value));

        List<Peak> kept = new ArrayList<>();
        for (Peak peak : all) {
            boolean close = false;
            for (Peak existing : kept) {
                if (Math.abs(existing.position - peak.position) <= 2) {
                    close = true;
                    break;
                }
            }
            if (!close) kept.add(peak);
            if (kept.size() >= MAX_PEAKS) break;
        }
        kept.sort(Comparator.comparingInt(peak -> peak.position));
        return kept;
    }

    private Peak[] matchSequence(List<Peak> peaks, float start, float pitch, int count, float tolerance) {
        Peak[] matched = new Peak[count];
        for (int i = 0; i < count; i++) {
            Peak peak = nearest(peaks, start + i * pitch, tolerance);
            if (peak == null) return null;
            matched[i] = peak;
        }
        return matched;
    }

    private Peak nearest(List<Peak> peaks, float expected, float tolerance) {
        Peak best = null;
        float bestDistance = tolerance + 1f;
        for (Peak peak : peaks) {
            float distance = Math.abs(peak.position - expected);
            if (distance <= tolerance && distance < bestDistance) {
                best = peak;
                bestDistance = distance;
            }
            if (peak.position > expected + tolerance) {
                break;
            }
        }
        return best;
    }

    private float regularity(float[] centers) {
        if (centers.length <= 1) return 1f;
        float mean = (centers[centers.length - 1] - centers[0]) / (centers.length - 1f);
        if (mean <= 0f) return 0f;
        float error = 0f;
        for (int i = 1; i < centers.length; i++) {
            error += Math.abs((centers[i] - centers[i - 1]) - mean) / mean;
        }
        return Math.max(0f, 1f - error / (centers.length - 1f) * 2.5f);
    }

    private float[] scale(float[] values, float factor) {
        float[] scaled = values.clone();
        for (int i = 0; i < scaled.length; i++) scaled[i] *= factor;
        return scaled;
    }

    private static final class Peak {
        final int position;
        final float value;

        Peak(int position, float value) {
            this.position = position;
            this.value = value;
        }
    }

    private static final class AxisCandidate {
        final int size;
        final float[] centers;
        final float pitch;
        final float score;

        AxisCandidate(int size, float[] centers, float pitch, float score) {
            this.size = size;
            this.centers = centers;
            this.pitch = pitch;
            this.score = score;
        }

        float side() {
            return pitch * size;
        }
    }

    private static final class Candidate {
        final int size;
        final AxisCandidate x;
        final AxisCandidate y;
        final float score;

        Candidate(int size, AxisCandidate x, AxisCandidate y, float score) {
            this.size = size;
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }
}
