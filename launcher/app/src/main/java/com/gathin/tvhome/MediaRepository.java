package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Fetches and caches IMDb-connected Movie & Series catalogs (Cinemeta + PT-BR localization),
 * guarantees mixed Movie+Series trios in "Novidades" and "Em Alta", and loads artwork asynchronously.
 */
public class MediaRepository {

    public static final int CAT_NOVIDADES = 0;
    public static final int CAT_EM_ALTA = 1;
    public static final int TYPE_ALL = 0;
    public static final int TYPE_MOVIES = 1;
    public static final int TYPE_SERIES = 2;

    private static final String PREFS_CACHE = "tvhome_media_cache_v3";
    private static final String KEY_JSON = "catalog_json";
    private static final String KEY_TS = "catalog_ts";
    private static final long REFRESH_INTERVAL_MS = 4 * 60 * 60_000L; // 4 hours
    private static final java.util.regex.Pattern IMDB_ID=java.util.regex.Pattern.compile("tt\\d{5,12}"),ISO_DATE=java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}.*");

    public static final class MediaItem {
        public final String imdbId;
        public volatile String title;
        public volatile String originalTitle="";
        public boolean titleInPtBr;
        String originalSubtitle() { return originalTitle.trim().isEmpty()||WatchNavigation.normalize(originalTitle).equals(WatchNavigation.normalize(title))?"":originalTitle; }
        String displayTitles() { return title+(originalSubtitle().isEmpty()?"":"\n"+originalSubtitle()); }
        public final String type; // "movie" or "series"
        public volatile String rating;
        public boolean hasRating() {
            try { double value=Double.parseDouble(rating.trim()); return Double.isFinite(value)&&value>0&&value<=10; } catch (Exception ignored) { return false; }
        }
        public String ratingLabel(boolean imdb) { return hasRating()?"★ "+rating.trim()+(imdb?" IMDb":""):"Sem nota"; }
        public final String year;
        public final String genresPt;
        public volatile String synopsisPt;
        public volatile boolean synopsisInPtBr;
        String visibleSynopsis() { return synopsisInPtBr&&synopsisPt!=null?synopsisPt.trim():""; }
        public final String backdropUrl;
        public final String posterUrl;
        volatile String posterPtBrUrl="";
        boolean posterPtBrChecked;
        public String trailerYtId;
        public final String releasedIso;
        public final double popularity;
        List<String> contentFeatures=Collections.emptyList();

        public MediaItem(String imdbId, String title, String type, String rating, String year,
                         String genresPt, String synopsisPt, String backdropUrl, String posterUrl,
                         String trailerYtId, String releasedIso, double popularity) {
            this.imdbId = imdbId;
            this.title = title;
            this.type = type;
            this.rating = rating;
            this.year = year;
            this.genresPt = genresPt;
            this.synopsisPt = synopsisPt;
            this.backdropUrl = backdropUrl;
            this.posterUrl = posterUrl;
            this.trailerYtId = trailerYtId;
            this.releasedIso = releasedIso != null ? releasedIso : "";
            this.popularity = popularity;
        }

        public boolean isSeries() {
            return "series".equalsIgnoreCase(type);
        }

        public String typeBadgePt() {
            return isSeries() ? "SÉRIE" : "FILME";
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("imdbId", imdbId);
                o.put("title", title);
                o.put("originalTitle",originalTitle);
                o.put("titleInPtBr",titleInPtBr);
                o.put("type", type);
                o.put("rating", rating);
                o.put("year", year);
                o.put("genresPt", genresPt);
                o.put("synopsisPt", synopsisPt);
                o.put("synopsisInPtBr", synopsisInPtBr);
                o.put("synopsisLocaleVersion",2);
                o.put("backdropUrl", backdropUrl);
                o.put("posterUrl", posterUrl);
                o.put("posterPtBrUrl",posterPtBrUrl); o.put("posterPtBrChecked",posterPtBrChecked);
                o.put("trailerYtId", trailerYtId);
                o.put("releasedIso", releasedIso);
                o.put("popularity", popularity);
                o.put("contentFeatures",new JSONArray(contentFeatures));
            } catch (Exception ignored) {
            }
            return o;
        }

        public static MediaItem fromJson(JSONObject o) {
            MediaItem item=new MediaItem(
                    o.optString("imdbId", ""),
                    o.optString("title", ""),
                    o.optString("type", "movie"),
                    o.optString("rating", ""),
                    o.optString("year", ""),
                    o.optString("genresPt", ""),
                    o.optString("synopsisPt", ""),
                    o.optString("backdropUrl", ""),
                    o.optString("posterUrl", ""),
                    o.optString("trailerYtId", ""),
                    o.optString("releasedIso", ""),
                    o.optDouble("popularity", 100.0)
            );
            item.posterPtBrUrl=o.optString("posterPtBrUrl","").replace("/s592/","/s332/"); item.posterPtBrChecked=o.optBoolean("posterPtBrChecked",false);
            item.originalTitle=o.optString("originalTitle","");
            item.titleInPtBr=o.optBoolean("titleInPtBr",false);
            item.contentFeatures=RecommendationFeatures.read(o.optJSONArray("contentFeatures"));
            item.synopsisInPtBr=o.optBoolean("synopsisInPtBr",false)&&o.optInt("synopsisLocaleVersion",0)==2; return item;
        }
    }

    private static final Map<String, String> GENRE_PT = new HashMap<>();
    static {
        GENRE_PT.put("action", "Ação");
        GENRE_PT.put("adventure", "Aventura");
        GENRE_PT.put("animation", "Animação");
        GENRE_PT.put("biography", "Biografia");
        GENRE_PT.put("comedy", "Comédia");
        GENRE_PT.put("crime", "Policial");
        GENRE_PT.put("documentary", "Documentário");
        GENRE_PT.put("drama", "Drama");
        GENRE_PT.put("family", "Família");
        GENRE_PT.put("fantasy", "Fantasia");
        GENRE_PT.put("history", "História");
        GENRE_PT.put("horror", "Terror");
        GENRE_PT.put("music", "Música");
        GENRE_PT.put("musical", "Musical");
        GENRE_PT.put("mystery", "Mistério");
        GENRE_PT.put("romance", "Romance");
        GENRE_PT.put("sci-fi", "Ficção Científica");
        GENRE_PT.put("science fiction", "Ficção Científica");
        GENRE_PT.put("sport", "Esporte");
        GENRE_PT.put("thriller", "Suspense");
        GENRE_PT.put("war", "Guerra");
        GENRE_PT.put("western", "Faroeste");
        GENRE_PT.put("short","Curta-metragem");
        GENRE_PT.put("reality-tv","Reality show");
        GENRE_PT.put("talk-show","Entrevistas");
        GENRE_PT.put("game-show","Competição");
    }
    static String genreLabel(String value) {
        String clean=value.trim(),translated=GENRE_PT.get(clean.toLowerCase(Locale.ROOT));
        if (translated!=null) return translated;
        for (String label:GENRE_PT.values()) if (label.equalsIgnoreCase(clean)) return label;
        return clean;
    }

    private final Context ctx;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService imagePool = Executors.newFixedThreadPool(4);
    private final ExecutorService discoveryImagePool = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ConcurrentHashMap<String,Object> imageLocks=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int prefetchGeneration;
    private final ExecutorService trailerPool = Executors.newFixedThreadPool(3);
    private final ExecutorService titlePool=Executors.newFixedThreadPool(2);
    private final Map<String,List<Runnable>> titleCallbacks=new HashMap<>();
    private final Map<String,Long> titleAttempts=new HashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String,MediaItem> metadataDetails=new java.util.concurrent.ConcurrentHashMap<>();
    private boolean titlesClosed;
    private final LruCache<String, Bitmap> memCache;
    private final File diskCacheDir;
    private volatile boolean imagesClosed;

    private final List<MediaItem> listNovidades = new ArrayList<>();
    private final List<MediaItem> listEmAlta = new ArrayList<>();
    private final List<MediaItem> allMovies = new ArrayList<>();
    private final List<MediaItem> allSeries = new ArrayList<>();
    private ViewerProfiles profiles;
    private boolean refreshing;
    private final Map<Integer,RecommendationBatch> recommendations=new HashMap<>();
    private String recommendationVersion="";
    private final ExecutorService recommendationPool=Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> pendingRecommendations;
    private int recommendationGeneration;
    private java.util.function.IntConsumer recommendationsUpdated;
    synchronized void onRecommendationsUpdated(java.util.function.IntConsumer callback) { recommendationsUpdated=callback; }

    synchronized void setProfiles(ViewerProfiles profiles) { this.profiles = profiles; profiles.observeCatalog(allMovies); profiles.observeCatalog(allSeries); recommendationVersion=""; recommendations.clear(); }

    public MediaRepository(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.diskCacheDir = new File(this.ctx.getCacheDir(), "media_img");
        if (!this.diskCacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            this.diskCacheDir.mkdirs();
        }
        // 18 MB bitmap cache (safe and fast on 2 GB RAM TV)
        this.memCache = new LruCache<String, Bitmap>(18 * 1024 * 1024) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        loadSeedOrCached();
    }

    public synchronized List<MediaItem> getCategoryItems(int category, int type) {
        return getRecommendations(category,type).items;
    }
    static List<MediaItem> variedOrder(List<MediaItem> ranked,java.util.Set<String> recent,java.util.Set<String> previous,java.util.Random random) {
        List<MediaItem> result=new ArrayList<>(); java.util.Set<String> chosen=new java.util.HashSet<>();
        // Fresh titles first; the previous screen is the last resort for small catalogs.
        for (int tier=0;tier<3&&result.size()<4;tier++) {
            while (result.size()<Math.min(4,ranked.size())) {
                MediaItem pick=null; double total=0;
                for (int i=0;i<ranked.size();i++) {
                    MediaItem item=ranked.get(i);
                    int group=previous.contains(item.imdbId)?2:recent.contains(item.imdbId)?1:0;
                    if (group!=tier||chosen.contains(item.imdbId)) continue;
                    double weight=1.0/(i+1); total+=weight;
                    if (random.nextDouble()*total<weight) pick=item;
                }
                if (pick==null) break;
                chosen.add(pick.imdbId); result.add(pick);
            }
        }
        for (MediaItem item:ranked) if (chosen.add(item.imdbId)) result.add(item);
        return result;
    }
    static final class RecommendationBatch {
        final List<MediaItem> items; final String notice;
        final boolean ready;
        RecommendationBatch(List<MediaItem> items,String notice) { this(items,notice,true); }
        RecommendationBatch(List<MediaItem> items,String notice,boolean ready) { this.items=items; this.notice=notice; this.ready=ready; }
    }
    private String currentRecommendationVersion() { return (profiles==null?"":profiles.recommendationVersion())+":"+java.time.LocalDate.now(); }
    synchronized RecommendationBatch getRecommendations(int category,int type) {
        java.time.LocalDate today=java.time.LocalDate.now();
        String version=currentRecommendationVersion();
        if (!version.equals(recommendationVersion)) { recommendations.clear(); recommendationVersion=version; recommendationGeneration++; if (pendingRecommendations!=null) pendingRecommendations.cancel(true); }
        int key=category*3+type;
        RecommendationBatch cached=recommendations.get(key); if (cached!=null) return cached;
        List<MediaItem> reserve=new ArrayList<>(getSeedMovies()); reserve.addAll(getSeedSeries());
        List<MediaItem> releases=new ArrayList<>(listNovidades),hot=new ArrayList<>(listEmAlta);
        long now=System.currentTimeMillis();
        RecommendationBatch initial=homeRecommendations(category==CAT_EM_ALTA?hot:releases,reserve,profiles,category,type,today,now,false);
        recommendations.put(key,new RecommendationBatch(initial.items,initial.notice,false));
        // Other filters are ranked in the worker; opening Home only prepares the visible filter.
        if (pendingRecommendations!=null&&!pendingRecommendations.isDone()) return recommendations.get(key);
        final int token=recommendationGeneration; final String profileJson=profiles==null?null:profiles.toJson();
        pendingRecommendations=recommendationPool.submit(() -> {
            try {
                ViewerProfiles snapshot=profileJson==null?null:ViewerProfiles.fromJson(profileJson);
                if (snapshot!=null) { snapshot.observeCatalog(releases); snapshot.observeCatalog(hot); }
                for (int offset=0;offset<6;offset++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    int combination=(key+offset)%6; long started=System.nanoTime();
                    RecommendationBatch result=homeRecommendations(combination/3==CAT_EM_ALTA?hot:releases,reserve,snapshot,combination/3,combination%3,today,now);
                    synchronized(MediaRepository.this) { if (token!=recommendationGeneration||!version.equals(currentRecommendationVersion())) return; recommendations.put(combination,result); }
                    android.util.Log.d("TVHomeRecommendations","Background rank key="+combination+" count="+result.items.size()+" ms="+((System.nanoTime()-started)/1_000_000));
                    mainHandler.post(() -> { synchronized(MediaRepository.this) { if (token==recommendationGeneration&&version.equals(currentRecommendationVersion())&&recommendationsUpdated!=null) recommendationsUpdated.accept(combination); } });
                }
            } catch (Exception e) { android.util.Log.w("TVHomeRecommendations","Keeping available catalog suggestions",e); }
        });
        return recommendations.get(key);
    }
    static RecommendationBatch homeRecommendations(List<MediaItem> catalog,List<MediaItem> reserve,ViewerProfiles profiles,int category,int type,java.time.LocalDate today,long nowMs) {
        return homeRecommendations(catalog,reserve,profiles,category,type,today,nowMs,true);
    }
    static RecommendationBatch homeRecommendations(List<MediaItem> catalog,List<MediaItem> reserve,ViewerProfiles profiles,int category,int type,java.time.LocalDate today,long nowMs,boolean personalized) {
        List<MediaItem> available=availableTitles(catalog,type,today);
        List<MediaItem> primary=category==CAT_NOVIDADES?newReleases(available,today):available;
        boolean mixed=category==CAT_EM_ALTA&&type==TYPE_ALL;
        List<MediaItem> ranked=profiles==null?primary:personalized?profiles.rank(primary,mixed,today.getYear(),nowMs):profiles.unseen(primary,nowMs);
        if (ranked.size()>=3) return new RecommendationBatch(ranked,"");
        List<MediaItem> preferred=ranked;
        List<MediaItem> pool=availableTitles(mergeLists(catalog,reserve),type,today);
        if (category==CAT_NOVIDADES) pool.sort((a,b) -> releaseDate(b).compareTo(releaseDate(a)));
        if (profiles!=null) { if (personalized) profiles.observeCatalog(pool); ranked=personalized?profiles.rank(pool,mixed,today.getYear(),nowMs):profiles.unseen(pool,nowMs); } else ranked=pool;
        String notice=category==CAT_NOVIDADES?"Sem lançamentos inéditos deste ano · sugestões de outros anos para você":"Mais sugestões do catálogo para você";
        if (!ranked.isEmpty()) {
            List<MediaItem> filled=mergeLists(preferred,ranked);
            if (!preferred.isEmpty()) notice=filled.size()==preferred.size()?"":category==CAT_NOVIDADES?"Poucos lançamentos inéditos deste ano · outras sugestões para completar":"Mais sugestões do catálogo para completar";
            return new RecommendationBatch(filled,notice);
        }
        // Only after all unseen options are exhausted may the latest always-show request override the cooldown.
        ranked=profiles==null||!personalized?pool:profiles.revisitCandidates(pool,mixed,today.getYear(),nowMs);
        return new RecommendationBatch(ranked,"Para rever · todos os títulos disponíveis já foram assistidos recentemente");
    }
    private static List<MediaItem> availableTitles(List<MediaItem> catalog,int type,java.time.LocalDate today) {
        Map<String,MediaItem> unique=new java.util.LinkedHashMap<>();
        for (MediaItem item:filterByType(catalog,type)) {
            int year=DiscoveryQuery.releaseYear(item.year);
            if (!IMDB_ID.matcher(item.imdbId).matches()||item.title.trim().isEmpty()||year>today.getYear()) continue;
            if (ISO_DATE.matcher(item.releasedIso).matches()&&item.releasedIso.substring(0,10).compareTo(today.toString())>0) continue;
            unique.putIfAbsent(item.imdbId,item);
        }
        return new ArrayList<>(unique.values());
    }
    synchronized RecommendationBatch recommendDiscovery(List<MediaItem> candidates,DiscoveryQuery query) {
        List<MediaItem> reserve=new ArrayList<>(allMovies); reserve.addAll(allSeries); reserve.addAll(getSeedMovies()); reserve.addAll(getSeedSeries());
        return discoveryRecommendations(candidates,reserve,profiles,query,java.time.LocalDate.now());
    }
    static RecommendationBatch discoveryRecommendations(List<MediaItem> candidates,List<MediaItem> reserve,ViewerProfiles profiles,DiscoveryQuery query,java.time.LocalDate today) {
        int type="series".equals(query.type)?TYPE_SERIES:TYPE_MOVIES;
        List<MediaItem> source=availableTitles(candidates,type,today);
        profiles.observeCatalog(source);
        List<MediaItem> ranked=profiles.discover(source,query,today.getYear());
        if (!ranked.isEmpty()) return new RecommendationBatch(ranked,"");
        List<MediaItem> pool=availableTitles(mergeLists(candidates,reserve),type,today); profiles.observeCatalog(pool);
        ranked=profiles.discover(pool,query,today.getYear());
        if (!ranked.isEmpty()) return new RecommendationBatch(ranked,"Mais descobertas encontradas no catálogo salvo");
        DiscoveryQuery broader=new DiscoveryQuery(query); broader.era=1;
        String history=query.includeWatched?"incluindo assistidos conforme sua escolha":"mantendo os assistidos excluídos";
        ranked=profiles.discover(pool,broader,today.getYear());
        if (!ranked.isEmpty()) return new RecommendationBatch(ranked,"Sem opções exatas · ampliamos a época e mantivemos os gêneros, "+history);
        broader.genres.clear(); ranked=profiles.discover(pool,broader,today.getYear());
        if (!ranked.isEmpty()) return new RecommendationBatch(ranked,"Sem opções exatas · ampliamos gêneros e época, "+history);
        broader.includeWatched=true; ranked=profiles.discover(pool,broader,today.getYear());
        return new RecommendationBatch(ranked,"Para rever · filtros ampliados porque não restaram títulos ainda não assistidos");
    }
    static List<MediaItem> newReleases(List<MediaItem> ranked,java.time.LocalDate today) {
        List<MediaItem> out=new ArrayList<>();
        for (MediaItem item:ranked) {
            if (DiscoveryQuery.releaseYear(item.year)!=today.getYear()) continue;
            String date=releaseDate(item);
            if (date.startsWith(String.valueOf(today.getYear()))&&date.compareTo(today.toString())<=0) out.add(item);
        }
        // This becomes the recency bonus in the existing personal ranking.
        out.sort((a,b) -> releaseDate(b).compareTo(releaseDate(a))); return out;
    }
    private static String releaseDate(MediaItem item) {
        if (ISO_DATE.matcher(item.releasedIso).matches()) return item.releasedIso.substring(0,10);
        int year=DiscoveryQuery.releaseYear(item.year); return year==0?"":year+"-01-01";
    }

    static List<MediaItem> filterByType(List<MediaItem> source, int type) {
        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : source) if (type == TYPE_ALL || (type == TYPE_SERIES ? item.isSeries() : !item.isSeries())) filtered.add(item);
        return filtered;
    }

    static final class DiscoveryCatalog {
        final List<MediaItem> items;
        final boolean online;
        DiscoveryCatalog(List<MediaItem> items,boolean online) { this.items=items; this.online=online; }
    }
    static final int MIN_DISCOVERY_RESULTS=100;
    static List<String> discoveryPaths(DiscoveryQuery query,int year) {
        List<String> paths=new ArrayList<>(); List<String> sources=new ArrayList<>();
        if (query.genres.isEmpty()) sources.add("top/");
        else for (String genre:query.genres) if (Arrays.asList(DiscoveryQuery.GENRES).contains(genre)) sources.add("top/genre="+genre+"&");
        if (query.era!=1) for (int age:query.era==0?new int[]{0,1,2}:new int[]{20,30,45}) sources.add("year/genre="+(year-age)+"&");
        for (int skip=0;skip<1000;skip+=50) for (String source:sources) { paths.add(source+"skip="+skip); if (paths.size()==36) return paths; }
        return paths;
    }

    /** Called off the UI thread; bounded parallel catalog requests, with an explicit cached fallback. */
    DiscoveryCatalog discover(DiscoveryQuery query) throws InterruptedException { return discover(query,Collections.emptySet()); }
    DiscoveryCatalog discover(DiscoveryQuery query,java.util.Set<String> excluded) throws InterruptedException {
        java.util.LinkedHashMap<String,MediaItem> candidates=new java.util.LinkedHashMap<>();
        synchronized(this) { for (MediaItem item:"series".equals(query.type)?allSeries:allMovies) if (!excluded.contains(item.imdbId)) candidates.put(item.imdbId,item); }
        int year=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        List<String> paths=discoveryPaths(query,year);
        java.util.Set<String> accepted=new java.util.HashSet<>();
        for (MediaItem item:candidates.values()) if (profiles==null?query.accepts(item,year):profiles.acceptsDiscovery(item,query,year)) accepted.add(item.imdbId);
        java.util.concurrent.ExecutorService pool=Executors.newFixedThreadPool(4);
        java.util.concurrent.CompletionService<List<MediaItem>> completed=new java.util.concurrent.ExecutorCompletionService<>(pool);
        List<java.util.concurrent.Future<List<MediaItem>>> tasks=new ArrayList<>();
        boolean online=false;
        try {
            int submitted=0,received=0;
            for (;submitted<Math.min(4,paths.size());submitted++) { final String path=paths.get(submitted); tasks.add(completed.submit(() -> parseDiscoveryCatalog(NativeTrailerSource.request("https://v3-cinemeta.strem.io/catalog/"+query.type+"/"+path+".json",null),query.type))); }
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
            while (received<submitted) {
                long remaining=end-System.nanoTime(); if (remaining<=0) break;
                java.util.concurrent.Future<List<MediaItem>> ready=completed.poll(remaining,java.util.concurrent.TimeUnit.NANOSECONDS);
                if (ready==null) break;
                received++;
                try {
                    for (MediaItem item:ready.get()) {
                        if (excluded.contains(item.imdbId)) continue;
                        if (candidates.putIfAbsent(item.imdbId,item)==null&&(profiles==null?query.accepts(item,year):profiles.acceptsDiscovery(item,query,year))) accepted.add(item.imdbId);
                    }
                    online=true;
                } catch (java.util.concurrent.ExecutionException e) { android.util.Log.d("TVHomeDiscover","Catalog unavailable: "+e.getCause()); }
                if (online&&accepted.size()>=MIN_DISCOVERY_RESULTS) break;
                if (submitted<paths.size()) { final String path=paths.get(submitted++); tasks.add(completed.submit(() -> parseDiscoveryCatalog(NativeTrailerSource.request("https://v3-cinemeta.strem.io/catalog/"+query.type+"/"+path+".json",null),query.type))); }
            }
        } finally { for (java.util.concurrent.Future<?> task:tasks) task.cancel(true); pool.shutdownNow(); }
        android.util.Log.d("TVHomeDiscover","CATALOG online="+online+" candidates="+candidates.size()+" eligible="+accepted.size()+" requests="+tasks.size());
        return new DiscoveryCatalog(new ArrayList<>(candidates.values()),online);
    }

    static List<MediaItem> parseDiscoveryCatalog(String body,String type) throws Exception {
        JSONArray metas=new JSONObject(body).getJSONArray("metas"); List<MediaItem> items=new ArrayList<>();
        for (int i=0;i<Math.min(100,metas.length());i++) {
            JSONObject m=metas.optJSONObject(i); if (m==null) continue;
            String id=m.optString("imdb_id",m.optString("id")), title=m.optString("name").trim();
            if (!id.matches("tt\\d{5,12}") || title.isEmpty()) continue;
            JSONArray genres=m.optJSONArray("genre"); if (genres==null) genres=m.optJSONArray("genres");
            List<String> translated=new ArrayList<>();
            if (genres!=null) for (int j=0;j<genres.length();j++) { String g=genres.optString(j); translated.add(GENRE_PT.getOrDefault(g.toLowerCase(Locale.ROOT),g)); }
            String year=m.optString("releaseInfo",""), poster=m.optString("poster",""), background=m.optString("background","");
            if (poster.isEmpty()) poster="https://images.metahub.space/poster/medium/"+id+"/img";
            if (background.isEmpty()) background="https://images.metahub.space/background/medium/"+id+"/img";
            MediaItem item=new MediaItem(id,title,type,m.optString("imdbRating","—"),year,String.join(", ",translated),
                    m.optString("description",""),background,poster,m.optString("trailerYtId",""),m.optString("released",""),100-i);
            RecommendationFeatures.fromCatalog(item,m); items.add(item);
        }
        return items;
    }
    static final class SearchPage {
        final List<MediaItem> items;
        final String cursor;
        final boolean more;
        SearchPage(List<MediaItem> items,String cursor,boolean more) { this.items=items; this.cursor=cursor; this.more=more; }
    }
    static List<MediaItem> appendUnique(List<MediaItem> existing,List<MediaItem> extra) {
        java.util.LinkedHashMap<String,MediaItem> combined=new java.util.LinkedHashMap<>();
        for (List<MediaItem> source:Arrays.asList(existing,extra)) for (MediaItem item:source)
            if (IMDB_ID.matcher(item.imdbId).matches()&&(item.type.equals("movie")||item.type.equals("series"))) combined.putIfAbsent(item.imdbId,item);
        return new ArrayList<>(combined.values());
    }
    List<MediaItem> browseCatalog(String type,int skip) throws Exception {
        if (skip<0||skip>100000||!Arrays.asList("all","movie","series").contains(type)) throw new IllegalArgumentException("Catálogo inválido");
        if (type.equals("all")) return mixedSearch("",browseCatalog("movie",skip),browseCatalog("series",skip));
        return parseDiscoveryCatalog(NativeTrailerSource.request("https://v3-cinemeta.strem.io/catalog/"+type+"/top/skip="+skip+".json",null),type);
    }
    List<MediaItem> relatedLocal(MediaItem seed) {
        try {
            String saved=ctx.getSharedPreferences("related_titles_v2",Context.MODE_PRIVATE).getString(seed.type+":"+seed.imdbId,"");
            if (!saved.isEmpty()) {
                JSONObject cache=new JSONObject(saved);
                if (cache.optInt("version")==2&&cache.optLong("expires")>System.currentTimeMillis()) {
                    List<MediaItem> items=new ArrayList<>(); JSONArray rows=cache.getJSONArray("items");
                    for (int i=0;i<Math.min(36,rows.length());i++) { MediaItem item=MediaItem.fromJson(rows.getJSONObject(i)); if (IMDB_ID.matcher(item.imdbId).matches()&&!item.imdbId.equals(seed.imdbId)) items.add(item); }
                    if (!items.isEmpty()) return items;
                }
            }
        } catch (Exception ignored) { }
        List<MediaItem> pool=new ArrayList<>(); synchronized(this) { pool.addAll(allMovies); pool.addAll(allSeries); }
        return RelatedTitles.order(seed,Collections.emptyMap(),pool);
    }
    List<MediaItem> relatedTitles(MediaItem seed) throws Exception {
        try {
            String cache=ctx.getSharedPreferences("related_titles_v2",Context.MODE_PRIVATE).getString(seed.type+":"+seed.imdbId,"");
            JSONObject saved=cache.isEmpty()?new JSONObject():new JSONObject(cache);
            if (saved.optInt("version")==2&&saved.optLong("expires")>System.currentTimeMillis()) return relatedLocal(seed);
        } catch (Exception ignored) { }
        Map<String,Integer> links=RelatedTitles.fetch(seed.imdbId);
        List<MediaItem> pool=new ArrayList<>(); synchronized(this) { pool.addAll(allMovies); pool.addAll(allSeries); }
        Map<String,MediaItem> known=new HashMap<>(); for (MediaItem item:pool) known.put(item.imdbId,item);
        List<String> ids=new ArrayList<>(links.keySet()); ids.sort(java.util.Comparator.comparingInt(links::get));
        java.util.concurrent.ExecutorService workers=Executors.newFixedThreadPool(3);
        java.util.concurrent.CompletionService<MediaItem> completed=new java.util.concurrent.ExecutorCompletionService<>(workers);
        List<java.util.concurrent.Future<MediaItem>> tasks=new ArrayList<>(); List<MediaItem> resolved=new ArrayList<>();
        try {
            for (String imdb:ids.subList(0,Math.min(16,ids.size()))) {
                if (known.containsKey(imdb)) { resolved.add(known.get(imdb)); continue; }
                tasks.add(completed.submit(() -> {
                    for (String type:new String[]{seed.type,seed.isSeries()?"movie":"series"}) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        try { return metadataDetails(new MediaItem(imdb,"",type,"","","","","","","","",0)); }
                        catch (Exception error) { if (Thread.currentThread().isInterrupted()) throw new InterruptedException(); }
                    }
                    return null;
                }));
            }
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            for (int i=0;i<tasks.size();i++) {
                long remaining=deadline-System.nanoTime(); if (remaining<=0) break;
                java.util.concurrent.Future<MediaItem> task=completed.poll(remaining,java.util.concurrent.TimeUnit.NANOSECONDS); if (task==null) break;
                try { MediaItem item=task.get(); if (item!=null) resolved.add(item); } catch (java.util.concurrent.ExecutionException ignored) { }
            }
        } finally { for (java.util.concurrent.Future<MediaItem> task:tasks) task.cancel(true); workers.shutdownNow(); }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        List<MediaItem> ordered=RelatedTitles.order(seed,links,appendUnique(resolved,pool));
        JSONArray rows=new JSONArray(); for (MediaItem item:ordered) rows.put(item.toJson());
        SharedPreferences cache=ctx.getSharedPreferences("related_titles_v2",Context.MODE_PRIVATE); SharedPreferences.Editor edit=cache.edit();
        Map<String,?> saved=cache.getAll(); List<String> oldest=new ArrayList<>(saved.keySet());
        oldest.sort(java.util.Comparator.comparingLong(key -> { try { return new JSONObject(String.valueOf(saved.get(key))).optLong("time"); } catch (Exception ignored) { return 0L; } }));
        for (int i=0;i<Math.max(0,oldest.size()-23);i++) edit.remove(oldest.get(i));
        Set<String> available=new HashSet<>(known.keySet()); for (MediaItem item:resolved) available.add(item.imdbId);
        boolean complete=available.containsAll(ids.subList(0,Math.min(16,ids.size()))); long now=System.currentTimeMillis();
        edit.putString(seed.type+":"+seed.imdbId,new JSONObject().put("version",2).put("time",now).put("expires",now+(complete?24*60*60_000L:60_000L)).put("items",rows).toString()).apply();
        return ordered;
    }
    List<MediaItem> searchForRating(String query,String type) throws Exception { return searchPage(query,type,"").items; }
    static SearchPage parseSearchPage(String body,String type) throws Exception {
        JSONObject root=new JSONObject(body),data=root.optJSONObject("data"),titles=data==null?null:data.optJSONObject("popularTitles");
        List<MediaItem> items=parseBrazilianSearch(body,type);
        JSONObject page=titles==null?null:titles.optJSONObject("pageInfo");
        String cursor=page==null?"":page.optString("endCursor","");
        boolean more=page!=null&&page.optBoolean("hasNextPage")&&!cursor.isEmpty()&&cursor.length()<=256;
        return new SearchPage(items,more?cursor:"",more);
    }
    SearchPage searchPage(String query,String type,String cursor) throws Exception {
        query=query.trim();
        if (query.length()<2||query.length()>100||!Arrays.asList("all","movie","series").contains(type)||cursor==null||cursor.length()>256) throw new IllegalArgumentException("Pesquisa inválida");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        String format=type.equals("all")?"":",objectTypes:["+(type.equals("series")?"SHOW":"MOVIE")+"]";
        String graph="query($title:String!){popularTitles(country:\"BR\",filter:{searchQuery:$title"+format+"},first:40,after:"+(cursor.isEmpty()?"null":JSONObject.quote(cursor))+"){pageInfo{hasNextPage endCursor}edges{node{__typename content(country:\"BR\",language:\"pt-BR\"){title originalTitle originalReleaseYear posterUrl scoring{imdbScore} externalIds{imdbId}}}}}}";
        String body=new JSONObject().put("query",graph).put("variables",new JSONObject().put("title",query)).toString();
        java.util.concurrent.ExecutorService pool=Executors.newFixedThreadPool(3);
        java.util.concurrent.Future<SearchPage> brazil=pool.submit(() -> parseSearchPage(NativeTrailerSource.request("https://apis.justwatch.com/graphql",body),type));
        List<java.util.concurrent.Future<List<MediaItem>>> originals=new ArrayList<>();
        if (cursor.isEmpty()) {
            String encoded=java.net.URLEncoder.encode(query,"UTF-8").replace("+","%20");
            for (String kind:type.equals("all")?new String[]{"movie","series"}:new String[]{type}) originals.add(pool.submit(() -> parseDiscoveryCatalog(NativeTrailerSource.request("https://v3-cinemeta.strem.io/catalog/"+kind+"/top/search="+encoded+".json",null),kind)));
        }
        try {
            SearchPage local=null; List<MediaItem> original=new ArrayList<>(); Exception failure=null;
            try { local=brazil.get(); } catch (java.util.concurrent.ExecutionException e) { failure=e; }
            for (java.util.concurrent.Future<List<MediaItem>> task:originals) try { original=appendUnique(original,task.get()); } catch (java.util.concurrent.ExecutionException e) { failure=e; }
            if (local==null&&original.isEmpty()&&failure!=null) throw failure;
            List<MediaItem> localized=local==null?Collections.emptyList():local.items;
            for (MediaItem item:localized) cacheTitle(item);
            return new SearchPage(mergeSearch(query,localized,original),local==null?"":local.cursor,local!=null&&local.more&&!local.cursor.equals(cursor));
        } finally { brazil.cancel(true); for (java.util.concurrent.Future<?> task:originals) task.cancel(true); pool.shutdownNow(); }
    }
    static List<MediaItem> mixedSearch(String query,List<MediaItem> movies,List<MediaItem> series) throws Exception {
        List<MediaItem> all=new ArrayList<>(); for (int i=0;i<Math.max(movies.size(),series.size());i++) { if (i<movies.size()) all.add(movies.get(i)); if (i<series.size()) all.add(series.get(i)); }
        return mergeSearch(query,all,java.util.Collections.emptyList());
    }
    static List<MediaItem> parseBrazilianSearch(String response,String type) throws Exception {
        JSONObject root=new JSONObject(response),data=root.optJSONObject("data"),popular=data==null?null:data.optJSONObject("popularTitles");
        JSONArray edges=popular==null?null:popular.optJSONArray("edges");
        if (root.has("errors")||edges==null) throw new java.io.IOException("Brazilian title search unavailable");
        List<MediaItem> found=new ArrayList<>();
        for (int i=0;i<edges.length();i++) {
            JSONObject edge=edges.optJSONObject(i),node=edge==null?null:edge.optJSONObject("node"),content=node==null?null:node.optJSONObject("content");
            if (content==null) continue;
            String kind="Show".equals(node.optString("__typename"))?"series":"Movie".equals(node.optString("__typename"))?"movie":"";
            if (kind.isEmpty()||(!type.equals("all")&&!type.equals(kind))) continue;
            JSONObject ids=content.optJSONObject("externalIds"); String id=ids==null?"":ids.optString("imdbId"),title=content.optString("title","").trim();
            if (!id.matches("tt\\d{5,12}")||title.isEmpty()) continue;
            JSONObject scoring=content.optJSONObject("scoring"); double score=scoring==null?0:scoring.optDouble("imdbScore",0);
            MediaItem item=new MediaItem(id,title,kind,score>0&&score<=10?String.valueOf(score):"",content.optString("originalReleaseYear",""),"","","https://images.metahub.space/background/medium/"+id+"/img","https://images.metahub.space/poster/medium/"+id+"/img","","",100-i);
            item.originalTitle=content.optString("originalTitle","").trim(); item.titleInPtBr=true; item.posterPtBrUrl=brazilianPoster(content.optString("posterUrl","")); item.posterPtBrChecked=true; found.add(item);
        }
        return found;
    }
    static List<MediaItem> mergeSearch(String query,List<MediaItem> local,List<MediaItem> original) throws Exception {
        java.util.LinkedHashMap<String,MediaItem> found=new java.util.LinkedHashMap<>();
        for (MediaItem item:local) found.putIfAbsent(item.imdbId,item);
        for (MediaItem item:original) {
            MediaItem br=found.get(item.imdbId);
            found.put(item.imdbId,br==null?item:MediaItem.fromJson(item.toJson().put("title",br.title).put("originalTitle",br.originalTitle).put("titleInPtBr",true).put("posterPtBrUrl",br.posterPtBrUrl).put("posterPtBrChecked",br.posterPtBrChecked)));
        }
        List<MediaItem> ordered=new ArrayList<>(found.values()); String target=WatchNavigation.normalize(query);
        ordered.sort(java.util.Comparator.comparingInt(item -> WatchNavigation.normalize(item.title).equals(target)||WatchNavigation.normalize(item.originalTitle).equals(target)?0:1));
        return ordered;
    }
    private MediaItem metadataDetails(MediaItem item) throws Exception {
        String key=item.type+":"+item.imdbId;
        synchronized(imageLocks.computeIfAbsent("meta:"+key,k -> new Object())) {
            MediaItem known=metadataDetails.get(key); if (known!=null) return selectSearchDetails(item,known);
            JSONObject meta=new JSONObject(NativeTrailerSource.request("https://v3-cinemeta.strem.io/meta/"+item.type+"/"+item.imdbId+".json",null)).getJSONObject("meta");
            if (!item.type.equals(meta.optString("type",item.type))) throw new java.io.IOException("Tipo de título incompatível");
            List<MediaItem> detailed=parseDiscoveryCatalog(new JSONObject().put("metas",new JSONArray().put(meta)).toString(),item.type);
            if (detailed.isEmpty()||!detailed.get(0).imdbId.equals(item.imdbId)) throw new java.io.IOException("Metadados incompatíveis");
            metadataDetails.put(key,detailed.get(0)); return selectSearchDetails(item,detailed.get(0));
        }
    }
    MediaItem ratingDetails(MediaItem item) throws Exception {
        if (!item.genresPt.trim().isEmpty()) return item;
        try { return metadataDetails(item); }
        catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            android.util.Log.w("TVHomeSearch","Keeping selected catalog title; extra metadata unavailable",e); return item;
        }
    }
    static MediaItem selectSearchDetails(MediaItem item,MediaItem detail) throws Exception {
        if (detail==null||!item.imdbId.equals(detail.imdbId)||!item.type.equals(detail.type)) return item;
        return !item.titleInPtBr&&item.originalTitle.isEmpty()?detail:MediaItem.fromJson(detail.toJson().put("title",item.title).put("originalTitle",item.originalTitle).put("titleInPtBr",item.titleInPtBr).put("posterPtBrUrl",item.posterPtBrUrl).put("posterPtBrChecked",item.posterPtBrChecked));
    }

    static boolean applyBrazilianTitle(MediaItem item,MediaItem brazilian) {
        if (brazilian==null||!item.imdbId.equals(brazilian.imdbId)||!item.type.equals(brazilian.type)||brazilian.title.trim().isEmpty()) return false;
        item.title=brazilian.title; item.originalTitle=brazilian.originalTitle; item.titleInPtBr=true;
        if (item.ratingLabel(false).equals("Sem nota")&&!brazilian.ratingLabel(false).equals("Sem nota")) item.rating=brazilian.rating;
        if (brazilian.posterPtBrChecked) { item.posterPtBrUrl=brazilian.posterPtBrUrl; item.posterPtBrChecked=true; }
        return true;
    }
    private void cacheTitle(MediaItem item) {
        ctx.getSharedPreferences("titles_br_v1",Context.MODE_PRIVATE).edit().putString(item.imdbId,item.toJson().toString()).apply();
    }
    void bindTitle(android.widget.TextView heading,android.widget.TextView original,MediaItem item) {
        original.setIncludeFontPadding(false);
        original.setPadding(original.getPaddingLeft(),0,original.getPaddingRight(),Math.round(4*ctx.getResources().getDisplayMetrics().density));
        heading.setTag(item);
        Runnable bind=() -> { if (heading.getTag()!=item) return; heading.setText(item.title); original.setText(item.originalSubtitle()); original.setVisibility(item.originalSubtitle().isEmpty()?android.view.View.GONE:android.view.View.VISIBLE); };
        localizeTitle(item,bind); bind.run();
    }
    void localizeTitle(MediaItem item,Runnable updated) {
        if (item==null) return;
        if (item.titleInPtBr&&item.posterPtBrChecked) { if (updated!=null) updated.run(); return; }
        try { String cached=ctx.getSharedPreferences("titles_br_v1",Context.MODE_PRIVATE).getString(item.imdbId,""); if (!cached.isEmpty()) applyBrazilianTitle(item,MediaItem.fromJson(new JSONObject(cached))); } catch (Exception ignored) { }
        if (item.titleInPtBr&&item.posterPtBrChecked) { if (updated!=null) updated.run(); return; }
        synchronized(titleCallbacks) {
            if (titlesClosed) return;
            if (titleCallbacks.containsKey(item.imdbId)) { if (updated!=null) titleCallbacks.get(item.imdbId).add(() -> { try { String cached=ctx.getSharedPreferences("titles_br_v1",Context.MODE_PRIVATE).getString(item.imdbId,""); if (!cached.isEmpty()) applyBrazilianTitle(item,MediaItem.fromJson(new JSONObject(cached))); } catch (Exception ignored) { } updated.run(); }); return; }
            if (System.currentTimeMillis()-titleAttempts.getOrDefault(item.imdbId,0L)<300_000) { if (updated!=null) updated.run(); return; }
            titleAttempts.put(item.imdbId,System.currentTimeMillis()); List<Runnable> callbacks=new ArrayList<>(); if (updated!=null) callbacks.add(updated); titleCallbacks.put(item.imdbId,callbacks);
            titlePool.execute(() -> {
                try {
                    String graph="query($title:String!){popularTitles(country:\"BR\",filter:{searchQuery:$title},first:40){edges{node{__typename content(country:\"BR\",language:\"pt-BR\"){title originalTitle originalReleaseYear posterUrl scoring{imdbScore} externalIds{imdbId}}}}}}";
                    String body=new JSONObject().put("query",graph).put("variables",new JSONObject().put("title",item.title)).toString();
                    for (MediaItem candidate:parseBrazilianSearch(NativeTrailerSource.request("https://apis.justwatch.com/graphql",body),item.type)) if (applyBrazilianTitle(item,candidate)) { cacheTitle(item); break; }
                } catch (Exception e) { android.util.Log.d("TVHomeTitles","Keeping original title: "+item.imdbId); }
                List<Runnable> callbacksDone; synchronized(titleCallbacks) { callbacksDone=titleCallbacks.remove(item.imdbId); }
                if (callbacksDone!=null) for (Runnable callback:callbacksDone) mainHandler.post(callback);
            });
        }
    }

    private void loadSeedOrCached() {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_JSON, null);
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONObject root = new JSONObject(raw);
                List<MediaItem> movies = parseArray(root.optJSONArray("movies"));
                List<MediaItem> series = parseArray(root.optJSONArray("series"));
                if (movies.size() >= 6 && series.size() >= 6) {
                    rebuildCategories(movies, series);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        rebuildCategories(getSeedMovies(), getSeedSeries());
    }

    private static List<MediaItem> parseArray(JSONArray arr) {
        List<MediaItem> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(MediaItem.fromJson(o));
        }
        return out;
    }

    private synchronized void rebuildCategories(List<MediaItem> movies, List<MediaItem> series) {
        // Sort copies for Novidades (by release date / year descending)
        List<MediaItem> movNew = new ArrayList<>(movies);
        List<MediaItem> serNew = new ArrayList<>(series);
        Collections.sort(movNew, (a, b) -> b.releasedIso.compareTo(a.releasedIso));
        Collections.sort(serNew, (a, b) -> b.releasedIso.compareTo(a.releasedIso));

        // Sort copies for Em Alta (by popularity + IMDb rating descending)
        List<MediaItem> movHot = new ArrayList<>(movies);
        List<MediaItem> serHot = new ArrayList<>(series);
        Collections.sort(movHot, (a, b) -> Double.compare(b.popularity, a.popularity));
        Collections.sort(serHot, (a, b) -> Double.compare(b.popularity, a.popularity));

        listNovidades.clear();
        // Guarantees every trio of 3 cards has BOTH Movies and Series ([M, S, M], [S, M, S], ...)
        listNovidades.addAll(buildMixedTrios(movNew, serNew, true));

        listEmAlta.clear();
        // Guarantees every trio of 3 cards has BOTH Series and Movies ([S, M, S], [M, S, M], ...)
        listEmAlta.addAll(buildMixedTrios(movHot, serHot, false));

        allMovies.clear();
        allMovies.addAll(movies);
        allSeries.clear();
        allSeries.addAll(series);
        recommendations.clear();
        recommendationVersion=""; recommendationGeneration++; if (pendingRecommendations!=null) pendingRecommendations.cancel(true);
        if (profiles!=null) { profiles.observeCatalog(movies); profiles.observeCatalog(series); }
    }

    public synchronized void persistCatalog() {
        try {
            JSONObject cacheObj = new JSONObject();
            JSONArray mArr = new JSONArray();
            for (MediaItem m : allMovies) mArr.put(m.toJson());
            JSONArray sArr = new JSONArray();
            for (MediaItem s : allSeries) sArr.put(s.toJson());
            cacheObj.put("movies", mArr);
            cacheObj.put("series", sArr);
            ctx.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_JSON, cacheObj.toString())
                    .putLong(KEY_TS, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Builds groups of 3 items where EVERY trio of 3 cards is guaranteed to contain both Movies and Series.
     * Trio pattern alternates: [Movie, Series, Movie] then [Series, Movie, Series].
     */
    private static List<MediaItem> buildMixedTrios(List<MediaItem> movies, List<MediaItem> series, boolean startWithTwoMovies) {
        List<MediaItem> out = new ArrayList<>();
        if (movies.isEmpty()) return new ArrayList<>(series);
        if (series.isEmpty()) return new ArrayList<>(movies);
        int mIdx = 0;
        int sIdx = 0;
        int totalTrios = (movies.size() + series.size() + 2) / 3;
        Set<String> used = new HashSet<>();

        for (int t = 0; t < totalTrios; t++) {
            boolean twoMovies = (t % 2 == 0) == startWithTwoMovies;
            if (twoMovies) {
                MediaItem m1 = nextItem(movies, mIdx++, used);
                MediaItem s1 = nextItem(series, sIdx++, used);
                MediaItem m2 = nextItem(movies, mIdx++, used);
                if (!out.contains(m1)) out.add(m1);
                if (!out.contains(s1)) out.add(s1);
                if (!out.contains(m2)) out.add(m2);
            } else {
                MediaItem s1 = nextItem(series, sIdx++, used);
                MediaItem m1 = nextItem(movies, mIdx++, used);
                MediaItem s2 = nextItem(series, sIdx++, used);
                if (!out.contains(s1)) out.add(s1);
                if (!out.contains(m1)) out.add(m1);
                if (!out.contains(s2)) out.add(s2);
            }
        }
        for (MediaItem item : movies) if (!out.contains(item)) out.add(item);
        for (MediaItem item : series) if (!out.contains(item)) out.add(item);
        return out;
    }

    private static MediaItem nextItem(List<MediaItem> list, int startIndex, Set<String> used) {
        int n = list.size();
        for (int i = 0; i < n; i++) {
            MediaItem cand = list.get((startIndex + i) % n);
            if (used.add(cand.imdbId)) {
                return cand;
            }
        }
        return list.get(startIndex % n);
    }

    public synchronized void refreshFromNetworkIfNeeded(final Runnable onUpdated) {
        if (refreshing) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE);
        long lastTs = prefs.getLong(KEY_TS, 0L);
        int currentYear=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (System.currentTimeMillis() - lastTs < REFRESH_INTERVAL_MS && prefs.contains(KEY_JSON)&&prefs.getInt("release_year",0)==currentYear&&prefs.getInt("recommendation_catalog_version",0)==2) {
            return;
        }
        refreshing=true;
        new Thread(() -> {
            try {
                List<MediaItem> netMovies = fetchCinemetaCatalog("movie");
                List<MediaItem> netSeries = fetchCinemetaCatalog("series");
                int year=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                try { netMovies=mergeLists(parseDiscoveryCatalog(NativeTrailerSource.request("https://v3-cinemeta.strem.io/catalog/movie/year/genre="+year+"&skip=0.json",null),"movie"),netMovies); } catch (Exception e) { android.util.Log.w("TVHomeMedia","Current movie releases unavailable",e); }
                try { netSeries=mergeLists(parseDiscoveryCatalog(NativeTrailerSource.request("https://v3-cinemeta.strem.io/catalog/series/year/genre="+year+"&skip=0.json",null),"series"),netSeries); } catch (Exception e) { android.util.Log.w("TVHomeMedia","Current series releases unavailable",e); }
                if (netMovies.size() >= 8 && netSeries.size() >= 8) {
                    // Combine with curated PT-BR hits so classic/popular Brazilian titles are always rich
                    List<MediaItem> mergedMovies = mergeLists(netMovies, getSeedMovies());
                    List<MediaItem> mergedSeries = mergeLists(netSeries, getSeedSeries());

                    JSONObject cacheObj = new JSONObject();
                    JSONArray mArr = new JSONArray();
                    for (MediaItem m : mergedMovies) mArr.put(m.toJson());
                    JSONArray sArr = new JSONArray();
                    for (MediaItem s : mergedSeries) sArr.put(s.toJson());
                    cacheObj.put("movies", mArr);
                    cacheObj.put("series", sArr);

                    prefs.edit()
                            .putString(KEY_JSON, cacheObj.toString())
                            .putLong(KEY_TS, System.currentTimeMillis())
                            .putInt("release_year",currentYear)
                            .putInt("recommendation_catalog_version",2)
                            .apply();

                    rebuildCategories(mergedMovies, mergedSeries);
                    if (onUpdated != null) {
                        mainHandler.post(onUpdated);
                    }
                }
            } catch (Exception e) { android.util.Log.w("TVHomeMedia","Keeping cached catalog",e); }
            finally { synchronized(MediaRepository.this) { refreshing=false; } }
        }).start();
    }

    static List<MediaItem> mergeLists(List<MediaItem> primary, List<MediaItem> fallback) {
        List<MediaItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String,MediaItem> localized=new HashMap<>(); for (MediaItem item:fallback) if (item.titleInPtBr) localized.put(item.imdbId,item);
        for (MediaItem item : primary) {
            MediaItem br=localized.get(item.imdbId);
            if (br!=null&&!item.titleInPtBr&&br.type.equals(item.type)) {
                item.originalTitle=item.originalTitle.isEmpty()?item.title:item.originalTitle; item.title=br.title; item.titleInPtBr=true;
            }
            if (seen.add(item.imdbId)) out.add(item);
        }
        for (MediaItem item : fallback) {
            if (seen.add(item.imdbId)) out.add(item);
        }
        return out;
    }

    public interface SynopsisCallback {
        void onSynopsisReady(String fullPt);
    }

    private List<MediaItem> fetchCinemetaCatalog(String type) throws Exception {
        return parseDiscoveryCatalog(NativeTrailerSource.request("https://v3-cinemeta.strem.io/catalog/"+type+"/top/skip=0.json",null),type);
    }

    public String translateToPtBr(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        try {
            String clean = text.replace("\r", " ").replace("\n", " ").trim();
            String q = URLEncoder.encode(clean, "UTF-8");
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt-BR&dt=t&q=" + q;
            return parsePtBrTranslation(NativeTrailerSource.request(url,null),clean);
        } catch (Exception ignored) {
        }
        return "";
    }
    static String parsePtBrTranslation(String response,String original) throws Exception {
        JSONArray outer=new JSONArray(response),parts=outer.optJSONArray(0); if (parts==null) return "";
        StringBuilder text=new StringBuilder();
        for (int i=0;i<parts.length();i++) { JSONArray segment=parts.optJSONArray(i); if (segment!=null) text.append(segment.optString(0,"")); }
        String translated=text.toString().trim();
        if (translated.equals(original.trim())&&!outer.optString(2,"").toLowerCase(Locale.ROOT).startsWith("pt")) return "";
        return translated;
    }
    String localizedSynopsis(MediaItem item) {
        if (item.visibleSynopsis().isEmpty()) {
            String saved=ctx.getSharedPreferences(PREFS_CACHE,Context.MODE_PRIVATE).getString("synopsis_br_v2_"+item.imdbId,"");
            if (!saved.isEmpty()) { item.synopsisPt=saved; item.synopsisInPtBr=true; }
        }
        return item.visibleSynopsis();
    }

    public void ensureFullSynopsisPt(final MediaItem item, final SynopsisCallback cb) {
        if (item == null || item.imdbId == null || item.imdbId.isEmpty()) return;

        localizedSynopsis(item);
        boolean incomplete = !item.synopsisInPtBr || item.synopsisPt == null
                || item.synopsisPt.trim().isEmpty()
                || item.synopsisPt.endsWith("...")
                || item.synopsisPt.endsWith("…");

        if (!incomplete) {
            if (cb!=null) mainHandler.post(() -> cb.onSynopsisReady(item.visibleSynopsis()));
            return;
        }

        new Thread(() -> {
            String fullDesc=item.synopsisPt==null?"":item.synopsisPt;
            try {
                if (fullDesc.trim().isEmpty()||fullDesc.endsWith("...")||fullDesc.endsWith("…")) {
                    try {
                        String metaUrl = "https://v3-cinemeta.strem.io/meta/" + item.type + "/" + item.imdbId + ".json";
                        String metaBody = httpGet(metaUrl, 6000);
                        JSONObject metaObj = new JSONObject(metaBody).optJSONObject("meta");
                        if (metaObj != null) {
                            fullDesc = metaObj.optString("description", "").replace("\n", " ").trim();
                            if (item.trailerYtId == null || item.trailerYtId.isEmpty()) {
                                String tId = metaObj.optString("trailerYtId", "");
                                if (tId.isEmpty()) {
                                    JSONArray trailers = metaObj.optJSONArray("trailers");
                                    if (trailers != null && trailers.length() > 0) {
                                        for (int i = 0; i < trailers.length(); i++) {
                                            JSONObject t = trailers.optJSONObject(i);
                                            if (t != null && !t.optString("source", "").isEmpty()) {
                                                tId = t.optString("source", "");
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (!tId.isEmpty()) {
                                    item.trailerYtId = tId;
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                }

                String pt="";
                try { pt=brazilianSynopsis(item); } catch (Exception ignored) { }
                if (pt.isEmpty()) pt=translateToPtBr(fullDesc);
                if (pt != null && !pt.trim().isEmpty()) {
                    item.synopsisPt = pt.trim();
                    item.synopsisInPtBr=true;
                    ctx.getSharedPreferences(PREFS_CACHE,Context.MODE_PRIVATE).edit().putString("synopsis_br_v2_"+item.imdbId,item.synopsisPt).apply();
                    persistCatalog();
                }
            } catch (Exception ignored) {
            }
            if (cb!=null) mainHandler.post(() -> cb.onSynopsisReady(item.visibleSynopsis()));
        }).start();
    }
    private String brazilianSynopsis(MediaItem item) throws Exception {
        String query="query($title:String!){popularTitles(country:\"BR\",filter:{searchQuery:$title},first:8){edges{node{content(country:\"BR\",language:\"pt-BR\"){externalIds{imdbId} shortDescription}}}}}";
        JSONObject request=new JSONObject().put("query",query).put("variables",new JSONObject().put("title",item.title));
        return parseBrazilianSynopsis(NativeTrailerSource.request("https://apis.justwatch.com/graphql",request.toString()),item.imdbId);
    }
    static String parseBrazilianSynopsis(String response,String imdb) throws Exception {
        JSONObject data=new JSONObject(response).optJSONObject("data"),popular=data==null?null:data.optJSONObject("popularTitles");
        JSONArray edges=popular==null?null:popular.optJSONArray("edges"); if (edges==null) return "";
        for (int i=0;i<edges.length();i++) { JSONObject edge=edges.optJSONObject(i),node=edge==null?null:edge.optJSONObject("node"),content=node==null?null:node.optJSONObject("content");
            JSONObject ids=content==null?null:content.optJSONObject("externalIds");
            if (ids!=null&&imdb.equals(ids.optString("imdbId"))) return content.optString("shortDescription","").trim(); }
        return "";
    }

    private String httpGet(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString();
    }

    // ---- Async Image Loading with Memory + Disk Cache --------------------------------------------

    static List<Integer> discoveryPrefetchIndices(int size,int first,int last,int direction) {
        List<Integer> indices=new ArrayList<>();
        if (direction<0) {
            for (int i=Math.min(size,first)-1;i>=Math.max(0,first-DiscoveryGrid.PAGE_SIZE);i--) indices.add(i);
        } else {
            for (int i=Math.max(0,last+1);i<Math.min(size,last+1+DiscoveryGrid.PAGE_SIZE);i++) indices.add(i);
        }
        return indices;
    }
    void prefetchDiscovery(List<MediaItem> items,int first,int last,int direction) {
        if (discoveryImagePool.isShutdown()) return;
        final int generation=++prefetchGeneration;
        List<MediaItem> upcoming=new ArrayList<>();
        for (int index:discoveryPrefetchIndices(items.size(),first,last,direction)) upcoming.add(items.get(index));
        discoveryImagePool.execute(() -> {
            for (MediaItem item:upcoming) {
                if (generation!=prefetchGeneration) return;
                localizeTitle(item,() -> { if (!discoveryImagePool.isShutdown()) discoveryImagePool.execute(() -> { if (generation!=prefetchGeneration) return; cachedArtwork(portraitPrimary(item),portraitFallback(item),320,480); }); });
            }
        });
    }
    void prepareDiscoveryImages(List<MediaItem> items) throws InterruptedException {
        java.util.concurrent.CompletionService<Boolean> completed=new java.util.concurrent.ExecutorCompletionService<>(imagePool);
        List<java.util.concurrent.Future<Boolean>> tasks=new ArrayList<>(); int ready=0;
        try {
            for (int i=0;i<Math.min(DiscoveryGrid.PAGE_SIZE*3,items.size());i++) { final MediaItem item=items.get(i); tasks.add(completed.submit(() -> cachedArtwork(portraitPrimary(item),portraitFallback(item),320,480)!=null)); }
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            for (int i=0;i<tasks.size();i++) {
                long remaining=end-System.nanoTime(); if (remaining<=0) break;
                java.util.concurrent.Future<Boolean> result=completed.poll(remaining,java.util.concurrent.TimeUnit.NANOSECONDS); if (result==null) break;
                try { if (result.get()) ready++; } catch (java.util.concurrent.ExecutionException ignored) { }
            }
        } finally { for (java.util.concurrent.Future<Boolean> task:tasks) task.cancel(true); }
        android.util.Log.d("TVHomeDiscover","ARTWORK ready="+ready+" requested="+tasks.size());
    }
    void cancelDiscoveryPrefetch() { prefetchGeneration++; }
    void destroyDiscoveryPrefetch() { imagesClosed=true; imagePool.shutdownNow(); mainHandler.removeCallbacksAndMessages(null); prefetchGeneration++; discoveryImagePool.shutdownNow(); synchronized(this) { recommendationGeneration++; recommendationsUpdated=null; recommendationPool.shutdownNow(); } synchronized(titleCallbacks) { titlesClosed=true; titleCallbacks.clear(); titlePool.shutdownNow(); } }

    private Bitmap cachedImage(String url,int width,int height) throws Exception {
        String key=url+"_"+width+"x"+height;
        Bitmap cached=memCache.get(key); if (cached!=null&&!cached.isRecycled()) return cached;
        synchronized(imageLocks.computeIfAbsent(url,k -> new Object())) {
            cached=memCache.get(key); if (cached!=null&&!cached.isRecycled()) return cached;
            File diskFile=new File(diskCacheDir,"img_"+Math.abs(url.hashCode())+".jpg");
            for (int attempt=0;attempt<2;attempt++) {
                if (!diskFile.exists()||diskFile.length()<512) {
                    HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
                    connection.setRequestProperty("User-Agent","Mozilla/5.0"); connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
                    File partial=new File(diskCacheDir,diskFile.getName()+".part");
                    try {
                        try (InputStream input=connection.getInputStream(); FileOutputStream output=new FileOutputStream(partial)) {
                            byte[] buffer=new byte[8192]; int count;
                            while ((count=input.read(buffer))!=-1) output.write(buffer,0,count);
                        }
                        if (!partial.renameTo(diskFile)) throw new java.io.IOException("Image cache unavailable");
                    } finally { connection.disconnect(); if (partial.exists()) partial.delete(); }
                }
                Bitmap bitmap=decodeScaledBitmap(diskFile.getAbsolutePath(),width,height);
                if (bitmap!=null) { memCache.put(key,bitmap); return bitmap; }
                diskFile.delete();
            }
            return null;
        }
    }

    private Bitmap cachedArtwork(String primary,String fallback,int width,int height) {
        String key=primary+"|"+fallback+"_"+width+"x"+height;
        Bitmap cached=memCache.get(key); if (cached!=null&&!cached.isRecycled()) return cached;
        for (String url:primary!=null&&primary.equals(fallback)?new String[]{primary}:new String[]{primary,fallback}) {
            if (url==null||url.isEmpty()) continue;
            try {
                Bitmap bitmap=cachedImage(url,width,height);
                if (bitmap!=null) { memCache.put(key,bitmap); return bitmap; }
            } catch (Exception failure) { android.util.Log.w("TVHomeArtwork","Artwork unavailable: "+url+" ("+failure.getClass().getSimpleName()+")"); }
        }
        return null;
    }

    public void loadImage(final String url, final ImageView target, final int reqWidth, final int reqHeight) {
        loadImage(url,null,target,reqWidth,reqHeight);
    }
    static String brazilianPoster(String path) {
        if (path==null||!path.startsWith("/poster/")||path.contains("..")) return "";
        return "https://images.justwatch.com"+path.replace("{profile}","s332").replace("{format}","jpg");
    }
    static String portraitPrimary(MediaItem item) { return item.posterPtBrUrl.isEmpty()?item.posterUrl:item.posterPtBrUrl; }
    static String portraitFallback(MediaItem item) { return item.posterPtBrUrl.isEmpty()?item.backdropUrl:item.posterUrl; }
    void loadGridArtwork(MediaItem item,ImageView target,Runnable localized) {
        if (target.getTag(R.id.discoveryArtwork)==item) {
            if (target.getTag() instanceof ArtworkRequest) { resumeArtwork(target); if (localized!=null) localized.run(); return; }
            if (Boolean.TRUE.equals(target.getTag(R.id.artworkLoaded))) { if (localized!=null) localized.run(); return; }
        }
        target.setTag(R.id.discoveryArtwork,item); target.setTag(R.id.artworkLoaded,null); target.animate().cancel(); target.setAlpha(1f);
        target.setImageResource(R.drawable.artwork_placeholder); target.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        String primary=portraitPrimary(item),fallback=portraitFallback(item);
        loadArtwork(item,true,target,320,480);
        localizeTitle(item,() -> {
            if (target.getTag(R.id.discoveryArtwork)!=item) return;
            if (!primary.equals(portraitPrimary(item))||!fallback.equals(portraitFallback(item))) loadArtwork(item,true,target,320,480);
            if (localized!=null) localized.run();
        });
    }
    private static final class ArtworkRequest {
        boolean running=true; long nextAttempt; Runnable retry;
    }
    private void observeArtworkAttach(ImageView target) {
        if (target.getTag(R.id.artworkAttachListener)!=null) return;
        target.setTag(R.id.artworkAttachListener,Boolean.TRUE);
        target.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View view) { resumeArtwork(target); }
            @Override public void onViewDetachedFromWindow(android.view.View view) { }
        });
    }
    void resumeArtwork(ImageView target) {
        if (target==null||imagesClosed||!target.isShown()||target.getWindowVisibility()!=android.view.View.VISIBLE) return;
        Object value=target.getTag(); long now=android.os.SystemClock.uptimeMillis();
        if (value instanceof ArtworkRequest) {
            ArtworkRequest request=(ArtworkRequest)value;
            if (!request.running&&request.retry!=null&&now>=request.nextAttempt) request.retry.run();
        }
        HomeArtworkRequest home=(HomeArtworkRequest)target.getTag(R.id.homeArtworkRequest);
        if (home!=null&&homeArtworkCurrent(home)&&!home.running&&!HomeArtworkPolicy.adequate(home.quality)&&now>=home.nextAttempt) upgradeHomeArtwork(home);
    }
    private static final class HomeArtworkRequest {
        final MediaItem item; final ImageView target; final int width,height; final boolean landscape,reloadSource;
        final String primary,fallback;
        volatile boolean cancelled; boolean running; int attempt; float quality; long nextAttempt; Runnable updated;
        HomeArtworkRequest(MediaItem item,ImageView target,int width,int height,boolean landscape,boolean reloadSource) {
            this.item=item; this.target=target; this.width=width; this.height=height; this.landscape=landscape; this.reloadSource=reloadSource;
            primary=HomeArtworkPolicy.primary(item,landscape); fallback=HomeArtworkPolicy.fallback(item,landscape);
        }
        boolean sourcesChanged() { return !primary.equals(HomeArtworkPolicy.primary(item,landscape))||!fallback.equals(HomeArtworkPolicy.fallback(item,landscape)); }
    }
    private String[] homeArtworkUrls(MediaItem item,boolean landscape) {
        String primary=HomeArtworkPolicy.primary(item,landscape),fallback=HomeArtworkPolicy.fallback(item,landscape);
        return new String[]{HomeArtworkPolicy.highResolution(primary),primary,HomeArtworkPolicy.highResolution(fallback),fallback};
    }
    Bitmap peekHomeArtwork(MediaItem item,int width,int height,boolean landscape) {
        width=Math.max(landscape?1:480,Math.min(1920,width)); height=Math.max(1,Math.min(1080,height));
        Bitmap best=null; float quality=0;
        for (String url:homeArtworkUrls(item,landscape)) {
            Bitmap candidate=memCache.get(url+"_"+width+"x"+height);
            if (candidate==null||candidate.isRecycled()) continue;
            float next=HomeArtworkPolicy.quality(candidate.getWidth(),candidate.getHeight(),width,height);
            if (next>quality) { best=candidate; quality=next; }
        }
        return best;
    }
    void loadHomeArtwork(MediaItem item,ImageView target,int width,int height,boolean landscape,Runnable localized) {
        if (imagesClosed) return;
        observeArtworkAttach(target);
        width=Math.max(landscape?1:480,Math.min(1920,width)); height=Math.max(1,Math.min(1080,height));
        HomeArtworkRequest request=(HomeArtworkRequest)target.getTag(R.id.homeArtworkRequest);
        if (request==null||request.item!=item||request.width!=width||request.height!=height||request.landscape!=landscape||request.sourcesChanged()) {
            boolean sameRole=request!=null&&request.landscape==landscape;
            boolean sourceChanged=request!=null&&request.item==item&&request.sourcesChanged();
            if (request!=null) request.cancelled=true;
            boolean same=target.getTag(R.id.discoveryArtwork)==item;
            request=new HomeArtworkRequest(item,target,width,height,landscape,sourceChanged);
            Bitmap bitmap=HomeArtworkFade.bitmapOf(target.getDrawable());
            if (same&&sameRole&&!sourceChanged&&bitmap!=null) {
                request.quality=HomeArtworkPolicy.quality(bitmap.getWidth(),bitmap.getHeight(),width,height);
            } else if (!same) { target.setImageResource(R.drawable.artwork_placeholder); target.setScaleType(ImageView.ScaleType.CENTER_INSIDE); }
            target.setTag(new Object()); target.setTag(R.id.discoveryArtwork,item); target.setTag(R.id.homeArtworkRequest,request);
            target.setTag(R.id.artworkLoaded,request.quality>0||same&&bitmap!=null?Boolean.TRUE:null);
        }
        request.updated=localized;
        final HomeArtworkRequest current=request;
        Bitmap ready=current.reloadSource?null:peekHomeArtwork(item,width,height,landscape);
        if (ready!=null) publishHomeArtwork(current,ready);
        if (!current.running&&!HomeArtworkPolicy.adequate(current.quality)&&android.os.SystemClock.uptimeMillis()>=current.nextAttempt) upgradeHomeArtwork(current);
        localizeTitle(item,() -> {
            if (!homeArtworkCurrent(current)) return;
            if (current.sourcesChanged()) { loadHomeArtwork(item,target,current.width,current.height,landscape,localized); return; }
            if (localized!=null) localized.run();
            if (!current.running&&!HomeArtworkPolicy.adequate(current.quality)&&android.os.SystemClock.uptimeMillis()>=current.nextAttempt) upgradeHomeArtwork(current);
        });
    }
    private boolean homeArtworkCurrent(HomeArtworkRequest request) { return !imagesClosed&&!request.cancelled&&request.target.getTag(R.id.homeArtworkRequest)==request; }
    private void upgradeHomeArtwork(HomeArtworkRequest request) {
        if (!homeArtworkCurrent(request)||request.running) return;
        request.running=true;
        imagePool.execute(() -> {
            java.util.LinkedHashSet<String> urls=new java.util.LinkedHashSet<>();
            String primary=HomeArtworkPolicy.primary(request.item,request.landscape),fallback=HomeArtworkPolicy.fallback(request.item,request.landscape);
            float best=request.quality;
            for (String url:request.reloadSource?new String[]{primary}:new String[]{primary,fallback}) {
                if (request.cancelled||imagesClosed) return;
                File file=new File(diskCacheDir,"img_"+Math.abs(url.hashCode())+".jpg");
                if (file.exists()) {
                    Bitmap preview=decodeScaledBitmap(file.getAbsolutePath(),request.width,request.height);
                    if (preview!=null) { float quality=HomeArtworkPolicy.quality(preview.getWidth(),preview.getHeight(),request.width,request.height); if (quality>best) { best=quality; publishHomeArtwork(request,preview); } }
                }
            }
            if (best==0) { urls.add(primary); urls.add(fallback); best=loadHomeCandidates(request,urls,best); urls.clear(); }
            urls.add(HomeArtworkPolicy.highResolution(primary)); urls.add(HomeArtworkPolicy.highResolution(fallback));
            urls.add(primary); urls.add(fallback);
            best=loadHomeCandidates(request,urls,best);
            if (!HomeArtworkPolicy.adequate(best)&&(best==0||request.attempt>0)&&!request.cancelled&&!imagesClosed) try {
                MediaItem detail=metadataDetails(request.item);
                if (detail.imdbId.equals(request.item.imdbId)) {
                    urls.clear(); urls.add(HomeArtworkPolicy.highResolution(request.landscape?detail.backdropUrl:detail.posterUrl)); urls.add(HomeArtworkPolicy.highResolution(request.landscape?detail.posterUrl:detail.backdropUrl));
                    best=loadHomeCandidates(request,urls,best);
                }
            } catch (Exception ignored) { }
            mainHandler.post(() -> {
                if (!homeArtworkCurrent(request)) return;
                request.running=false;
                if (HomeArtworkPolicy.adequate(request.quality)) return;
                request.target.setTag(R.id.artworkLoaded,request.quality>0?Boolean.TRUE:Boolean.FALSE);
                long delay=artworkRetryDelay(request.attempt++); request.nextAttempt=android.os.SystemClock.uptimeMillis()+delay;
                mainHandler.postDelayed(() -> {
                    if (homeArtworkCurrent(request)) resumeArtwork(request.target);
                },delay);
            });
        });
    }
    private float loadHomeCandidates(HomeArtworkRequest request,java.util.Set<String> urls,float best) {
        for (String url:urls) {
            if (request.cancelled||imagesClosed||HomeArtworkPolicy.adequate(best)) break;
            if (url==null||url.isEmpty()) continue;
            try {
                if (request.attempt>0) synchronized(imageLocks.computeIfAbsent(url,k -> new Object())) {
                    memCache.remove(url+"_"+request.width+"x"+request.height);
                    File source=new File(diskCacheDir,"img_"+Math.abs(url.hashCode())+".jpg");
                    BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeFile(source.getAbsolutePath(),bounds);
                    if (!HomeArtworkPolicy.adequate(HomeArtworkPolicy.quality(bounds.outWidth,bounds.outHeight,request.width,request.height))) source.delete();
                }
                Bitmap bitmap=cachedImage(url,request.width,request.height);
                if (bitmap==null) continue;
                float quality=HomeArtworkPolicy.quality(bitmap.getWidth(),bitmap.getHeight(),request.width,request.height);
                if (quality>best) { best=quality; publishHomeArtwork(request,bitmap); }
            } catch (Exception ignored) { }
        }
        return best;
    }
    private void publishHomeArtwork(HomeArtworkRequest request,Bitmap bitmap) {
        mainHandler.post(() -> {
            if (!homeArtworkCurrent(request)) return;
            float quality=HomeArtworkPolicy.quality(bitmap.getWidth(),bitmap.getHeight(),request.width,request.height);
            if (quality<=request.quality) return;
            request.quality=quality; request.target.setTag(R.id.artworkLoaded,Boolean.TRUE);
            android.graphics.drawable.Drawable next=HomeArtworkFade.transition(request.target.getDrawable(),bitmap);
            request.target.setImageDrawable(next);
            request.target.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (request.updated!=null) request.updated.run();
            android.util.Log.d("TVHomeArtwork","HOME "+request.item.imdbId+" "+bitmap.getWidth()+"x"+bitmap.getHeight()+" quality="+quality);
        });
    }
    void cancelHomeArtwork(ImageView target) {
        HomeArtworkRequest request=(HomeArtworkRequest)target.getTag(R.id.homeArtworkRequest);
        if (request!=null) request.cancelled=true;
        target.setTag(R.id.homeArtworkRequest,null);
    }
    void loadArtwork(MediaItem item,boolean portrait,ImageView target,int width,int height) {
        loadImage(item,portrait?portraitPrimary(item):item.backdropUrl,portrait?portraitFallback(item):item.posterUrl,target,width,height,0);
    }
    public void loadImage(final String url,final String fallback,final ImageView target,final int reqWidth,final int reqHeight) {
        loadImage(null,url,fallback,target,reqWidth,reqHeight,0);
    }
    private void loadImage(final MediaItem item,final String url,final String fallback,final ImageView target,final int reqWidth,final int reqHeight,final int attempt) {
        if (target==null||imagesClosed) return;
        observeArtworkAttach(target);
        boolean keep=item!=null&&target.getTag(R.id.discoveryArtwork)==item&&HomeArtworkFade.bitmapOf(target.getDrawable())!=null;
        final ArtworkRequest request=new ArtworkRequest(); target.setTag(request); if (!keep) target.setTag(R.id.artworkLoaded,null);
        if (item!=null) target.setTag(R.id.discoveryArtwork,item);
        target.animate().cancel(); target.setAlpha(1f);
        final String cacheKey = url+"|"+fallback+"_"+reqWidth+"x"+reqHeight;
        Bitmap cached = memCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            target.animate().cancel(); target.setAlpha(1f);
            if (item!=null) target.setScaleType(ImageView.ScaleType.CENTER_CROP);
            request.running=false; target.setTag(R.id.artworkLoaded,Boolean.TRUE); target.setImageBitmap(cached);
            return;
        }
        final ImageView.ScaleType scale=target.getScaleType()==ImageView.ScaleType.CENTER_INSIDE?ImageView.ScaleType.CENTER_CROP:target.getScaleType();
        if (item==null) target.setImageDrawable(null);
        else if (!keep) { target.setImageResource(R.drawable.artwork_placeholder); target.setScaleType(ImageView.ScaleType.CENTER_INSIDE); }
        imagePool.execute(() -> {
            Bitmap artwork=resolveArtwork(item,url,fallback,reqWidth,reqHeight);
            if (artwork!=null) memCache.put(cacheKey,artwork);
            final Bitmap bmp=artwork;
            mainHandler.post(() -> {
                if (target.getTag()!=request) return;
                request.running=false;
                if (bmp!=null) {
                    target.setTag(R.id.artworkLoaded,Boolean.TRUE); target.animate().cancel(); target.setScaleType(scale);
                    if (keep) { target.setAlpha(1f); target.setImageDrawable(HomeArtworkFade.transition(target.getDrawable(),bmp)); }
                    else { target.setAlpha(0f); target.setImageBitmap(bmp); target.animate().alpha(1f).setDuration(180).start(); }
                } else {
                    if (!keep) target.setTag(R.id.artworkLoaded,Boolean.FALSE);
                    long delay=artworkRetryDelay(attempt); request.nextAttempt=android.os.SystemClock.uptimeMillis()+delay;
                    request.retry=() -> { if (target.getTag()==request) loadImage(item,url,fallback,target,reqWidth,reqHeight,attempt+1); };
                    mainHandler.postDelayed(() -> { if (target.getTag()==request) resumeArtwork(target); },delay);
                }
            });
        });
    }
    // A retry only runs while the same visible card is still bound; sustained failures are limited to once per minute.
    static long artworkRetryDelay(int attempt) {
        return attempt==0?1800:attempt==1?4500:attempt==2?10000:attempt==3?30000:60000;
    }
    private Bitmap resolveArtwork(MediaItem item,String url,String fallback,int width,int height) {
        Bitmap bitmap=cachedArtwork(url,fallback,width,height);
        if (bitmap==null&&item!=null) try {
            MediaItem detail=metadataDetails(item); boolean portrait=height>width;
            bitmap=cachedArtwork(portrait?detail.posterUrl:detail.backdropUrl,portrait?detail.backdropUrl:detail.posterUrl,width,height);
        } catch (Exception failure) { android.util.Log.d("TVHomeArtwork","Metadata artwork unavailable: "+item.imdbId); }
        return bitmap;
    }
    void loadBackdrop(MediaItem item,int width,int height,java.util.function.Consumer<Bitmap> ready) {
        imagePool.execute(() -> { Bitmap bitmap=resolveArtwork(item,item.backdropUrl,portraitPrimary(item),width,height); mainHandler.post(() -> ready.accept(bitmap)); });
    }

    private static Bitmap decodeScaledBitmap(String path, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int inSampleSize = 1;
            if (opts.outHeight > reqHeight || opts.outWidth > reqWidth) {
                int halfH = opts.outHeight / 2;
                int halfW = opts.outWidth / 2;
                while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = Math.max(1, inSampleSize);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded=BitmapFactory.decodeFile(path,opts);
            if (decoded==null) return null;
            int[] size=scaledImageSize(decoded.getWidth(),decoded.getHeight(),reqWidth,reqHeight);
            if (size[0]==decoded.getWidth()&&size[1]==decoded.getHeight()) { decoded.prepareToDraw(); return decoded; }
            Bitmap resized=Bitmap.createScaledBitmap(decoded,size[0],size[1],true); decoded.recycle(); resized.prepareToDraw(); return resized;
        } catch (Exception e) {
            return null;
        }
    }

    static int[] scaledImageSize(int width,int height,int requestedWidth,int requestedHeight) {
        float factor=Math.min(1f,Math.max(requestedWidth/(float)width,requestedHeight/(float)height));
        return new int[]{Math.max(1,Math.round(width*factor)),Math.max(1,Math.round(height*factor))};
    }

    // ---- Curated PT-BR Seed Catalog (Instant 0ms Load + Real IMDb IDs & Trailers) ----------------

    private static List<MediaItem> getSeedMovies() {
        List<MediaItem> m = new ArrayList<>();
        m.add(seed("tt15239678", "Duna: Parte Dois", "movie", "8.5", "2024", "Ficção Científica, Aventura",
                "Paul Atreides se une a Chani e aos Fremen em uma jornada de vingança contra os conspiradores que destruíram sua família, enfrentando a escolha entre o amor de sua vida e o destino do universo.",
                "Way9Dexny3w", "2025-12-10", 980));
        m.add(seed("tt1262426", "Wicked", "movie", "7.6", "2024", "Fantasia, Musical",
                "A história não contada das bruxas de Oz acompanha Elphaba, uma jovem incompreendida por sua pele verde, e Glinda, uma estudante popular, cuja amizade chega a uma encruzilhada.",
                "6COmYeLsz4c", "2025-11-22", 950));
        m.add(seed("tt9218128", "Gladiador II", "movie", "6.8", "2024", "Ação, Aventura",
                "Anos após testemunhar a morte do venerado herói Maximus pelas mãos de seu tio, Lucius é forçado a entrar no Coliseu após seu lar ser conquistado pelos imperadores tirânicos que agora lideram Roma.",
                "4rgYUipGJNo", "2025-11-15", 940));
        m.add(seed("tt14961016", "Ainda Estou Aqui", "movie", "8.9", "2024", "Biografia, Drama",
                "No Rio de Janeiro do início dos anos 1970, Eunice Paiva é obrigada a se reinventar como ativista e mãe após o desaparecimento forçado de seu marido, o ex-deputado Rubens Paiva.",
                "_NzqP0jmk3o", "2025-11-07", 995));
        m.add(seed("tt17526714", "A Substância", "movie", "7.3", "2024", "Terror, Ficção Científica",
                "Uma celebridade em declínio decide usar uma droga clandestina de replicação celular que cria temporariamente uma versão mais jovem e perfeita de si mesma.",
                "LNlrGhBpYjc", "2025-09-20", 910));
        m.add(seed("tt29623480", "Robô Selvagem", "movie", "8.2", "2024", "Animação, Aventura",
                "Após um naufrágio, um robô inteligente chamado Roz fica preso em uma ilha desabitada. Para sobreviver ao ambiente hostil, ela cria laços com os animais da ilha e cuida de um filhote de ganso órfão.",
                "67vbA5ZJdKQ", "2025-09-27", 930));
        m.add(seed("tt20215234", "Conclave", "movie", "7.4", "2024", "Drama, Suspense",
                "O Cardeal Lawrence recebe a tarefa de liderar um dos eventos mais secretos e antigos do mundo: a escolha de um novo Papa, descobrindo uma conspiração que pode abalar os alicerces da Igreja.",
                "JX9jasdi3ic", "2025-10-25", 900));
        m.add(seed("tt6263850", "Deadpool & Wolverine", "movie", "7.6", "2024", "Ação, Comédia",
                "Deadpool recebe uma oferta da Autoridade de Variância Temporal para se juntar ao Universo Cinematográfico Marvel, mas acaba recrutando uma variante relutante do Wolverine para salvar seu universo.",
                "73_1biulkYk", "2025-07-26", 920));
        m.add(seed("tt28607951", "Anora", "movie", "7.8", "2024", "Comédia, Drama",
                "Anora, uma jovem do Brooklyn, tem a chance de viver uma história de Cinderela quando conhece e se casa impulsivamente com o filho de um oligarca russo.",
                "p1HxTmV5i7c", "2025-10-18", 890));
        m.add(seed("tt5040012", "Nosferatu", "movie", "7.4", "2024", "Terror, Fantasia",
                "Um conto gótico de obsessão entre uma jovem atormentada na Alemanha do século XIX e o antigo vampiro da Transilvânia que a persegue, trazendo consigo um horror indescritível.",
                "nulvWqYUM8k", "2025-12-25", 960));
        m.add(seed("tt22022452", "Divertida Mente 2", "movie", "7.6", "2024", "Animação, Comédia",
                "Acompanhe Riley na adolescência e o surgimento de novas emoções como Ansiedade, Inveja, Tédio e Vergonha no centro de controle de sua mente.",
                "LEjhY15eCx0", "2025-06-14", 880));
        m.add(seed("tt12037194", "Furiosa: Uma Saga Mad Max", "movie", "7.5", "2024", "Ação, Ficção Científica",
                "A história de origem da guerreira renegada Furiosa antes de seu encontro e aliança com Mad Max no deserto pós-apocalíptico.",
                "XJMuhwVlca4", "2025-05-24", 870));
        return m;
    }

    private static List<MediaItem> getSeedSeries() {
        List<MediaItem> s = new ArrayList<>();
        s.add(seed("tt11280740", "Ruptura (Severance)", "series", "8.7", "2025", "Ficção Científica, Suspense",
                "Mark lidera uma equipe de funcionários de escritório cujas memórias foram cirurgicamente divididas entre a vida profissional e a vida pessoal.",
                "xEQP4VVuyrY", "2025-12-18", 990));
        s.add(seed("tt15435876", "Pinguim", "series", "8.7", "2024", "Policial, Drama",
                "Após a queda de Carmine Falcone, Oswald 'Oz' Cobb dá início à sua ascensão implacável pelo submundo do crime de Gotham City.",
                "sfJG6IiA_s8", "2025-11-10", 975));
        s.add(seed("tt10919420", "Round 6", "series", "8.0", "2024", "Ação, Suspense",
                "Centenas de jogadores falidos aceitam um estranho convite para competir em jogos infantis com apostas mortais e um prêmio milionário.",
                "oqxAJKy0ii4", "2025-12-26", 985));
        s.add(seed("tt12637874", "Fallout", "series", "8.4", "2024", "Ação, Ficção Científica",
                "Em uma Los Angeles pós-apocalíptica, os cidadãos precisam viver em abrigos subterrâneos para se protegerem da radiação, mutantes e bandidos.",
                "V-mugKDQDlg", "2025-08-11", 945));
        s.add(seed("tt2788316", "Xógum: A Gloriosa Saga do Japão", "series", "8.6", "2024", "Drama, História",
                "No Japão do ano 1600, o Senhor Yoshii Toranaga luta pela própria vida enquanto seus inimigos no Conselho de Regentes se unem contra ele.",
                "yIs1Ia03ksI", "2025-07-15", 935));
        s.add(seed("tt14452776", "O Urso (The Bear)", "series", "8.5", "2024", "Comédia, Drama",
                "Um jovem chef da alta gastronomia volta para Chicago para administrar a lanchonete de sanduíches de sua família após uma tragédia.",
                "gBmkI4jlaIo", "2025-09-12", 915));
        s.add(seed("tt31510819", "MobLand", "series", "8.3", "2025", "Policial, Drama",
                "Duas famílias criminosas rivais em Londres colidem em uma disputa de poder que ameaça derrubar impérios e destruir vidas.",
                "qL6P8nN0f5k", "2025-12-20", 965));
        s.add(seed("tt11126994", "Arcane", "series", "9.0", "2024", "Animação, Ação",
                "Em meio ao conflito crescente entre a rica cidade de Piltover e o submundo oprimido de Zaun, duas irmãs lutam em lados opostos de uma guerra.",
                "fXmAurh012s", "2025-11-23", 955));
        s.add(seed("tt9813792", "Origem (From)", "series", "7.8", "2024", "Terror, Mistério",
                "Desvende o mistério de uma cidade no interior dos Estados Unidos que prende todos que nela entram, enquanto os moradores lutam para sobreviver às criaturas noturnas.",
                "pDHqAj4eJcM", "2025-10-15", 925));
        s.add(seed("tt11198330", "A Casa do Dragão", "series", "8.3", "2024", "Ação, Fantasia",
                "A história da Casa Targaryen, ambientada 200 anos antes dos eventos de Game of Thrones, em meio à guerra civil conhecida como a Dança dos Dragões.",
                "DotnJ7tTA34", "2025-08-25", 905));
        s.add(seed("tt14688458", "Silo", "series", "8.1", "2024", "Drama, Ficção Científica",
                "Homens e mulheres vivem em um silo subterrâneo gigante com várias regras que eles acreditam existir para protegê-los do mundo tóxico e arruinado na superfície.",
                "8ZYhuvIv1pA", "2025-11-15", 912));
        s.add(seed("tt24053860", "Chacal (The Day of the Jackal)", "series", "8.2", "2024", "Ação, Suspense",
                "Um assassino solitário, implacável e inigualável, o Chacal, ganha a vida realizando ataques sob encomenda até encontrar uma agente da inteligência britânica determinada a capturá-lo.",
                "2s6bQ5v8n4w", "2025-11-28", 940));
        return s;
    }

    private static MediaItem seed(String imdbId, String title, String type, String rating, String year,
                                  String genresPt, String synopsisPt, String trailerYtId,
                                  String releasedIso, double pop) {
        String bg = "https://images.metahub.space/background/medium/" + imdbId + "/img";
        String poster = "https://images.metahub.space/poster/medium/" + imdbId + "/img";
        MediaItem item=new MediaItem(imdbId, title, type, rating, year, genresPt, synopsisPt, bg, poster, trailerYtId, releasedIso, pop);
        item.synopsisInPtBr=true; item.titleInPtBr=true; return item;
    }

    // ---- Native trailers: Apple HLS first, then on-demand YouTube extraction -------------------

    public interface TrailerCallback {
        void onResult(List<NativeTrailerSource.Candidate> candidates);
    }

    private final Map<String, List<NativeTrailerSource.Candidate>> trailerCache = new HashMap<>();
    private final Map<String, List<TrailerCallback>> trailerRequests = new HashMap<>();
    private final Map<String, Long> trailerCacheTimes = new HashMap<>();

    public List<NativeTrailerSource.Candidate> getCachedTrailerCandidates(String imdbId) {
        synchronized (trailerCache) {
            Long cachedAt = trailerCacheTimes.get(imdbId);
            List<NativeTrailerSource.Candidate> cached=trailerCache.get(imdbId);
            if (cachedAt == null || cached == null || System.currentTimeMillis() - cachedAt > (cached.isEmpty()?30_000L:5 * 60_000L)) return null;
            return cached;
        }
    }

    public void resolveTrailerCandidates(final MediaItem item, final TrailerCallback callback) {
        if (item == null) return;
        synchronized (trailerCache) {
            List<NativeTrailerSource.Candidate> cached = getCachedTrailerCandidates(item.imdbId);
            if (cached != null) {
                if (callback != null) mainHandler.post(() -> callback.onResult(cached));
                return;
            }
            List<TrailerCallback> waiting = trailerRequests.get(item.imdbId);
            if (waiting != null) {
                if (callback != null) waiting.add(callback);
                return;
            }
            waiting = new ArrayList<>();
            if (callback != null) waiting.add(callback);
            trailerRequests.put(item.imdbId, waiting);
        }
        trailerPool.execute(() -> {
            List<NativeTrailerSource.Candidate> found = Collections.emptyList();
            try {
                found = NativeTrailerSource.resolve(item.imdbId, item.title, item.isSeries());
                android.util.Log.d("TVHomeTrailer", "Resolved " + item.imdbId + ": " + found.size() + " native trailers");
            } catch (Exception e) {
                android.util.Log.w("TVHomeTrailer", "Trailer source unavailable: " + e.getClass().getSimpleName());
            }
            if (found.isEmpty()) {
                resolveYoutubeTrailers(item,result -> finishTrailerRequest(item.imdbId,result));
            } else {
                finishTrailerRequest(item.imdbId,found);
            }
        });
    }

    private void finishTrailerRequest(String key,List<NativeTrailerSource.Candidate> found) {
        final List<NativeTrailerSource.Candidate> result=Collections.unmodifiableList(new ArrayList<>(found));
        List<TrailerCallback> callbacks;
        synchronized (trailerCache) {
            trailerCache.put(key,result); trailerCacheTimes.put(key,System.currentTimeMillis());
            if (key.startsWith("yt:")&&!result.isEmpty()) {
                trailerCache.put(key.substring(3),result); trailerCacheTimes.put(key.substring(3),System.currentTimeMillis());
            }
            callbacks=trailerRequests.remove(key);
        }
        if (callbacks!=null) for (TrailerCallback cb:callbacks) mainHandler.post(() -> cb.onResult(result));
    }

    public void resolveYoutubeTrailers(final MediaItem item, final TrailerCallback callback) {
        if (item==null) return;
        String key="yt:"+item.imdbId;
        synchronized (trailerCache) {
            List<NativeTrailerSource.Candidate> cached=getCachedTrailerCandidates(key);
            if (cached!=null) { if (callback!=null) mainHandler.post(() -> callback.onResult(cached)); return; }
            List<TrailerCallback> waiting=trailerRequests.get(key);
            if (waiting!=null) { if (callback!=null) waiting.add(callback); return; }
            waiting=new ArrayList<>(); if (callback!=null) waiting.add(callback); trailerRequests.put(key,waiting);
        }
        trailerPool.execute(() -> {
            List<NativeTrailerSource.Candidate> found = Collections.emptyList();
            try {
                found = YoutubeTrailerSource.resolve(item.imdbId, item.title, item.type, item.trailerYtId);
                android.util.Log.d("TVHomeTrailer", "YOUTUBE_RESOLVED " + item.imdbId + " streams=" + found.size());
            } catch (Exception e) {
                android.util.Log.w("TVHomeTrailer", "YouTube fallback unavailable", e);
            }
            finishTrailerRequest(key,found);
        });
    }

    // ---- Streaming Availability Resolution (Brazil) --------------------------------------------

    public interface ProvidersCallback {
        void onResult(List<WatchAvailability.Offer> offers);
    }

    private final Map<String, List<WatchAvailability.Offer>> providersCache = new HashMap<>();
    private final Map<String, Long> providerCacheTimes = new HashMap<>();
    private final Map<String, List<ProvidersCallback>> providerRequests = new HashMap<>();

    public List<WatchAvailability.Offer> getAvailableProviders(MediaItem item) {
        if (item == null) return Collections.emptyList();
        synchronized (providersCache) {
            Long cachedAt = providerCacheTimes.get(item.imdbId);
            if (cachedAt == null || System.currentTimeMillis() - cachedAt > 30 * 60_000L) return Collections.emptyList();
            return providersCache.get(item.imdbId);
        }
    }

    public void resolveProvidersAsync(final MediaItem item, final ProvidersCallback callback) {
        if (item == null) return;
        synchronized (providersCache) {
            Long cachedAt = providerCacheTimes.get(item.imdbId);
            if (cachedAt != null && System.currentTimeMillis() - cachedAt <= 30 * 60_000L) {
                List<WatchAvailability.Offer> cached = providersCache.get(item.imdbId);
                if (callback != null) mainHandler.post(() -> callback.onResult(cached));
                return;
            }
            List<ProvidersCallback> waiting = providerRequests.get(item.imdbId);
            if (waiting != null) {
                if (callback != null) waiting.add(callback);
                return;
            }
            waiting = new ArrayList<>();
            if (callback != null) waiting.add(callback);
            providerRequests.put(item.imdbId, waiting);
        }
        imagePool.execute(() -> {
            List<WatchAvailability.Offer> found = null;
            try {
                found = Collections.unmodifiableList(WatchAvailability.resolve(item.imdbId, item.title, item.isSeries()));
                android.util.Log.d("TVHomeWatch", "RESOLVED imdb=" + item.imdbId + " offers=" + found.size());
            } catch (Exception e) {
                android.util.Log.w("TVHomeWatch", "Availability lookup failed", e);
            }
            final List<WatchAvailability.Offer> result = found;
            List<ProvidersCallback> callbacks;
            synchronized (providersCache) {
                if (result != null) {
                    providersCache.put(item.imdbId, result);
                    providerCacheTimes.put(item.imdbId, System.currentTimeMillis());
                }
                callbacks = providerRequests.remove(item.imdbId);
            }
            if (callbacks != null) for (ProvidersCallback cb : callbacks) mainHandler.post(() -> cb.onResult(result));
        });
    }

}
