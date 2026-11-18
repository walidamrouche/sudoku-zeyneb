package com.zeyneb.sudoku.engine;

/**
 * Backtracking solver for killer and classic sudoku (bitmasks, most-constrained cell first).
 * Counts solutions up to a limit, with a node budget so that a hard case is rejected instead of hanging.
 */
public final class Solver {
    public static final int[] ROW = new int[81], COL = new int[81], BOX = new int[81];
    static {
        for (int i = 0; i < 81; i++) {
            ROW[i] = i / 9;
            COL[i] = i % 9;
            BOX[i] = (i / 27) * 3 + (i % 9) / 3;
        }
    }
    private static final int ALL = 0x3FE; // bits 1..9
    /** COMBOS[k][sum] = masks of every set of k distinct digits adding up to sum. */
    private static final int[][][] COMBOS = new int[10][46][];
    static {
        int[][] count = new int[10][46];
        for (int m = 0; m < 1024; m += 2) {
            int k = Integer.bitCount(m), s = 0;
            for (int d = 1; d <= 9; d++) if ((m >> d & 1) != 0) s += d;
            count[k][s]++;
        }
        int[][] fill = new int[10][46];
        for (int k = 0; k < 10; k++) for (int s = 0; s < 46; s++) COMBOS[k][s] = new int[count[k][s]];
        for (int m = 0; m < 1024; m += 2) {
            int k = Integer.bitCount(m), s = 0;
            for (int d = 1; d <= 9; d++) if ((m >> d & 1) != 0) s += d;
            COMBOS[k][s][fill[k][s]++] = m;
        }
    }

    public static final class Budget extends RuntimeException {
        Budget() { super(null, null, false, false); }
    }

    private final int[] owner;      // cage of each cell, -1 if none
    private final int[] cageSize;
    private final int[] sums;
    private int[] g;
    private int[] rows, cols, boxes, cageUsed, cageLeft, cageSum;
    private int found, limit;
    public long nodes;
    private long maxNodes;
    public int[] firstSolution;

    /** Killer: cages and sums. Classic: pass empty arrays. */
    public Solver(int[][] cages, int[] sums) {
        owner = new int[81];
        java.util.Arrays.fill(owner, -1);
        cageSize = new int[cages.length];
        for (int c = 0; c < cages.length; c++) {
            cageSize[c] = cages[c].length;
            for (int i : cages[c]) owner[i] = c;
        }
        this.sums = sums;
    }

    /** Number of solutions found (stops at limit). Throws Budget if more than maxNodes are visited. */
    public int count(int[] givens, int limit, long maxNodes) {
        this.limit = limit;
        this.maxNodes = maxNodes;
        nodes = 0;
        found = 0;
        firstSolution = null;
        g = new int[81];
        rows = new int[9]; cols = new int[9]; boxes = new int[9];
        int n = cageSize.length;
        cageUsed = new int[n]; cageLeft = cageSize.clone(); cageSum = new int[n];
        for (int i = 0; i < 81; i++) {
            int d = givens[i];
            if (d == 0) continue;
            int bit = 1 << d;
            if (((rows[ROW[i]] | cols[COL[i]] | boxes[BOX[i]]) & bit) != 0) return 0;
            place(i, d);
        }
        search();
        return found;
    }

    private void place(int i, int d) {
        int bit = 1 << d;
        g[i] = d;
        rows[ROW[i]] |= bit; cols[COL[i]] |= bit; boxes[BOX[i]] |= bit;
        int c = owner[i];
        if (c >= 0) { cageUsed[c] |= bit; cageLeft[c]--; cageSum[c] += d; }
    }

    private void unplace(int i, int d) {
        int bit = 1 << d;
        g[i] = 0;
        rows[ROW[i]] ^= bit; cols[COL[i]] ^= bit; boxes[BOX[i]] ^= bit;
        int c = owner[i];
        if (c >= 0) { cageUsed[c] ^= bit; cageLeft[c]++; cageSum[c] -= d; }
    }

    private int cageAllowed(int c) {
        int k = cageLeft[c], rem = sums[c] - cageSum[c];
        if (rem < 0 || rem > 45) return 0;
        int used = cageUsed[c], m = 0;
        for (int combo : COMBOS[k][rem]) if ((combo & used) == 0) m |= combo;
        return m;
    }

    private boolean search() {
        if (++nodes > maxNodes) throw new Budget();
        int best = -1, bestMask = 0, bestN = 10;
        int[] cache = new int[cageSize.length];
        java.util.Arrays.fill(cache, -1);
        for (int i = 0; i < 81; i++) {
            if (g[i] != 0) continue;
            int m = ALL & ~(rows[ROW[i]] | cols[COL[i]] | boxes[BOX[i]]);
            int c = owner[i];
            if (c >= 0) {
                if (cache[c] < 0) cache[c] = cageAllowed(c);
                m &= cache[c];
            }
            int n = Integer.bitCount(m);
            if (n < bestN) {
                best = i; bestMask = m; bestN = n;
                if (n <= 1) break;
            }
        }
        if (best < 0) {
            found++;
            if (firstSolution == null) firstSolution = g.clone();
            return found >= limit;
        }
        if (bestN == 0) return false;
        for (int d = 1; d <= 9; d++) {
            if ((bestMask >> d & 1) == 0) continue;
            place(best, d);
            boolean stop = search();
            unplace(best, d);
            if (stop) return true;
        }
        return false;
    }
}
