package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class TrailerDataSourceTest {
    @Test public void chunksOnlyValidatedProgressiveStreamsWithKnownSize() {
        assertEquals(1234567,TrailerDataSource.contentLength("https://rr1.googlevideo.com/videoplayback?id=abc&clen=1234567&sig=abc"));
        for (String url:new String[]{"https://rr1.googlevideo.com/videoplayback", "https://rr1.googlevideo.com/videoplayback?clen=0", "https://rr1.googlevideo.com/videoplayback?clen=bad", "https://rr1.googlevideo.com/videoplayback?clen=12&range=0-11", "https://rr1.googlevideo.com/api/manifest/hls?clen=100", "https://evil.example/videoplayback?clen=100"}) assertEquals(url,-1,TrailerDataSource.contentLength(url));
    }
    @Test public void finalChunkStopsAtTheRequestedEnd() {
        assertEquals(512*1024,TrailerDataSource.chunkLength(2_000_000));
        assertEquals(25,TrailerDataSource.chunkLength(25));
        assertEquals(0,TrailerDataSource.chunkLength(0));
        assertEquals(0,TrailerDataSource.chunkLength(-1));
    }
}
