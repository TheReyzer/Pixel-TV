package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkTransitionTest {
    @Test public void delayedLossDoesNotMarkOfflineWhenAUsableNetworkIsAlreadyActive() {
        assertTrue(MainActivity.hasUsableNetwork(true, true));
        assertFalse(MainActivity.hasUsableNetwork(false, true));
        assertFalse(MainActivity.hasUsableNetwork(true, false));
    }
}
