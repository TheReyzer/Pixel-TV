package com.gathin.tvhome;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Trim the master before Media3's expensive codec/rendition parsing on the TV. */
final class TrailerManifest {
    private static final java.util.Map<String, Pattern> ATTRIBUTES = new java.util.HashMap<>();
    static {
        for (String key : new String[]{"CODECS", "RESOLUTION", "AUDIO", "SUBTITLES", "TYPE", "GROUP-ID", "LANGUAGE"}) {
            ATTRIBUTES.put(key, Pattern.compile("(?:[:,])" + key + "=(?:\"([^\"]*)\"|([^,]*))"));
        }
    }
    static String filter(String manifest, String originalLanguage) throws IOException {
        if (!manifest.startsWith("#EXTM3U") || manifest.contains("#EXT-X-SESSION-KEY:")) {
            throw new IOException("Expected a public, unencrypted trailer playlist");
        }
        if (!manifest.contains("#EXT-X-STREAM-INF:")) return manifest;
        String[] lines = manifest.split("\\r?\\n");
        Set<String> sizes = new HashSet<>();
        Set<String> audioGroups = new HashSet<>();
        Set<String> subtitleGroups = new HashSet<>();
        StringBuilder variants = new StringBuilder();
        for (int i = 0; i + 1 < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue;
            String codecs = attribute(line, "CODECS");
            String size = attribute(line, "RESOLUTION");
            String[] dimensions = size.split("x");
            if (!codecs.contains("avc1.") || codecs.contains("ac-3") || codecs.contains("ec-3")
                    || dimensions.length != 2 || lines[i + 1].startsWith("#")) continue;
            try {
                if (Integer.parseInt(dimensions[0]) > 1280 || Integer.parseInt(dimensions[1]) > 720) continue;
            } catch (NumberFormatException e) { continue; }
            if (!sizes.add(size)) continue;
            audioGroups.add(attribute(line, "AUDIO"));
            subtitleGroups.add(attribute(line, "SUBTITLES"));
            variants.append(line).append('\n').append(lines[i + 1]).append('\n');
        }
        if (sizes.isEmpty()) throw new IOException("Trailer has no H.264 stream up to 720p");
        StringBuilder result = new StringBuilder("#EXTM3U\n");
        for (String line : lines) {
            if (line.startsWith("#EXT-X-VERSION:") || line.equals("#EXT-X-INDEPENDENT-SEGMENTS")
                    || line.startsWith("#EXT-X-DEFINE:")) result.append(line).append('\n');
            if (!line.startsWith("#EXT-X-MEDIA:")) continue;
            String type = attribute(line, "TYPE");
            String group = attribute(line, "GROUP-ID");
            String language = attribute(line, "LANGUAGE");
            boolean portuguese = language.equals("pt") || language.startsWith("pt-");
            boolean original = !originalLanguage.isEmpty() && language.startsWith(originalLanguage);
            if ((type.equals("AUDIO") && audioGroups.contains(group)
                    && (portuguese || original || language.startsWith("en") || language.isEmpty()))
                    || (type.equals("SUBTITLES") && subtitleGroups.contains(group) && portuguese)) {
                result.append(line).append('\n');
            }
        }
        return result.append(variants).toString();
    }

    private static String attribute(String line, String key) {
        Matcher match = ATTRIBUTES.get(key).matcher(line);
        return match.find() ? (match.group(1) != null ? match.group(1) : match.group(2)) : "";
    }
}
