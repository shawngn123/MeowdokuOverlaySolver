package com.shawngn123.meowdokuoverlaysolver;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class BoardDetector {
    private static final int MIN_N = 4;
    private static final int MAX_N = 12;
    private static final int MAX_DIM = 720;

    BoardGeometry detect(Bitmap source) {
        if (source == null || source.isRecycled()) return null;
        float scale = Math.min(1f, MAX_DIM / (float) Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, Math.round(source.getWidth() * scale));
        int h = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap small = scale == 1f ? source : Bitmap.createScaledBitmap(source, w, h, false);
        int[] pixels = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        if (small != source) small.recycle();

        int[] vx = new int[w * h];
        int[] hy = new int[w * h];
        long total = 0;
        int count = 0;
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                vx[i] = diff(pixels[i - 1], pixels[i + 1]);
                hy[i] = diff(pixels[i - w], pixels[i + w]);
                total += vx[i] + hy[i];
                count += 2;
            }
        }
        float meanEdge = count == 0 ? 1f : total / (float) count;
        List<Peak> xPeaks = peaks(project(vx, w, h, true));
        List<Peak> yPeaks = peaks(project(hy, w, h, false));
        List<Seq> xs = sequences(xPeaks, w, h);
        List<Seq> ys = sequences(yPeaks, h, w);

        Candidate best = null;
        for (Seq x : xs) for (Seq y : ys) {
            if (x.n != y.n) continue;
            float ratio = Math.min(x.side(), y.side()) / Math.max(x.side(), y.side());
            if (ratio < 0.90f) continue;
            float[] xl = refine(vx, w, h, true, x, y.a, y.b);
            float[] yl = refine(hy, w, h, false, y, x.a, x.b);
            if (xl == null || yl == null) continue;
            float strength = 0f;
            for (float p : xl) strength += segment(vx, w, h, true, Math.round(p), Math.round(yl[0]), Math.round(yl[yl.length - 1]));
            for (float p : yl) strength += segment(hy, w, h, false, Math.round(p), Math.round(xl[0]), Math.round(xl[xl.length - 1]));
            strength /= (xl.length + yl.length);
            float regular = regularity(xl) * regularity(yl);
            float score = strength / Math.max(1f, meanEdge) * ratio * (0.65f + 0.35f * regular) * (0.75f + 0.25f * ((x.quality + y.quality) * 0.5f));
            if (regular >= 0.68f && score >= 1.55f && (best == null || score > best.score)) best = new Candidate(x.n, xl, yl, score);
        }
        if (best == null) return null;
        float inv = 1f / scale;
        for (int i = 0; i < best.x.length; i++) best.x[i] *= inv;
        for (int i = 0; i < best.y.length; i++) best.y[i] *= inv;
        return new BoardGeometry(best.n, best.n, best.x, best.y, best.score);
    }

    private float[] project(int[] edge, int w, int h, boolean vertical) {
        int axis = vertical ? w : h, cross = vertical ? h : w;
        int window = Math.min(cross, Math.max(20, Math.round(Math.min(w, h) * 0.28f)));
        float[] score = new float[axis];
        for (int a = 1; a < axis - 1; a++) {
            int sum = 0;
            for (int c = 0; c < window; c++) sum += edge[vertical ? c * w + a : a * w + c];
            int best = sum;
            for (int c = window; c < cross; c++) {
                sum += edge[vertical ? c * w + a : a * w + c];
                int old = c - window;
                sum -= edge[vertical ? old * w + a : a * w + old];
                if (sum > best) best = sum;
            }
            score[a] = best / (float) window;
        }
        return score;
    }

    private List<Peak> peaks(float[] score) {
        float[] sorted = score.clone();
        Arrays.sort(sorted);
        float median = sorted[sorted.length / 2];
        float p75 = sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.75f))];
        float threshold = median + Math.max(2.5f, (p75 - median) * 0.40f);
        List<Peak> all = new ArrayList<>();
        for (int i = 2; i < score.length - 2; i++) if (score[i] >= threshold && score[i] >= score[i - 1] && score[i] >= score[i + 1] && score[i] >= score[i - 2] && score[i] >= score[i + 2]) all.add(new Peak(i, score[i]));
        all.sort((a, b) -> Float.compare(b.s, a.s));
        List<Peak> kept = new ArrayList<>();
        for (Peak p : all) {
            boolean close = false;
            for (Peak k : kept) if (Math.abs(k.p - p.p) <= 2) { close = true; break; }
            if (!close) kept.add(p);
            if (kept.size() == 90) break;
        }
        kept.sort(Comparator.comparingInt(p -> p.p));
        return kept;
    }

    private List<Seq> sequences(List<Peak> peaks, int axis, int cross) {
        List<Seq> out = new ArrayList<>();
        float minSide = Math.min(axis, cross) * 0.30f, maxSide = Math.min(axis, cross) * 0.98f;
        float max = 1f;
        for (Peak p : peaks) max = Math.max(max, p.s);
        for (int a = 0; a < peaks.size() - 1; a++) for (int b = a + 1; b < peaks.size(); b++) {
            float side = peaks.get(b).p - peaks.get(a).p;
            if (side < minSide || side > maxSide) continue;
            for (int n = MIN_N; n <= MAX_N; n++) {
                float cell = side / n;
                if (cell < 9f) continue;
                float tol = Math.max(2.5f, cell * 0.17f), sum = 0f, error = 0f;
                int matched = 0;
                for (int k = 0; k <= n; k++) {
                    float expected = peaks.get(a).p + k * cell;
                    Peak near = nearest(peaks, expected, tol);
                    if (near != null) { matched++; sum += near.s / max; error += Math.abs(near.p - expected) / cell; }
                }
                if (matched < n) continue;
                float q = matched / (n + 1f) * (1f - Math.min(1f, error / Math.max(1, matched) * 2.5f)) * (sum / matched);
                if (q > 0.42f) out.add(new Seq(peaks.get(a).p, peaks.get(b).p, n, q));
            }
        }
        out.sort((a, b) -> Float.compare(b.quality, a.quality));
        return out.size() > 45 ? new ArrayList<>(out.subList(0, 45)) : out;
    }

    private Peak nearest(List<Peak> peaks, float expected, float tolerance) {
        Peak best = null;
        float distance = tolerance + 1;
        for (Peak p : peaks) {
            float d = Math.abs(p.p - expected);
            if (d <= tolerance && d < distance) { best = p; distance = d; }
            if (p.p > expected + tolerance) break;
        }
        return best;
    }

    private float[] refine(int[] edge, int w, int h, boolean vertical, Seq seq, float crossA, float crossB) {
        float cell = seq.side() / seq.n;
        int radius = Math.max(2, Math.round(cell * 0.13f));
        float[] lines = new float[seq.n + 1];
        for (int k = 0; k <= seq.n; k++) {
            int expected = Math.round(seq.a + k * cell), bestP = expected;
            float best = -1;
            for (int p = expected - radius; p <= expected + radius; p++) {
                if (p < 1 || p >= (vertical ? w : h) - 1) continue;
                float s = segment(edge, w, h, vertical, p, Math.round(crossA), Math.round(crossB));
                if (s > best) { best = s; bestP = p; }
            }
            lines[k] = bestP;
        }
        for (int i = 1; i < lines.length; i++) if (lines[i] - lines[i - 1] < Math.max(3f, cell * 0.55f)) return null;
        return lines;
    }

    private float segment(int[] edge, int w, int h, boolean vertical, int position, int start, int end) {
        int axisLimit = vertical ? w : h, crossLimit = vertical ? h : w;
        position = clamp(position, 1, axisLimit - 2);
        start = clamp(start, 0, crossLimit - 1);
        end = clamp(end, start + 1, crossLimit);
        long total = 0;
        for (int c = start; c < end; c++) {
            int best = 0;
            for (int o = -1; o <= 1; o++) best = Math.max(best, edge[vertical ? c * w + position + o : (position + o) * w + c]);
            total += best;
        }
        return total / (float) Math.max(1, end - start);
    }

    private float regularity(float[] lines) {
        float mean = (lines[lines.length - 1] - lines[0]) / (lines.length - 1f), error = 0f;
        if (mean <= 0) return 0f;
        for (int i = 1; i < lines.length; i++) error += Math.abs((lines[i] - lines[i - 1]) - mean) / mean;
        return Math.max(0f, 1f - error / (lines.length - 1f) * 2.8f);
    }

    private int diff(int a, int b) {
        return (Math.abs(Color.red(a) - Color.red(b)) * 3 + Math.abs(Color.green(a) - Color.green(b)) * 5 + Math.abs(Color.blue(a) - Color.blue(b)) * 2) / 10;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class Peak { final int p; final float s; Peak(int p, float s) { this.p = p; this.s = s; } }
    private static final class Seq { final float a, b; final int n; final float quality; Seq(float a, float b, int n, float quality) { this.a = a; this.b = b; this.n = n; this.quality = quality; } float side() { return b - a; } }
    private static final class Candidate { final int n; final float[] x, y; final float score; Candidate(int n, float[] x, float[] y, float score) { this.n = n; this.x = x; this.y = y; this.score = score; } }
}
