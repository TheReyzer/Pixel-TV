package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Only the current review conversation; never persisted as pending history. */
final class WatchReviewSession {
    private final List<PlaybackJournal.Entry> remaining=new ArrayList<>();
    final String apps;
    final boolean multipleTitles;
    int saved,handled;
    WatchReviewSession(List<PlaybackJournal.Entry> newestFirst) {
        List<PlaybackJournal.Entry> chronological=new ArrayList<>(newestFirst); Collections.reverse(chronological);
        LinkedHashSet<String> names=new LinkedHashSet<>();
        for (PlaybackJournal.Entry entry:chronological) {
            names.add(PlaybackJournal.APPS.get(entry.pkg));
            boolean duplicate=false;
            for (PlaybackJournal.Entry old:remaining) if (same(old,entry)) { duplicate=true; if (old.item==null&&entry.item!=null) old.item=entry.item; break; }
            if (!duplicate) remaining.add(entry);
        }
        apps=String.join(", ",names); int named=0; for (PlaybackJournal.Entry entry:remaining) if (named(entry)) named++;
        multipleTitles=named>=2;
    }
    private static boolean named(PlaybackJournal.Entry e) { return e.item!=null||!e.title.isEmpty(); }
    private static String id(PlaybackJournal.Entry e) { return e.item==null?PlaybackJournal.imdb(e.mediaId):e.item.imdbId; }
    private static boolean same(PlaybackJournal.Entry a,PlaybackJournal.Entry b) {
        String left=id(a),right=id(b); if (!left.isEmpty()&&!right.isEmpty()) return left.equals(right);
        return named(a)&&named(b)&&WatchNavigation.normalize(a.displayTitle()).equals(WatchNavigation.normalize(b.displayTitle()));
    }
    PlaybackJournal.Entry nextDetected() { for (PlaybackJournal.Entry entry:remaining) if (named(entry)) return entry; return null; }
    PlaybackJournal.Entry first() { PlaybackJournal.Entry named=nextDetected(); return named!=null?named:remaining.isEmpty()?null:remaining.get(remaining.size()-1); }
    void completed(MediaRepository.MediaItem item,boolean changed) {
        remaining.removeIf(entry -> !id(entry).isEmpty()?id(entry).equals(item.imdbId):WatchNavigation.normalize(entry.displayTitle()).equals(WatchNavigation.normalize(item.title)));
        if (changed) saved++;
        handled++;
    }
}
