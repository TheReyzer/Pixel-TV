package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceCaptureWindowTest {
    @Test public void prematureSilenceCanRetryForTwelveSecondsOnly() {
        VoiceCaptureWindow window=new VoiceCaptureWindow(1000);
        assertTrue(window.retrySilence(1001)); assertTrue(window.retrySilence(6000));
        assertTrue(window.retrySilence(12999)); assertFalse(window.retrySilence(13000));
        assertFalse(window.retrySilence(19000));
    }
    @Test public void speechOrPartialResultsNeverRestartTheUtterance() {
        VoiceCaptureWindow window=new VoiceCaptureWindow(1000); window.speechStarted();
        assertFalse(window.retrySilence(1001)); assertFalse(window.retrySilence(13000));
    }
}
