package com.gathin.tvhome;

import org.junit.Test;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;

public class TitleSearchResultsTest {
    private MediaRepository.MediaItem item(int index) { return new MediaRepository.MediaItem("tt"+(9000000+index),"Título "+index,"movie","8","2020","Drama","","","","","",1); }
    @Test public void pagesAppendWithoutDuplicatesOrReorderingExistingResults() {
        TitleSearchResults state=new TitleSearchResults();state.reset("Star","all");
        MediaRepository.MediaItem first=item(1),second=item(2);
        state.append(new MediaRepository.SearchPage(Arrays.asList(first,second),"MjA=",true));
        state.append(new MediaRepository.SearchPage(Arrays.asList(item(1),item(3)),"NDA=",true));
        assertEquals(3,state.items.size());assertSame(first,state.items.get(0));assertSame(second,state.items.get(1));assertEquals("NDA=",state.cursor);assertTrue(state.more);
    }
    @Test public void resultsAreRevealedInSmallWindowsAndNextQueryResetsCursor() {
        TitleSearchResults state=new TitleSearchResults();state.reset("Star","movie");List<MediaRepository.MediaItem> items=new ArrayList<>();for(int i=0;i<40;i++)items.add(item(i));
        state.append(new MediaRepository.SearchPage(items,"NDA=",true));assertEquals(12,state.reveal());assertFalse(state.nearEnd(7));assertTrue(state.nearEnd(8));assertEquals(24,state.reveal());
        assertTrue(state.matches(" Star ","movie"));assertFalse(state.matches("Star","series"));
        state.reset("Rocky","series");assertEquals(0,state.shown);assertTrue(state.items.isEmpty());assertEquals("",state.cursor);assertFalse(state.more);
    }
    @Test public void repeatedOrFinalCursorStopsAutomaticRequestsWithoutLosingResults() {
        TitleSearchResults state=new TitleSearchResults();state.append(new MediaRepository.SearchPage(Arrays.asList(item(1)),"same",true));
        state.append(new MediaRepository.SearchPage(Arrays.asList(item(2)),"same",true));assertFalse(state.more);assertEquals(2,state.items.size());
        state.append(new MediaRepository.SearchPage(Collections.emptyList(),"",false));assertFalse(state.more);assertEquals(2,state.items.size());
    }
    @Test public void serverCursorAndFormatsAreReadTogether() throws Exception {
        String body="{\"data\":{\"popularTitles\":{\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"NDA=\"},\"edges\":[{\"node\":{\"__typename\":\"Movie\",\"content\":{\"title\":\"Filme\",\"externalIds\":{\"imdbId\":\"tt1234567\"}}}},{\"node\":{\"__typename\":\"Show\",\"content\":{\"title\":\"Série\",\"externalIds\":{\"imdbId\":\"tt7654321\"}}}}]}}}";
        MediaRepository.SearchPage both=MediaRepository.parseSearchPage(body,"all");assertEquals(2,both.items.size());assertTrue(both.more);assertEquals("NDA=",both.cursor);
        assertEquals("series",MediaRepository.parseSearchPage(body,"series").items.get(0).type);
        JSONObject root=new JSONObject(body);root.getJSONObject("data").getJSONObject("popularTitles").getJSONObject("pageInfo").put("hasNextPage",false);
        assertFalse(MediaRepository.parseSearchPage(root.toString(),"all").more);
    }
    @Test public void uniqueCatalogAppendKeepsBothFormatsAndRejectsMalformedEntries() {
        MediaRepository.MediaItem movie=item(1),series=new MediaRepository.MediaItem(item(2).imdbId,"Série","series","8","2020","Drama","","","","","",1);
        MediaRepository.MediaItem invalid=new MediaRepository.MediaItem("invalid","Inválido","movie","","","","","","","","",1);
        List<MediaRepository.MediaItem> list=MediaRepository.appendUnique(Arrays.asList(movie),Arrays.asList(item(1),new MediaRepository.MediaItem(movie.imdbId,"Classificação divergente","series","8","2020","Drama","","","","","",1),series,invalid));assertEquals(2,list.size());assertSame(movie,list.get(0));assertSame(series,list.get(1));
    }
}
