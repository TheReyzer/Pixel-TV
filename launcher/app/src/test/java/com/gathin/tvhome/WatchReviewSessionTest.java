package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class WatchReviewSessionTest {
    private static final String APP="com.stremio.one";
    private static MediaRepository.MediaItem item(String id,String name) { return new MediaRepository.MediaItem(id,name,"movie","8","2026","Drama","","","","","",100); }
    private static PlaybackJournal.Entry entry(String name,String id) {
        PlaybackJournal.Entry e=new PlaybackJournal.Entry(); e.pkg=APP; e.title=name; e.mediaId=id; return e;
    }
    @Test public void multipleTitlesUseAppQuestionThenFollowChronologicalOrder() {
        PlaybackJournal.Entry first=entry("Primeiro","tt1234567"),second=entry("Segundo","tt7654321"),third=entry("Terceiro","tt1234568");
        WatchReviewSession s=new WatchReviewSession(Arrays.asList(third,second,first));
        assertTrue(s.multipleTitles); assertEquals("Stremio",s.apps); assertSame(first,s.first());
        s.completed(item("tt1234567","Primeiro"),true); assertSame(second,s.nextDetected());
        s.completed(item("tt7654321","Segundo"),true); assertSame(third,s.nextDetected());
        s.completed(item("tt1234568","Terceiro"),true); assertNull(s.nextDetected()); assertEquals(3,s.saved);
    }
    @Test public void repeatedSeriesEpisodesAndTheSameTitleAcrossAppsAreOfferedOnce() {
        PlaybackJournal.Entry first=entry("Série","tt1234567:1:1"),second=entry("Série","tt1234567:1:2");
        second.pkg="com.netflix.ninja";
        WatchReviewSession s=new WatchReviewSession(Arrays.asList(second,first)); assertFalse(s.multipleTitles); assertEquals("Stremio, Netflix",s.apps);
        s.completed(item("tt1234567","Série"),true); assertNull(s.nextDetected());
    }
    @Test public void differentImdbIdsWithTheSameNameRemainDistinct() {
        WatchReviewSession s=new WatchReviewSession(Arrays.asList(entry("Mesmo nome","tt1234567"),entry("Mesmo nome","tt7654321")));
        assertTrue(s.multipleTitles); s.completed(item("tt1234567","Mesmo nome"),true); assertNotNull(s.nextDetected());
    }
    @Test public void unknownPlaybackAllowsManualReviewWithoutInventingAnotherTitle() {
        WatchReviewSession s=new WatchReviewSession(Collections.singletonList(entry("","")));
        assertFalse(s.multipleTitles); assertNotNull(s.first()); assertNull(s.nextDetected());
        s.completed(item("tt1234567","Escolhido manualmente"),true); assertNull(s.nextDetected()); assertEquals(1,s.saved);
    }
    @Test public void keepingExistingRatingsSkipsThatTitleWithoutCountingANewSave() {
        WatchReviewSession s=new WatchReviewSession(Arrays.asList(entry("B","tt7654321"),entry("A","tt1234567")));
        s.completed(item("tt1234567","A"),false); assertEquals("B",s.nextDetected().title); assertEquals(0,s.saved); assertEquals(1,s.handled);
    }
    @Test public void humanConfirmedSearchResolvesAnObservedAliasWithoutRepeatingIt() {
        PlaybackJournal.Entry observed=entry("Nome original",""); WatchReviewSession s=new WatchReviewSession(Collections.singletonList(observed));
        MediaRepository.MediaItem selected=item("tt1234567","Nome traduzido"); observed.item=selected;
        s.completed(selected,true); assertNull(s.nextDetected());
        WatchReviewSession manual=new WatchReviewSession(Collections.emptyList()); manual.completed(selected,true); assertEquals(1,manual.saved); assertNull(manual.nextDetected());
    }
}
