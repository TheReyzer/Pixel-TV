package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** Keep the spoken query and localized catalog names together without duplicate pills. */
final class SearchSuggestions {
    static List<String> titles(String query,List<String> spoken,List<MediaRepository.MediaItem> items) {
        List<String> titles=new ArrayList<>();
        if (items.isEmpty()&&spoken.isEmpty()) return titles;
        add(titles,query);
        if (!items.isEmpty()) add(titles,items.get(0).title);
        for (String title:spoken) add(titles,title);
        for (MediaRepository.MediaItem item:items) { add(titles,item.title); add(titles,item.originalSubtitle()); }
        return titles;
    }
    private static void add(List<String> titles,String value) {
        String title=value==null?"":value.trim();
        if (title.length()<2||titles.size()==8) return;
        String key=WatchNavigation.normalize(title);
        if (titles.stream().noneMatch(existing -> WatchNavigation.normalize(existing).equals(key))) titles.add(title.substring(0,Math.min(100,title.length())));
    }
}
