package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class TvMotionTest {
    @Test public void navigationDurationIsVisibleButBounded() {
        assertEquals(160,TvSmoothScroller.duration(1,320));
        assertTrue(TvSmoothScroller.duration(300,320)>160);
        assertEquals(300,TvSmoothScroller.duration(10000,320));
        assertEquals(TvSmoothScroller.duration(300,320),TvSmoothScroller.duration(-300,320));
    }
    @Test public void distantTargetsOnlyPrepareTheLastRow() {
        assertEquals(-1,TvSmoothScroller.nearby(0,12,6));
        assertEquals(6,TvSmoothScroller.nearby(100,0,6));
        assertEquals(94,TvSmoothScroller.nearby(0,100,6));
        assertTrue(TvSmoothScroller.nearby(Integer.MAX_VALUE-1,0,3)>=0);
    }
    @Test public void focusedPosterUsesOneScrollThatAlreadyIncludesItsExpandedEdges() {
        assertEquals(0,TvSmoothScroller.verticalOffset(50,450,20,650,16));
        assertEquals(-26,TvSmoothScroller.verticalOffset(10,410,20,650,16));
        assertEquals(26,TvSmoothScroller.verticalOffset(260,660,20,650,16));
        assertEquals(0,TvSmoothScroller.verticalOffset(36,634,20,650,16));
        assertEquals(0,TvSmoothScroller.verticalOffset(22,442,0,656,16));
    }
}
