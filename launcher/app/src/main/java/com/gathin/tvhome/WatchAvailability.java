package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Brazil offers matched to the exact IMDb title, retaining the provider's content link. */
final class WatchAvailability {
    static final class Offer {
        final String provider, url, monetization, service;
        final boolean channel;

        Offer(String provider, String url, String monetization, String service, boolean channel) {
            this.provider = provider;
            this.url = url;
            this.monetization = monetization;
            this.service = service;
            this.channel = channel;
        }

        String description() {
            if (channel) return "Requer canal " + service.replace(" Amazon Channel", "");
            if ("RENT".equals(monetization)) return "Aluguel";
            if ("BUY".equals(monetization)) return "Compra";
            if ("FREE".equals(monetization) || "ADS".equals(monetization)) return "Grátis / com anúncios";
            return "Assinatura";
        }
    }

    static List<Offer> resolve(String imdbId, String title, boolean series) throws Exception {
        if (imdbId == null || !imdbId.matches("tt\\d{5,12}")) throw new IllegalArgumentException("Invalid IMDb ID");
        String query = "query($title:String!){popularTitles(country:\"BR\",filter:{searchQuery:$title},first:8)"
                + "{edges{node{content(country:\"BR\",language:\"pt\"){externalIds{imdbId}}"
                + " ... on MovieOrShow{offers(country:\"BR\",platform:WEB){package{clearName shortName technicalName}"
                + " standardWebURL monetizationType}}}}}}";
        JSONObject body = new JSONObject().put("query", query).put("variables", new JSONObject().put("title", title));
        JSONObject response = new JSONObject(NativeTrailerSource.request("https://apis.justwatch.com/graphql", body.toString()));
        JSONObject data = response.optJSONObject("data");
        JSONObject popular = data == null ? null : data.optJSONObject("popularTitles");
        if (response.has("errors") || popular == null || popular.optJSONArray("edges") == null)
            throw new java.io.IOException("Availability response invalid");
        return parse(response, imdbId, series);
    }

