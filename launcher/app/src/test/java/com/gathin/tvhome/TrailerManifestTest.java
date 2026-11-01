package com.gathin.tvhome;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class TrailerManifestTest {
    @Test public void keepsAdaptiveAvcStereoAndPortugueseTracksOnly() throws Exception {
        String manifest = "#EXTM3U\n#EXT-X-INDEPENDENT-SEGMENTS\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"stereo\",LANGUAGE=\"pt-BR\",URI=\"pt.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"stereo\",LANGUAGE=\"en\",URI=\"en.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"stereo\",LANGUAGE=\"de\",URI=\"de.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"surround\",LANGUAGE=\"pt-BR\",URI=\"surround.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",LANGUAGE=\"pt-BR\",URI=\"sub.m3u8\"\n"
                + variant("avc1.64001f,mp4a.40.2", "640x360", "small.m3u8")
                + variant("avc1.64001f,mp4a.40.2", "1280x720", "hd.m3u8")
                + variant("avc1.64001f,mp4a.40.2", "1280x720", "duplicate-cdn.m3u8")
                + variant("avc1.640028,mp4a.40.2", "1920x1080", "fullhd.m3u8")
                + variant("dvh1.05.06,ec-3", "3840x2160", "dolby.m3u8");
        String filtered = TrailerManifest.filter(manifest, "en");
        assertTrue(filtered.contains("small.m3u8"));
        assertTrue(filtered.contains("hd.m3u8"));
        assertTrue(filtered.contains("pt.m3u8"));
        assertTrue(filtered.contains("en.m3u8"));
        assertTrue(filtered.contains("sub.m3u8"));
        for (String removed : new String[]{"de.m3u8", "surround.m3u8", "duplicate-cdn.m3u8", "fullhd.m3u8", "dolby.m3u8"}) {
            assertFalse(removed, filtered.contains(removed));
        }
    }

    private String variant(String codecs, String size, String uri) {
        return "#EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS=\"" + codecs + "\",RESOLUTION=" + size
                + ",AUDIO=\"stereo\",SUBTITLES=\"subs\"\n" + uri + "\n";
    }

    @Test public void preservesMediaPlaylist() throws Exception {
        String media = "#EXTM3U\n#EXTINF:6,\nsegment.m4s\n#EXT-X-ENDLIST\n";
        assertEquals(media, TrailerManifest.filter(media, "en"));
    }

    @Test(expected = IOException.class) public void rejectsDrmMaster() throws Exception {
        TrailerManifest.filter("#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\n", "en");
    }

    @Test(expected = IOException.class) public void rejectsUnsupportedVideoInsteadOfStartingDecoder() throws Exception {
        TrailerManifest.filter("#EXTM3U\n" + variant("hvc1.1.6.L93,mp4a.40.2", "1280x720", "hevc.m3u8"), "en");
    }
}
