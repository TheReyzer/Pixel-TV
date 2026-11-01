package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** One search session, retained when another rating is requested. */
final class TitleSearchResults {
    List<MediaRepository.MediaItem> items=new ArrayList<>();
    String query="",type="all",cursor="";
    int shown;
    boolean more,loading,failed;
    void reset(String query,String type) {
        this.query=query; this.type=type; cursor=""; shown=0; more=false; loading=false; failed=false; items.clear();
    }
    void append(MediaRepository.SearchPage page) {
        items=MediaRepository.appendUnique(items,page.items);
        more=page.more&&!page.cursor.equals(cursor); cursor=page.cursor; loading=false; failed=false;
    }
    int reveal() { shown=Math.min(items.size(),shown+12); return shown; }
    boolean nearEnd(int index) { return index>=shown-4; }
    boolean matches(String query,String type) { return this.query.equals(query.trim())&&this.type.equals(type); }
}
