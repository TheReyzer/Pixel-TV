package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackJournalTest {
    private static final String NETFLIX="com.netflix.ninja",UNITV="com.global.unitviptv";
    private static final long START=1_800_000_000_000L;
    private static MediaRepository.MediaItem item() { return new MediaRepository.MediaItem("tt1234567","Título","movie","8","2026","Drama","","","","","2026-01-01",100); }
    private static void play(PlaybackJournal journal,String pkg,String title,String id,int seconds,long start) {
        for (int i=0;i<=seconds;i+=10) journal.sample(pkg,title,id,true,3_600_000,start+i*1000L,"session");
    }
    @Test public void browsingAndShortSamplesDoNotCountAsWatched() {
        PlaybackJournal j=new PlaybackJournal(); j.sample(NETFLIX,"","",false,0,START,"session"); j.home(START+120_000); assertTrue(j.pending().isEmpty());
        play(j,NETFLIX,"","",80,START); j.home(START+80_000); assertTrue(j.pending().isEmpty());
    }
    @Test public void netflixWithoutTitleStillOffersAnAppQuestionAfter90Seconds() {
        PlaybackJournal j=new PlaybackJournal(); play(j,NETFLIX,"","",90,START); j.home(START+90_000);
        assertEquals(1,j.pending().size()); assertNull(j.pending().get(0).item); assertEquals("",j.pending().get(0).title); assertEquals(90_000,j.pending().get(0).playedMs);
    }
    @Test public void manualPlaybackInNetflixAndUniTvRequiresExactly90ObservedSeconds() {
        assertEquals(90_000,PlaybackJournal.MIN_PLAY_MS);
        for (String pkg:new String[]{NETFLIX,UNITV}) for (long duration:new long[]{5_000,89_999,90_000}) {
            PlaybackJournal j=new PlaybackJournal(); String source=pkg.equals(UNITV)?"screen":"session";
            for (long elapsed=0;elapsed<duration;elapsed+=10_000) j.sample(pkg,pkg.equals(UNITV)?"The Walking Dead":"","",true,0,START+elapsed,source);
            j.home(START+duration);
            assertEquals(pkg+" at "+duration,duration>=90_000?1:0,j.pending().size());
            if (duration>=90_000) { assertEquals(duration,j.pending().get(0).playedMs); assertNull(j.pending().get(0).item); assertEquals(source,j.pending().get(0).source); }
        }
    }
    @Test public void knownLaunchPresumesTheTitleButNeverWritesAProfileVote() {
        PlaybackJournal j=new PlaybackJournal(); MediaRepository.MediaItem title=item(); j.rememberLaunch(UNITV,title,START); j.home(START+15_000);
        assertEquals(title,j.pending().get(0).item); assertEquals("opened",j.pending().get(0).source); assertEquals(0,j.pending().get(0).playedMs);
        j.discardSuggestions(); j.home(START+20_000); assertTrue(j.pending().isEmpty());
    }
    @Test public void aDifferentTitleInvalidatesThePresumedLaunch() {
        PlaybackJournal j=new PlaybackJournal(); j.rememberLaunch(UNITV,item(),START); play(j,UNITV,"Outro título","tt7654321",90,START); j.home(START+90_000);
        assertEquals(1,j.pending().size()); assertNull(j.pending().get(0).item); assertEquals("Outro título",j.pending().get(0).title);
    }
    @Test public void pausesAndClockJumpsNeverAddUnobservedTime() {
        PlaybackJournal j=new PlaybackJournal(); play(j,NETFLIX,"","",40,START); j.pauseAll(START+40_000);
        play(j,NETFLIX,"","",40,START+3_600_000); j.home(START+3_640_000); assertTrue(j.pending().isEmpty());
        j=new PlaybackJournal(); j.sample(NETFLIX,"","",true,0,START,"session"); j.sample(NETFLIX,"","",true,0,START+300_000,"session"); j.home(START+300_000); assertTrue(j.pending().isEmpty());
    }
    @Test public void trailersAndUnsupportedAppsAreIgnored() {
        PlaybackJournal j=new PlaybackJournal(); play(j,NETFLIX,"Trailer oficial","",120,START); play(j,"com.spotify.tv.android","Música","",120,START);
        for (int i=0;i<=120;i+=10) j.sample(UNITV,"Anúncio","",true,30_000,START+i*1000L,"screen");
        j.home(START+120_000); assertTrue(j.pending().isEmpty());
    }
    @Test public void recoveryRetainsObservedTimeButDoesNotCountOfflineTime() throws Exception {
        PlaybackJournal j=new PlaybackJournal(); play(j,NETFLIX,"","",50,START);
        j=PlaybackJournal.fromJson(j.toJson()); play(j,NETFLIX,"","",40,START+1000_000); j.home(START+1040_000);
        assertEquals(90_000,j.pending().get(0).playedMs);
        j.discardSuggestions(); assertTrue(PlaybackJournal.fromJson(j.toJson()).pending().isEmpty());
    }
    @Test public void aSeriesWithTrailerInItsActualNameIsNotMistakenForAPreview() {
        PlaybackJournal j=new PlaybackJournal(); play(j,"com.stremio.one","Trailer Park Boys","",90,START); j.home(START+90_000);
        assertEquals("Trailer Park Boys",j.pending().get(0).title);
    }
    @Test public void temporaryMissingMetadataKeepsOneIdentityAndHomeDeduplicates() {
        PlaybackJournal j=new PlaybackJournal(); j.rememberLaunch(NETFLIX,item(),START);
        play(j,NETFLIX,"Título","tt1234567",60,START); play(j,NETFLIX,"","",30,START+60_000); j.home(START+90_000);
        assertEquals(1,j.pending().size()); assertEquals("tt1234567",j.pending().get(0).item.imdbId);
    }
    @Test public void titleChangesKeepAllWatchedTitlesIncludingTheLastObservedInterval() throws Exception {
        PlaybackJournal j=new PlaybackJournal(); play(j,"com.stremio.one","Primeiro","tt1234567",80,START);
        play(j,"com.stremio.one","Segundo","tt7654321",90,START+90_000); j.home(START+180_000);
        assertEquals(2,j.pending().size()); assertEquals("Segundo",j.pending().get(0).title); assertEquals("Primeiro",j.pending().get(1).title);
        assertEquals(90_000,j.pending().get(1).playedMs);
        assertTrue(new WatchReviewSession(PlaybackJournal.fromJson(j.toJson()).pending()).multipleTitles);
        j.discardSuggestions(); assertTrue(PlaybackJournal.fromJson(j.toJson()).pending().isEmpty());
    }
}
