package com.gathin.tvhome;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ProfileHistoryTest {
    private MediaRepository.MediaItem item(int id) { return new MediaRepository.MediaItem("tt"+(8000000+id),"Título "+id,"movie","8","2020","Drama","","","","","",1); }
    private void vote(ViewerProfiles model,String profile,int id,int rating,long at) {
        MediaRepository.MediaItem item=item(id);
        model.find(profile).votes.put(item.imdbId,new ViewerProfiles.Vote(item.imdbId,item.title,item.genresPt,item.type,item.year,rating,at,"Original "+id,Collections.singletonList("g:Drama"),at));
    }
    @Test public void individualHistoryIncludesOnlyThatPersonsRecordsInLatestOrder() {
        ViewerProfiles model=ProfileFixtures.household();vote(model,"two",1,3,1000);vote(model,"two",2,-1,3000);vote(model,"one",3,4,5000);
        List<MediaRepository.MediaItem> history=model.history("two");assertEquals(2,history.size());assertEquals(item(2).imdbId,history.get(0).imdbId);assertEquals(item(1).imdbId,history.get(1).imdbId);
        assertEquals("Original 1",history.get(1).originalTitle);assertEquals(Collections.singletonList("g:Drama"),history.get(1).contentFeatures);
    }
    @Test public void generalHistoryDeduplicatesTitlesWithoutCombiningOrRewritingVotes() {
        ViewerProfiles model=ProfileFixtures.household();vote(model,"two",1,0,1000);vote(model,"one",1,4,5000);vote(model,"two",2,3,3000);
        String saved=model.toJson();List<MediaRepository.MediaItem> history=model.history(ViewerProfiles.GENERAL);
        assertEquals(2,history.size());assertEquals(item(1).imdbId,history.get(0).imdbId);
        String summary=model.historySummary(ViewerProfiles.GENERAL,item(1).imdbId);assertTrue(summary.contains("Pessoa Dois: Odiei"));assertTrue(summary.contains("Pessoa Um: Amei"));assertEquals(saved,model.toJson());
    }
    @Test public void detailMarkersRespectActivePersonAndGeneralUnion() {
        ViewerProfiles model=ProfileFixtures.household();MediaRepository.MediaItem title=item(1);model.saveForLater(Collections.singleton("one"),title);vote(model,"two",1,0,1000);
        assertTrue(model.isSaved("one",title.imdbId));assertFalse(model.isSaved("two",title.imdbId));assertTrue(model.isSaved(ViewerProfiles.GENERAL,title.imdbId));
        assertTrue(model.isRated("two",title.imdbId));assertFalse(model.isRated("one",title.imdbId));assertTrue(model.isRated(ViewerProfiles.GENERAL,title.imdbId));
    }
    @Test public void watchedWithoutOpinionIsInHistoryButNotMarkedRated() {
        ViewerProfiles model=ProfileFixtures.household();vote(model,"two",1,-1,1000);
        assertEquals(1,model.history("two").size());assertFalse(model.isRated("two",item(1).imdbId));assertFalse(model.isRated(ViewerProfiles.GENERAL,item(1).imdbId));
        assertTrue(model.historySummary("two",item(1).imdbId).contains("Assistido sem nota"));assertEquals("",model.historySummary("one",item(1).imdbId));
    }
}
