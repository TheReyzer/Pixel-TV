package com.gathin.tvhome;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class RecommendationRankingTest {
    private static final long DAY=86_400_000L;
    private MediaRepository.MediaItem item(int id,String genre) {
        return new MediaRepository.MediaItem("tt"+(9000000+id),"Título "+id,"movie","8","2026",genre,"","","","","2026-01-01",100);
    }
    @Test public void generalDuplicateDoesNotAddAnExtraPersonOrRepeatTheirOpinion() {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem rated=item(1,"Drama");
        p.rate("one",rated,4); p.rate("two",item(2,"Drama"),3);
        double before=p.overview(ViewerProfiles.GENERAL,2026).category("g:").get("Drama");
        p.rate(ViewerProfiles.GENERAL,rated,0);
        assertEquals(2,p.overview(ViewerProfiles.GENERAL,2026).people);
        assertEquals(before,p.overview(ViewerProfiles.GENERAL,2026).category("g:").get("Drama"),.0001);
        assertEquals(0,p.vote(ViewerProfiles.GENERAL,rated.imdbId).rating);
    }
    @Test public void uniqueGeneralFeedbackStillHelpsTheGroupWithoutLeakingToIndividuals() {
        ViewerProfiles p=ProfileFixtures.household(); p.rate("one",item(1,"Drama"),4);
        p.rate(ViewerProfiles.GENERAL,item(2,"Aventura"),4);
        assertTrue(p.overview(ViewerProfiles.GENERAL,2026).category("g:").get("Aventura")>0);
        assertFalse(p.overview("one",2026).category("g:").containsKey("Aventura"));
        assertEquals(1,p.overview(ViewerProfiles.GENERAL,2026).people);
    }
    @Test public void unknownGenreIsNotCountedAsAnExplicitNeutralOpinion() {
        ViewerProfiles p=ProfileFixtures.household(); p.rate("one",item(1,"Drama"),4); p.rate("two",item(2,"Terror"),4);
        double unknown=p.overview(ViewerProfiles.GENERAL,2026).category("g:").get("Drama");
        assertEquals(p.overview("one",2026).category("g:").get("Drama"),unknown,.0001);
        p.rate("two",item(3,"Drama"),2);
        assertTrue(p.overview(ViewerProfiles.GENERAL,2026).category("g:").get("Drama")<unknown);
        assertEquals(0,p.overview("two",2026).category("g:").get("Drama"),0);
    }
    @Test public void recentFeedbackCanChangeAnOldPreferenceWithoutErasingHistory() {
        ViewerProfiles p=ProfileFixtures.household(); long now=System.currentTimeMillis();
        for (int i=0;i<6;i++) p.rate("one",item(i,"Drama"),0,now-730*DAY);
        assertTrue(p.overview("one",2026).category("g:").get("Drama")<0);
        for (int i=6;i<12;i++) p.rate("one",item(i,"Drama"),4,now);
        assertTrue(p.overview("one",2026).category("g:").get("Drama")>0);
        assertEquals(12,p.find("one").votes.size()); assertEquals(now-730*DAY,p.vote("one",item(0,"Drama").imdbId).watchedAt);
    }
    @Test public void watchedPriorityPenaltyEventuallyExpiresInsteadOfLastingForever() {
        ViewerProfiles p=ProfileFixtures.household(); p.select("one"); long now=System.currentTimeMillis();
        MediaRepository.MediaItem seen=item(1,"Drama"),fresh=item(2,"Drama"); p.rate("one",seen,4,now);
        List<MediaRepository.MediaItem> base=Arrays.asList(seen,fresh);
        assertEquals(fresh,p.rank(base,false,2026,now+21*DAY).get(0));
        assertEquals(seen,p.rank(base,false,2026,now+730*DAY).get(0));
    }
    @Test public void themesAndDurationDistinguishTitlesWithTheSameBroadGenre() throws Exception {
        ViewerProfiles p=ProfileFixtures.household(); p.select("one");
        MediaRepository.MediaItem rated=item(1,"Drama"),medical=item(2,"Drama"),other=item(3,"Drama");
        rated.synopsisPt="Uma médica enfrenta desafios em um hospital."; medical.synopsisPt="A medical team in a hospital."; other.synopsisPt="A courtroom lawyer takes a case.";
        RecommendationFeatures.fromCatalog(rated,new JSONObject("{\"runtime\":\"90 min\"}"));
        RecommendationFeatures.fromCatalog(medical,new JSONObject("{\"runtime\":\"91 min\"}"));
        RecommendationFeatures.fromCatalog(other,new JSONObject("{\"runtime\":\"150 min\"}"));
        p.rate("one",rated,4);
        assertEquals(medical,p.rank(Arrays.asList(other,medical),false,2026).get(0));
        assertTrue(p.reason(medical,Collections.singleton("one"),2026).contains("Medicina")||p.reason(medical,Collections.singleton("one"),2026).contains("Drama"));
    }
    @Test public void richerEvidenceSurvivesRestartPartialMetadataAndLegacyProfiles() throws Exception {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem rated=item(1,"Drama");
        rated.synopsisPt="Viagem no tempo"; RecommendationFeatures.fromCatalog(rated,new JSONObject("{\"runtime\":\"100 min\",\"director\":[\"Diretora A\"],\"cast\":[\"Pessoa B\"]}"));
        p.rate("one",rated,4);
        MediaRepository.MediaItem partial=new MediaRepository.MediaItem(rated.imdbId,"Novo nome","movie","","","","","","","","",0); p.rate("one",partial,3);
        ViewerProfiles restored=ViewerProfiles.fromJson(p.toJson());
        assertEquals(4,restored.vote("one",rated.imdbId).contentFeatures.size());
        JSONObject legacy=new JSONObject(p.toJson()).put("version",4);
        for (int i=0;i<legacy.getJSONArray("profiles").length();i++) {
            JSONArray votes=legacy.getJSONArray("profiles").getJSONObject(i).getJSONArray("votes");
            for (int j=0;j<votes.length();j++) votes.getJSONObject(j).remove("contentFeatures");
        }
        assertEquals(3,ViewerProfiles.fromJson(legacy.toString()).vote("one",rated.imdbId).rating);
    }
    @Test public void catalogCanSupplyMissingRichEvidenceForOldVotesWithoutChangingSavedHistory() {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem rated=item(1,"Drama"); p.rate("one",rated,4);
        String before=p.toJson(); rated.synopsisPt="Uma investigação policial"; p.observeCatalog(Arrays.asList(rated));
        assertTrue(p.overview("one",2026).category("k:").containsKey("Investigação")); assertEquals(before,p.toJson());
    }
    @Test public void explorationBreaksRepetitiveRunsWithoutDroppingOrDuplicatingTitles() {
        ViewerProfiles p=ProfileFixtures.household(); p.select("one"); p.rate("one",item(1,"Drama"),4);
        List<MediaRepository.MediaItem> base=new ArrayList<>(); for (int i=10;i<22;i++) base.add(item(i,"Drama")); for (int i=30;i<33;i++) base.add(item(i,"Aventura"));
        List<MediaRepository.MediaItem> result=p.rank(base,false,2026);
        assertEquals("Drama",result.get(0).genresPt); assertEquals("Aventura",result.get(4).genresPt);
        assertEquals(new HashSet<>(base),new HashSet<>(result)); assertEquals(base.size(),result.size());
    }
    @Test public void optionalMetadataIsValidatedAndNeverInventedFromMissingFields() throws Exception {
        String body="{\"metas\":[{\"id\":\"tt9000001\",\"name\":\"Medical comedy\",\"description\":\"A comedic story.\",\"runtime\":\"44 min\",\"cast\":[\"Person One\"],\"director\":[\"Person Two\"]}]}";
        MediaRepository.MediaItem parsed=MediaRepository.parseDiscoveryCatalog(body,"series").get(0);
        assertTrue(parsed.contentFeatures.contains("r:Episódios de 31 a 60 min")); assertEquals(3,parsed.contentFeatures.size());
        assertFalse(RecommendationFeatures.of(parsed).contains("k:Medicina"));
        assertEquals(parsed.contentFeatures,MediaRepository.MediaItem.fromJson(parsed.toJson()).contentFeatures);
        assertTrue(RecommendationFeatures.read(new JSONArray("[\"x:inventado\",\"r:ok\",\"a:\"]")).equals(Arrays.asList("r:ok")));
    }
    @Test public void freshCatalogMetadataWinsWhileKnownBrazilianTitlesStayLocalized() {
        MediaRepository.MediaItem fresh=item(1,"Drama"),old=new MediaRepository.MediaItem(fresh.imdbId,"Nome brasileiro","movie","7","2024","Drama","","","","","2024-01-01",1); old.titleInPtBr=true;
        List<MediaRepository.MediaItem> merged=MediaRepository.mergeLists(Arrays.asList(fresh),Arrays.asList(old));
        assertEquals(1,merged.size()); assertEquals("2026",merged.get(0).year); assertEquals("Nome brasileiro",merged.get(0).title); assertEquals("Título 1",merged.get(0).originalTitle);
    }
    @Test public void missingFieldsDoNotManufactureGenreOrReleaseYearEvidence() throws Exception {
        MediaRepository.MediaItem unknown=MediaRepository.MediaItem.fromJson(new JSONObject("{\"imdbId\":\"tt9000001\",\"title\":\"Unknown\"}"));
        assertEquals("",unknown.genresPt); assertEquals("",unknown.year);
        ViewerProfiles p=ProfileFixtures.household(); p.rate("one",unknown,4);
        assertTrue(p.overview("one",2026).category("g:").isEmpty()); assertTrue(p.overview("one",2026).category("e:").isEmpty());
    }
    @Test public void explorationDoesNotPromoteAGroupMembersStrongRejectionOverSafeChoices() {
        ViewerProfiles p=ProfileFixtures.household();
        p.rate("one",item(1,"Drama"),4); p.rate("two",item(2,"Drama"),4); p.rate("one",item(3,"Terror"),4); p.rate("two",item(4,"Terror"),0);
        List<MediaRepository.MediaItem> base=new ArrayList<>(); for (int i=10;i<20;i++) base.add(item(i,"Drama")); for (int i=20;i<30;i++) base.add(item(i,"Terror"));
        List<MediaRepository.MediaItem> ranked=p.rank(base,false,2026);
        for (int i=0;i<10;i++) assertEquals("Drama",ranked.get(i).genresPt);
    }
    @Test public void watchingAgainWithoutReratingKeepsTheActualOpinionDate() throws Exception {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem title=item(1,"Drama"); long old=System.currentTimeMillis()-730*DAY;
        p.rate("one",title,4,old); p.markWatched("one",title);
        assertEquals(old,p.vote("one",title.imdbId).ratedAt); assertTrue(p.vote("one",title.imdbId).watchedAt>old);
        p.rateIndividually(Collections.singletonMap("one",-1),title);
        ViewerProfiles restored=ViewerProfiles.fromJson(p.toJson()); assertEquals(old,restored.vote("one",title.imdbId).ratedAt);
        p.rateIndividually(Collections.singletonMap("one",3),title); assertTrue(p.vote("one",title.imdbId).ratedAt>old);
    }
    @Test public void sameImdbInTwoSourceFormatsCannotRepeatedlyInvalidateTheCache() {
        ViewerProfiles p=ProfileFixtures.household(); MediaRepository.MediaItem movie=item(1,"Drama"),series=new MediaRepository.MediaItem(movie.imdbId,"Série","series","8","2026","Drama","","","","","",1);
        movie.contentFeatures=Arrays.asList("r:Filmes até 95 min"); series.contentFeatures=Arrays.asList("r:Episódios até 30 min");
        p.observeCatalog(Arrays.asList(movie,series)); String version=p.recommendationVersion();
        p.rank(Arrays.asList(movie),false,2026); p.rank(Arrays.asList(series),false,2026);
        assertEquals(version,p.recommendationVersion());
    }
    @Test public void toneAndPaceAreUsedOnlyWhenExplicitlyDescribed() {
        MediaRepository.MediaItem title=item(1,"Drama"); title.synopsisPt="A slow-burn psychological investigation.";
        assertTrue(RecommendationFeatures.of(title).contains("k:Ritmo contemplativo")); assertTrue(RecommendationFeatures.of(title).contains("k:Psicológico"));
        title.synopsisPt="Uma história sobre duas pessoas."; assertTrue(RecommendationFeatures.of(title).isEmpty());
    }
}
