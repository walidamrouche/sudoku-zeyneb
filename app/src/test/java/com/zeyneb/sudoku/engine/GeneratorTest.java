package com.zeyneb.sudoku.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class GeneratorTest {
    @Test
    public void killerPuzzlesHaveOneSolution() {
        for (int k = 0; k < 5; k++) {
            Puzzle p = Generator.killer(new Random(k));
            Solver s = new Solver(p.cages, p.sums);
            assertEquals(1, s.count(new int[81], 2, 5_000_000));
            assertArrayEquals(p.solution, s.firstSolution);
            assertTrue(p.cages.length >= 18);
            assertEquals(p.encode(), Puzzle.decode(p.encode()).encode());
        }
    }

    @Test
    public void classicPuzzlesHaveOneSolution() {
        for (int k = 0; k < 5; k++) {
            Puzzle p = Generator.classic(new Random(100 + k));
            Solver s = new Solver(new int[0][], new int[0]);
            assertEquals(1, s.count(p.givens, 2, 5_000_000));
            assertArrayEquals(p.solution, s.firstSolution);
        }
    }
}
