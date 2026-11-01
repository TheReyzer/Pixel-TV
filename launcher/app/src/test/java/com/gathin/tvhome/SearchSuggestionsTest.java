package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class SearchSuggestionsTest {
    private MediaRepository.MediaItem item(String title,String original) {
        MediaRepository.MediaItem item=new MediaRepository.MediaItem("tt1234567",title,"series","8.0","2024","Drama","","","","","",0);
        item.originalTitle=original; return item;
    }
    @Test public void keepsSpokenQueryAndBrazilianNameTogether() {
        List<String> titles=SearchSuggestions.titles("The Handmaid’s Tale",Arrays.asList("The Handmaid’s Tale","Handmaid"),Arrays.asList(item("O Conto da Aia","The Handmaid’s Tale")));
        assertEquals("The Handmaid’s Tale",titles.get(0)); assertEquals("O Conto da Aia",titles.get(1)); assertEquals(3,titles.size());
    }
    @Test public void boundsPillsWithoutDroppingLocalizedName() {
        List<String> spoken=new ArrayList<>(); for(int i=0;i<15;i++) spoken.add("Interpretation "+i);
        List<String> titles=SearchSuggestions.titles("Rookie",spoken,Arrays.asList(item("O Novato","The Rookie")));
        assertEquals(8,titles.size()); assertTrue(titles.contains("O Novato"));
    }
    @Test public void deduplicatesAccentCaseAndWhitespace() {
        List<String> titles=SearchSuggestions.titles("  Série  ",Arrays.asList("SERIE","série"),Arrays.asList(item("Série","Serie")));
        assertEquals(Collections.singletonList("Série"),titles);
    }
    @Test public void noFakeSuggestionsForEmptyResults() {
        assertTrue(SearchSuggestions.titles("Something",Collections.emptyList(),Collections.emptyList()).isEmpty());
    }
}
