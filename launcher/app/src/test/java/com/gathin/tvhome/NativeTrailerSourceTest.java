package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NativeTrailerSourceTest {
    private static final String ID = "umc.cmc.example";
    private static final String STREAM = "https://play-edge.itunes.apple.com/hls/playlist.m3u8?cc=BR";

    private JSONObject playable() throws Exception {
        return new JSONObject().put("title", "Trailer oficial").put("duration", 125).put("contentType", "Extra")
                .put("isEntitledToPlay", true).put("primaryLocale", new JSONObject().put("locale", "pt_BR"))
                .put("assets", new JSONObject().put("hlsUrl", STREAM));
    }

    private String page(JSONObject clip, String shelf, boolean playlist) throws Exception {
        JSONObject item = new JSONObject().put("playable", clip);
        JSONObject trailers = new JSONObject().put("id", shelf);
        if (playlist) {
            trailers.put("playlistItems", new JSONArray().put(item));
        } else {
            trailers.put("items", new JSONArray().put(new JSONObject().put("playAction", new JSONObject()
                    .put("contentDescriptor", new JSONObject().put("items", new JSONArray().put(item))))));
        }
        JSONObject entry = new JSONObject().put("intent", new JSONObject().put("id", ID))
                .put("data", new JSONObject().put("shelves", new JSONArray().put(trailers)));
        return "<script type=\"application/json\" id=\"serialized-server-data\">"
                + new JSONObject().put("data", new JSONArray().put(entry)) + "</script>";
    }

    @Test public void parsesMovieAndSeriesTrailerShapes() throws Exception {
        for (boolean playlist : new boolean[]{false, true}) {
            List<NativeTrailerSource.Candidate> candidates = NativeTrailerSource.parseAppleTrailers(
                    page(playable(), "uts.col.Trailers." + ID, playlist), ID);
            assertEquals(1, candidates.size());
            assertEquals(STREAM, candidates.get(0).streamUrl);
            assertEquals("pt-BR", candidates.get(0).originalLanguage);
        }
    }

    @Test public void rejectsUnrelatedShelvesAndTitles() throws Exception {
        assertTrue(NativeTrailerSource.parseAppleTrailers(page(playable(), "uts.col.ContentRelated." + ID, true), ID).isEmpty());
        assertTrue(NativeTrailerSource.parseAppleTrailers(page(playable(), "uts.col.Trailers." + ID, true), "umc.cmc.other").isEmpty());
        assertTrue(NativeTrailerSource.parseAppleTrailers("<html>Unavailable</html>", ID).isEmpty());
    }

    @Test public void acceptsMovieTrailerWithoutOptionalDuration() throws Exception {
        JSONObject clip = playable();
        clip.remove("duration");
        assertEquals(1, NativeTrailerSource.parseAppleTrailers(page(clip, "uts.col.Trailers." + ID, false), ID).size());
        assertTrue(NativeTrailerSource.parseAppleTrailers(page(playable().put("contentType", "Movie"), "uts.col.Trailers." + ID, false), ID).isEmpty());
    }

    @Test public void rejectsPaidRestrictedOrNonTrailerPlayback() throws Exception {
        for (String flag : new String[]{"isGeoRestricted", "isEmbargoed"}) {
            assertTrue(NativeTrailerSource.parseAppleTrailers(page(playable().put(flag, true), "uts.col.Trailers." + ID, true), ID).isEmpty());
        }
        for (JSONObject clip : new JSONObject[]{playable().put("isEntitledToPlay", false), playable().put("duration", 7200),
                playable().put("assets", new JSONObject().put("hlsUrl", STREAM).put("requiresPlaybackId", true))}) {
            assertTrue(NativeTrailerSource.parseAppleTrailers(page(clip, "uts.col.Trailers." + ID, true), ID).isEmpty());
        }
    }

    @Test public void rejectsUntrustedStreamUrls() throws Exception {
        for (String url : new String[]{"http://play-edge.itunes.apple.com/x.m3u8", "https://evil.example/x.m3u8",
                "https://apple.com.evil.example/x.m3u8", "https://user@play-edge.itunes.apple.com/x.m3u8",
                "https://play-edge.itunes.apple.com:444/x.m3u8", "https://play-edge.itunes.apple.com/movie.mp4"}) {
            JSONObject clip = playable().put("assets", new JSONObject().put("hlsUrl", url));
            assertTrue(url, NativeTrailerSource.parseAppleTrailers(page(clip, "uts.col.Trailers." + ID, true), ID).isEmpty());
        }
    }

    private JSONObject searchNode(String imdb, String url) throws Exception {
        return new JSONObject().put("node", new JSONObject()
                .put("content", new JSONObject().put("externalIds", new JSONObject().put("imdbId", imdb)))
                .put("brOffers", new JSONArray().put(new JSONObject().put("standardWebURL", url))));
    }

    private JSONObject search(JSONObject... nodes) throws Exception {
        JSONArray edges = new JSONArray();
        for (JSONObject node : nodes) edges.put(node);
        return new JSONObject().put("data", new JSONObject().put("popularTitles", new JSONObject().put("edges", edges)));
    }

    @Test public void matchesImdbExactlyAndDeduplicatesStorefronts() throws Exception {
        JSONObject results = search(searchNode("ttWrong", "https://tv.apple.com/br/movie/wrong/umc.cmc.wrong"),
                searchNode("tt14961016", "https://tv.apple.com/us/movie/right/" + ID),
                searchNode("tt14961016", "https://tv.apple.com/us/movie/right/" + ID));
        List<String> pages = NativeTrailerSource.findApplePages(results, "tt14961016", false);
        assertEquals(2, pages.size());
        assertEquals("https://tv.apple.com/br/movie/trailer/" + ID, pages.get(0));
        assertEquals("https://tv.apple.com/us/movie/trailer/" + ID, pages.get(1));
    }

    @Test public void resolvesSeriesFromEpisodeOfferWithoutPlayingEpisode() throws Exception {
        JSONObject results = search(searchNode("tt14688458", "https://tv.apple.com/br/episode/pilot/umc.cmc.episode?showId=" + ID));
        assertEquals("https://tv.apple.com/br/show/trailer/" + ID,
                NativeTrailerSource.findApplePages(results, "tt14688458", true).get(0));
        assertTrue(NativeTrailerSource.findApplePages(results, "tt14688458", false).isEmpty());
        assertTrue(NativeTrailerSource.findApplePages(search(searchNode("tt14688458", "https://tv.apple.com.evil.example/br/show/x/" + ID)), "tt14688458", true).isEmpty());
    }
}
