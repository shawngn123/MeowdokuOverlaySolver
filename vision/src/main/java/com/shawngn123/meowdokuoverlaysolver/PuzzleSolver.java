package com.shawngn123.meowdokuoverlaysolver;

import java.util.Arrays;
import java.util.BitSet;

public final class PuzzleSolver {
    public static final class Result {
        public final int solutionCount;
        public final int[] columns;

        Result(int solutionCount, int[] columns) {
            this.solutionCount = solutionCount;
            this.columns = columns;
        }

        public boolean hasUniqueSolution() {
            return solutionCount == 1 && columns != null;
        }
    }

    private int size;
    private int[][] region;
    private int[] firstSolution;
    private int solutionCount;

    public Result solve(PuzzleModel model) {
        if (model == null || !model.isValid()) return new Result(0, null);
        size = model.size;
        region = model.regions.cells;
        firstSolution = null;
        solutionCount = 0;
        int[] assignment = new int[size];
        Arrays.fill(assignment, -1);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (model.occupied[row][column]) assignment[row] = column;
            }
        }
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
        BitSet mask = candidates(row, assignment);
        for (int column = mask.nextSetBit(0); column >= 0 && solutionCount < 2; column = mask.nextSetBit(column + 1)) {
            int[] next = assignment.clone();
            next[row] = column;
            if (isPartialValid(next)) search(next);
        }
    }

    private boolean propagate(int[] assignment) {
        boolean changed;
        do {
            changed = false;
            if (!isPartialValid(assignment)) return false;
            BitSet[] masks = new BitSet[size];
            for (int row = 0; row < size; row++) {
                if (assignment[row] >= 0) continue;
                masks[row] = candidates(row, assignment);
                int count = masks[row].cardinality();
                if (count == 0) return false;
                if (count == 1) {
                    assignment[row] = masks[row].nextSetBit(0);
                    changed = true;
                }
            }
            if (changed) continue;
            BitSet usedColumns = usedColumns(assignment);
            for (int column = 0; column < size; column++) {
                if (usedColumns.get(column)) continue;
                int onlyRow = -1;
                for (int row = 0; row < size; row++) {
                    if (assignment[row] < 0 && masks[row].get(column)) {
                        if (onlyRow >= 0) {
                            onlyRow = -2;
                            break;
                        }
                        onlyRow = row;
                    }
                }
                if (onlyRow == -1) return false;
                if (onlyRow >= 0) {
                    assignment[onlyRow] = column;
                    changed = true;
                    break;
                }
            }
            if (changed) continue;
            BitSet usedRegions = usedRegions(assignment);
            for (int target = 0; target < size; target++) {
                if (usedRegions.get(target)) continue;
                int onlyRow = -1, onlyColumn = -1, possibilities = 0;
                for (int row = 0; row < size && possibilities <= 1; row++) {
                    if (assignment[row] >= 0) continue;
                    BitSet mask = masks[row];
                    for (int column = mask.nextSetBit(0); column >= 0; column = mask.nextSetBit(column + 1)) {
                        if (region[row][column] == target) {
                            possibilities++;
                            onlyRow = row;
                            onlyColumn = column;
                            if (possibilities > 1) break;
                        }
                    }
                }
                if (possibilities == 0) return false;
                if (possibilities == 1) {
                    assignment[onlyRow] = onlyColumn;
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return isPartialValid(assignment);
    }

    private int chooseRow(int[] assignment) {
        int bestRow = -1, bestCount = Integer.MAX_VALUE;
        for (int row = 0; row < size; row++) {
            if (assignment[row] >= 0) continue;
            int count = candidates(row, assignment).cardinality();
            if (count < bestCount) {
                bestCount = count;
                bestRow = row;
            }
        }
        return bestRow;
    }

    private BitSet candidates(int row, int[] assignment) {
        BitSet mask = new BitSet(size);
        if (assignment[row] >= 0) {
            mask.set(assignment[row]);
            return mask;
        }
        BitSet usedColumns = usedColumns(assignment);
        BitSet usedRegions = usedRegions(assignment);
        for (int column = 0; column < size; column++) {
            if (usedColumns.get(column)) continue;
            if (usedRegions.get(region[row][column])) continue;
            if (row > 0 && assignment[row - 1] >= 0 && Math.abs(column - assignment[row - 1]) == 1) continue;
            if (row + 1 < size && assignment[row + 1] >= 0 && Math.abs(column - assignment[row + 1]) == 1) continue;
            mask.set(column);
        }
        return mask;
    }

    private BitSet usedColumns(int[] assignment) {
        BitSet used = new BitSet(size);
        for (int column : assignment) if (column >= 0) used.set(column);
        return used;
    }

    private BitSet usedRegions(int[] assignment) {
        BitSet used = new BitSet(size);
        for (int row = 0; row < size; row++) if (assignment[row] >= 0) used.set(region[row][assignment[row]]);
        return used;
    }

    private boolean isPartialValid(int[] assignment) {
        BitSet columns = new BitSet(size);
        BitSet regions = new BitSet(size);
        for (int row = 0; row < size; row++) {
            int column = assignment[row];
            if (column < 0) continue;
            if (column >= size) return false;
            int regionId = region[row][column];
            if (regionId < 0 || regionId >= size) return false;
            if (columns.get(column) || regions.get(regionId)) return false;
            columns.set(column);
            regions.set(regionId);
            if (row > 0 && assignment[row - 1] >= 0 && Math.abs(column - assignment[row - 1]) == 1) return false;
        }
        return true;
    }

    private boolean isCompleteValid(int[] assignment) {
        for (int value : assignment) if (value < 0) return false;
        return isPartialValid(assignment)
                && usedColumns(assignment).cardinality() == size
                && usedRegions(assignment).cardinality() == size;
    }
}
