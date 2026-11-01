package com.gathin.tvhome;

import android.speech.SpeechRecognizer;
import org.junit.Test;
import static org.junit.Assert.*;

public class TitleSearchControllerTest {
    @Test public void onlyAudioAndMissingSpeechOfferPairingHelp() {
        assertTrue(TitleSearchController.needsPairingHelp(SpeechRecognizer.ERROR_AUDIO));
        assertTrue(TitleSearchController.needsPairingHelp(SpeechRecognizer.ERROR_NO_MATCH));
        assertTrue(TitleSearchController.needsPairingHelp(SpeechRecognizer.ERROR_SPEECH_TIMEOUT));
        assertFalse(TitleSearchController.needsPairingHelp(SpeechRecognizer.ERROR_NETWORK));
        assertFalse(TitleSearchController.needsPairingHelp(SpeechRecognizer.ERROR_NETWORK_TIMEOUT));
        assertFalse(TitleSearchController.needsPairingHelp(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS));
        assertFalse(TitleSearchController.needsPairingHelp(SpeechRecognizer.ERROR_RECOGNIZER_BUSY));
    }
    @Test public void shrinksOnlyTheInputByTenPercent() {
        assertEquals(454,TitleSearchController.editorWidth(480,220));
        assertEquals(220,TitleSearchController.editorWidth(220,220));
        assertEquals(180,TitleSearchController.editorWidth(180,220));
        assertEquals(0,TitleSearchController.editorWidth(0,220));
    }
}
