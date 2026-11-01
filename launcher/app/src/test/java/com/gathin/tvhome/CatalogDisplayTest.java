package com.gathin.tvhome;
import org.junit.Test;
import java.util.*;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class CatalogDisplayTest {
    private MediaRepository.MediaItem item(int id,String genre,String date) { return new MediaRepository.MediaItem("tt"+(8000000+id),"Original "+id,"movie","8",date.substring(0,4),genre,"","","","",date,1); }
    @Test public void novidadesOnlyContainsReleasedTitlesThisYearInDescendingOrder() {
        MediaRepository.MediaItem old=item(1,"Drama","2024-12-01"),future=item(2,"Drama","2026-12-01"),first=item(3,"Drama","2026-01-01"),newest=item(4,"Drama","2026-10-22"),tie=item(5,"Drama","2026-10-22");
        assertEquals(Arrays.asList(newest,tie,first),MediaRepository.newReleases(Arrays.asList(old,first,newest,future,tie),LocalDate.of(2026,10,23)));
        assertTrue(MediaRepository.newReleases(Arrays.asList(old),LocalDate.of(2026,10,23)).isEmpty());
        MediaRepository.MediaItem conflicting=new MediaRepository.MediaItem("tt12345678","Antigo","movie","8","2024","Drama","","","","","2026-10-01",1);
        assertTrue(MediaRepository.newReleases(Arrays.asList(conflicting),LocalDate.of(2026,10,23)).isEmpty());
    }
    @Test public void mixedSearchRetainsBothFormatsWithoutDiscardingNextRows() throws Exception {
        List<MediaRepository.MediaItem> movies=new ArrayList<>(),series=new ArrayList<>();
        for (int i=0;i<40;i++) { movies.add(item(i,"Drama","2026-01-01")); series.add(new MediaRepository.MediaItem("tt"+(7000000+i),"Série "+i,"series","8","2026","Drama","","","","","2026-01-01",1)); }
        List<MediaRepository.MediaItem> results=MediaRepository.mixedSearch("geral",movies,series);
        assertEquals(80,results.size()); assertEquals(40,results.stream().filter(MediaRepository.MediaItem::isSeries).count());
    }
    @Test public void oldEnglishGenresAndNewBrazilianGenresShareTheSamePreference() {
        ViewerProfiles profiles=ProfileFixtures.household(); profiles.select("one"); profiles.rate("one",item(5,"Short, Comedy","2026-01-01"),4);
        assertTrue(profiles.overview("one",2026).category("g:").containsKey("Curta-metragem"));
        MediaRepository.MediaItem match=item(1,"Curta-metragem, Comédia","2026-01-01"),other=item(2,"Drama","2026-01-01");
        assertEquals(match,profiles.rank(Arrays.asList(other,match),false,2026).get(0));
    }
    @Test public void localizedTitleNeedsExactIdentityAndOriginalIsSecondary() {
        MediaRepository.MediaItem original=item(1,"Drama","2026-01-01"),br=item(1,"Drama","2026-01-01"); br.title="Título brasileiro"; br.originalTitle=original.title;
        assertTrue(MediaRepository.applyBrazilianTitle(original,br)); assertEquals("Título brasileiro",original.title); assertEquals("Original 1",original.originalSubtitle());
        assertFalse(MediaRepository.applyBrazilianTitle(original,item(2,"Drama","2026-01-01"))); assertEquals("Título brasileiro",original.title);
        original.originalTitle="Título brasileiro"; assertEquals("",original.originalSubtitle());
        MediaRepository.MediaItem cached=MediaRepository.MediaItem.fromJson(original.toJson()); assertTrue(cached.titleInPtBr); assertEquals(original.title,cached.title);
    }
    @Test public void changingActiveProfileUsesThatPersonsPreferencesImmediately() {
        ViewerProfiles profiles=ProfileFixtures.household(); MediaRepository.MediaItem drama=item(1,"Drama","2026-01-01"),comedy=item(2,"Comédia","2026-01-01");
        profiles.rate("one",item(3,"Drama","2026-01-01"),4); profiles.rate("one",item(4,"Comédia","2026-01-01"),0);
        profiles.rate("two",item(5,"Drama","2026-01-01"),0); profiles.rate("two",item(6,"Comédia","2026-01-01"),4);
        profiles.select("one"); assertEquals(drama,profiles.rank(Arrays.asList(comedy,drama),false,2026).get(0));
        profiles.select("two"); assertEquals(comedy,profiles.rank(Arrays.asList(drama,comedy),false,2026).get(0));
    }
    @Test public void novidadesCombinesPersonalTasteWithThisYearAndRecency() {
        ViewerProfiles profiles=ProfileFixtures.household(); profiles.select("one");
        MediaRepository.MediaItem recent=item(1,"Terror","2026-10-22"),olderThisYear=item(2,"Drama","2026-05-01"),old=item(3,"Drama","2024-01-01");
        List<MediaRepository.MediaItem> releases=MediaRepository.newReleases(Arrays.asList(old,olderThisYear,recent),LocalDate.of(2026,10,23));
        assertEquals(recent,profiles.rank(releases,false,2026).get(0));
        profiles.rate("one",item(4,"Drama","2025-01-01"),4); profiles.rate("one",item(5,"Terror","2025-01-01"),0);
        List<MediaRepository.MediaItem> personal=profiles.rank(releases,false,2026); assertEquals(olderThisYear,personal.get(0)); assertFalse(personal.contains(old));
    }
}
