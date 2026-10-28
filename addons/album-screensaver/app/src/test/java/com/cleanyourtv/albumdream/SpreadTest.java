package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpreadTest {

    @Test public void weightGrowsWithTheShareOfPortraits() {
        assertEquals(Spread.MIN_WEIGHT, Spread.landscapeWeight(0, 10), 1e-6);
        assertEquals(Spread.MAX_WEIGHT, Spread.landscapeWeight(10, 0), 1e-6);
        assertEquals(1.625f, Spread.landscapeWeight(5, 5), 1e-6);
        assertTrue(Spread.landscapeWeight(8, 2) > Spread.landscapeWeight(2, 8));
        assertEquals(1.625f, Spread.landscapeWeight(0, 0), 1e-6);
    }

    @Test public void widthsAddUpAndLandscapeIsWider() {
        float[] w = Spread.columnWidths(new boolean[]{false, true, false}, 2f, 1200f);
        assertEquals(300f, w[0], 1e-3);
        assertEquals(600f, w[1], 1e-3);
        assertEquals(300f, w[2], 1e-3);
        assertEquals(1200f, w[0] + w[1] + w[2], 1e-3);
    }

    @Test public void equalColumnsWhenNothingIsLandscape() {
        float[] w = Spread.columnWidths(new boolean[]{false, false, false}, 2f, 900f);
        assertEquals(300f, w[0], 1e-3);
        assertEquals(300f, w[2], 1e-3);
    }
}
