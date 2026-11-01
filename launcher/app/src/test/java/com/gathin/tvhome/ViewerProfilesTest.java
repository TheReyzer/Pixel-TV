package com.gathin.tvhome;

import org.junit.Test;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class ViewerProfilesTest {
    @Test public void onlyFeedbackChangesTheFeedbackVersion() {
        ViewerProfiles model=ProfileFixtures.household();long before=model.feedbackVersion();
        MediaRepository.MediaItem title=item(771,"series","Comédia",2026);
        model.observeCatalog(Arrays.asList(title));model.select("one");model.saveName("one","Pessoa Um");model.setAppearance("one",ViewerProfiles.AVATARS[0],0);
        assertEquals(before,model.feedbackVersion());
        model.rate("one",title,4);assertTrue(model.feedbackVersion()>before);long rated=model.feedbackVersion();
        model.markWatched("one",title);assertTrue(model.feedbackVersion()>rated);long watched=model.feedbackVersion();
        model.removeVote("one",title.imdbId);assertTrue(model.feedbackVersion()>watched);
    }
    @Test public void changingOnePeoplesRatingKeepsTheOthersAndAdvancesFeedbackVersion() throws Exception {
        ViewerProfiles model=ProfileFixtures.household();MediaRepository.MediaItem title=item(772,"movie","Drama",2026);
        model.rate("two",title,3);long before=model.feedbackVersion();
        model.rate("one",title,4);assertTrue(model.feedbackVersion()>before);assertEquals(3,model.vote("two",title.imdbId).rating);
        ViewerProfiles restored=ViewerProfiles.fromJson(model.toJson());assertEquals(model.toJson(),restored.toJson());
    }
    @Test public void partialSearchMetadataNeverErasesKnownPreferences() {
        ViewerProfiles model=ProfileFixtures.household(); MediaRepository.MediaItem original=item(321,"series","Drama",2018);
        model.rate("one",original,3);
        MediaRepository.MediaItem partial=new MediaRepository.MediaItem(original.imdbId,"Nome brasileiro","series","","","","","","","","",1);
        model.rate("one",partial,4);
        ViewerProfiles.Vote saved=model.vote("one",original.imdbId);
        assertEquals(4,saved.rating); assertEquals("Drama",saved.genres); assertEquals("2018",saved.year); assertEquals("Nome brasileiro",saved.title);
    }
    @Test public void groupRatingsAreIndependentAndReplaceOnlyTheChosenPeoplesVotes() throws Exception {
        ViewerProfiles model=ProfileFixtures.household(); MediaRepository.MediaItem title=item(987,"series","Drama",2026);
        model.rate("three",title,2); java.util.Map<String,Integer> ratings=new java.util.LinkedHashMap<>(); ratings.put("one",4); ratings.put("two",0);
        model.rateIndividually(ratings,title);
        assertEquals(4,model.vote("one",title.imdbId).rating); assertEquals(0,model.vote("two",title.imdbId).rating); assertEquals(2,model.vote("three",title.imdbId).rating); assertNull(model.vote(ViewerProfiles.GENERAL,title.imdbId));
        assertEquals(model.vote("one",title.imdbId).watchedAt,model.vote("two",title.imdbId).watchedAt);
        ratings.put("one",1); ratings.put("two",-1); model.rateIndividually(ratings,title);
        ViewerProfiles restored=ViewerProfiles.fromJson(model.toJson());
        assertEquals(1,restored.vote("one",title.imdbId).rating); assertEquals(0,restored.vote("two",title.imdbId).rating); assertEquals(1,restored.find("one").votes.size());
        String before=model.toJson(); ratings.put("missing",3); assertThrows(IllegalArgumentException.class,()->model.rateIndividually(ratings,title)); assertEquals(before,model.toJson());
        ratings.remove("missing"); ratings.put(ViewerProfiles.GENERAL,3); assertThrows(IllegalArgumentException.class,()->model.rateIndividually(ratings,title)); assertEquals(before,model.toJson());
    }
    @Test public void generalRemainsExclusiveAndInvalidGroupsNeverPartiallyWrite() {
        ViewerProfiles model=ProfileFixtures.household(); MediaRepository.MediaItem title=item(980,"movie","Drama",2026);
        java.util.Set<String> people=new java.util.LinkedHashSet<>();
        ViewerProfiles.togglePerson(people,"one"); ViewerProfiles.togglePerson(people,"two");
        assertEquals(2,people.size()); ViewerProfiles.togglePerson(people,ViewerProfiles.GENERAL);
        assertEquals(java.util.Collections.singleton(ViewerProfiles.GENERAL),people);
        java.util.Map<String,Integer> ratings=new java.util.LinkedHashMap<>(); ratings.put(ViewerProfiles.GENERAL,0); model.rateIndividually(ratings,title);
        assertEquals(0,model.vote(ViewerProfiles.GENERAL,title.imdbId).rating); assertNull(model.vote("one",title.imdbId)); assertNull(model.vote("two",title.imdbId));
        ViewerProfiles.togglePerson(people,"three"); assertEquals(java.util.Collections.singleton("three"),people);
        ViewerProfiles.togglePerson(people,"three"); assertTrue(people.isEmpty());
        String before=model.toJson(); ratings.clear(); assertThrows(IllegalArgumentException.class,()->model.rateIndividually(ratings,title));
        ratings.put("one",4); ratings.put("two",5); assertThrows(IllegalArgumentException.class,()->model.rateIndividually(ratings,title)); assertEquals(before,model.toJson());
        ratings.put("two",null); assertThrows(IllegalArgumentException.class,()->model.rateIndividually(ratings,title)); assertEquals(before,model.toJson());
    }
    private static MediaRepository.MediaItem item(int id, String type, String genre, int year) {
        return new MediaRepository.MediaItem("tt" + (1000000 + id), "Título " + id, type, "8", String.valueOf(year), genre, "", "", "", "", year + "-01-01", 100);
    }
    @Test public void defaultAggregateAndColdProfilesPreserveCatalogOrder() {
        ViewerProfiles model = ProfileFixtures.household();
        assertEquals("Geral", model.selected().name);
        assertEquals(4, model.profiles().size());
        List<MediaRepository.MediaItem> base = Arrays.asList(item(1,"movie","Drama",2026),item(2,"series","Terror",2020));
        assertEquals(base, model.rank(base, false, 2026));
        model.select("two"); assertEquals(base, model.rank(base, false, 2026));
    }
    @Test public void renameAndRestartPreserveIdentityRatingsPhotosAndSelection() throws Exception {
        ViewerProfiles model = ProfileFixtures.household();
        MediaRepository.MediaItem title = item(1,"movie","Drama",2026);
        model.rate("one",title,3); model.saveName("one","  Pessoa Renomeada  "); model.setAppearance("one","wolf",5); model.select("one");
        ViewerProfiles reloaded = ViewerProfiles.fromJson(model.toJson());
        assertEquals("Pessoa Renomeada", reloaded.selected().name);
        assertEquals("wolf", reloaded.selected().avatar); assertEquals(5,reloaded.selected().color);
        assertEquals(3, reloaded.vote("one",title.imdbId).rating);
        assertEquals(1, reloaded.selected().votes.size());
    }
    @Test public void ratingsReplaceRatherThanDuplicateAndCanBeRemoved() {
        ViewerProfiles model = ProfileFixtures.household(); MediaRepository.MediaItem title=item(1,"movie","Drama",2026);
        model.rate("one",title,0); model.rate("one",title,3);
        assertEquals(1,model.find("one").votes.size()); assertEquals(3,model.vote("one",title.imdbId).rating);
        model.removeVote("one",title.imdbId); assertNull(model.vote("one",title.imdbId));
    }
    @Test public void personalRatingsDoNotLeakIntoOtherProfiles() {
        ViewerProfiles model = ProfileFixtures.household(); model.rate("one",item(1,"movie","Terror",2026),3); model.rate("one",item(2,"movie","Romance",2026),0);
        List<MediaRepository.MediaItem> base = Arrays.asList(item(3,"movie","Romance",2026),item(4,"movie","Terror",2026));
        model.select("one"); assertEquals(base.get(1),model.rank(base,false,2026).get(0));
        model.select("two"); assertEquals(base,model.rank(base,false,2026));
    }
    @Test public void learnsFormatAndReleaseAgeAndKeepsMixedTrios() {
        ViewerProfiles model = ProfileFixtures.household();
        model.rate("one",item(1,"series","Drama",1998),3); model.rate("one",item(2,"movie","Drama",2026),0); model.select("one");
        List<MediaRepository.MediaItem> base = Arrays.asList(item(3,"movie","Drama",2026),item(4,"movie","Drama",2025),item(5,"series","Drama",1999),item(6,"series","Drama",2000),item(7,"series","Drama",2001),item(8,"movie","Drama",1997));
        List<MediaRepository.MediaItem> ranked=model.rank(base,true,2026);
        assertTrue(ranked.get(0).isSeries()); assertTrue(ranked.get(1).isSeries()); assertFalse(ranked.get(2).isSeries());
        for (int i=0;i<ranked.size();i+=3) { int series=0; for(int j=i;j<i+3;j++)if(ranked.get(j).isSeries())series++; assertTrue(series>0&&series<3); }
        assertTrue(model.summary("one",2026).contains("Clássicos")); assertTrue(model.summary("one",2026).contains("séries"));
    }
    @Test public void aggregateBalancesMembersInsteadOfLettingProlificRatingsOutvoteAnotherPerson() {
        ViewerProfiles model=ProfileFixtures.household();
        for(int i=1;i<=30;i++)model.rate("one",item(i,"movie","Terror",2026),3);
        model.rate("two",item(40,"movie","Terror",2026),0);
        model.rate("one",item(41,"movie","Drama",2026),3); model.rate("two",item(42,"movie","Drama",2026),3);
        List<MediaRepository.MediaItem> base=Arrays.asList(item(50,"movie","Terror",2026),item(51,"movie","Drama",2026));
        assertEquals(base.get(1),model.rank(base,false,2026).get(0));
        assertTrue(model.summary(ViewerProfiles.GENERAL,2026).contains("2 perfis com avaliações combinadas"));
    }
    @Test public void watchedTitleIsHiddenForExactly21DaysThenReturnsWithLowerPriority() {
        ViewerProfiles model=ProfileFixtures.household(); MediaRepository.MediaItem seen=item(1,"movie","Drama",2026),fresh=item(2,"movie","Drama",2026);
        long watched=1_700_000_000_000L;
        model.rate("one",seen,4,watched); model.select("one");
        List<MediaRepository.MediaItem> base=Arrays.asList(seen,fresh);
        assertEquals(Arrays.asList(fresh),model.rank(base,false,2026,watched+ViewerProfiles.WATCHED_COOLDOWN_MS-1));
        assertEquals(Arrays.asList(fresh,seen),model.rank(base,false,2026,watched+ViewerProfiles.WATCHED_COOLDOWN_MS));
    }
    @Test public void aggregateHidesAnyMembersWatchedTitleButIndividualsKeepTheirOwnHistory() {
        ViewerProfiles model=ProfileFixtures.household(); MediaRepository.MediaItem seen=item(1,"movie","Drama",2026);
        model.markWatched("one",seen);
        assertTrue(model.rank(Arrays.asList(seen),true,2026).isEmpty());
        model.select("two"); assertEquals(Arrays.asList(seen),model.rank(Arrays.asList(seen),false,2026));
        model.markWatched(ViewerProfiles.GENERAL,seen);
        assertEquals(Arrays.asList(seen),model.rank(Arrays.asList(seen),false,2026));
        model.select(ViewerProfiles.GENERAL); assertTrue(model.rank(Arrays.asList(seen),false,2026).isEmpty());
    }
    @Test public void watchedOnlyDoesNotInventTasteAndRatingChangesPreserveOneRecord() {
        ViewerProfiles model=ProfileFixtures.household(); MediaRepository.MediaItem seen=item(1,"movie","Drama",2026);
        model.markWatched("one",seen); assertEquals(-1,model.vote("one",seen.imdbId).rating);
        assertTrue(model.summary("one",2026).contains("Ainda sem avaliações"));
        model.rate("one",seen,4); model.markWatched("one",seen);
        assertEquals(4,model.vote("one",seen.imdbId).rating); assertEquals(1,model.find("one").votes.size());
    }
    @Test public void versionOneMigrationKeepsPreviousRatingMeaningsAndAddsCooldown() throws Exception {
        ViewerProfiles model=ProfileFixtures.household();model.rate("one",item(1,"movie","Drama",2026),3);
        JSONObject old=new JSONObject(model.toJson()).put("version",1);
        ViewerProfiles restored=ViewerProfiles.fromJson(old.toString());
        assertEquals("Amei",ViewerProfiles.RATINGS[restored.vote("one","tt1000001").rating]);
        assertTrue(restored.vote("one","tt1000001").watchedAt>0);
    }
    @Test public void hatedHasMoreNegativeInfluenceThanDislikedAndJointVotesStayOutOfIndividuals() {
        ViewerProfiles model=ProfileFixtures.household();model.rate("one",item(1,"movie","Terror",2026),0);
        model.rate("one",item(2,"movie","Romance",2026),1);model.select("one");
        List<MediaRepository.MediaItem> base=Arrays.asList(item(3,"movie","Terror",2026),item(4,"movie","Romance",2026));
        assertEquals(base.get(1),model.rank(base,false,2026).get(0));
        model.rate(ViewerProfiles.GENERAL,item(5,"movie","Drama",2026),4);model.select("two");
        assertEquals(base,model.rank(base,false,2026));
    }
    @Test public void aDislikedRecentFilmDoesNotInventAPositivePreferenceForOldSeries() {
        ViewerProfiles model=ProfileFixtures.household();model.rate("one",item(1,"movie","Policial",2026),0);model.select("one");
        List<MediaRepository.MediaItem> base=Arrays.asList(item(2,"movie","Drama",2026),item(3,"series","Drama",1990));
        assertEquals(base,model.rank(base,false,2026));
        assertTrue(model.summary("one",2026).contains("Sem preferência clara"));
    }
    @Test public void acceptsJointFeedbackButRejectsDuplicateNamesAndInvalidRatings() {
        ViewerProfiles model=ProfileFixtures.household(); MediaRepository.MediaItem item=item(1,"movie","Drama",2026);
        model.rate(ViewerProfiles.GENERAL,item,3); assertNotNull(model.vote(ViewerProfiles.GENERAL,item.imdbId));
        assertThrows(IllegalArgumentException.class,()->model.rate("two",item,5));
        assertThrows(IllegalArgumentException.class,()->model.saveName(null,"PESSOA TRÊS"));
        assertThrows(IllegalArgumentException.class,()->model.saveName("two","  "));
        assertEquals(4,model.profiles().size());
        ViewerProfiles.Profile created=model.saveName(null,"Novo perfil"); assertFalse(created.aggregate());
    }
    @Test public void avatarAndColorChangesPreserveHistoryAndRejectUnknownOptions() throws Exception {
        ViewerProfiles model=ProfileFixtures.household();model.rate("two",item(1,"movie","Drama",2026),4);
        model.setAppearance("two","koala",2);
        assertThrows(IllegalArgumentException.class,()->model.setAppearance("two","https://example.com/image.png",0));
        assertThrows(IllegalArgumentException.class,()->model.setAppearance("two","cat",99));
        ViewerProfiles restored=ViewerProfiles.fromJson(model.toJson());
        assertEquals("koala",restored.find("two").avatar); assertEquals(2,restored.find("two").color);
        assertNotNull(restored.vote("two","tt1000001"));
    }
    @Test public void animalPackHasNoDuplicatesAndNewProfilesStartWithUnusedAnimalsWhileAvailable() {
        assertEquals(24,new java.util.HashSet<>(Arrays.asList(ViewerProfiles.AVATARS)).size());
        assertEquals(24,Arrays.stream(ViewerProfiles.COLORS).distinct().count());
        ViewerProfiles model=ProfileFixtures.household();
        for (int i=0;i<20;i++) model.saveName(null,"Pessoa "+i);
        java.util.Set<String> used=new java.util.HashSet<>();
        for (ViewerProfiles.Profile p:model.profiles()) assertTrue(used.add(p.avatar));
    }
    @Test public void expandedPackPersistsLastAnimalAndColorWithoutChangingExistingChoices() throws Exception {
        ViewerProfiles model=ProfileFixtures.household(); model.setAppearance("one","fox",5);
        model.setAppearance("two","capybara",23);
        ViewerProfiles restored=ViewerProfiles.fromJson(model.toJson());
        assertEquals("capybara",restored.find("two").avatar); assertEquals(23,restored.find("two").color);
        assertEquals("fox",restored.find("one").avatar); assertEquals(5,restored.find("one").color);
    }
    @Test public void unknownSelectionAndUntrustedAvatarValuesFallBackToLocalOptions() throws Exception {
        ViewerProfiles model=ProfileFixtures.household(); JSONObject json=new JSONObject(model.toJson());
        json.put("selected","missing");json.getJSONArray("profiles").getJSONObject(0).put("avatar","../../outside.jpg").put("color",999);
        ViewerProfiles restored=ViewerProfiles.fromJson(json.toString());
        assertEquals(ViewerProfiles.GENERAL,restored.selected().id); assertEquals("lion",restored.selected().avatar); assertTrue(restored.selected().color < ViewerProfiles.COLORS.length);
        assertThrows(Exception.class,()->ViewerProfiles.fromJson("broken json"));
        assertThrows(Exception.class,()->ViewerProfiles.fromJson("{\"version\":99}"));
    }
}
