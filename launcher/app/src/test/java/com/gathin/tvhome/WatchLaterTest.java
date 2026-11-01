package com.gathin.tvhome;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WatchLaterTest {
    private MediaRepository.MediaItem item(int id) { return new MediaRepository.MediaItem("tt"+(9000000+id),"Título "+id,"movie","8","2026","Drama","","","","","2026-10-01",1); }
    @Test public void multipleListsRemainIndependentAndGeneralDeduplicates() throws Exception {
        ViewerProfiles profiles=ProfileFixtures.household(); MediaRepository.MediaItem a=item(1),b=item(2); a.originalTitle="Original title";
        profiles.saveForLater(new LinkedHashSet<>(Arrays.asList("one","two")),a);
        profiles.saveForLater(Collections.singleton(ViewerProfiles.GENERAL),b);
        assertEquals(1,profiles.watchLater("one").size()); assertEquals(1,profiles.watchLater("two").size()); assertEquals(2,profiles.watchLater(ViewerProfiles.GENERAL).size());
        assertEquals(0,profiles.overview("one",2026).rated); assertEquals(0,profiles.overview("one",2026).watched);
        assertTrue(profiles.rank(Arrays.asList(a,b),false,2026).contains(a));
        ViewerProfiles restored=ViewerProfiles.fromJson(profiles.toJson()); assertEquals(profiles.toJson(),restored.toJson());
        assertEquals("Original title",restored.watchLater("one").get(0).originalTitle);
        restored.removeWatchLater("one",a.imdbId); assertTrue(restored.watchLater("one").isEmpty()); assertEquals(1,restored.watchLater("two").size());
        restored.removeWatchLater(ViewerProfiles.GENERAL,a.imdbId); assertTrue(restored.watchLater("two").isEmpty()); assertEquals(1,restored.watchLater(ViewerProfiles.GENERAL).size());
    }
    @Test public void invalidSelectionsDoNotPartiallySave() {
        ViewerProfiles profiles=ProfileFixtures.household(); String before=profiles.toJson();
        for (Set<String> people:Arrays.asList(Collections.<String>emptySet(),new HashSet<>(Arrays.asList("one","missing")),new HashSet<>(Arrays.asList(ViewerProfiles.GENERAL,"two")))) assertThrows(IllegalArgumentException.class,() -> profiles.saveForLater(people,item(1)));
        assertEquals(before,profiles.toJson());
    }
    @Test public void oldProfilesAndVotesMigrateWithoutChangingFeedback() throws Exception {
        ViewerProfiles profiles=ProfileFixtures.household(); profiles.rate("one",item(1),0,1000L);
        org.json.JSONObject old=new org.json.JSONObject(profiles.toJson()).put("version",3);
        for (int i=0;i<old.getJSONArray("profiles").length();i++) old.getJSONArray("profiles").getJSONObject(i).remove("watchLater");
        ViewerProfiles restored=ViewerProfiles.fromJson(old.toString()); assertEquals(0,restored.vote("one",item(1).imdbId).rating); assertEquals(1000L,restored.vote("one",item(1).imdbId).watchedAt); assertTrue(restored.watchLater("one").isEmpty());
    }
}
