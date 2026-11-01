package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class SearchMotionTest {
    @Test public void keepsMotionLegibleAtHalfScale() {
        assertEquals(640,TvSmoothScroller.animationDuration(320,.5f));
        assertEquals(320,TvSmoothScroller.animationDuration(320,1f));
        assertEquals(320,TvSmoothScroller.animationDuration(320,0f));
        assertEquals(320,TvSmoothScroller.animationDuration(320,2f));
    }
    @Test public void raisesRelatedCardsByHalfThePreviousTopGapWithoutClippingTheHeading() {
        int scroll=TvSmoothScroller.relatedStart(600,860,420,12,4,32);
        assertEquals(552,scroll);assertTrue(600-scroll-32>=12);assertTrue(860-scroll<=416);
        assertEquals(0,TvSmoothScroller.relatedStart(0,260,420,12,4,32));
        assertEquals(scroll+100,TvSmoothScroller.relatedStart(700,960,420,12,4,32));
    }
}
