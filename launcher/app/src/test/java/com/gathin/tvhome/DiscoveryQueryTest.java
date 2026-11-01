package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class DiscoveryQueryTest {
    @Test public void pagedCatalogDoesNotSkipFiftyItemPagesAndSamplesChosenEra() {
        DiscoveryQuery query=new DiscoveryQuery("one");
        assertEquals(Arrays.asList("top/skip=0","top/skip=50","top/skip=100"),MediaRepository.discoveryPaths(query,2026).subList(0,3));
        query.genres.add("Drama"); query.era=0; java.util.List<String> paths=MediaRepository.discoveryPaths(query,2026);
        assertEquals("top/genre=Drama&skip=0",paths.get(0)); assertTrue(paths.contains("year/genre=2024&skip=50"));
        assertEquals(36,paths.size()); assertEquals(paths.size(),new java.util.HashSet<>(paths).size());
        query.era=2; assertTrue(MediaRepository.discoveryPaths(query,2026).contains("year/genre=1996&skip=0"));
    }
    @Test public void targetCountUsesTheSameHistoryAndGenreRulesAsFinalRanking() {
        ViewerProfiles profiles=ProfileFixtures.household(); DiscoveryQuery query=new DiscoveryQuery("one"); query.genres.add("Drama");
        java.util.List<MediaRepository.MediaItem> items=new java.util.ArrayList<>();
        for (int i=0;i<160;i++) { MediaRepository.MediaItem title=item(i,"movie",i%4==0?"Terror":"Drama","2026");items.add(title); if (i<20) profiles.markWatched("one",title); }
        int eligible=0; for (MediaRepository.MediaItem title:items) if (profiles.acceptsDiscovery(title,query,2026)) eligible++;
        assertEquals(105,eligible); assertEquals(eligible,profiles.discover(items,query,2026).size()); assertTrue(eligible>=MediaRepository.MIN_DISCOVERY_RESULTS);
    }
    @Test public void repeatedSearchChangesFirstPageWithoutMutatingRankingOrRemovingResults() {
        MediaRepository.MediaItem a=item(1,"movie","Drama","2026"),b=item(2,"movie","Drama","2026"),c=item(3,"movie","Drama","2026");
        List<MediaRepository.MediaItem> base=Arrays.asList(a,b,c);
        assertEquals(Arrays.asList(b,a,c),DiscoveryQuery.avoidRepeatedOrder(base,Arrays.asList(a.imdbId,b.imdbId,c.imdbId)));
        assertEquals(a,base.get(0)); assertEquals(base,DiscoveryQuery.avoidRepeatedOrder(base,Arrays.asList(b.imdbId,a.imdbId,c.imdbId)));
        assertEquals(Arrays.asList(a),DiscoveryQuery.avoidRepeatedOrder(Arrays.asList(a),Arrays.asList(a.imdbId)));
        List<MediaRepository.MediaItem> last=base;
        for (int i=0;i<30;i++) { List<MediaRepository.MediaItem> next=DiscoveryQuery.avoidRepeatedOrder(base,Arrays.asList(last.get(0).imdbId,last.get(1).imdbId,last.get(2).imdbId)); assertNotEquals(last,next); last=next; }
    }
    private MediaRepository.MediaItem item(int id,String type,String genre,String year) {
        return new MediaRepository.MediaItem("tt"+(1000000+id),"Título "+id,type,"8",year,genre,"","","","","",0);
    }
    @Test public void peopleAllowMultipleIndividualsOrGeneralExclusivelyAndDraftDoesNotMutateOriginal() {
        DiscoveryQuery query=new DiscoveryQuery(ViewerProfiles.GENERAL); query.togglePerson("one"); query.togglePerson("two");
        assertEquals(2,query.people.size()); assertFalse(query.people.contains(ViewerProfiles.GENERAL));
        DiscoveryQuery copy=new DiscoveryQuery(query); copy.togglePerson(ViewerProfiles.GENERAL); copy.genres.add("Drama");
        assertEquals(1,copy.people.size()); assertEquals(2,query.people.size()); assertTrue(query.genres.isEmpty());
        query.togglePerson("one"); query.togglePerson("two"); assertTrue(query.people.isEmpty());
    }
    @Test public void multipleGenresMatchAnySelectedButNotSubstringAndFormatIsStrict() {
        DiscoveryQuery query=new DiscoveryQuery("one"); query.genres.add("Comedy"); query.genres.add("Drama");
        assertTrue(query.accepts(item(1,"movie","Comédia, Romance","2026"),2026));
        assertTrue(query.accepts(item(2,"movie","Drama","2026"),2026));
        assertFalse(query.accepts(item(3,"movie","Comédia dramática","2026"),2026));
        assertFalse(query.accepts(item(4,"series","Drama","2026"),2026));
        query.genres.clear(); assertTrue(query.accepts(item(5,"movie","Terror","2026"),2026));
    }
    @Test public void erasUseReleaseYearAndExactBoundariesWithoutInventingMissingYears() {
        DiscoveryQuery query=new DiscoveryQuery("two"); assertEquals(1,query.era); query.era=0;
        assertTrue(query.accepts(item(1,"movie","Drama","2024"),2026)); assertFalse(query.accepts(item(2,"movie","Drama","2023"),2026));
        assertFalse(query.accepts(item(3,"movie","Drama","2027"),2026)); assertFalse(query.accepts(item(4,"movie","Drama",""),2026));
        query.era=2; assertFalse(query.accepts(item(5,"movie","Drama","2011"),2026)); assertTrue(query.accepts(item(6,"movie","Drama","2010"),2026));
        query.era=1; assertTrue(query.accepts(item(7,"movie","Drama","2018"),2026)); assertTrue(query.accepts(item(8,"movie","Drama",""),2026));
        assertArrayEquals(new String[]{"Lançamentos","Gerais","Clássicos"},DiscoveryQuery.ERAS);
        assertEquals(2020,DiscoveryQuery.releaseYear("2020–2026"));
    }
    @Test public void genreOrderUsesPortugueseAlphabetWithoutChangingCatalogIds() {
        java.util.List<String> names=new java.util.ArrayList<>();
        for (int index:DiscoveryQuery.alphabeticalGenres()) names.add(DiscoveryQuery.GENRE_NAMES[index]);
        assertEquals(Arrays.asList("Ação","Animação","Aventura","Biografia","Comédia","Documentário","Drama","Esporte","Família","Fantasia","Faroeste","Ficção Científica","Guerra","História","Mistério","Policial","Romance","Suspense","Terror"),names);
        assertEquals(19,new java.util.HashSet<>(DiscoveryQuery.alphabeticalGenres()).size());
    }
    @Test public void chosenPeopleDetermineConsensusWithoutUsingActiveOrUnchosenProfiles() {
        ViewerProfiles profiles=ProfileFixtures.household();
        profiles.rate("one",item(1,"movie","Drama","2026"),4); profiles.rate("two",item(2,"movie","Comédia","2026"),4);
        profiles.rate("three",item(3,"movie","Terror","2026"),4); profiles.select("three");
        DiscoveryQuery query=new DiscoveryQuery("one"); query.togglePerson("two");
        List<MediaRepository.MediaItem> base=Arrays.asList(item(4,"movie","Terror","2026"),item(5,"movie","Drama","2026"),item(6,"movie","Comédia","2026"));
        List<MediaRepository.MediaItem> ranked=profiles.discover(base,query,2026);
        assertEquals(base.get(0),ranked.get(2)); assertEquals("three",profiles.selected().id);
        query.people.clear(); query.people.add("three"); assertEquals(base.get(0),profiles.discover(base,query,2026).get(0));
    }
    @Test public void groupDisagreementLowersRankAndUnequalVoteCountsDoNotAddPeople() {
        ViewerProfiles profiles=ProfileFixtures.household();
        for (int i=1;i<=20;i++) profiles.rate("one",item(i,"movie","Terror","2026"),4);
        for (int i=21;i<=40;i++) profiles.rate("two",item(i,"movie","Terror","2026"),0);
        DiscoveryQuery query=new DiscoveryQuery("one"); query.togglePerson("two");
        List<MediaRepository.MediaItem> base=Arrays.asList(item(41,"movie","Terror","2026"),item(42,"movie","Drama","2026"));
        assertEquals(base.get(1),profiles.discover(base,query,2026).get(0));
    }
    @Test public void excludeWatchedIsTemporaryReadOnlyAndIndependentOfTwentyOneDayCooldown() {
        ViewerProfiles profiles=ProfileFixtures.household(); MediaRepository.MediaItem movie=item(1,"movie","Drama","2026");
        profiles.rate("one",movie,4,1000); profiles.select("one"); String before=profiles.toJson();
        DiscoveryQuery query=new DiscoveryQuery("one"); List<MediaRepository.MediaItem> base=Arrays.asList(movie);
        assertTrue(profiles.discover(base,query,2026).isEmpty()); assertEquals(before,profiles.toJson());
        assertEquals(base,profiles.rank(base,false,2026));
        query.includeWatched=true; assertEquals(base,profiles.discover(base,query,2026)); assertEquals(before,profiles.toJson());
        query.includeWatched=false; query.people.clear(); query.people.add("two"); assertEquals(base,profiles.discover(base,query,2026));
        query.togglePerson(ViewerProfiles.GENERAL); assertTrue(profiles.discover(base,query,2026).isEmpty());
    }
    @Test public void generalIncludesJointHistoryAndColdGroupKeepsDeduplicatedOrder() {
        ViewerProfiles profiles=ProfileFixtures.household(); MediaRepository.MediaItem movie=item(1,"movie","Drama","2026");
        profiles.markWatched(ViewerProfiles.GENERAL,movie); DiscoveryQuery query=new DiscoveryQuery("two");
        assertEquals(1,profiles.discover(Arrays.asList(movie,movie),query,2026).size());
        query.togglePerson(ViewerProfiles.GENERAL); assertTrue(profiles.discover(Arrays.asList(movie),query,2026).isEmpty());
    }
    @Test public void networkCatalogUsesTrustedIdsBothGenreKeysAndDoesNotRequirePerItemRequests() throws Exception {
        String body="{\"metas\":[{\"id\":\"tt1000001\",\"name\":\"One\",\"genres\":[\"Comedy\"],\"releaseInfo\":\"1999\"},{\"imdb_id\":\"tt1000002\",\"name\":\"Two\",\"genre\":[\"Sci-Fi\"]},{\"id\":\"../../bad\",\"name\":\"Bad\"},{\"id\":\"tt1000003\"}]}";
        List<MediaRepository.MediaItem> result=MediaRepository.parseDiscoveryCatalog(body,"movie");
        assertEquals(2,result.size()); assertEquals("Comédia",result.get(0).genresPt); assertEquals("Ficção Científica",result.get(1).genresPt);
        assertTrue(result.get(0).posterUrl.startsWith("https://images.metahub.space/"));
        assertThrows(Exception.class,()->MediaRepository.parseDiscoveryCatalog("{}","movie"));
    }
}
