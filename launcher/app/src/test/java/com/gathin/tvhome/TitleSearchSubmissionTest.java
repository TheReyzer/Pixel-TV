package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class TitleSearchSubmissionTest {
    @Test public void successfulSearchStaysHiddenUntilTextOrFormatChanges() {
        TitleSearchSubmission state=new TitleSearchSubmission();
        assertFalse(state.changed("","movie")); assertFalse(state.changed("a","movie"));
        assertTrue(state.changed("The Rookie","movie")); state.completed("The Rookie","movie");
        assertFalse(state.changed("The Rookie ","movie"));
        assertTrue(state.changed("The Rookie","series"));
        assertTrue(state.changed("Ainda Estou Aqui","movie"));
        state.completed("Ainda Estou Aqui","movie"); assertFalse(state.changed("Ainda Estou Aqui","movie"));
    }
    @Test public void returningToTheSubmittedTextDoesNotNeedAnotherRequest() {
        TitleSearchSubmission state=new TitleSearchSubmission(); state.completed("Rookie","series");
        assertTrue(state.changed("Rooki","series")); assertFalse(state.changed("Rookie","series"));
    }
}
