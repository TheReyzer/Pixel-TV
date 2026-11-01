package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class RelatedTitlesTest {
    private MediaRepository.MediaItem item(String id,String title,String year,String genres) { return new MediaRepository.MediaItem(id,title,"movie","8.0",year,genres,"","","","","",0); }
    @Test public void explicitSequelsAndSpinoffsAlwaysLeadSimilarity() {
        MediaRepository.MediaItem main=item("tt0133093","The Matrix","1999","Ação, Ficção Científica"),similar=item("tt0000001","Other","2000","Ação, Ficção Científica"),sequel=item("tt0234215","Reloaded","2003","Ação"),spinoff=item("tt0328832","Animatrix","2003","Animação"),later=item("tt10838180","Resurrections","2021","Ação");
        Map<String,Integer> links=new HashMap<>();links.put(sequel.imdbId,0);links.put(spinoff.imdbId,1);links.put(later.imdbId,0);
        List<MediaRepository.MediaItem> ordered=RelatedTitles.order(main,links,Arrays.asList(similar,spinoff,later,sequel,main,sequel));
        assertEquals(Arrays.asList(sequel,later,spinoff,similar),ordered);
    }
    @Test public void aMatchingTitlePrefixDoesNotProveASequel() {
        MediaRepository.MediaItem main=item("tt0000001","Night","2000","Comédia"),fake=item("tt0000002","Night 2","2025","Terror"),sameGenre=item("tt0000003","Different title","2002","Comédia");
        assertEquals(sameGenre,RelatedTitles.order(main,Collections.emptyMap(),Arrays.asList(fake,sameGenre)).get(0));
        assertEquals(4,RelatedTitles.priority(main,fake,Collections.emptyMap()));
    }
    @Test public void keepsTheStrongestDuplicateRelationshipAndRejectsBadRows() throws Exception {
        String rows="{\"results\":{\"bindings\":[{\"imdb\":{\"value\":\"tt0234215\"},\"rank\":{\"value\":\"2\"}},{\"imdb\":{\"value\":\"tt0234215\"},\"rank\":{\"value\":\"0\"}},{\"imdb\":{\"value\":\"tt0133093\"},\"rank\":{\"value\":\"0\"}},{\"imdb\":{\"value\":\"http://bad\"},\"rank\":{\"value\":\"0\"}},{}]}}";
        Map<String,Integer> links=RelatedTitles.connections(rows,"tt0133093");assertEquals(1,links.size());assertEquals(Integer.valueOf(0),links.get("tt0234215"));
    }
    @Test public void rejectsQueryInjection() {
        assertThrows(IllegalArgumentException.class,()->RelatedTitles.rootQuery("tt12345\" }"));
        assertThrows(IllegalArgumentException.class,()->RelatedTitles.connectionsQuery("Q1079 }"));
    }
    @Test public void readsValidatedRootIdentity() throws Exception {
        assertEquals("Q1079",RelatedTitles.root("{\"results\":{\"bindings\":[{\"root\":{\"value\":\"http://www.wikidata.org/entity/Q1079\"}}]}}"));
        assertEquals("",RelatedTitles.root("{\"results\":{\"bindings\":[]}}"));
    }
    @Test public void invalidRatingsStayFiniteAndResultsAreBounded() {
        MediaRepository.MediaItem main=item("tt0000001","Main","2020","Drama");List<MediaRepository.MediaItem> pool=new ArrayList<>();
        for(int i=2;i<80;i++) { MediaRepository.MediaItem value=item(String.format("tt%07d",i),"Title "+i,"2024","Drama");value.rating="NaN";pool.add(value); }
        assertTrue(Double.isFinite(RelatedTitles.similarity(main,pool.get(0))));assertEquals(36,RelatedTitles.order(main,Collections.emptyMap(),pool).size());
    }
    @Test public void derivativeQualityPreventsALowRatedRemakeLeadingTheSpinoff() {
        MediaRepository.MediaItem main=item("tt0000001","Original","2008","Drama, Policial"),spinoff=item("tt0000002","Spin-off","2015","Drama"),remake=item("tt0000003","Remake","2014","Drama, Policial");
        spinoff.rating="9.0";remake.rating="2.5";Map<String,Integer> links=new HashMap<>();links.put(spinoff.imdbId,1);links.put(remake.imdbId,1);
        assertEquals(spinoff,RelatedTitles.order(main,links,Arrays.asList(remake,spinoff)).get(0));
    }
    @Test public void newerFranchiseMembershipAloneDoesNotProveASequel() {
        MediaRepository.MediaItem main=item("tt0000001","Original","2000","Drama"),later=item("tt0000002","Derivative","2024","Drama");
        assertEquals(2,RelatedTitles.priority(main,later,Collections.singletonMap(later.imdbId,2)));
    }
}
