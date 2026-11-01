package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Native playback fallback: extract on demand, without retaining expired signed URLs. */
final class YoutubeTrailerSource {
    private static final Http DOWNLOADER = new Http();
    private static final ThreadLocal<Long> DEADLINE=new ThreadLocal<>();
    static { NewPipe.init(DOWNLOADER, new Localization("pt", "BR")); }

    static List<NativeTrailerSource.Candidate> resolve(String imdbId, String title, String type,
                                                      String knownId) throws Exception {
        DEADLINE.set(System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20));
        try { return resolveWithinBudget(imdbId,title,type,knownId); }
        finally { DEADLINE.remove(); }
    }
    static int requestTimeout(long remainingNanos,int maximumMillis) throws IOException {
        if (remainingNanos<=0) throw new IOException("Trailer lookup deadline exceeded");
        return (int)Math.min(maximumMillis,Math.max(1,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
    }
    private static List<NativeTrailerSource.Candidate> resolveWithinBudget(String imdbId,String title,String type,String knownId) throws Exception {
        List<String> ids = new ArrayList<>();
        String year = "";
        try {
            Response metadata = DOWNLOADER.get("https://v3-cinemeta.strem.io/meta/" + type + "/" + imdbId + ".json");
            JSONObject root = new JSONObject(metadata.responseBody());
            ids.addAll(metadataIds(root));
            if (root.optJSONObject("meta") != null) year = root.getJSONObject("meta").optString("year");
        } catch (Exception ignored) {
        }
        if (ids.isEmpty() && validVideoId(knownId)) ids.add(knownId);
        String cleanTitle = title.replaceAll("\\s*\\(.*?\\)", "").trim();
        List<NativeTrailerSource.Candidate> best = new ArrayList<>();
        List<StreamInfoItem> results = new ArrayList<>();
        try {
            SearchExtractor search = ServiceList.YouTube.getSearchExtractor(cleanTitle + " trailer dublado legendado portugues");
            search.fetchPage();
            for (InfoItem item : search.getInitialPage().getItems()) {
                if (!(item instanceof StreamInfoItem)) continue;
                StreamInfoItem video = (StreamInfoItem) item;
                if (video.getDuration() <= 0 || video.getDuration() > 600
                        || !relevantTrailer(video.getName(), cleanTitle) || !matchesYear(video.getName(), year)) continue;
                if ("movie".equals(type) && normalize(video.getName()).matches(".*\\b(temporada|season|serie|series)\\b.*")) continue;
                results.add(video);
            }
            results.sort(java.util.Comparator.comparingInt((StreamInfoItem v) -> languagePriority(v.getName()))
                    .thenComparing(v -> !v.isUploaderVerified()));
        } catch (Exception ignored) { }
        int attempts = 0;
        Set<String> tried = new LinkedHashSet<>();
        for (StreamInfoItem video : results) {
            int priority = languagePriority(video.getName());
            if (priority > 1) continue;
            String id = ServiceList.YouTube.getStreamLHFactory().getId(video.getUrl());
            if (!validVideoId(id) || !tried.add(id)) continue;
            try {
                List<NativeTrailerSource.Candidate> streams = extract(id);
                if (!streams.isEmpty() && (best.isEmpty() || streams.get(0).languagePriority < best.get(0).languagePriority)) best = streams;
                if (!best.isEmpty() && best.get(0).languagePriority == 0) return best;
            } catch (Exception ignored) { }
            if (++attempts >= 3) break;
        }
        for (String id : ids.subList(0, Math.min(3, ids.size()))) {
            if (!tried.add(id)) continue;
            try {
                List<NativeTrailerSource.Candidate> streams = extract(id);
                if (!streams.isEmpty() && (best.isEmpty() || streams.get(0).languagePriority < best.get(0).languagePriority)) best = streams;
                if (!best.isEmpty() && best.get(0).languagePriority == 0) return best;
            } catch (Exception ignored) { }
        }
        if (!best.isEmpty()) return best;
        for (StreamInfoItem video : results.subList(0, Math.min(3, results.size()))) {
            String id = ServiceList.YouTube.getStreamLHFactory().getId(video.getUrl());
            if (!validVideoId(id) || !tried.add(id)) continue;
            try {
                List<NativeTrailerSource.Candidate> streams = extract(id);
                if (!streams.isEmpty()) return streams;
            } catch (Exception ignored) { }
        }
        return best;
    }

    static boolean validVideoId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    static List<String> metadataIds(JSONObject root) {
        Set<String> ids = new LinkedHashSet<>();
        JSONObject meta = root.optJSONObject("meta");
        if (meta == null) return new ArrayList<>();
        String known = meta.optString("trailerYtId");
        if (validVideoId(known)) ids.add(known);
        JSONArray trailers = meta.optJSONArray("trailers");
        if (trailers != null) for (int i = 0; i < trailers.length(); i++) {
            JSONObject trailer = trailers.optJSONObject(i);
            if (trailer == null || !"Trailer".equalsIgnoreCase(trailer.optString("type", "Trailer"))) continue;
            String id = trailer.optString("source");
            if (validVideoId(id)) ids.add(id);
        }
        return new ArrayList<>(ids);
    }

    static boolean relevantTrailer(String name, String title) {
        String found = normalize(name);
        if (!(found.contains("trailer") || found.contains("teaser"))
                || found.matches(".*\\b(fan|concept|reaction|react|review|analise|critica|completo)\\b.*")) return false;
        int words = 0;
        for (String word : normalize(title).split(" +")) {
            if (word.length() < 3) continue;
            words++;
            if (!(" " + found + " ").contains(" " + word + " ")) return false;
        }
        return words > 0;
    }

    static boolean matchesYear(String name, String year) {
        java.util.regex.Matcher expected = java.util.regex.Pattern.compile("(?:19|20)\\d{2}").matcher(year);
        if (!expected.find()) return true;
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("\\b(?:19|20)\\d{2}\\b").matcher(name);
        return !found.find() || expected.group().equals(found.group());
    }

    static int languagePriority(String title) {
        String name = normalize(title);
        if (name.matches(".*\\b(legendado|legendada|subtitulado)\\b.*")) return 1;
        if (name.matches(".*\\b(dublado|dublada)\\b.*") || name.contains("dubbed brazil")) return 0;
        return name.contains("official trailer") ? 2 : 3;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
    }

    static boolean validStreamUrl(String url) {
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && uri.getHost() != null && uri.getHost().endsWith(".googlevideo.com");
        } catch (Exception e) { return false; }
    }

    private static List<NativeTrailerSource.Candidate> extract(String id) throws Exception {
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=" + id);
        extractor.fetchPage();
        if (extractor.getLength() > 600) return new ArrayList<>();
        String language = "";
        try { if (extractor.getLanguageInfo() != null) language = extractor.getLanguageInfo().toLanguageTag(); }
        catch (Exception ignored) { }
        String subtitles = "";
        try {
            for (SubtitlesStream track : extractor.getSubtitles(MediaFormat.VTT)) {
                URI uri = new URI(track.getContent());
                if (track.getLanguageTag().startsWith("pt") && "https".equals(uri.getScheme())
                        && "www.youtube.com".equals(uri.getHost()) && uri.getUserInfo() == null) {
                    subtitles = track.getContent();
                    break;
                }
            }
        } catch (Exception ignored) { }
        List<NativeTrailerSource.Candidate> streams = new ArrayList<>();
        VideoStream hd = selectVideo(extractor.getVideoOnlyStreams());
        AudioStream audio = selectAudio(extractor.getAudioStreams());
        int videoPriority = languagePriority(extractor.getName());
        String selectedLanguage = audio != null && audio.getAudioLocale() != null ? audio.getAudioLocale().toLanguageTag() : language;
        int priority = selectedLanguage.startsWith("pt") && !selectedLanguage.equalsIgnoreCase("pt-PT") ? 0
                : !subtitles.isEmpty() || videoPriority == 1 ? 1 : videoPriority;
        if (videoPriority == 0 && (selectedLanguage.isEmpty() || selectedLanguage.equals("und"))) {
            selectedLanguage = "pt-BR";
            priority = 0;
        }
        if (priority > 1 && selectedLanguage.startsWith("en")) priority = 2;
        if (hd != null && audio != null) {
            streams.add(new NativeTrailerSource.Candidate(hd.getContent(), extractor.getName(), selectedLanguage,
                    true, "video/mp4", subtitles, audio.getContent(), priority));
        }
        String hls = extractor.getHlsUrl();
        if (validStreamUrl(hls)) streams.add(new NativeTrailerSource.Candidate(hls, extractor.getName(), language,
                true, "application/x-mpegURL", subtitles, "", videoPriority == 0 ? 0 : !subtitles.isEmpty() ? 1 : videoPriority));
        for (VideoStream video : extractor.getVideoStreams()) {
            if (video.isVideoOnly() || video.getFormat() != MediaFormat.MPEG_4 || !validStreamUrl(video.getContent())) continue;
            streams.add(new NativeTrailerSource.Candidate(video.getContent(), extractor.getName(), language,
                    true, "video/mp4", subtitles, "", videoPriority == 0 ? 0 : !subtitles.isEmpty() ? 1 : videoPriority));
            break;
        }
        return streams;
    }

    static AudioStream selectAudio(List<AudioStream> tracks) {
        AudioStream selected = null;
        for (AudioStream track : tracks) {
            if (track.getFormat() != MediaFormat.M4A || !validStreamUrl(track.getContent()) || track.getAverageBitrate() > 192) continue;
            if (selected == null || audioPriority(track) < audioPriority(selected)
                    || (audioPriority(track) == audioPriority(selected) && track.getAverageBitrate() > selected.getAverageBitrate())) selected = track;
        }
        return selected;
    }

    private static int audioPriority(AudioStream audio) {
        if (audio.getAudioLocale() == null) return 3;
        String tag = audio.getAudioLocale().toLanguageTag();
        if (tag.equalsIgnoreCase("pt-BR")) return 0;
        if (tag.equals("pt")) return 1;
        return tag.startsWith("en") ? 2 : 3;
    }

    static VideoStream selectVideo(List<VideoStream> videos) {
        VideoStream selected = null;
        int bestHeight = 0;
        for (VideoStream video : videos) {
            if (video.getFormat() != MediaFormat.MPEG_4 || !validStreamUrl(video.getContent())
                    || video.getFps() > 30) continue;
            String codec = video.getCodec();
            if (codec != null && !codec.isEmpty() && !codec.startsWith("avc1")) continue;
            java.util.regex.Matcher size = java.util.regex.Pattern.compile("^(\\d+)p(?:\\d+)?$")
                    .matcher(video.getResolution());
            if (!size.matches()) continue;
            int height;
            try { height = Integer.parseInt(size.group(1)); }
            catch (NumberFormatException e) { continue; }
            if (height > bestHeight && height <= 720) {
                selected = video;
                bestHeight = height;
            }
        }
        return selected;
    }

    private static final class Http extends Downloader {
        @Override public Response execute(Request request) throws IOException {
            Long deadline=DEADLINE.get();
            long remaining=deadline==null?Long.MAX_VALUE:deadline-System.nanoTime();
            int connectTimeout=requestTimeout(remaining,5000),readTimeout=requestTimeout(remaining,6000);
            HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod(request.httpMethod());
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
                connection.setRequestProperty(header.getKey(), String.join(", ", header.getValue()));
            }
            try {
                if (request.dataToSend() != null) {
                    connection.setDoOutput(true);
                    try (java.io.OutputStream out = connection.getOutputStream()) { out.write(request.dataToSend()); }
                }
                int code = connection.getResponseCode();
                InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String body = "";
                if (input != null) try (InputStream in = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (deadline!=null) requestTimeout(deadline-System.nanoTime(),6000);
                        if (output.size() + count > 6 * 1024 * 1024) throw new IOException("Trailer response too large");
                        output.write(buffer, 0, count);
                    }
                    body = output.toString("UTF-8");
                }
                return new Response(code, connection.getResponseMessage(), connection.getHeaderFields(), body,
                        connection.getURL().toString());
            } finally { connection.disconnect(); }
        }
    }
}
