package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class RecommendationRailTest {
    @Test public void visibleArtworkLoadsBeforeHiddenPreview() {
        assertArrayEquals(new boolean[]{true,false},RecommendationRail.artworkLoadOrder(true));
        assertArrayEquals(new boolean[]{false,true},RecommendationRail.artworkLoadOrder(false));
    }
}
