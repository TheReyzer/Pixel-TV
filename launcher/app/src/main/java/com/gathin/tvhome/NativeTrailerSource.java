package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves public trailer playlists, never the subscription/movie playback assets. */
final class NativeTrailerSource {
    static final class Candidate {
        final String streamUrl;
        final String title;
        final String originalLanguage;
        final boolean youtube;
        final String mimeType;
        final String portugueseSubtitles;
        final String audioStreamUrl;
        final int languagePriority;

        Candidate(String streamUrl, String title, String originalLanguage) {
            this(streamUrl, title, originalLanguage, false, "application/x-mpegURL", "");
        }

        Candidate(String streamUrl, String title, String originalLanguage, boolean youtube,
                  String mimeType, String portugueseSubtitles) {
            this(streamUrl, title, originalLanguage, youtube, mimeType, portugueseSubtitles, "");
        }

        Candidate(String streamUrl, String title, String originalLanguage, boolean youtube,
                  String mimeType, String portugueseSubtitles, String audioStreamUrl) {
            this(streamUrl, title, originalLanguage, youtube, mimeType, portugueseSubtitles, audioStreamUrl, -1);
        }

        Candidate(String streamUrl, String title, String originalLanguage, boolean youtube,
                  String mimeType, String portugueseSubtitles, String audioStreamUrl, int languagePriority) {
            this.languagePriority = languagePriority;
            this.audioStreamUrl = audioStreamUrl;
            this.youtube = youtube;
            this.mimeType = mimeType;
            this.portugueseSubtitles = portugueseSubtitles;
            this.streamUrl = streamUrl;
            this.title = title;
            this.originalLanguage = originalLanguage;
        }
    }

    // shortcut: public page schemas can change; replace this adapter when a stable trailer API is available.
    private static final Pattern SERVER_DATA = Pattern.compile(
            "<script\\b(?=[^>]*\\bid=[\"']serialized-server-data[\"'])[^>]*>(.*?)</script>",
            Pattern.DOTALL);
    private static final Pattern CANONICAL_ID = Pattern.compile("umc\\.cmc\\.[a-z0-9]+");

    static List<Candidate> resolve(String imdbId, String title, boolean series) throws Exception {
        String query = "query($title:String!){popularTitles(country:\"BR\",filter:{searchQuery:$title},first:8)"
                + "{edges{node{content(country:\"BR\",language:\"pt\"){externalIds{imdbId}}"
                + " ... on MovieOrShow {brOffers:offers(country:\"BR\",platform:WEB){standardWebURL}"
                + " usOffers:offers(country:\"US\",platform:WEB){standardWebURL}}}}}}";
        JSONObject body = new JSONObject().put("query", query)
                .put("variables", new JSONObject().put("title", title));
        List<String> pages = findApplePages(new JSONObject(request(
                "https://apis.justwatch.com/graphql", body.toString())), imdbId, series);
        List<Candidate> candidates = new ArrayList<>();
        for (String page : pages) {
            try {
                candidates.addAll(parseAppleTrailers(request(page, null), canonicalId(page)));
                if (!candidates.isEmpty()) return candidates;
            } catch (Exception ignored) {
                // The other storefront can still have a public trailer for this same IMDb title.
            }
        }
        return candidates;
    }

