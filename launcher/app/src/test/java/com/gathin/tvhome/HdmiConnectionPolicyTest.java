package com.gathin.tvhome;

import android.media.tv.TvInputManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class HdmiConnectionPolicyTest {
    @Test public void acceptsAnActiveSourceOnOnePort() {
        assertTrue(HdmiConnectionPolicy.hasActiveSource(TvInputManager.INPUT_STATE_CONNECTED, 1, 3));
        assertTrue(HdmiConnectionPolicy.hasActiveSource(TvInputManager.INPUT_STATE_CONNECTED, 1, 1));
    }

    @Test public void rejectsStandbyAndDisconnectedEvenWhenAnotherPortIsActive() {
        assertFalse(HdmiConnectionPolicy.hasActiveSource(TvInputManager.INPUT_STATE_CONNECTED_STANDBY, 1, 3));
        assertFalse(HdmiConnectionPolicy.hasActiveSource(TvInputManager.INPUT_STATE_DISCONNECTED, 1, 3));
    }

    @Test public void suppressesUnreliableOrMissingPortStates() {
        assertFalse(HdmiConnectionPolicy.hasActiveSource(TvInputManager.INPUT_STATE_CONNECTED, 3, 3));
        assertFalse(HdmiConnectionPolicy.hasActiveSource(TvInputManager.INPUT_STATE_CONNECTED, 0, 3));
        assertFalse(HdmiConnectionPolicy.hasActiveSource(TvInputManager.INPUT_STATE_CONNECTED, 0, 0));
    }
}
