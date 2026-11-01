package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class WatchAvailabilityTest {
    @Test public void usesOnlyTheExactImdbEvenWhenHomonymsComeFirst() throws Exception {
        JSONObject wrong = node("tt9999999", offer("netflix", "Netflix", "https://www.netflix.com/title/999", "FLATRATE"));
        JSONObject correct = node("tt6933238", offer("globoplay", "Globoplay", "https://globoplay.globo.com/filme/t/abc/", "FLATRATE"));
        List<WatchAvailability.Offer> result = WatchAvailability.parse(root(wrong, correct), "tt6933238", false);
        assertEquals(1, result.size());
        assertEquals("globoplay", result.get(0).provider);
    }

    @Test public void amazonChannelsAppearUnderPrimeAndKeepTheExtraSubscriptionLabel() throws Exception {
        List<WatchAvailability.Offer> result = WatchAvailability.parse(root(node("tt23148340",
                offer("amazonuniversalplus", "Universal+ Amazon Channel", "https://app.primevideo.com/detail?gti=amzn1.dv.gti.123", "FLATRATE"))), "tt23148340", true);
        assertEquals("prime", result.get(0).provider);
        assertTrue(result.get(0).channel);
        assertEquals("Requer canal Universal+", result.get(0).description());
    }

    @Test public void prefersDirectSubscriptionAndDeduplicatesStoreOffers() throws Exception {
        List<WatchAvailability.Offer> result = WatchAvailability.parse(root(node("tt6933238",
                offer("amazon", "Amazon", "https://www.amazon.com/gp/video/detail/B012345678", "RENT"),
                offer("amazonprimevideo", "Amazon Prime Video", "https://app.primevideo.com/detail?gti=B012345678", "FLATRATE"),
                offer("amazonprimevideowithads", "Amazon Prime Video with Ads", "https://app.primevideo.com/detail?gti=B012345678", "FLATRATE"))), "tt6933238", false);
        assertEquals(1, result.size());
        assertEquals("Assinatura", result.get(0).description());
    }

    @Test public void rejectsCinemaInsecureLinksAndProviderHostMismatches() throws Exception {
        for (String url : new String[]{"http://www.netflix.com/title/1", "https://www.netflix.com.evil.example/title/1",
                "https://user@www.netflix.com/title/1", "https://www.netflix.com:444/title/1", "javascript:alert(1)"}) {
            assertTrue(WatchAvailability.parse(root(node("tt6933238", offer("netflix", "Netflix", url, "FLATRATE"))), "tt6933238", false).isEmpty());
        }
        assertTrue(WatchAvailability.parse(root(node("tt6933238", offer("netflix", "Netflix", "https://www.netflix.com/title/1", "CINEMA"))), "tt6933238", false).isEmpty());
        assertTrue(WatchAvailability.parse(root(node("tt6933238", offer("globoplay", "Globoplay", "https://www.netflix.com/title/1", "FLATRATE"))), "tt6933238", false).isEmpty());
    }

    @Test public void buildsTitleLinksWithoutAutoplayAndPreservesSeriesIdentity() {
        assertEquals("https://www.netflix.com/title/82010386", WatchAvailability.contentLink("netflix", "https://www.netflix.com/br/watch/82010386?track=abc", false));
        assertEquals("https://app.primevideo.com/detail?gti=B012345678", WatchAvailability.contentLink("prime", "https://www.amazon.com/gp/video/detail/B012345678", false));
        assertEquals("https://tv.apple.com/br/show/umc.cmc.abc123", WatchAvailability.contentLink("apple", "https://tv.apple.com/br/episode/foo/umc.cmc.ep123?showId=umc.cmc.abc123", true));
        assertEquals("stremio:///detail/movie/tt6933238/tt6933238?autoPlay=false", WatchAvailability.stremioLink("tt6933238", false));
        assertEquals("stremio:///detail/series/tt23148340?autoPlay=false", WatchAvailability.stremioLink("tt23148340", true));
        assertEquals("", WatchAvailability.stremioLink("../evil", true));
    }

    @Test public void bindsDeferredLinksToTheChosenPackage() {
        assertTrue(WatchAvailability.allowedLink("com.netflix.ninja", "https://www.netflix.com/title/82010386"));
        assertFalse(WatchAvailability.allowedLink("com.apple.atve.androidtv.appletv", "https://www.netflix.com/title/82010386"));
        assertFalse(WatchAvailability.allowedLink("com.netflix.ninja", "https://www.netflix.com/watch/82010386"));
        assertFalse(WatchAvailability.allowedLink("com.netflix.ninja", "https://user@www.netflix.com/title/82010386"));
    }

    private JSONObject offer(String tech, String name, String url, String monetization) throws Exception {
        return new JSONObject().put("package", new JSONObject().put("technicalName", tech).put("clearName", name))
                .put("standardWebURL", url).put("monetizationType", monetization);
    }

    private JSONObject node(String imdb, JSONObject... offers) throws Exception {
        JSONArray list = new JSONArray(); for (JSONObject offer : offers) list.put(offer);
        return new JSONObject().put("node", new JSONObject().put("content", new JSONObject().put("externalIds", new JSONObject().put("imdbId", imdb))).put("offers", list));
    }

    private JSONObject root(JSONObject... nodes) throws Exception {
        JSONArray edges = new JSONArray(); for (JSONObject node : nodes) edges.put(node);
        return new JSONObject().put("data", new JSONObject().put("popularTitles", new JSONObject().put("edges", edges)));
    }
}