    static List<Offer> parse(JSONObject root, String imdbId, boolean series) {
        Map<String, Offer> offers = new LinkedHashMap<>();
        JSONObject data = root.optJSONObject("data");
        JSONObject popular = data == null ? null : data.optJSONObject("popularTitles");
        JSONArray edges = popular == null ? null : popular.optJSONArray("edges");
        if (edges == null) return new ArrayList<>();
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            JSONObject node = edge == null ? null : edge.optJSONObject("node");
            JSONObject content = node == null ? null : node.optJSONObject("content");
            JSONObject ids = content == null ? null : content.optJSONObject("externalIds");
            if (ids == null || !imdbId.equals(ids.optString("imdbId"))) continue;
            JSONArray list = node.optJSONArray("offers");
            if (list == null) continue;
            for (int j = 0; j < list.length(); j++) {
                JSONObject offer = list.optJSONObject(j);
                JSONObject pkg = offer == null ? null : offer.optJSONObject("package");
                if (pkg == null) continue;
                String monetization = offer.optString("monetizationType");
                if (!(monetization.equals("FLATRATE") || monetization.equals("FREE") || monetization.equals("ADS")
                        || monetization.equals("RENT") || monetization.equals("BUY"))) continue;
                String url = offer.optString("standardWebURL");
                String provider = providerFor(pkg, url);
                if (provider.isEmpty()) continue;
                String normalized = contentLink(provider, url, series);
                if (normalized.isEmpty()) continue;
                String tech = pkg.optString("technicalName").toLowerCase(Locale.ROOT);
                boolean channel = provider.equals("prime") && !tech.equals("amazonprimevideo")
                        && !tech.equals("amazonprimevideowithads") && !tech.equals("amazon");
                Offer candidate = new Offer(provider, normalized, monetization, pkg.optString("clearName"), channel);
                Offer existing = offers.get(provider);
                if (existing == null || rank(candidate) < rank(existing)) offers.put(provider, candidate);
            }
        }
        return new ArrayList<>(offers.values());
    }

    private static int rank(Offer offer) {
        if (offer.channel) return 2;
        if (offer.monetization.equals("RENT") || offer.monetization.equals("BUY")) return 3;
        return 0;
    }

    private static String providerFor(JSONObject pkg, String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1) return "";
            String host = uri.getHost();
            String tech = pkg.optString("technicalName").toLowerCase(Locale.ROOT);
            if (host == null) return "";
            if ((host.equals("www.netflix.com") || host.equals("netflix.com")) && tech.startsWith("netflix")) return "netflix";
            if (host.equals("tv.apple.com") && (tech.equals("appletvplus") || tech.equals("itunes"))) return "apple";
            if ((host.equals("app.primevideo.com") || host.equals("www.primevideo.com") || host.equals("www.amazon.com")
                    || host.equals("www.amazon.com.br") || host.equals("watch.amazon.com")) && tech.startsWith("amazon")) return "prime";
            if (host.equals("globoplay.globo.com") && tech.equals("globoplay")) return "globoplay";
            if ((host.equals("www.max.com") || host.equals("play.max.com") || host.equals("www.hbomax.com")
                    || host.equals("play.hbomax.com")) && (tech.equals("max") || tech.equals("hbomax"))) return "max";
            if ((host.equals("www.disneyplus.com") || host.equals("www.disneyplus.com.br")) && tech.equals("disneyplus")) return "disney";
        } catch (Exception ignored) { }
        return "";
    }

    static String contentLink(String provider, String url, boolean series) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (provider.equals("netflix")) {
                java.util.regex.Matcher id = java.util.regex.Pattern.compile("/(?:title|watch)/(\\d+)").matcher(path);
                return id.find() ? "https://www.netflix.com/title/" + id.group(1) : "";
            }
            if (provider.equals("prime")) {
                if (uri.getHost().equals("app.primevideo.com")) return uri.toString();
                if (uri.getHost().equals("www.primevideo.com")) return new URI("https", "app.primevideo.com", path, uri.getQuery(), null).toString();
                java.util.regex.Matcher id = java.util.regex.Pattern.compile("/(?:detail|dp|gp/video/detail)/([A-Za-z0-9.\\-]+)").matcher(path);
                return id.find() ? "https://app.primevideo.com/detail?gti=" + id.group(1) : "";
            }
            if (provider.equals("apple") && series && path.contains("/episode/")) {
                if (uri.getQuery() == null) return "";
                for (String param : uri.getQuery().split("&")) {
                    if (param.matches("showId=umc\\.cmc\\.[a-z0-9]+")) return "https://tv.apple.com/br/show/" + param.substring(7);
                }
                return "";
            }
            if (provider.equals("apple") && !path.contains(series ? "/show/" : "/movie/")) return "";
            return new URI("https", uri.getHost(), path, null, null).toString();
        } catch (Exception ignored) { return ""; }
    }

    static String stremioLink(String imdbId, boolean series) {
        if (imdbId == null || !imdbId.matches("tt\\d{5,12}")) return "";
        return "stremio:///detail/" + (series ? "series/" : "movie/") + imdbId
                + (series ? "" : "/" + imdbId) + "?autoPlay=false";
    }

    static boolean allowedLink(String pkg, String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1) return false;
            String host = uri.getHost();
            if (pkg.equals("com.netflix.ninja")) return "www.netflix.com".equals(host) && uri.getPath().matches("/title/\\d+");
            if (pkg.equals("com.amazon.amazonvideo.livingroom")) return "app.primevideo.com".equals(host);
            if (pkg.equals("com.apple.atve.androidtv.appletv")) return "tv.apple.com".equals(host);
            if (pkg.equals("com.globo.globotv")) return "globoplay.globo.com".equals(host);
            if (pkg.equals("com.wbd.stream")) return "www.max.com".equals(host) || "play.max.com".equals(host)
                    || "www.hbomax.com".equals(host) || "play.hbomax.com".equals(host);
            if (pkg.equals("com.disney.disneyplus")) return "www.disneyplus.com".equals(host) || "www.disneyplus.com.br".equals(host);
        } catch (Exception ignored) { }
        return false;
    }
}
