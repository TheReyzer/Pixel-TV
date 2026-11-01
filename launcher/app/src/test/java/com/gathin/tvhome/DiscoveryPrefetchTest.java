package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DiscoveryPrefetchTest {
    @Test public void warmsOnlyTheNextTwoRowsInTheActualScrollDirection() {
        java.util.List<Integer> next=new java.util.ArrayList<>(); for (int i=12;i<24;i++) next.add(i);
        assertEquals(next,MediaRepository.discoveryPrefetchIndices(84,0,11,1));
        java.util.List<Integer> previous=new java.util.ArrayList<>(); for (int i=23;i>=12;i--) previous.add(i);
        assertEquals(previous,MediaRepository.discoveryPrefetchIndices(84,24,35,-1));
        assertEquals(Arrays.asList(12),MediaRepository.discoveryPrefetchIndices(13,0,11,1));
        assertTrue(MediaRepository.discoveryPrefetchIndices(84,72,83,1).isEmpty());
        assertTrue(MediaRepository.discoveryPrefetchIndices(84,0,11,-1).isEmpty());
        assertTrue(MediaRepository.discoveryPrefetchIndices(0,0,0,1).isEmpty());
    }
    @Test public void cachedArtworkPreservesAspectRatioAndCoversCardWithoutUpscaling() {
        assertArrayEquals(new int[]{498,280},MediaRepository.scaledImageSize(1920,1080,360,280));
        assertArrayEquals(new int[]{640,360},MediaRepository.scaledImageSize(1920,1080,640,280));
        assertArrayEquals(new int[]{320,180},MediaRepository.scaledImageSize(320,180,640,280));
    }
}
