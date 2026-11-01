package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Collections;

public class BrazilianPosterTest {
    private MediaRepository.MediaItem local() throws Exception {
        String body="{\"data\":{\"popularTitles\":{\"edges\":[{\"node\":{\"__typename\":\"Show\",\"content\":{\"title\":\"O Novato\",\"originalTitle\":\"The Rookie\",\"posterUrl\":\"/poster/339866324/{profile}/the-rookie.{format}\",\"scoring\":{\"imdbScore\":8},\"externalIds\":{\"imdbId\":\"tt7587890\"}}}}]}}}";
        return MediaRepository.parseBrazilianSearch(body,"series").get(0);
    }
    private MediaRepository.MediaItem original(String id,String rating) {
        return new MediaRepository.MediaItem(id,"The Rookie","series",rating,"2018","Drama","","https://example.com/back.jpg","https://example.com/poster.jpg","","",1);
    }
    @Test public void regionalCoverAndScoreAreReadAndCached() throws Exception {
        MediaRepository.MediaItem item=local();
        assertEquals("https://images.justwatch.com/poster/339866324/s332/the-rookie.jpg",MediaRepository.portraitPrimary(item));
        assertEquals(item.posterUrl,MediaRepository.portraitFallback(item));
        assertEquals("★ 8.0",item.ratingLabel(false));
        MediaRepository.MediaItem cached=MediaRepository.MediaItem.fromJson(item.toJson());
        assertEquals(item.posterPtBrUrl,cached.posterPtBrUrl); assertTrue(cached.posterPtBrChecked);
    }
    @Test public void absentRegionalPosterFallsBackToSameTitleArtwork() {
        MediaRepository.MediaItem item=original("tt7587890","");
        assertEquals(item.posterUrl,MediaRepository.portraitPrimary(item));
        assertEquals(item.backdropUrl,MediaRepository.portraitFallback(item));
    }
    @Test public void localizationNeverTakesAnotherTitleOrOverwritesKnownScore() throws Exception {
        MediaRepository.MediaItem item=original("tt7587890","8.3");
        assertTrue(MediaRepository.applyBrazilianTitle(item,local())); assertEquals("8.3",item.rating);
        MediaRepository.MediaItem other=original("tt1234567","");
        assertFalse(MediaRepository.applyBrazilianTitle(other,local())); assertEquals("",other.posterPtBrUrl);
        MediaRepository.MediaItem missing=original("tt7587890",""); MediaRepository.applyBrazilianTitle(missing,local()); assertEquals("8.0",missing.rating);
    }
    @Test public void mergingAndDetailEnrichmentRetainBrazilianPoster() throws Exception {
        MediaRepository.MediaItem chosen=local(),detail=original(chosen.imdbId,"8.3");
        MediaRepository.MediaItem merged=MediaRepository.mergeSearch("The Rookie",Collections.singletonList(chosen),Collections.singletonList(detail)).get(0);
        assertEquals(chosen.posterPtBrUrl,merged.posterPtBrUrl); assertEquals("8.3",merged.rating);
        assertEquals(chosen.posterPtBrUrl,MediaRepository.selectSearchDetails(chosen,detail).posterPtBrUrl);
    }
    @Test public void invalidPosterPathsAreRejected() {
        for(String path:new String[]{"https://other.com/poster/a","/poster/../private","file:///poster/x",""}) assertEquals("",MediaRepository.brazilianPoster(path));
    }
}
