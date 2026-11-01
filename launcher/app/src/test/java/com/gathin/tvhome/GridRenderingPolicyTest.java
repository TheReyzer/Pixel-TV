package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class GridRenderingPolicyTest {
    @Test public void gridBackdropIsLighterWhileHomeKeepsItsResolution() {
        assertEquals(640,MediaBackdropController.width(true));
        assertEquals(960,MediaBackdropController.width(false));
    }
    @Test public void postersFillSixColumnsAndFocusedHeightFitsTheViewport() {
        for (int[] viewport:new int[][]{{1728,716},{1728,656},{1280,440},{864,350}}) {
            int margin=12;
            int[] size=DiscoveryGrid.cellSize(viewport[0],viewport[1],margin);
            assertEquals(viewport[0]/6-2*margin,size[0]);
            assertTrue(size[1]*DiscoveryGrid.FOCUS_SCALE+2*margin<=viewport[1]);
            assertEquals(size[1],size[2]);
            assertEquals(1.5,size[1]/(double)size[0],.01);
        }
    }
    @Test public void aVeryShortViewportStillContainsTheWholeFocusedPoster() {
        int[] size=DiscoveryGrid.cellSize(1728,220,12);
        assertTrue(size[0]<264);
        assertTrue(size[1]*DiscoveryGrid.FOCUS_SCALE+24<=220);
    }
    @Test public void retryScheduleBacksOffAndCapsFrequencyWhileVisible() {
        assertEquals(1800,MediaRepository.artworkRetryDelay(0));
        assertTrue(MediaRepository.artworkRetryDelay(1)>MediaRepository.artworkRetryDelay(0));
        assertTrue(MediaRepository.artworkRetryDelay(2)>MediaRepository.artworkRetryDelay(1));
        assertEquals(30000,MediaRepository.artworkRetryDelay(3));
        assertEquals(60000,MediaRepository.artworkRetryDelay(4));
        assertEquals(60000,MediaRepository.artworkRetryDelay(50));
    }
    @Test public void unavailableRatingsDoNotCountAsScores() {
        for (String value:new String[]{"","—","NaN","Infinity","0","-1","11"}) assertFalse(item(value).hasRating());
        assertTrue(item("8.3").hasRating()); assertTrue(item("10").hasRating());
    }
    @Test public void backdropTintUsesOnlyOpaqueSamples() {
        assertEquals(0xff7f007f,MediaBackdropController.meanColor(new int[]{0xffff0000,0xff0000ff}));
        assertEquals(0xff123456,MediaBackdropController.meanColor(new int[]{0x00000000,0xff123456}));
        assertEquals(0xff121216,MediaBackdropController.meanColor(new int[]{}));
    }
    private MediaRepository.MediaItem item(String rating) { return new MediaRepository.MediaItem("tt7587890","O Novato","series",rating,"2018","Drama","","","","","",0); }
}
