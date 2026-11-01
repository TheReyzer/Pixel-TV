package com.gathin.tvhome;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class MediaRatingTest {
    @Test public void absentOrInvalidScoresNeverShowAnEmptyStarOrInventedRating() throws Exception {
        for (String score:new String[]{""," ","—","null","NaN","Infinity","0","11","bad"}) {
            MediaRepository.MediaItem item=MediaRepository.MediaItem.fromJson(new JSONObject().put("rating",score));
            assertEquals("Sem nota",item.ratingLabel(false)); assertEquals("Sem nota",item.ratingLabel(true));
        }
        assertEquals("Sem nota",MediaRepository.MediaItem.fromJson(new JSONObject()).ratingLabel(true));
        MediaRepository.MediaItem item=MediaRepository.MediaItem.fromJson(new JSONObject().put("rating","8.1"));
        assertEquals("★ 8.1",item.ratingLabel(false)); assertEquals("★ 8.1 IMDb",item.ratingLabel(true));
    }
}
