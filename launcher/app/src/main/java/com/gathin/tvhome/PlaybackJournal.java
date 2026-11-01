package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Playback evidence and review queue. Entries never write to a person's watched history. */
final class PlaybackJournal {
    static final long MIN_PLAY_MS=90_000;
    static final Map<String,String> APPS=new LinkedHashMap<>();
    static {
        APPS.put("com.stremio.one","Stremio"); APPS.put("com.global.unitviptv","UniTV"); APPS.put("com.netflix.ninja","Netflix");
        APPS.put("com.amazon.amazonvideo.livingroom","Prime Video"); APPS.put("com.globo.globotv","Globoplay");
        APPS.put("com.apple.atve.androidtv.appletv","Apple TV"); APPS.put("com.disney.disneyplus","Disney+");
        APPS.put("com.wbd.stream","HBO Max"); APPS.put("com.hbo.hbonow","HBO Max"); APPS.put("com.cbs.ca","Paramount+");
    }
    static final class Entry {
        String id,pkg,title,mediaId,source;
        MediaRepository.MediaItem item;
        long playedMs,at;
        String displayTitle() { return item!=null?item.title:title.isEmpty()?"Conteúdo de "+APPS.get(pkg):title; }
        JSONObject json() throws Exception {
            return new JSONObject().put("id",id).put("pkg",pkg).put("title",title).put("mediaId",mediaId).put("source",source)
                    .put("playedMs",playedMs).put("at",at).put("item",item==null?JSONObject.NULL:item.toJson());
        }
        static Entry read(JSONObject json) {
            if (json==null||!APPS.containsKey(json.optString("pkg"))) return null;
            Entry e=new Entry(); e.id=json.optString("id"); if (e.id.isEmpty()) return null;
            e.pkg=json.optString("pkg"); e.title=clean(json.optString("title")); e.mediaId=clean(json.optString("mediaId"));
            e.source=json.optString("source","session"); e.playedMs=Math.max(0,json.optLong("playedMs"));
            e.at=json.optLong("at");
            JSONObject item=json.optJSONObject("item"); if (item!=null&&item.optString("imdbId").matches("tt\\d{5,12}")) e.item=MediaRepository.MediaItem.fromJson(item);
            return e;
        }
    }
    private static final class Live {
        String pkg,title,mediaId,source,key;
        MediaRepository.MediaItem item;
        long playedMs,lastTick;
        boolean playing;
    }
    private static final class Launch {
        MediaRepository.MediaItem item; long at;
    }
    private final List<Entry> entries=new ArrayList<>();
    private final Map<String,Live> live=new LinkedHashMap<>();
    private final Map<String,Launch> launches=new LinkedHashMap<>();
    static String clean(String value) { String result=value==null?"":value.replaceAll("[\\p{Cntrl}]"," ").trim(); return result.substring(0,Math.min(result.length(),400)); }
    static String imdb(String value) {
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?<![a-zA-Z0-9])tt\\d{5,12}(?!\\d)").matcher(value==null?"":value);
        return matcher.find()?matcher.group():"";
    }
    synchronized void rememberLaunch(String pkg,MediaRepository.MediaItem item,long now) {
        if (!APPS.containsKey(pkg)||item==null) return;
        Launch launch=new Launch(); launch.item=item; launch.at=now; launches.put(pkg,launch);
    }
    synchronized void clearLaunch(String pkg) { launches.remove(pkg); }
    synchronized void sample(String pkg,String title,String mediaId,boolean playing,long duration,long now,String source) {
        if (!APPS.containsKey(pkg)) return;
        title=clean(title); mediaId=clean(mediaId);
        String normalized=WatchNavigation.normalize(title);
        if (normalized.equals(WatchNavigation.normalize(APPS.get(pkg)))) title="";
        if (normalized.matches("(trailer|teaser|previa|preview|propaganda)( oficial)?")||(duration>0&&duration<180_000)) playing=false;
        Launch launch=launches.get(pkg); MediaRepository.MediaItem item=null;
        if (launch!=null&&now>=launch.at&&now-launch.at<4*60*60_000L) {
            String id=imdb(mediaId);
            if (!id.isEmpty()?id.equals(launch.item.imdbId):title.isEmpty()||WatchNavigation.titleMatches(title,launch.item.title,launch.item.year)) item=launch.item;
            else launches.remove(pkg);
        }
        String key=!imdb(mediaId).isEmpty()?imdb(mediaId):!title.isEmpty()?WatchNavigation.normalize(title):item!=null?item.imdbId:"unknown";
        Live previous=live.get(pkg);
        // Metadata may disappear briefly while a player pauses or switches streams.
        if (previous!=null&&key.equals("unknown")&&!previous.key.equals("unknown")) { key=previous.key; title=previous.title; mediaId=previous.mediaId; item=previous.item; }
        if (previous!=null&&!key.equals(previous.key)) { stop(previous,now); finish(previous,now); previous=null; }
        if (previous==null) { previous=new Live(); previous.pkg=pkg; previous.key=key; live.put(pkg,previous); }
        long delta=now-previous.lastTick;
        if (previous.playing&&delta>0&&delta<=20_000) previous.playedMs+=delta;
        previous.lastTick=now; previous.playing=playing; previous.title=title; previous.mediaId=mediaId; previous.item=item;
        previous.source=item!=null&&title.isEmpty()?"opened":source;
    }
    synchronized void pauseAll(long now) {
        for (Live current:live.values()) stop(current,now);
    }
    private void stop(Live current,long now) { long delta=now-current.lastTick; if (current.playing&&delta>0&&delta<=20_000) current.playedMs+=delta; current.playing=false; current.lastTick=now; }
    synchronized void home(long now) {
        pauseAll(now); for (Live current:live.values()) finish(current,now);
        for (Map.Entry<String,Launch> opened:launches.entrySet()) {
            Launch launch=opened.getValue();
            if (now-launch.at>=15_000&&now-launch.at<4*60*60_000L) add(opened.getKey(),launch.item.title,"",launch.item,"opened",0,now);
        }
        live.clear(); launches.clear();
    }
    private void finish(Live current,long now) {
        if (current.playedMs>=MIN_PLAY_MS) add(current.pkg,current.title,current.mediaId,current.item,current.source,current.playedMs,now);
    }
    private void add(String pkg,String title,String mediaId,MediaRepository.MediaItem item,String source,long playedMs,long now) {
        String identity=item!=null?item.imdbId:!imdb(mediaId).isEmpty()?imdb(mediaId):WatchNavigation.normalize(title);
        for (Entry old:entries) {
            String oldIdentity=old.item!=null?old.item.imdbId:!imdb(old.mediaId).isEmpty()?imdb(old.mediaId):WatchNavigation.normalize(old.title);
            if (old.pkg.equals(pkg)&&oldIdentity.equals(identity)) { old.playedMs=Math.max(old.playedMs,playedMs); return; }
        }
        Entry entry=new Entry(); entry.id=UUID.randomUUID().toString(); entry.pkg=pkg; entry.title=title; entry.mediaId=mediaId;
        entry.item=item; entry.source=source; entry.playedMs=playedMs; entry.at=now; entries.add(entry);
    }
    synchronized List<Entry> pending() { List<Entry> result=new ArrayList<>(entries); java.util.Collections.reverse(result); return result; }
    synchronized void discardSuggestions() { entries.clear(); }
    synchronized String toJson() {
        try {
            JSONArray list=new JSONArray(),sessions=new JSONArray(),opened=new JSONArray();
            for (Entry e:entries) list.put(e.json());
            for (Live l:live.values()) sessions.put(new JSONObject().put("pkg",l.pkg).put("title",l.title).put("mediaId",l.mediaId).put("key",l.key).put("source",l.source).put("playedMs",l.playedMs).put("item",l.item==null?JSONObject.NULL:l.item.toJson()));
            for (Map.Entry<String,Launch> e:launches.entrySet()) opened.put(new JSONObject().put("pkg",e.getKey()).put("at",e.getValue().at).put("item",e.getValue().item.toJson()));
            return new JSONObject().put("version",1).put("entries",list).put("live",sessions).put("launches",opened).toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static PlaybackJournal fromJson(String raw) throws Exception {
        PlaybackJournal result=new PlaybackJournal(); if (raw==null||raw.isEmpty()) return result;
        JSONObject root=new JSONObject(raw); if (root.optInt("version")!=1) throw new IllegalArgumentException("Histórico incompatível");
        JSONArray entries=root.optJSONArray("entries"); if (entries!=null) for (int i=0;i<entries.length();i++) { Entry entry=Entry.read(entries.optJSONObject(i)); if (entry!=null) result.entries.add(entry); }
        JSONArray sessions=root.optJSONArray("live"); if (sessions!=null) for (int i=0;i<sessions.length();i++) {
            JSONObject json=sessions.optJSONObject(i); if (json==null||!APPS.containsKey(json.optString("pkg"))) continue;
            Live l=new Live(); l.pkg=json.optString("pkg"); l.title=clean(json.optString("title")); l.mediaId=clean(json.optString("mediaId")); l.key=json.optString("key","unknown");
            l.source=json.optString("source","session"); l.playedMs=Math.max(0,json.optLong("playedMs")); JSONObject item=json.optJSONObject("item");
            if (item!=null&&item.optString("imdbId").matches("tt\\d{5,12}")) l.item=MediaRepository.MediaItem.fromJson(item); result.live.put(l.pkg,l);
        }
        JSONArray opened=root.optJSONArray("launches"); if (opened!=null) for (int i=0;i<opened.length();i++) {
            JSONObject json=opened.optJSONObject(i); if (json==null||!APPS.containsKey(json.optString("pkg"))) continue;
            JSONObject item=json.optJSONObject("item"); if (item==null||!item.optString("imdbId").matches("tt\\d{5,12}")) continue;
            Launch launch=new Launch(); launch.at=json.optLong("at"); launch.item=MediaRepository.MediaItem.fromJson(item); result.launches.put(json.optString("pkg"),launch);
        }
        return result;
    }
}
