package com.zeyneb.sudoku.engine;

/** A generated puzzle. Killer puzzles have cages and sums; classic puzzles have givens. */
public final class Puzzle {
    public static final int KILLER = 0;
    public static final int CLASSIC = 1;

    public final int mode;
    public final int[] solution;   // 81 digits, row by row
    public final int[] givens;     // 81 values, 0 = empty (all zero for killer)
    public final int[][] cages;    // killer only: cell indices of each cage
    public final int[] sums;       // killer only: sum of each cage

    public Puzzle(int mode, int[] solution, int[] givens, int[][] cages, int[] sums) {
        this.mode = mode;
        this.solution = solution;
        this.givens = givens;
        this.cages = cages;
        this.sums = sums;
    }

    /** Compact text form used for saving: mode;solution;givens;cage|cage...;sums */
    public String encode() {
        StringBuilder b = new StringBuilder();
        b.append(mode).append(';');
        for (int v : solution) b.append(v);
        b.append(';');
        for (int v : givens) b.append(v);
        b.append(';');
        for (int c = 0; c < cages.length; c++) {
            if (c > 0) b.append('|');
            for (int k = 0; k < cages[c].length; k++) {
                if (k > 0) b.append(',');
                b.append(cages[c][k]);
            }
        }
        b.append(';');
        for (int s = 0; s < sums.length; s++) {
            if (s > 0) b.append(',');
            b.append(sums[s]);
        }
        return b.toString();
    }

    public static Puzzle decode(String text) {
        String[] p = text.split(";", -1);
        int mode = Integer.parseInt(p[0]);
        int[] sol = new int[81], giv = new int[81];
        for (int i = 0; i < 81; i++) {
            sol[i] = p[1].charAt(i) - '0';
            giv[i] = p[2].charAt(i) - '0';
        }
        int[][] cages = new int[0][];
        int[] sums = new int[0];
        if (!p[3].isEmpty()) {
            String[] cs = p[3].split("\\|");
            cages = new int[cs.length][];
            for (int c = 0; c < cs.length; c++) {
                String[] cells = cs[c].split(",");
                cages[c] = new int[cells.length];
                for (int k = 0; k < cells.length; k++) cages[c][k] = Integer.parseInt(cells[k]);
            }
            String[] ss = p[4].split(",");
            sums = new int[ss.length];
            for (int s = 0; s < ss.length; s++) sums[s] = Integer.parseInt(ss[s]);
        }
        return new Puzzle(mode, sol, giv, cages, sums);
    }
}
