package com.gathin.tvhome;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TasteOverviewTest {
    @Test public void radarKeepsRejectionInsideNeutralAndPreferenceOutside() {
        float negative=TasteChartView.radarFraction(-3),neutral=TasteChartView.radarFraction(0),positive=TasteChartView.radarFraction(3);
        assertTrue(negative>=0&&negative<neutral); assertTrue(neutral<positive&&positive<=1);
        assertEquals(.75f,TasteChartView.radarFraction(1),0);
        assertEquals(positive,TasteChartView.radarFraction(100),0);
    }
    @Test public void affinityPercentUsesTheSameRatingScaleForEveryChart() {
        assertEquals(0,ViewerProfiles.affinityPercent(-3));
        assertEquals(33,ViewerProfiles.affinityPercent(-1));
        assertEquals(50,ViewerProfiles.affinityPercent(0));
        assertEquals(75,ViewerProfiles.affinityPercent(1));
        assertEquals(100,ViewerProfiles.affinityPercent(2));
        assertEquals(0,ViewerProfiles.affinityPercent(-10));
        assertEquals(100,ViewerProfiles.affinityPercent(10));
        assertEquals(50,ViewerProfiles.affinityPercent(Double.NaN));
    }
    @Test public void creationAppliesNameAndAppearanceTogetherAndValidationLeavesNoOrphan() {
        ViewerProfiles profiles=ProfileFixtures.household(); String before=profiles.toJson();
        assertThrows(IllegalArgumentException.class,() -> profiles.createProfile("Novo","unknown",2));
        assertThrows(IllegalArgumentException.class,() -> profiles.createProfile("Pessoa Um","cat",2));
        assertThrows(IllegalArgumentException.class,() -> profiles.createProfile("Novo","cat",100));
        assertEquals(before,profiles.toJson());
        ViewerProfiles.Profile created=profiles.createProfile("  Novo Perfil  ","otter",12);
        assertEquals("Novo Perfil",created.name); assertEquals("otter",created.avatar); assertEquals(12,created.color); assertEquals(5,profiles.profiles().size());
        assertTrue(created.votes.isEmpty()); assertEquals(ViewerProfiles.GENERAL,profiles.selected().id);
    }
    private MediaRepository.MediaItem item(int id,String type,String genre,int year) {
        return new MediaRepository.MediaItem("tt"+(8000000+id),"Título "+id,type,"8",String.valueOf(year),genre,"","","","","",1);
    }
    @Test public void emptyAndWatchedOnlyProfilesDoNotInventAffinities() {
        ViewerProfiles profiles=ProfileFixtures.household();
        assertTrue(profiles.overview("one",2026).features.isEmpty());
        profiles.markWatched("one",item(1,"series","Drama",2024));
        ViewerProfiles.TasteOverview data=profiles.overview("one",2026);
        assertEquals(1,data.watched); assertEquals(1,data.series); assertEquals(0,data.rated); assertEquals(0,data.people); assertTrue(data.features.isEmpty());
    }
    @Test public void oneBadMovieDoesNotImplyDislikingAllMoviesOrItsEra() {
        ViewerProfiles profiles=ProfileFixtures.household(); profiles.rate("one",item(1,"movie","Terror",2026),0);
        ViewerProfiles.TasteOverview data=profiles.overview("one",2026);
        assertTrue(data.category("g:").get("Terror")<0);
        assertEquals(0,data.category("t:").get("movie"),0);
        for (double value:data.category("e:").values()) assertEquals(0,value,0);
    }
    @Test public void countsTitlesOnceButRetainsEachPersonsOpinion() {
        ViewerProfiles profiles=ProfileFixtures.household(); MediaRepository.MediaItem shared=item(1,"movie","Drama",2024);
        profiles.rate("one",shared,4); profiles.rate("two",shared,0); profiles.rate("one",item(2,"series","Comédia",1998),3);
        profiles.markWatched("three",item(3,"movie","Aventura",2026));
        String before=profiles.toJson(); ViewerProfiles.TasteOverview data=profiles.overview(ViewerProfiles.GENERAL,2026);
        assertEquals(3,data.watched); assertEquals(2,data.movies); assertEquals(1,data.series); assertEquals(3,data.rated); assertEquals(2,data.liked); assertEquals(2,data.people);
        assertArrayEquals(new int[]{1,0,0,1,1},data.ratings); assertEquals(before,profiles.toJson());
        ViewerProfiles.TasteOverview person=profiles.overview("one",2026);
        assertEquals(2,person.watched); assertEquals(2,person.rated); assertEquals(2,person.liked); assertEquals(0,person.ratings[0]);
    }
    @Test public void chartsUsePositiveNegativeAndNeutralSignalsAndGroupConsensus() {
        ViewerProfiles profiles=ProfileFixtures.household();
        profiles.rate("one",item(1,"movie","Drama",2026),4); profiles.rate("one",item(2,"series","Terror",1990),0); profiles.rate("one",item(3,"movie","Comédia",2015),2);
        ViewerProfiles.TasteOverview data=profiles.overview("one",2026);
        assertTrue(data.category("g:").get("Drama")>0); assertTrue(data.category("g:").get("Terror")<0); assertEquals(0,data.category("g:").get("Comédia"),0);
        assertTrue(data.category("d:").containsKey("Anos 1990")); assertTrue(data.category("t:").containsKey("series"));
        profiles.rate("two",item(4,"movie","Drama",2026),0);
        double general=profiles.overview(ViewerProfiles.GENERAL,2026).category("g:").get("Drama");
        assertTrue(general<0); assertTrue(general<data.category("g:").get("Drama"));
    }
    @Test public void confirmedRatingsImmediatelyReorderHomeAndDiscovery() {
        ViewerProfiles profiles=ProfileFixtures.household(); profiles.select("one");
        MediaRepository.MediaItem romance=item(1,"movie","Romance",2026),drama=item(2,"movie","Drama",2026),rated=item(3,"movie","Drama",2026);
        List<MediaRepository.MediaItem> base=Arrays.asList(romance,drama);
        DiscoveryQuery query=new DiscoveryQuery("one"); query.type="movie"; query.includeWatched=false;
        assertEquals(romance,profiles.rank(base,false,2026).get(0));
        profiles.rate("one",rated,4);
        assertEquals(drama,profiles.rank(base,false,2026).get(0)); assertEquals(drama,profiles.discover(base,query,2026).get(0));
        profiles.rate("one",rated,0);
        assertEquals(romance,profiles.rank(base,false,2026).get(0)); assertEquals(romance,profiles.discover(base,query,2026).get(0));
        profiles.rate("one",romance,3);
        assertFalse(profiles.rank(base,false,2026).contains(romance)); assertFalse(profiles.discover(base,query,2026).contains(romance));
    }
}
