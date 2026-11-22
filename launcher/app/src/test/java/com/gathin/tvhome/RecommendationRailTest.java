package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RecommendationRailTest {
    @Test public void visibleArtworkLoadsBeforeHiddenPreview() {
        assertArrayEquals(new boolean[]{true,false},RecommendationRail.artworkLoadOrder(true));
        assertArrayEquals(new boolean[]{false,true},RecommendationRail.artworkLoadOrder(false));
    }
    @Test public void typeBadgeIsShownOnlyWhenMixedFormatsNeedContext() {
        MediaRepository.MediaItem movie=new MediaRepository.MediaItem("tt1234567","Filme","movie","","2026","Drama","","","","","",1);
        MediaRepository.MediaItem series=new MediaRepository.MediaItem("tt1234568","Série","series","8","2026","Drama","","","","","",1);
        assertEquals("Filme",RecommendationRail.badgeText(movie,true));
        assertEquals("",RecommendationRail.badgeText(movie,false));
        assertEquals("Série · ★ 8",RecommendationRail.badgeText(series,true));
    }
}
