package com.gathin.tvhome;

import org.junit.Test;
import java.util.*;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class RecommendationFallbackTest {
    private static final LocalDate TODAY=LocalDate.of(2026,10,23);
    private MediaRepository.MediaItem item(int id,String type,String genre,String year) {
        return new MediaRepository.MediaItem("tt"+(9100000+id),"Título "+id,type,"8",year,genre,"","","","","",100);
    }
    @Test public void emptyOrOfflineCurrentYearUsesRealReserveTitlesAndExplainsOtherYears() {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem movie=item(1,"movie","Drama","2024"),series=item(2,"series","Drama","2024");
        for (int type:new int[]{MediaRepository.TYPE_ALL,MediaRepository.TYPE_MOVIES,MediaRepository.TYPE_SERIES}) {
            MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(Collections.emptyList(),Arrays.asList(movie,series),p,MediaRepository.CAT_NOVIDADES,type,TODAY,System.currentTimeMillis());
            assertFalse(batch.items.isEmpty()); assertTrue(batch.notice.contains("outros anos"));
            for (MediaRepository.MediaItem title:batch.items) assertTrue(type==0||(type==2)==title.isSeries());
        }
    }
    @Test public void fallbackPrioritizesUnseenOlderOptionsBeforeBreakingTheCooldown() {
        ViewerProfiles p=ProfileFixtures.household(); p.select("one"); MediaRepository.MediaItem seen=item(1,"movie","Drama","2026"),unseen=item(2,"movie","Drama","2024"); p.markWatched("one",seen);
        String before=p.toJson();
        MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(Arrays.asList(seen),Arrays.asList(unseen),p,0,1,TODAY,System.currentTimeMillis());
        assertEquals(Arrays.asList(unseen),batch.items); assertFalse(batch.notice.startsWith("Para rever")); assertEquals(before,p.toJson());
    }
    @Test public void twoNewReleasesKeepPriorityAndUseOlderUnseenTitlesToCompleteThePage() {
        ViewerProfiles p=ProfileFixtures.household();
        MediaRepository.MediaItem first=item(1,"movie","Drama","2026"),second=item(2,"movie","Drama","2026"),older=item(3,"movie","Drama","2024");
        for (boolean personalized:new boolean[]{false,true}) {
            MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(Arrays.asList(first,second),Arrays.asList(older),p,0,1,TODAY,System.currentTimeMillis(),personalized);
            assertEquals(3,batch.items.size());assertTrue(batch.items.subList(0,2).containsAll(Arrays.asList(first,second)));assertEquals(older,batch.items.get(2));assertTrue(batch.notice.contains("Poucos lançamentos"));
        }
    }
    @Test public void completingShortPagesRespectsFormatFutureDatesAndTheWatchedCooldown() {
        ViewerProfiles p=ProfileFixtures.household();p.select("one");
        MediaRepository.MediaItem first=item(1,"movie","Drama","2026"),second=item(2,"movie","Drama","2026"),seen=item(3,"movie","Drama","2024"),series=item(4,"series","Drama","2024"),future=item(5,"movie","Drama","2027"),older=item(6,"movie","Drama","2023");
        p.markWatched("one",seen);String before=p.toJson();
        MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(Arrays.asList(first,second),Arrays.asList(seen,series,future,older),p,0,1,TODAY,System.currentTimeMillis());
        assertEquals(3,batch.items.size());assertTrue(batch.items.contains(older));assertFalse(batch.items.contains(seen));assertFalse(batch.items.contains(series));assertFalse(batch.items.contains(future));assertEquals(before,p.toJson());
    }
    @Test public void genuinelySmallCatalogsDoNotInventTitlesOrClaimToHaveAddedSuggestions() {
        List<MediaRepository.MediaItem> two=Arrays.asList(item(1,"movie","Drama","2026"),item(2,"movie","Drama","2026"));
        MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(two,Collections.emptyList(),ProfileFixtures.household(),0,1,TODAY,System.currentTimeMillis());
        assertEquals(two,batch.items);assertEquals("",batch.notice);
    }
    @Test public void onlyWhenEveryOptionIsWatchedHomeShowsExplicitRewatchSuggestions() {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem title=item(1,"movie","Drama","2026"); p.markWatched("two",title); String before=p.toJson();
        MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(Arrays.asList(title),Arrays.asList(title),p,0,1,TODAY,System.currentTimeMillis());
        assertEquals(Arrays.asList(title),batch.items); assertTrue(batch.notice.startsWith("Para rever")); assertEquals(before,p.toJson());
    }
    @Test public void immediateSuggestionsRespectTheSameHistoryWhilePersonalRankingRuns() {
        ViewerProfiles p=ProfileFixtures.household(); p.select("one"); MediaRepository.MediaItem seen=item(1,"movie","Drama","2026"),unseen=item(2,"movie","Terror","2026"); p.markWatched("one",seen);
        MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(Arrays.asList(seen,unseen),Collections.emptyList(),p,0,1,TODAY,System.currentTimeMillis(),false);
        assertEquals(Arrays.asList(unseen),batch.items);
        p.markWatched("one",unseen); batch=MediaRepository.homeRecommendations(Arrays.asList(seen,unseen),Collections.emptyList(),p,0,1,TODAY,System.currentTimeMillis(),false);
        assertFalse(batch.items.isEmpty()); assertTrue(batch.notice.startsWith("Para rever"));
    }
    @Test public void fallbackRejectsFutureReleasesAndDeduplicatesRealIds() {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem future=item(1,"movie","Drama","2027"),real=item(2,"movie","Drama","2024");
        MediaRepository.RecommendationBatch batch=MediaRepository.homeRecommendations(Arrays.asList(future),Arrays.asList(real,real),p,1,1,TODAY,System.currentTimeMillis());
        assertEquals(Arrays.asList(real),batch.items); assertFalse(batch.items.contains(future));
    }
    @Test public void discoveryKeepsExactChoicesWheneverAnyMatchingTitleExists() {
        ViewerProfiles p=ProfileFixtures.household(); DiscoveryQuery q=new DiscoveryQuery("one"); q.genres.add("Drama"); q.era=0;
        MediaRepository.MediaItem match=item(1,"movie","Drama","2026"),other=item(2,"series","Drama","2026");
        MediaRepository.RecommendationBatch batch=MediaRepository.discoveryRecommendations(Arrays.asList(match,other),Collections.emptyList(),p,q,TODAY);
        assertEquals(Arrays.asList(match),batch.items); assertEquals("",batch.notice); assertEquals(0,q.era); assertEquals(Collections.singleton("Drama"),q.genres);
    }
    @Test public void discoveryBroadensEraBeforeGenresAndStillExcludesWatched() {
        ViewerProfiles p=ProfileFixtures.household(); DiscoveryQuery q=new DiscoveryQuery("one"); q.era=0; q.genres.add("Drama");
        MediaRepository.MediaItem older=item(1,"movie","Drama","1990"),wrongGenre=item(2,"movie","Terror","2026"),seen=item(3,"movie","Drama","2026"); p.markWatched("one",seen);
        MediaRepository.RecommendationBatch batch=MediaRepository.discoveryRecommendations(Arrays.asList(seen,wrongGenre),Arrays.asList(older),p,q,TODAY);
        assertEquals(Arrays.asList(older),batch.items); assertTrue(batch.notice.contains("ampliamos a época")); assertFalse(q.includeWatched); assertEquals(0,q.era);
    }
    @Test public void discoveryAlwaysReturnsSuggestionsEvenWhenGenresAndHistoryExhaustThePool() {
        ViewerProfiles p=ProfileFixtures.household(); DiscoveryQuery q=new DiscoveryQuery(ViewerProfiles.GENERAL); q.type="series"; q.genres.add("Comedy"); q.era=2;
        MediaRepository.MediaItem seen=item(1,"series","Drama","2026"),wrongType=item(2,"movie","Comédia","1990"); p.markWatched("two",seen); String before=p.toJson();
        MediaRepository.RecommendationBatch batch=MediaRepository.discoveryRecommendations(Collections.emptyList(),Arrays.asList(seen,wrongType),p,q,TODAY);
        assertEquals(Arrays.asList(seen),batch.items); assertTrue(batch.notice.startsWith("Para rever")); assertEquals(before,p.toJson());
        assertFalse(q.includeWatched); assertEquals(2,q.era); assertEquals(Collections.singleton("Comedy"),q.genres);
    }
}
