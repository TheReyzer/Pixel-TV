package com.gathin.tvhome;

import java.net.URI;

/** Only known image CDNs are rewritten; image identity and regional artwork are retained. */
final class HomeArtworkPolicy {
    static String primary(MediaRepository.MediaItem item,boolean landscape) { return landscape&&!item.backdropUrl.isEmpty()?item.backdropUrl:MediaRepository.portraitPrimary(item); }
    static String fallback(MediaRepository.MediaItem item,boolean landscape) { return landscape?MediaRepository.portraitPrimary(item):MediaRepository.portraitFallback(item); }
    static String highResolution(String url) {
        if (url==null||url.isEmpty()) return "";
        try {
            URI uri=new URI(url);
            if (!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1) return url;
            String host=uri.getHost(),path=uri.getPath();
            if ("images.justwatch.com".equals(host)&&path.matches("/poster/[0-9]+/s[0-9]+/.*")) return url.replaceFirst("/s[0-9]+/","/s718/");
            if ("images.metahub.space".equals(host)&&path.matches("/(poster|background)/(small|medium|large)/tt[0-9]+/.*")) return url.replaceFirst("/(small|medium|large)/","/large/");
            if ("image.tmdb.org".equals(host)&&path.startsWith("/t/p/")) return url.replaceFirst("/t/p/(w[0-9]+|original)/","/t/p/original/");
            if ("m.media-amazon.com".equals(host)&&path.startsWith("/images/")) return url.replaceFirst("_V1_.*\\.jpg","_V1_SX1280.jpg");
        } catch (Exception ignored) { }
        return url;
    }
    static float quality(int width,int height,int targetWidth,int targetHeight) {
        if (width<=0||height<=0||targetWidth<=0||targetHeight<=0) return 0;
        return Math.min(1f,Math.min(width/(float)targetWidth,height/(float)targetHeight));
    }
    static boolean adequate(float quality) { return quality>=.9f; }
}
