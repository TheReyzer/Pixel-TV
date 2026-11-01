package com.gathin.tvhome;

import android.media.tv.TvInputManager;

final class HdmiConnectionPolicy {
    static boolean hasActiveSource(int state, int connectedPorts, int portCount) {
        return state == TvInputManager.INPUT_STATE_CONNECTED
                && connectedPorts > 0 && portCount > 0
                && !(portCount > 1 && connectedPorts == portCount);
    }
}
