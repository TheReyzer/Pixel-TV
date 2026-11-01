package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class VoiceTitleQueryTest {
    @Test public void emptyFinalResultPreservesSpeechAlreadyRecognized() {
        assertEquals(Arrays.asList("The Rookie"),VoiceTitleQuery.results(Arrays.asList(" "),Arrays.asList("The Rookie")));
        assertEquals(Arrays.asList("Ainda Estou Aqui"),VoiceTitleQuery.results(Arrays.asList("Ainda Estou Aqui"),Arrays.asList("ainda")));
        assertTrue(VoiceTitleQuery.results(null,null).isEmpty());
    }
    @Test public void keepsAlternativeTitlesWithoutDuplicatesOrUnboundedProviderData() {
        assertEquals(Arrays.asList("The Rock","The Rookie","Hulk"),VoiceTitleQuery.alternatives(Arrays.asList("The Rock","the rock"," The Rookie ",null,"Hulk")));
        assertEquals(5,VoiceTitleQuery.alternatives(Arrays.asList("aa","bb","cc","dd","ee","ff")).size());
    }
    @Test public void acceptsPortugueseAndEnglishWithoutChangingTitle() {
        assertEquals("Ainda Estou Aqui",VoiceTitleQuery.first(Arrays.asList(null," ","a"," Ainda Estou Aqui ")));
        assertEquals("The Walking Dead",VoiceTitleQuery.first(Arrays.asList("The Walking Dead","alternativa")));
    }
    @Test public void emptyAndOversizedProviderResultsAreBounded() {
        assertEquals("",VoiceTitleQuery.first(null));
        assertEquals("",VoiceTitleQuery.first(Arrays.asList(""," ",null)));
        assertEquals(100,VoiceTitleQuery.first(Arrays.asList("a".repeat(200))).length());
    }
}
