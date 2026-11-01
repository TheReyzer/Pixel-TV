package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeArtworkPolicyTest {
    @Test public void leadingPositionUsesLandscapeAndOthersUseRegionalPosters() {
        MediaRepository.MediaItem item=new MediaRepository.MediaItem("tt1234567","Title","movie","8","2026","","","https://example.com/back.jpg","https://example.com/poster.jpg","","",1);
        item.posterPtBrUrl="https://images.justwatch.com/poster/123/s332/br.jpg";
        assertEquals(item.backdropUrl,HomeArtworkPolicy.primary(item,true));
        assertEquals(item.posterPtBrUrl,HomeArtworkPolicy.primary(item,false));
        assertEquals(item.posterPtBrUrl,HomeArtworkPolicy.fallback(item,true));
    }
    @Test public void knownCdnsKeepIdentityAndUseTheirLargerVariant() {
        assertEquals("https://images.justwatch.com/poster/123/s718/br.jpg",HomeArtworkPolicy.highResolution("https://images.justwatch.com/poster/123/s332/br.jpg"));
        assertEquals("https://images.metahub.space/poster/large/tt1234567/img",HomeArtworkPolicy.highResolution("https://images.metahub.space/poster/medium/tt1234567/img"));
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg",HomeArtworkPolicy.highResolution("https://image.tmdb.org/t/p/w500/abc.jpg"));
    }
    @Test public void unknownAndImpersonatedHostsAreNeverRewritten() {
        for(String url:new String[]{"https://example.com/s332/abc.jpg","https://images.justwatch.com.evil/poster/123/s332/a.jpg","https://user@images.justwatch.com/poster/123/s332/a.jpg"}) assertEquals(url,HomeArtworkPolicy.highResolution(url));
    }
    @Test public void coverQualityChecksBothDimensionsWithoutPretendingToUpscale() {
        assertFalse(HomeArtworkPolicy.adequate(HomeArtworkPolicy.quality(332,472,774,470)));
        assertTrue(HomeArtworkPolicy.adequate(HomeArtworkPolicy.quality(718,1024,774,470)));
        assertFalse(HomeArtworkPolicy.adequate(HomeArtworkPolicy.quality(1920,200,774,470)));
        assertEquals(1f,HomeArtworkPolicy.quality(1920,1080,774,470),0);
    }
}
