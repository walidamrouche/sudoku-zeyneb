package com.zeyneb.sudoku.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Expert puzzle generator, same method as the paper notebook:
 * random full solution, cages of 2 to 5 cells (killer) or clue removal (classic),
 * then a solver rejects any puzzle with more than one solution or that is too easy.
 */
public final class Generator {
    /** Killer cage sizes drawn at random, as in the notebook's expert grids. */
    private static final int[] CAGE_SIZES = {2, 2, 2, 3, 3, 3, 3, 4, 4, 5};
    /** Minimum search effort for an expert killer (nodes to reach the first solution). */
    static final int KILLER_MIN_EFFORT = 150;
    private static final long UNIQUE_BUDGET = 300_000;

    private Generator() {}

    public static Puzzle killer(Random rng) {
        while (true) {
            int[] sol = fullSolution(rng);
            int[][] cages = makeCages(sol, rng);
            int[] sums = new int[cages.length];
            for (int c = 0; c < cages.length; c++) for (int i : cages[c]) sums[c] += sol[i];
            Solver s = new Solver(cages, sums);
            int[] none = new int[81];
            try {
                if (s.count(none, 2, UNIQUE_BUDGET) != 1) continue;
                s.count(none, 1, UNIQUE_BUDGET);
            } catch (Solver.Budget b) {
                continue;
            }
            if (s.nodes < KILLER_MIN_EFFORT) continue;
            return new Puzzle(Puzzle.KILLER, sol, none, cages, sums);
        }
    }

    public static Puzzle classic(Random rng) {
        while (true) {
            int[] sol = fullSolution(rng);
            int[] giv = sol.clone();
            Integer[] order = new Integer[81];
            for (int i = 0; i < 81; i++) order[i] = i;
            shuffle(order, rng);
            Solver s = new Solver(new int[0][], new int[0]);
            boolean ok = true;
            for (int i : order) {
                int v = giv[i];
                giv[i] = 0;
                try {
                    if (s.count(giv, 2, UNIQUE_BUDGET) != 1) giv[i] = v;
                } catch (Solver.Budget b) {
                    giv[i] = v;
                }
            }
            int clues = 0;
            for (int v : giv) if (v != 0) clues++;
            if (!ok || clues > 26) continue; // expert: 26 clues or fewer
            return new Puzzle(Puzzle.CLASSIC, sol, giv, new int[0][], new int[0]);
        }
    }

    static int[] fullSolution(Random rng) {
        int[] g = new int[81];
        fill(g, 0, new int[9], new int[9], new int[9], rng);
        return g;
    }

    private static boolean fill(int[] g, int i, int[] rows, int[] cols, int[] boxes, Random rng) {
        if (i == 81) return true;
        int r = Solver.ROW[i], c = Solver.COL[i], b = Solver.BOX[i];
        Integer[] digits = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        shuffle(digits, rng);
        for (int d : digits) {
            int bit = 1 << d;
            if (((rows[r] | cols[c] | boxes[b]) & bit) != 0) continue;
            g[i] = d;
            rows[r] |= bit; cols[c] |= bit; boxes[b] |= bit;
            if (fill(g, i + 1, rows, cols, boxes, rng)) return true;
            rows[r] ^= bit; cols[c] ^= bit; boxes[b] ^= bit;
        }
        g[i] = 0;
        return false;
    }

    private static int[] neighbours(int i) {
        List<Integer> n = new ArrayList<>(4);
        int r = i / 9, c = i % 9;
        if (r > 0) n.add(i - 9);
        if (r < 8) n.add(i + 9);
        if (c > 0) n.add(i - 1);
        if (c < 8) n.add(i + 1);
        int[] out = new int[n.size()];
        for (int k = 0; k < out.length; k++) out[k] = n.get(k);
        return out;
    }

    static int[][] makeCages(int[] sol, Random rng) {
        int[] owner = new int[81];
        java.util.Arrays.fill(owner, -1);
        List<List<Integer>> cages = new ArrayList<>();
        Integer[] order = new Integer[81];
        for (int i = 0; i < 81; i++) order[i] = i;
        shuffle(order, rng);
        for (int start : order) {
            if (owner[start] != -1) continue;
            int target = CAGE_SIZES[rng.nextInt(CAGE_SIZES.length)];
            List<Integer> cage = new ArrayList<>();
            cage.add(start);
            int used = 1 << sol[start];
            owner[start] = cages.size();
            while (cage.size() < target) {
                List<Integer> cand = new ArrayList<>();
                for (int c : cage) for (int n : neighbours(c))
                    if (owner[n] == -1 && ((used >> sol[n]) & 1) == 0) cand.add(n);
                if (cand.isEmpty()) break;
                int n = cand.get(rng.nextInt(cand.size()));
                cage.add(n); owner[n] = cages.size(); used |= 1 << sol[n];
            }
            cages.add(cage);
        }
        // merge single cells into a neighbouring cage when the digits allow it
        for (int ci = 0; ci < cages.size(); ci++) {
            List<Integer> cage = cages.get(ci);
            if (cage.size() != 1) continue;
            int i = cage.get(0);
            List<Integer> opts = new ArrayList<>();
            for (int n : neighbours(i)) {
                int t = owner[n];
                if (t == ci || cages.get(t).size() >= 5) continue;
                boolean clash = false;
                for (int j : cages.get(t)) if (sol[j] == sol[i]) clash = true;
                if (!clash) opts.add(t);
            }
            if (!opts.isEmpty()) {
                int t = opts.get(rng.nextInt(opts.size()));
                cages.get(t).add(i); owner[i] = t; cage.clear();
            }
        }
        List<int[]> out = new ArrayList<>();
        for (List<Integer> c : cages) {
            if (c.isEmpty()) continue;
            int[] a = new int[c.size()];
            for (int k = 0; k < a.length; k++) a[k] = c.get(k);
            java.util.Arrays.sort(a);
            out.add(a);
        }
        return out.toArray(new int[0][]);
    }

    private static <T> void shuffle(T[] a, Random rng) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }
}