    static List<String> findApplePages(JSONObject response, String imdbId, boolean series) {
        Set<String> pages = new LinkedHashSet<>();
        JSONObject data = response.optJSONObject("data");
        JSONObject popular = data == null ? null : data.optJSONObject("popularTitles");
        JSONArray edges = popular == null ? null : popular.optJSONArray("edges");
        if (edges == null) return new ArrayList<>();
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            JSONObject node = edge == null ? null : edge.optJSONObject("node");
            JSONObject content = node == null ? null : node.optJSONObject("content");
            JSONObject ids = content == null ? null : content.optJSONObject("externalIds");
            if (ids == null || !imdbId.equals(ids.optString("imdbId"))) continue;
            for (String region : new String[]{"brOffers", "usOffers"}) {
                JSONArray offers = node.optJSONArray(region);
                if (offers == null) continue;
                for (int n = 0; n < offers.length(); n++) {
                    JSONObject offer = offers.optJSONObject(n);
                    if (offer == null) continue;
                    try {
                        URI uri = new URI(offer.optString("standardWebURL"));
                        if (!"https".equals(uri.getScheme()) || !"tv.apple.com".equals(uri.getHost())
                                || uri.getUserInfo() != null || uri.getPort() != -1) continue;
                        String id = canonicalId(uri.getPath());
                        if (series && uri.getPath().contains("/episode/")) {
                            id = "";
                            for (String param : uri.getRawQuery().split("&")) {
                                if (param.startsWith("showId=")) id = canonicalId(param.substring(7));
                            }
                        } else if (!uri.getPath().contains(series ? "/show/" : "/movie/")) {
                            continue;
                        }
                        if (id.isEmpty()) continue;
                        String path = (series ? "/show/trailer/" : "/movie/trailer/") + id;
                        pages.add("https://tv.apple.com/br" + path);
                        pages.add("https://tv.apple.com/us" + path);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return new ArrayList<>(pages);
    }

    private static String canonicalId(String value) {
        Matcher matcher = CANONICAL_ID.matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    static List<Candidate> parseAppleTrailers(String html, String expectedId) throws Exception {
        List<Candidate> results = new ArrayList<>();
        Matcher matcher = SERVER_DATA.matcher(html);
        if (!matcher.find() || expectedId.isEmpty()) return results;
        JSONArray entries = new JSONObject(matcher.group(1)).optJSONArray("data");
        if (entries == null) return results;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            JSONObject intent = entry == null ? null : entry.optJSONObject("intent");
            if (intent == null || !expectedId.equals(intent.optString("id"))) continue;
            JSONObject data = entry.optJSONObject("data");
            JSONArray shelves = data == null ? null : data.optJSONArray("shelves");
            if (shelves == null) continue;
            for (int n = 0; n < shelves.length(); n++) {
                JSONObject shelf = shelves.optJSONObject(n);
                if (shelf == null || !("uts.col.Trailers." + expectedId).equals(shelf.optString("id"))) continue;
                JSONArray playlist = shelf.optJSONArray("playlistItems");
                if (playlist != null) {
                    for (int p = 0; p < playlist.length(); p++) {
                        JSONObject item = playlist.optJSONObject(p);
                        Candidate candidate = trailerCandidate(item == null ? null : item.optJSONObject("playable"));
                        if (candidate != null) { results.add(candidate); return results; }
                    }
                }
                JSONArray items = shelf.optJSONArray("items");
                if (items == null) continue;
                for (int p = 0; p < items.length(); p++) {
                    JSONObject item = items.optJSONObject(p);
                    JSONObject action = item == null ? null : item.optJSONObject("playAction");
                    JSONObject descriptor = action == null ? null : action.optJSONObject("contentDescriptor");
                    JSONArray clips = descriptor == null ? null : descriptor.optJSONArray("items");
                    if (clips == null) continue;
                    for (int c = 0; c < clips.length(); c++) {
                        JSONObject clip = clips.optJSONObject(c);
                        Candidate candidate = trailerCandidate(clip == null ? null : clip.optJSONObject("playable"));
                        if (candidate != null) { results.add(candidate); return results; }
                    }
                }
            }
        }
        return results;
    }

    private static Candidate trailerCandidate(JSONObject playable) {
        if (playable == null || !"Extra".equals(playable.optString("contentType"))
                || !playable.optBoolean("isEntitledToPlay", false)
                || playable.optBoolean("isGeoRestricted", false)
                || playable.optBoolean("isEmbargoed", false)
                || playable.optDouble("duration", 0) > 600) return null;
        JSONObject assets = playable.optJSONObject("assets");
        String url = assets == null ? "" : assets.optString("hlsUrl");
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null
                    || !(host.endsWith(".apple.com") || host.endsWith(".mzstatic.com"))
                    || !uri.getPath().endsWith(".m3u8") || uri.getUserInfo() != null
                    || uri.getPort() != -1 || assets.optBoolean("requiresPlaybackId", false)) return null;
        } catch (Exception e) { return null; }
        JSONObject locale = playable.optJSONObject("primaryLocale");
        String language = locale == null ? "" : locale.optString("locale", "").replace('_', '-');
        return new Candidate(url, playable.optString("title", "Trailer oficial"), language);
    }

    static String request(String url, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Trailer HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 2 * 1024 * 1024) throw new java.io.IOException("Trailer response too large");
                    output.write(buffer, 0, count);
                }
                return output.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }
}
