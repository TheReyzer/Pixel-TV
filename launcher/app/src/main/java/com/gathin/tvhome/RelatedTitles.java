package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/** Explicit work relationships outrank similarity; title spelling never proves a sequel. */
final class RelatedTitles {
    static final java.util.regex.Pattern IMDB=java.util.regex.Pattern.compile("tt[0-9]{5,12}"),ENTITY=java.util.regex.Pattern.compile("Q[0-9]{1,12}");
    private static final Map<String,String> ROOTS=Collections.synchronizedMap(new LinkedHashMap<String,String>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry) { return size()>64; }
    });
    static String rootQuery(String imdb) {
        if (imdb==null||!IMDB.matcher(imdb).matches()) throw new IllegalArgumentException("IMDb inválido");
        return "SELECT ?root WHERE { ?root wdt:P345 \""+imdb+"\" } LIMIT 1";
    }
    static String connectionsQuery(String root) {
        if (root==null||!ENTITY.matcher(root).matches()) throw new IllegalArgumentException("Entidade inválida");
        // shortcut: WDQS's Blazegraph hint keeps the bounded work lookup first; revisit on backend migration.
        return "SELECT DISTINCT ?imdb ?rank WHERE { hint:Query hint:optimizer \"None\". VALUES ?root { wd:"+root+" } "+
            "{ { ?root (wdt:P156|^wdt:P155)/(wdt:P156|^wdt:P155)?/(wdt:P156|^wdt:P155)? ?work. FILTER NOT EXISTS { ?work wdt:P144 ?root } BIND(0 AS ?rank) } "+
            "UNION { ?root (wdt:P4969|^wdt:P144) ?work. BIND(1 AS ?rank) } "+
            "UNION { ?root p:P179 ?rootPart. ?rootPart ps:P179 ?franchise; pq:P1545 ?first. ?workPart ps:P179 ?franchise; pq:P1545 ?next. ?work p:P179 ?workPart. FILTER(xsd:decimal(?next)>xsd:decimal(?first)) BIND(0 AS ?rank) } "+
            "UNION { ?root wdt:P179 ?franchise. ?work wdt:P179 ?franchise. BIND(2 AS ?rank) } "+
            "UNION { ?root (wdt:P155|^wdt:P156|wdt:P144|^wdt:P4969) ?work. BIND(3 AS ?rank) } "+
            "UNION { ?root p:P179/pq:P156 ?work. BIND(0 AS ?rank) } "+
            "UNION { ?root p:P179/pq:P155 ?work. BIND(3 AS ?rank) } } "+
            "?work wdt:P345 ?imdb. FILTER(?work != ?root) FILTER NOT EXISTS { VALUES ?excluded { wd:Q7889 wd:Q21191270 wd:Q3464665 } ?work wdt:P31/wdt:P279* ?excluded } } LIMIT 80";
    }
    static String root(String response) throws Exception {
        JSONArray rows=new JSONObject(response).getJSONObject("results").getJSONArray("bindings");
        if (rows.length()==0) return "";
        String uri=rows.getJSONObject(0).getJSONObject("root").getString("value"),root=uri.substring(uri.lastIndexOf('/')+1);
        if (!ENTITY.matcher(root).matches()) throw new java.io.IOException("Entidade incompatível");
        return root;
    }
    static Map<String,Integer> connections(String response,String seed) throws Exception {
        Map<String,Integer> found=new LinkedHashMap<>(); JSONArray rows=new JSONObject(response).getJSONObject("results").getJSONArray("bindings");
        for (int i=0;i<Math.min(80,rows.length());i++) {
            JSONObject row=rows.optJSONObject(i),id=row==null?null:row.optJSONObject("imdb"),rank=row==null?null:row.optJSONObject("rank");
            String imdb=id==null?"":id.optString("value",""); int priority=rank==null?-1:rank.optInt("value",-1);
            if (!IMDB.matcher(imdb).matches()||imdb.equals(seed)||priority<0||priority>3) continue;
            found.merge(imdb,priority,Math::min);
        }
        return found;
    }
    static Map<String,Integer> fetch(String imdb) throws Exception {
        rootQuery(imdb); long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        String root=ROOTS.get(imdb);
        if (root==null) { root=root(request(rootQuery(imdb),deadline)); if (!root.isEmpty()) ROOTS.put(imdb,root); }
        return root.isEmpty()?Collections.emptyMap():connections(request(connectionsQuery(root),deadline),imdb);
    }
    private static String request(String query) throws Exception {
        return request(query,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15));
    }
    private static String request(String query,long deadline) throws Exception {
        for (int attempt=0;attempt<2;attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            int remaining=(int)Math.min(5000,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
            if (remaining<=0) throw new java.net.SocketTimeoutException("Relationship lookup deadline");
            HttpURLConnection connection=(HttpURLConnection)new URL("https://query.wikidata.org/sparql?format=json&query="+URLEncoder.encode(query,"UTF-8")).openConnection();
            connection.setRequestProperty("User-Agent","TVVibeHack/2.0 (Android TV related catalog)"); connection.setRequestProperty("Accept","application/sparql-results+json"); connection.setRequestProperty("Connection","close");
            connection.setConnectTimeout(Math.min(4000,remaining)); connection.setReadTimeout(remaining);
            try (java.io.InputStream input=connection.getInputStream(); java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while ((count=input.read(buffer))!=-1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedException(); if (out.size()+count>256*1024) throw new java.io.IOException("Resposta excessiva"); out.write(buffer,0,count); }
                return out.toString("UTF-8");
            } catch (java.net.SocketTimeoutException|java.net.SocketException failure) {
                if (attempt==1||Thread.currentThread().isInterrupted()||System.nanoTime()>=deadline) throw failure;
            } finally { connection.disconnect(); }
        }
        throw new java.io.IOException("Relationship lookup unavailable");
    }
    static int year(MediaRepository.MediaItem item) {
        try { String value=item.year.trim(); return value.length()<4?0:Integer.parseInt(value.substring(0,4)); } catch (RuntimeException ignored) { return 0; }
    }
    static int priority(MediaRepository.MediaItem seed,MediaRepository.MediaItem item,Map<String,Integer> links) {
        return links.getOrDefault(item.imdbId,4);
    }
    static double similarity(MediaRepository.MediaItem seed,MediaRepository.MediaItem item) {
        List<String> first=genres(seed.genresPt),second=genres(item.genresPt);
        double genres=RecommendationFeatures.similarity(first,second),features=RecommendationFeatures.similarity(RecommendationFeatures.of(seed),RecommendationFeatures.of(item));
        int gap=Math.abs(year(seed)-year(item));
        double rating=0; try { double value=Double.parseDouble(item.rating); if (Double.isFinite(value)&&value>0&&value<=10) rating=value/100; } catch (Exception ignored) { }
        return genres*6+features*3+(seed.type.equals(item.type)?.4:0)+(year(seed)>0&&year(item)>0?.3/(1+gap/5.0):0)+rating;
    }
    private static List<String> genres(String text) {
        List<String> values=new ArrayList<>(); for (String genre:text.split("[,·/|]")) { String key=WatchNavigation.normalize(genre); if (!key.isEmpty()) values.add(key); } return values;
    }
    static List<MediaRepository.MediaItem> order(MediaRepository.MediaItem seed,Map<String,Integer> links,List<MediaRepository.MediaItem> candidates) {
        Map<String,MediaRepository.MediaItem> unique=new LinkedHashMap<>();
        for (MediaRepository.MediaItem item:candidates) if (!item.imdbId.equals(seed.imdbId)&&IMDB.matcher(item.imdbId).matches()&&!item.title.trim().isEmpty()) unique.putIfAbsent(item.imdbId,item);
        List<MediaRepository.MediaItem> ordered=new ArrayList<>(unique.values());
        Map<String,Double> scores=new HashMap<>(); for (MediaRepository.MediaItem item:ordered) scores.put(item.imdbId,similarity(seed,item));
        ordered.sort((a,b) -> {
            int pa=priority(seed,a,links),pb=priority(seed,b,links),comparison=Integer.compare(pa,pb);
            if (comparison!=0) return comparison;
            if (pa==1&&a.hasRating()&&b.hasRating()) { comparison=Double.compare(Double.parseDouble(b.rating),Double.parseDouble(a.rating)); if (comparison!=0) return comparison; }
            if (pa<4&&pa!=1) { int ya=year(a),yb=year(b); comparison=Integer.compare(ya==0?9999:ya,yb==0?9999:yb); if (comparison!=0) return comparison;
                if (!a.releasedIso.isEmpty()&&!b.releasedIso.isEmpty()) { comparison=a.releasedIso.compareTo(b.releasedIso); if (comparison!=0) return comparison; }
            }
            comparison=Double.compare(scores.get(b.imdbId),scores.get(a.imdbId)); return comparison!=0?comparison:a.imdbId.compareTo(b.imdbId);
        });
        return new ArrayList<>(ordered.subList(0,Math.min(36,ordered.size())));
    }
}
