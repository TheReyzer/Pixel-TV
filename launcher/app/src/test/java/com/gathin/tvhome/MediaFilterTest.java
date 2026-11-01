package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class MediaFilterTest {
    private MediaRepository.MediaItem item(String id,String type) {
        return new MediaRepository.MediaItem(id,id,type,"8","2026","Drama","","","","","",0);
    }
    @Test public void secondaryFilterPreservesEachPrimaryOrderAndDoesNotMutateCatalog() {
        MediaRepository.MediaItem oldMovie=item("tt1000001","movie"),newMovie=item("tt1000002","movie"),series=item("tt1000003","series");
        List<MediaRepository.MediaItem> novidades=Arrays.asList(newMovie,series,oldMovie), hot=Arrays.asList(series,oldMovie,newMovie);
        assertEquals(novidades,MediaRepository.filterByType(novidades,MediaRepository.TYPE_ALL));
        assertEquals(Arrays.asList(newMovie,oldMovie),MediaRepository.filterByType(novidades,MediaRepository.TYPE_MOVIES));
        assertEquals(Arrays.asList(series),MediaRepository.filterByType(novidades,MediaRepository.TYPE_SERIES));
        assertEquals(hot,MediaRepository.filterByType(hot,MediaRepository.TYPE_ALL));
        assertEquals(Arrays.asList(oldMovie,newMovie),MediaRepository.filterByType(hot,MediaRepository.TYPE_MOVIES));
        assertEquals(Arrays.asList(series),MediaRepository.filterByType(hot,MediaRepository.TYPE_SERIES));
        assertEquals(3,novidades.size()); assertEquals(3,hot.size());
    }
    @Test public void absentTypeRemainsEmptyAndWatchedExclusionStillAppliesAfterFiltering() {
        MediaRepository.MediaItem movie=item("tt1000001","movie"); List<MediaRepository.MediaItem> source=Arrays.asList(movie);
        assertTrue(MediaRepository.filterByType(source,MediaRepository.TYPE_SERIES).isEmpty());
        ViewerProfiles profiles=ProfileFixtures.household(); profiles.markWatched(ViewerProfiles.GENERAL,movie);
        assertTrue(profiles.rank(MediaRepository.filterByType(source,MediaRepository.TYPE_MOVIES),false,2026).isEmpty());
    }
}
