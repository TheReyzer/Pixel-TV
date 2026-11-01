package com.gathin.tvhome;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class SynopsisLanguageTest {
    @Test public void brazilianDescriptionMatchesExactImdbAndNeverUsesAnotherSearchResult() throws Exception {
        String response="{\"data\":{\"popularTitles\":{\"edges\":[{\"node\":{\"content\":{\"externalIds\":{\"imdbId\":\"tt1000002\"},\"shortDescription\":\"Outro filme\"}}},{\"node\":{\"content\":{\"externalIds\":{\"imdbId\":\"tt1000001\"},\"shortDescription\":\" Uma família em busca de aventuras. \"}}}]}}}";
        assertEquals("Uma família em busca de aventuras.",MediaRepository.parseBrazilianSynopsis(response,"tt1000001"));
        assertEquals("",MediaRepository.parseBrazilianSynopsis(response,"tt1000003"));
        assertEquals("",MediaRepository.parseBrazilianSynopsis("{\"errors\":[{}]}","tt1000001"));
    }
    @Test public void rawAndLegacySynopsisNeverBecomeVisibleByGuessingAccentedNames() throws Exception {
        JSONObject legacy=new JSONObject().put("imdbId","tt1000001").put("synopsisPt","François and his family move to a new city.");
        MediaRepository.MediaItem item=MediaRepository.MediaItem.fromJson(legacy);
        assertEquals("",item.visibleSynopsis()); assertFalse(item.synopsisInPtBr);
        assertEquals("François and his family move to a new city.",item.synopsisPt);
        item.synopsisPt="François e sua família se mudam para uma nova cidade."; item.synopsisInPtBr=true;
        MediaRepository.MediaItem restored=MediaRepository.MediaItem.fromJson(item.toJson());
        assertEquals(item.synopsisPt,restored.visibleSynopsis());
    }
    @Test public void translationCombinesSegmentsAndRejectsUntranslatedEnglishOrEmptyResults() throws Exception {
        assertEquals("Uma família se muda.",MediaRepository.parsePtBrTranslation("[[[\"Uma família \",\"A family \"],[\"se muda.\",\"moves.\"]],null,\"en\"]","A family moves."));
        assertEquals("",MediaRepository.parsePtBrTranslation("[[[\"A family moves.\"]],null,\"en\"]","A family moves."));
        assertEquals("",MediaRepository.parsePtBrTranslation("[null,null,\"en\"]","A family moves."));
        assertEquals("Uma família se muda.",MediaRepository.parsePtBrTranslation("[[[\"Uma família se muda.\"]],null,\"pt\"]","Uma família se muda."));
        assertThrows(Exception.class,()->MediaRepository.parsePtBrTranslation("<html>error</html>","A family moves."));
    }
}
