package com.gathin.tvhome;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ProfileSyncMergeTest {
    private MediaRepository.MediaItem item(int id) { return new MediaRepository.MediaItem("tt"+(9100000+id),"Título "+id,"movie","8","2026","Drama","","","","","2026-10-01",1); }

    @Test public void remoteProfileImportsIntoAnExistingLocalProfile() throws Exception {
        ViewerProfiles local=ProfileFixtures.household(), remote=ProfileFixtures.household();
        MediaRepository.MediaItem title=item(1); remote.rate("one",title,4,2000L);
        assertTrue(local.mergeProfileJson("two",remote.profileJson("one")));
        assertEquals(4,local.vote("two",title.imdbId).rating);
        assertEquals(2000L,local.vote("two",title.imdbId).watchedAt);
    }

    @Test public void aLocalRemovalWinsOverAnOlderRemoteListSnapshot() throws Exception {
        ViewerProfiles profiles=ProfileFixtures.household(); MediaRepository.MediaItem title=item(2);
        profiles.saveForLater(Collections.singleton("one"),title);
        String olderRemote=profiles.profileJson("one");
        profiles.removeWatchLater("one",title.imdbId);
        profiles.mergeProfileJson("one",olderRemote);
        assertTrue(profiles.watchLater("one").isEmpty());
    }

    @Test public void profilesCreatedBeforeSyncStillImportTheirExistingList() throws Exception {
        ViewerProfiles remote=ProfileFixtures.household(); MediaRepository.MediaItem title=item(3);
        remote.saveForLater(Collections.singleton("one"),title);
        org.json.JSONObject document=new org.json.JSONObject(remote.profileJson("one"));
        document.getJSONObject("profile").remove("watchLaterAt");
        ViewerProfiles local=ProfileFixtures.household();
        local.mergeProfileJson("two",document.toString());
        assertEquals(1,local.watchLater("two").size());
    }
}
