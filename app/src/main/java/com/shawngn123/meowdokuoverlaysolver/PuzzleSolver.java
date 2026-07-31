package com.shawngn123.meowdokuoverlaysolver;

import java.util.Arrays;

final class PuzzleSolver {
    static final class Result {
        final int solutionCount;
        final int[] columns;
        Result(int solutionCount, int[] columns) { this.solutionCount = solutionCount; this.columns = columns; }
        boolean hasUniqueSolution() { return solutionCount == 1 && columns != null; }
    }

    private int size;
    private int[][] region;
    private int[] firstSolution;
    private int solutionCount;

    Result solve(PuzzleModel model) {
        if (model == null || !model.isValid()) return new Result(0, null);
        size = model.size;
        region = model.regions.cells;
        firstSolution = null;
        solutionCount = 0;
        int[] assignment = new int[size];
        Arrays.fill(assignment, -1);
        for (int row = 0; row < size; row++) for (int column = 0; column < size; column++) if (model.occupied[row][column]) assignment[row] = column;
        if (!isPartialValid(assignment)) return new Result(0, null);
        search(assignment);
        return new Result(solutionCount, firstSolution == null ? null : firstSolution.clone());
    }

    private void search(int[] original) {
        if (solutionCount >= 2) return;
        int[] assignment = original.clone();
        if (!propagate(assignment)) return;
        int row = chooseRow(assignment);
        if (row < 0) {
            if (isCompleteValid(assignment)) {
                solutionCount++;
                if (firstSolution == null) firstSolution = assignment.clone();
            }
            return;
        }
        int mask = candidates(row, assignment);
        while (mask != 0 && solutionCount < 2) {
            int bit = mask & -mask;
            mask -= bit;
            int[] next = assignment.clone();
            next[row] = Integer.numberOfTrailingZeros(bit);
            if (isPartialValid(next)) search(next);
        }
    }

    private boolean propagate(int[] assignment) {
        boolean changed;
        do {
            changed = false;
            if (!isPartialValid(assignment)) return false;
            int[] masks = new int[size];
            for (int row = 0; row < size; row++) {
                if (assignment[row] >= 0) continue;
                masks[row] = candidates(row, assignment);
                if (masks[row] == 0) return false;
                if (Integer.bitCount(masks[row]) == 1) {
                    assignment[row] = Integer.numberOfTrailingZeros(masks[row]);
                    changed = true;
                }
            }
            if (changed) continue;
            int usedColumns = usedColumns(assignment);
            for (int column = 0; column < size; column++) {
                if ((usedColumns & (1 << column)) != 0) continue;
                int onlyRow = -1;
                for (int row = 0; row < size; row++) {
                    if (assignment[row] < 0 && (masks[row] & (1 << column)) != 0) {
                        if (onlyRow >= 0) { onlyRow = -2; break; }
                        onlyRow = row;
                    }
                }
                if (onlyRow == -1) return false;
                if (onlyRow >= 0) { assignment[onlyRow] = column; changed = true; break; }
            }
            if (changed) continue;
            int usedRegions = usedRegions(assignment);
            for (int target = 0; target < size; target++) {
                if ((usedRegions & (1 << target)) != 0) continue;
                int onlyRow = -1, onlyColumn = -1, possibilities = 0;
                for (int row = 0; row < size && possibilities <= 1; row++) {
                    if (assignment[row] >= 0) continue;
                    int mask = masks[row];
                    while (mask != 0) {
                        int bit = mask & -mask;
                        mask -= bit;
                        int column = Integer.numberOfTrailingZeros(bit);
                        if (region[row][column] == target) {
                            possibilities++;
                            onlyRow = row;
                            onlyColumn = column;
                            if (possibilities > 1) break;
                        }
                    }
                }
                if (possibilities == 0) return false;
                if (possibilities == 1) { assignment[onlyRow] = onlyColumn; changed = true; break; }
            }
        } while (changed);
        return isPartialValid(assignment);
    }

    private int chooseRow(int[] assignment) {
        int bestRow = -1, bestCount = Integer.MAX_VALUE;
        for (int row = 0; row < size; row++) {
            if (assignment[row] >= 0) continue;
            int count = Integer.bitCount(candidates(row, assignment));
            if (count < bestCount) { bestCount = count; bestRow = row; }
        }
        return bestRow;
    }

    private int candidates(int row, int[] assignment) {
        if (assignment[row] >= 0) return 1 << assignment[row];
        int usedColumns = usedColumns(assignment), usedRegions = usedRegions(assignment), mask = 0;
        for (int column = 0; column < size; column++) {
            if ((usedColumns & (1 << column)) != 0) continue;
            if ((usedRegions & (1 << region[row][column])) != 0) continue;
            if (row > 0 && assignment[row - 1] >= 0 && Math.abs(column - assignment[row - 1]) == 1) continue;
            if (row + 1 < size && assignment[row + 1] >= 0 && Math.abs(column - assignment[row + 1]) == 1) continue;
            mask |= 1 << column;
        }
        return mask;
    }

    private int usedColumns(int[] assignment) {
        int mask = 0;
        for (int column : assignment) if (column >= 0) mask |= 1 << column;
        return mask;
    }

    private int usedRegions(int[] assignment) {
        int mask = 0;
        for (int row = 0; row < size; row++) if (assignment[row] >= 0) mask |= 1 << region[row][assignment[row]];
        return mask;
    }

    private boolean isPartialValid(int[] assignment) {
        int columns = 0, regions = 0;
        for (int row = 0; row < size; row++) {
            int column = assignment[row];
            if (column < 0) continue;
            if (column >= size) return false;
            int columnBit = 1 << column, regionBit = 1 << region[row][column];
            if ((columns & columnBit) != 0 || (regions & regionBit) != 0) return false;
            columns |= columnBit;
            regions |= regionBit;
            if (row > 0 && assignment[row - 1] >= 0 && Math.abs(column - assignment[row - 1]) == 1) return false;
        }
        return true;
    }

    private boolean isCompleteValid(int[] assignment) {
        for (int value : assignment) if (value < 0) return false;
        return isPartialValid(assignment) && Integer.bitCount(usedColumns(assignment)) == size && Integer.bitCount(usedRegions(assignment)) == size;
    }
}
