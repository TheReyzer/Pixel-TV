package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiscoveryFlowLayoutTest {
    @Test public void variableWidthChipsShareRowsUntilAvailableWidthIsConsumed() {
        assertArrayEquals(new int[]{0,0,1,1,2},DiscoveryFlowLayout.wrapLines(new int[]{100,130,80,160,250},250,8));
        assertArrayEquals(new int[]{0,0,0,0,1},DiscoveryFlowLayout.wrapLines(new int[]{100,130,80,160,250},500,8));
    }
    @Test public void exactFitDoesNotWrapAndNarrowSpaceWrapsBetweenWholeItems() {
        assertArrayEquals(new int[]{0,0},DiscoveryFlowLayout.wrapLines(new int[]{100,100},208,8));
        assertArrayEquals(new int[]{0,1},DiscoveryFlowLayout.wrapLines(new int[]{100,100},207,8));
        assertArrayEquals(new int[0],DiscoveryFlowLayout.wrapLines(new int[0],100,8));
    }
}
