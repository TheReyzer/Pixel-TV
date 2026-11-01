package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TitleSearchLocalizationTest {
    private JSONObject node(String kind,String id,String title,String original) throws Exception {
        return new JSONObject().put("node",new JSONObject().put("__typename",kind).put("content",new JSONObject().put("title",title).put("originalTitle",original).put("originalReleaseYear",2017).put("externalIds",new JSONObject().put("imdbId",id))));
    }
    private String response(JSONArray edges) throws Exception { return new JSONObject().put("data",new JSONObject().put("popularTitles",new JSONObject().put("edges",edges))).toString(); }
    @Test public void localizedSearchFiltersFormatAndRequiresImdb() throws Exception {
        List<MediaRepository.MediaItem> items=MediaRepository.parseBrazilianSearch(response(new JSONArray()
            .put(node("Show","tt5834204","O Conto da Aia","The Handmaid's Tale"))
            .put(node("Movie","tt0085334","Uma História de Natal","A Christmas Story"))
            .put(node("Show","invalid","Outro","Other"))),"series");
        assertEquals(1,items.size()); assertEquals("O Conto da Aia",items.get(0).title); assertEquals("The Handmaid's Tale",items.get(0).originalTitle);
    }
    @Test public void mergesOnlyByImdbPreservingMetadataAndLocalizedName() throws Exception {
        List<MediaRepository.MediaItem> br=MediaRepository.parseBrazilianSearch(response(new JSONArray().put(node("Show","tt5834204","O Conto da Aia","The Handmaid's Tale"))),"series");
        List<MediaRepository.MediaItem> original=MediaRepository.parseDiscoveryCatalog("{\"metas\":[{\"id\":\"tt5834204\",\"name\":\"The Handmaid's Tale\",\"imdbRating\":\"8.3\",\"genre\":[\"Drama\"],\"poster\":\"https://example.com/poster.jpg\"},{\"id\":\"tt1234567\",\"name\":\"O Conto da Aia\"}]}","series");
        List<MediaRepository.MediaItem> result=MediaRepository.mergeSearch("The Handmaid's Tale",br,original);
        assertEquals(2,result.size()); assertEquals("tt5834204",result.get(0).imdbId); assertEquals("O Conto da Aia",result.get(0).title);
        assertEquals("8.3",result.get(0).rating); assertEquals("Drama",result.get(0).genresPt); assertEquals("https://example.com/poster.jpg",result.get(0).posterUrl);
        assertEquals("The Handmaid's Tale",MediaRepository.MediaItem.fromJson(result.get(0).toJson()).originalTitle);
    }
    @Test(expected=java.io.IOException.class) public void serviceErrorsAreNotEmptySuccess() throws Exception {
        MediaRepository.parseBrazilianSearch("{\"errors\":[{\"message\":\"unavailable\"}]}","movie");
    }
    @Test public void unavailableOrWrongDetailsKeepConfirmedSelection() throws Exception {
        MediaRepository.MediaItem chosen=MediaRepository.parseBrazilianSearch(response(new JSONArray().put(node("Show","tt5834204","O Conto da Aia","The Handmaid's Tale"))),"series").get(0);
        MediaRepository.MediaItem other=MediaRepository.parseBrazilianSearch(response(new JSONArray().put(node("Show","tt7587890","The Rookie","The Rookie"))),"series").get(0);
        assertSame(chosen,MediaRepository.selectSearchDetails(chosen,null));
        assertSame(chosen,MediaRepository.selectSearchDetails(chosen,other));
        MediaRepository.MediaItem detail=MediaRepository.parseDiscoveryCatalog("{\"metas\":[{\"id\":\"tt5834204\",\"name\":\"The Handmaid's Tale\",\"genre\":[\"Drama\"]}]}","series").get(0);
        MediaRepository.MediaItem hydrated=MediaRepository.selectSearchDetails(chosen,detail);
        assertEquals("O Conto da Aia",hydrated.title); assertEquals("Drama",hydrated.genresPt); assertEquals(chosen.imdbId,hydrated.imdbId);
    }
}
