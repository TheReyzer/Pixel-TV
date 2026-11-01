package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class YoutubeTrailerSourceTest {
    @Test public void eachRequestUsesOnlyTheRemainingLookupBudget() throws Exception {
        assertEquals(6000,YoutubeTrailerSource.requestTimeout(20_000_000_000L,6000));
        assertEquals(750,YoutubeTrailerSource.requestTimeout(750_000_000L,6000));
        assertEquals(1,YoutubeTrailerSource.requestTimeout(1,6000));
        for (long expired:new long[]{0,-1}) {
            try { YoutubeTrailerSource.requestTimeout(expired,6000); fail("Expired lookup must not start another request"); }
            catch (java.io.IOException expected) { }
        }
    }
    @Test public void prioritizesBrazilianDubBeforePortugueseSubtitlesAndEnglish() {
        assertEquals(3, YoutubeTrailerSource.languagePriority("Unabomber | Trailer oficial | Netflix"));
        assertEquals(1, YoutubeTrailerSource.languagePriority("UNABOMBER Trailer Legendado"));
        assertEquals(0, YoutubeTrailerSource.languagePriority("Unabomber Trailer Dublado"));
        assertEquals(0, YoutubeTrailerSource.languagePriority("Unabomber | Trailer | Dubbed (Brazil) (FHD)"));
        assertEquals(2, YoutubeTrailerSource.languagePriority("UNABOMBER Official Trailer"));
    }

    @Test public void rejectsTrailersThatExplicitlyBelongToADifferentYear() {
        assertTrue(YoutubeTrailerSource.matchesYear("Unabomber Trailer (2026)", "2026"));
        assertTrue(YoutubeTrailerSource.matchesYear("Unabomber Trailer oficial", "2026"));
        assertFalse(YoutubeTrailerSource.matchesYear("Unabomber Trailer 2022", "2026"));
    }

    @Test public void choosesPortugueseAudioBeforeHigherBitrateEnglishAudio() {
        org.schabi.newpipe.extractor.stream.AudioStream en = audio("en-US", 128);
        org.schabi.newpipe.extractor.stream.AudioStream pt = audio("pt-BR", 96);
        assertSame(pt, YoutubeTrailerSource.selectAudio(Arrays.asList(en, pt, audio("pt-PT", 128))));
        assertSame(en, YoutubeTrailerSource.selectAudio(Arrays.asList(audio("it", 128), en)));
    }

    private org.schabi.newpipe.extractor.stream.AudioStream audio(String locale, int bitrate) {
        return new org.schabi.newpipe.extractor.stream.AudioStream.Builder().setId(locale)
                .setContent("https://rr1.googlevideo.com/videoplayback", true)
                .setMediaFormat(org.schabi.newpipe.extractor.MediaFormat.M4A)
                .setDeliveryMethod(org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP)
                .setAverageBitrate(bitrate).setAudioLocale(java.util.Locale.forLanguageTag(locale)).build();
    }
    @Test public void keepsValidatedCinemetaTrailerIdsAndRemovesDuplicates() throws Exception {
        JSONObject metadata = new JSONObject("{\"meta\":{\"trailerYtId\":\"Rs49eRHFD9c\",\"trailers\":["
                + "{\"source\":\"Rs49eRHFD9c\",\"type\":\"Trailer\"},"
                + "{\"source\":\"3lZ2ZenuCGc\",\"type\":\"Trailer\"},"
                + "{\"source\":\"https://evil.example\",\"type\":\"Trailer\"},"
                + "{\"source\":\"EUb_04MdnMI\",\"type\":\"Movie\"}]}}");
        assertEquals(Arrays.asList("Rs49eRHFD9c", "3lZ2ZenuCGc"), YoutubeTrailerSource.metadataIds(metadata));
        assertTrue(YoutubeTrailerSource.metadataIds(new JSONObject()).isEmpty());
    }

    @Test public void rejectsMalformedVideoIds() {
        for (String id : new String[]{"", "short", "Rs49eRHFD9c_extra", "Rs49eRHFD9'", "https://youtube.com/watch?v=Rs49eRHFD9c"}) {
            assertFalse(id, YoutubeTrailerSource.validVideoId(id));
        }
        assertFalse(YoutubeTrailerSource.validVideoId(null));
        assertTrue(YoutubeTrailerSource.validVideoId("Rs49eRHFD9c"));
    }

    @Test public void searchRequiresTheTitleAndTrailerAndExcludesFanVideos() {
        assertTrue(YoutubeTrailerSource.relevantTrailer("Crystal Lake | Official Trailer | Peacock", "Crystal Lake"));
        assertTrue(YoutubeTrailerSource.relevantTrailer("Ainda Estou Aqui — Trailer Oficial", "Ainda Estou Aqui"));
        assertFalse(YoutubeTrailerSource.relevantTrailer("Crystal Lake | Concept Trailer", "Crystal Lake"));
        assertFalse(YoutubeTrailerSource.relevantTrailer("Crystal Lake Trailer Reaction", "Crystal Lake"));
        assertFalse(YoutubeTrailerSource.relevantTrailer("The Lake | Official Trailer", "Crystal Lake"));
        assertFalse(YoutubeTrailerSource.relevantTrailer("Crystal Lake Full Episode", "Crystal Lake"));
    }

    @Test public void acceptsOnlySecureGoogleVideoStreams() {
        assertTrue(YoutubeTrailerSource.validStreamUrl("https://manifest.googlevideo.com/api/manifest/hls_playlist/id/test"));
        assertTrue(YoutubeTrailerSource.validStreamUrl("https://rr1---sn.example.googlevideo.com/videoplayback?expire=123"));
        for (String url : new String[]{"http://manifest.googlevideo.com/a", "https://googlevideo.com.evil.example/a",
                "https://user@manifest.googlevideo.com/a", "https://manifest.googlevideo.com:444/a", "javascript:alert(1)"}) {
            assertFalse(url, YoutubeTrailerSource.validStreamUrl(url));
        }
    }

    @Test public void choosesHighestMp4ResolutionUpTo720p() {
        org.schabi.newpipe.extractor.stream.VideoStream low = video("360p");
        org.schabi.newpipe.extractor.stream.VideoStream hd = video("720p");
        assertSame(hd, YoutubeTrailerSource.selectVideo(Arrays.asList(low, video("1080p"), hd)));
        assertSame(low, YoutubeTrailerSource.selectVideo(Arrays.asList(video("1080p"), low)));
        assertNull(YoutubeTrailerSource.selectVideo(Arrays.asList(video("2160p"), video("unknown"))));
    }

    private org.schabi.newpipe.extractor.stream.VideoStream video(String resolution) {
        return new org.schabi.newpipe.extractor.stream.VideoStream.Builder().setId(resolution)
                .setContent("https://rr1.googlevideo.com/videoplayback", true)
                .setMediaFormat(org.schabi.newpipe.extractor.MediaFormat.MPEG_4)
                .setDeliveryMethod(org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP)
                .setIsVideoOnly(true).setResolution(resolution).build();
    }
}
