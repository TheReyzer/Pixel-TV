package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Local, explicit feedback; the aggregate averages people rather than pooling their votes. */
final class ViewerProfiles {
    static final String GENERAL = "general";
    static final String[] RATINGS = {"Odiei", "Não gostei", "Mais ou menos", "Gostei", "Amei"};
    private static final double[] WEIGHTS = {-3, -1, 0, 1, 2};
    static int affinityPercent(double score) {
        if (!Double.isFinite(score)) return 50;
        double scale=score<0?-WEIGHTS[0]:WEIGHTS[WEIGHTS.length-1];
        return (int)Math.round(50+50*Math.max(-1,Math.min(1,score/scale)));
    }
    private static final long DAY_MS=86_400_000L;
    private static final java.util.regex.Pattern YEAR=java.util.regex.Pattern.compile("(?:19|20)\\d{2}");
    static final long WATCHED_COOLDOWN_MS = 21L * 24 * 60 * 60 * 1000;
    static final String[] AVATARS = {"cat", "dog", "fox", "panda", "koala", "tiger", "bear", "wolf", "monkey", "rabbit", "pig", "lion",
            "elephant", "raccoon", "otter", "owl", "frog", "penguin", "duck", "cow", "horse", "giraffe", "zebra", "capybara"};
    static final int[] COLORS = {0xff3457ad, 0xff7650a7, 0xff187d81, 0xffb44473, 0xffa86222, 0xff347751, 0xff465264, 0xffa83d45,
            0xff172b50, 0xff5b287b, 0xff336b27, 0xffbb941e, 0xff8f4939, 0xff4b8eab, 0xffa65f8b, 0xff352e2a,
            0xff2aafa0, 0xff24409b, 0xffb5a0d2, 0xffe2cda7, 0xffd98173, 0xff66722e, 0xff753653, 0xff1c1d22};
    static final class Profile {
        final String id;
        String name, avatar;
        int color;
        final Map<String, Vote> votes = new LinkedHashMap<>();
        final Map<String,MediaRepository.MediaItem> watchLater=new LinkedHashMap<>();
        Profile(String id, String name, String avatar) {
            this.id = id; this.name = name; this.avatar = avatar.isEmpty() ? "cat" : avatar;
            color = Math.floorMod(id.hashCode(), COLORS.length);
        }
        boolean aggregate() { return GENERAL.equals(id); }
    }
    static final class Vote {
        final String imdb, title, genres, type, year, originalTitle;
        final int rating;
        final long watchedAt,ratedAt;
        final List<String> contentFeatures;
        Vote(MediaRepository.MediaItem item, int rating, long watchedAt) {
            this(item.imdbId, item.title, item.genresPt, item.type, item.year, rating, watchedAt,item.originalTitle);
        }
        Vote(String imdb, String title, String genres, String type, String year, int rating, long watchedAt) {
            this(imdb,title,genres,type,year,rating,watchedAt,"");
        }
        Vote(String imdb, String title, String genres, String type, String year, int rating, long watchedAt,String originalTitle) {
            this(imdb,title,genres,type,year,rating,watchedAt,originalTitle,java.util.Collections.emptyList());
        }
        Vote(String imdb,String title,String genres,String type,String year,int rating,long watchedAt,String originalTitle,List<String> features) {
            this(imdb,title,genres,type,year,rating,watchedAt,originalTitle,features,rating<0?0:watchedAt);
        }
        Vote(String imdb,String title,String genres,String type,String year,int rating,long watchedAt,String originalTitle,List<String> features,long ratedAt) {
            this.imdb = imdb; this.title = title; this.genres = genres; this.type = type; this.year = year; this.rating = rating;
            this.watchedAt = watchedAt;
            this.originalTitle=originalTitle;
            this.contentFeatures=new ArrayList<>(features);
            this.ratedAt=ratedAt;
        }
    }
    private final List<Profile> profiles = new ArrayList<>();
    private String selected = GENERAL;
    private long revision;
    private long feedbackRevision;
    synchronized long feedbackVersion() { return feedbackRevision; }
    private final Map<String,List<String>> catalogFeatures=new LinkedHashMap<>();
    synchronized String recommendationVersion() { return selected+":"+revision; }
    synchronized void observeCatalog(List<MediaRepository.MediaItem> items) {
        for (MediaRepository.MediaItem item:items) {
            List<String> features=RecommendationFeatures.of(item);
            String key=item.imdbId+":"+item.type;
            if (!features.isEmpty()&&!features.equals(catalogFeatures.get(key))) { catalogFeatures.put(key,features); revision++; }
        }
    }

    ViewerProfiles() {
        profiles.add(new Profile(GENERAL, "Geral", "lion"));
    }
    synchronized boolean needsIndividualProfile() { for (Profile profile:profiles) if (!profile.aggregate()) return false; return true; }
    synchronized List<Profile> profiles() { return new ArrayList<>(profiles); }
    synchronized Profile find(String id) {
        for (Profile p : profiles) if (p.id.equals(id)) return p;
        return null;
    }
    synchronized Profile selected() { return find(selected); }
    synchronized void select(String id) { if (find(id) != null) selected = id; }
    synchronized void setAppearance(String id, String avatar, int color) {
        Profile p = find(id);
        if (p == null || !java.util.Arrays.asList(AVATARS).contains(avatar) || color < 0 || color >= COLORS.length)
            throw new IllegalArgumentException("Aparência inválida.");
        p.avatar = avatar; p.color = color;
    }
    synchronized Profile saveName(String id, String input) {
        String name = input == null ? "" : input.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 32) throw new IllegalArgumentException("Use um nome de 1 a 32 caracteres.");
        for (Profile p : profiles) if (!p.id.equals(id) && WatchNavigation.normalize(p.name).equals(WatchNavigation.normalize(name)))
            throw new IllegalArgumentException("Já existe um perfil com esse nome.");
        Profile p = find(id);
        if (p == null) {
            String avatar = AVATARS[profiles.size() % AVATARS.length];
            for (String candidate : AVATARS) {
                boolean used = false; for (Profile member : profiles) if (member.avatar.equals(candidate)) { used = true; break; }
                if (!used) { avatar = candidate; break; }
            }
            p = new Profile(UUID.randomUUID().toString(), name, avatar); profiles.add(p);
        }
        else p.name = name;
        return p;
    }
    synchronized void rate(String profile, MediaRepository.MediaItem item, int rating) {
        rate(profile, item, rating, System.currentTimeMillis());
    }
    synchronized Profile createProfile(String name,String avatar,int color) {
        if (!java.util.Arrays.asList(AVATARS).contains(avatar)||color<0||color>=COLORS.length) throw new IllegalArgumentException("Aparência inválida.");
        Profile profile=saveName(null,name); profile.avatar=avatar; profile.color=color; return profile;
    }
    synchronized void rate(String profile, MediaRepository.MediaItem item, int rating, long watchedAt) {
        record(profile,item,rating,watchedAt,rating<0?0:watchedAt);
    }
    private void record(String profile,MediaRepository.MediaItem item,int rating,long watchedAt,long ratedAt) {
        Profile p = find(profile);
        if (p == null || item == null || !item.imdbId.matches("tt\\d{5,12}") || rating < -1 || rating >= RATINGS.length || watchedAt <= 0)
            throw new IllegalArgumentException("Avaliação inválida.");
        Vote old=p.votes.get(item.imdbId);
        p.votes.put(item.imdbId,new Vote(item.imdbId,item.title,
                old!=null&&item.genresPt.trim().isEmpty()?old.genres:item.genresPt,item.type,
                old!=null&&item.year.trim().isEmpty()?old.year:item.year,rating,watchedAt,item.originalTitle.isEmpty()&&old!=null?old.originalTitle:item.originalTitle,
                mergeFeatures(old==null?java.util.Collections.emptyList():old.contentFeatures,RecommendationFeatures.of(item)),ratedAt));
        revision++;
        feedbackRevision++;
    }
    private static List<String> mergeFeatures(List<String> previous,List<String> fresh) {
        List<String> result=new ArrayList<>(fresh);
        for (String key:previous) { boolean replaced=false; for (String value:fresh) if (value.startsWith(key.substring(0,2))) { replaced=true; break; } if (!replaced) result.add(key); }
        return result;
    }
    synchronized void markWatched(String profile, MediaRepository.MediaItem item) {
        Vote old = vote(profile, item.imdbId);
        record(profile,item,old==null?-1:old.rating,System.currentTimeMillis(),old==null?0:old.ratedAt);
    }
    static void togglePerson(java.util.Set<String> people,String id) {
        if (GENERAL.equals(id)) { people.clear(); people.add(id); }
        else { people.remove(GENERAL); if (!people.remove(id)) people.add(id); }
    }
    synchronized void rateIndividually(java.util.Map<String,Integer> ratings,MediaRepository.MediaItem item) {
        if (ratings.isEmpty()||item==null||!item.imdbId.matches("tt\\d{5,12}")||(ratings.containsKey(GENERAL)&&ratings.size()>1)) throw new IllegalArgumentException("Avaliação inválida.");
        for (java.util.Map.Entry<String,Integer> entry:ratings.entrySet()) if (find(entry.getKey())==null||entry.getValue()==null||entry.getValue() < -1||entry.getValue()>=RATINGS.length) throw new IllegalArgumentException("Avaliação inválida.");
        long now=System.currentTimeMillis();
        for (java.util.Map.Entry<String,Integer> entry:ratings.entrySet()) { Vote old=vote(entry.getKey(),item.imdbId); int rating=entry.getValue(); record(entry.getKey(),item,rating==-1&&old!=null?old.rating:rating,now,rating==-1?(old==null?0:old.ratedAt):now); }
    }
    synchronized void removeVote(String profile, String imdb) { Profile p = find(profile); if (p != null&&p.votes.remove(imdb)!=null) { revision++; feedbackRevision++; } }
    synchronized Vote vote(String profile, String imdb) { Profile p = find(profile); return p == null ? null : p.votes.get(imdb); }

    synchronized void saveForLater(java.util.Set<String> people,MediaRepository.MediaItem item) {
        if (people.isEmpty()||people.contains(GENERAL)&&people.size()>1||item==null||!item.imdbId.matches("tt\\d{5,12}")||item.title.trim().isEmpty()) throw new IllegalArgumentException("Escolha ao menos um perfil.");
        for (String id:people) if (find(id)==null) throw new IllegalArgumentException("Perfil inválido.");
        for (String id:people) find(id).watchLater.put(item.imdbId,item);
    }
    synchronized List<MediaRepository.MediaItem> watchLater(String profile) {
        Profile selected=find(profile); if (selected==null) throw new IllegalArgumentException("Perfil inválido.");
        Map<String,MediaRepository.MediaItem> unique=new LinkedHashMap<>();
        for (Profile person:profiles) if (selected.aggregate()||person==selected) for (MediaRepository.MediaItem item:person.watchLater.values()) unique.putIfAbsent(item.imdbId,item);
        return new ArrayList<>(unique.values());
    }
    synchronized boolean isSaved(String profile,String imdb) {
        Profile selected=find(profile); if (selected==null) return false;
        for (Profile person:profiles) if ((selected.aggregate()||person==selected)&&person.watchLater.containsKey(imdb)) return true;
        return false;
    }
    synchronized boolean isRated(String profile,String imdb) {
        Profile selected=find(profile); if (selected==null) return false;
        for (Profile person:profiles) if (selected.aggregate()||person==selected) { Vote vote=person.votes.get(imdb); if (vote!=null&&vote.rating>=0) return true; }
        return false;
    }
    synchronized List<MediaRepository.MediaItem> history(String profile) {
        Profile selected=find(profile); if (selected==null) throw new IllegalArgumentException("Perfil inválido.");
        Map<String,Vote> latest=new LinkedHashMap<>();
        for (Profile person:profiles) if (selected.aggregate()||person==selected) for (Vote vote:person.votes.values()) {
            Vote previous=latest.get(vote.imdb); if (previous==null||vote.watchedAt>previous.watchedAt) latest.put(vote.imdb,vote);
        }
        List<Vote> ordered=new ArrayList<>(latest.values()); ordered.sort((a,b) -> Long.compare(b.watchedAt,a.watchedAt));
        List<MediaRepository.MediaItem> items=new ArrayList<>();
        for (Vote vote:ordered) {
            MediaRepository.MediaItem item=new MediaRepository.MediaItem(vote.imdb,vote.title,vote.type,"",vote.year,vote.genres,"","https://images.metahub.space/background/medium/"+vote.imdb+"/img","https://images.metahub.space/poster/medium/"+vote.imdb+"/img","","",0);
            item.originalTitle=vote.originalTitle; item.contentFeatures=vote.contentFeatures; items.add(item);
        }
        return items;
    }
    synchronized String historySummary(String profile,String imdb) {
        Profile selected=find(profile); if (selected==null) return "";
        List<String> notes=new ArrayList<>(); java.text.SimpleDateFormat dates=new java.text.SimpleDateFormat("dd/MM/yyyy",new java.util.Locale("pt","BR")); dates.setTimeZone(java.util.TimeZone.getTimeZone("America/Fortaleza"));
        for (Profile person:profiles) if (selected.aggregate()||person==selected) {
            Vote vote=person.votes.get(imdb); if (vote!=null) notes.add(person.name+": "+(vote.rating<0?"Assistido sem nota":RATINGS[vote.rating])+(vote.watchedAt>0?" · "+dates.format(new java.util.Date(vote.watchedAt)):""));
        }
        return String.join(" • ",notes);
    }
    synchronized String savedFor(String imdb) {
        List<String> names=new ArrayList<>(); for (Profile p:profiles) if (p.watchLater.containsKey(imdb)) names.add(p.name); return String.join(" · ",names);
    }
    synchronized void removeWatchLater(String profile,String imdb) {
        Profile selected=find(profile); if (selected==null) return;
        for (Profile p:profiles) if (selected.aggregate()||p==selected) p.watchLater.remove(imdb);
    }

    synchronized String toJson() {
        JSONObject root = new JSONObject();
        JSONArray list = new JSONArray();
        try {
            root.put("version", 5).put("selected", selected);
            for (Profile p : profiles) {
                JSONArray votes = new JSONArray();
                for (Vote v : p.votes.values()) votes.put(new JSONObject().put("imdb", v.imdb).put("title", v.title)
                        .put("genres", v.genres).put("type", v.type).put("year", v.year).put("rating", v.rating).put("watchedAt", v.watchedAt).put("ratedAt",v.ratedAt).put("originalTitle",v.originalTitle).put("contentFeatures",new JSONArray(v.contentFeatures)));
                JSONArray later=new JSONArray(); for (MediaRepository.MediaItem item:p.watchLater.values()) later.put(item.toJson());
                list.put(new JSONObject().put("id", p.id).put("name", p.name).put("avatar", p.avatar).put("color", p.color).put("votes", votes).put("watchLater",later));
            }
            root.put("profiles", list);
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        return root.toString();
    }
    static ViewerProfiles fromJson(String json) throws Exception {
        ViewerProfiles model = new ViewerProfiles();
        if (json == null || json.isEmpty()) return model;
        JSONObject root = new JSONObject(json);
        int version = root.getInt("version");
        if (version < 1 || version > 5) throw new IllegalArgumentException("Unknown profiles version");
        JSONArray list = root.getJSONArray("profiles");
        model.profiles.clear();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = o.getString("id"), name = o.getString("name").trim();
            if (!id.matches("[a-zA-Z0-9-]{1,64}") || name.isEmpty() || name.length() > 64 || model.find(id) != null)
                throw new IllegalArgumentException("Invalid profile");
            String avatar = o.optString("avatar", id.equals(GENERAL) ? "lion" : "dog");
            if (!java.util.Arrays.asList(AVATARS).contains(avatar)) avatar = id.equals(GENERAL) ? "lion" : "dog";
            Profile p = new Profile(id, name, avatar);
            int color = o.optInt("color", p.color);
            if (color >= 0 && color < COLORS.length) p.color = color;
            model.profiles.add(p);
            JSONArray later=o.optJSONArray("watchLater");
            if (later!=null) for (int j=0;j<later.length();j++) { JSONObject entry=later.optJSONObject(j); if (entry==null) continue; MediaRepository.MediaItem item=MediaRepository.MediaItem.fromJson(entry); if (item.imdbId.matches("tt\\d{5,12}")&&!item.title.trim().isEmpty()&&(item.type.equals("movie")||item.type.equals("series"))) p.watchLater.put(item.imdbId,item); }
            JSONArray votes = o.optJSONArray("votes");
            if (votes == null || version == 1 && p.aggregate()) continue;
            for (int j = 0; j < votes.length(); j++) {
                JSONObject v = votes.getJSONObject(j);
                String imdb = v.optString("imdb"); int rating = v.optInt("rating", -1);
                if (version == 1) { if (rating < 0 || rating > 3) continue; rating++; }
                if (!imdb.matches("tt\\d{5,12}") || rating < -1 || rating >= RATINGS.length) continue;
                p.votes.put(imdb, new Vote(imdb, v.optString("title"), v.optString("genres"),
                        v.optString("type"), v.optString("year"), rating, version == 1 ? System.currentTimeMillis() : v.optLong("watchedAt", 0),v.optString("originalTitle",""),RecommendationFeatures.read(v.optJSONArray("contentFeatures")),rating<0?0:v.optLong("ratedAt",version==1?System.currentTimeMillis():v.optLong("watchedAt",0))));
            }
        }
        if (model.find(GENERAL) == null) throw new IllegalArgumentException("Missing aggregate profile");
        model.select(root.optString("selected", GENERAL));
        return model;
    }

    private static int year(String value) {
        java.util.regex.Matcher m = YEAR.matcher(value == null ? "" : value);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }
    private static String era(String value, int now) {
        int y = year(value);
        return y == 0 ? "" : now - y <= 5 ? "Recentes (até 5 anos)" : now - y <= 15 ? "De 6 a 15 anos" : "Clássicos (mais de 15 anos)";
    }
    private static List<String> genres(String value) {
        List<String> out = new ArrayList<>();
        for (String g : (value == null ? "" : value).split(",")) {
            String clean = MediaRepository.genreLabel(g);
            if (!clean.isEmpty() && !clean.equalsIgnoreCase("Filme") && !clean.equalsIgnoreCase("Série") && !out.contains(clean)) out.add(clean);
        }
        return out;
    }
    private static final class Taste {
        final Map<String, double[]> features = new LinkedHashMap<>();
        void add(String key, double value, double weight) {
            if (key.endsWith(":")) return;
            double[] f = features.computeIfAbsent(key, k -> new double[2]);
            f[0] += value * weight; f[1] += weight;
        }
        double value(String key) { double[] f = features.get(key); return f == null ? 0 : f[0] / (f[1] + 2); }
        double confidence(String key) { double[] f=features.get(key); return f==null?0:f[1]/(f[1]+2); }
        double preference(String key) {
            double v = value(key);
            if (v >= 0) return v;
            // A disliked recent film alone is not evidence of liking old films or series.
            for (String other : features.keySet()) if (other.startsWith(key.substring(0, 2)) && value(other) > 0) return v;
            return 0;
        }
        Estimate estimate(MediaRepository.MediaItem item,int now,List<String> content) {
            Estimate result=new Estimate();
            List<String> keys=new ArrayList<>(); for (String genre:genres(item.genresPt)) keys.add("g:"+genre);
            keys.add("t:"+item.type); keys.add("e:"+era(item.year,now)); keys.add("d:"+decade(item.year)); keys.addAll(content);
            String[] groups={"g:","t:","e:","d:","k:","r:","c:","a:"};
            double[] weights={.48,.13,.08,.05,.16,.04,.04,.02};
            for (int i=0;i<groups.length;i++) {
                double signal=0,confidence=0; int count=0;
                for (String key:keys) if (key.startsWith(groups[i])) {
                    signal+=(i>=1&&i<=3)?preference(key):value(key); confidence+=confidence(key); count++;
                }
                if (count>0) { result.score+=weights[i]*signal/count; result.confidence+=weights[i]*confidence/count; }
            }
            result.worst=result.score; return result;
        }
    }
    private static final class Estimate { double score,confidence,worst; }
    private static String decade(String value) { int y = year(value); return y == 0 ? "" : "Anos " + y / 10 * 10; }
    private Taste taste(Profile p, int now) {
        return taste(p,now,System.currentTimeMillis());
    }
    private Taste taste(Profile p,int now,long nowMs) {
        Taste t = new Taste();
        for (Vote v : p.votes.values()) {
            if (v.rating < 0) continue;
            if (p.aggregate()&&individuallyRated(v.imdb)) continue;
            double weight = WEIGHTS[v.rating];
            double age=Math.max(0,(nowMs-v.ratedAt)/(double)DAY_MS);
            double recency=.35+.65*Math.pow(.5,age/180);
            List<String> gs = genres(v.genres);
            for (String g : gs) t.add("g:" + g, weight, recency / gs.size());
            t.add("t:" + v.type, weight, recency);
            t.add("e:" + era(v.year, now), weight, recency);
            t.add("d:" + decade(v.year), weight, recency);
            List<String> content=mergeFeatures(v.contentFeatures,catalogFeatures.getOrDefault(v.imdb+":"+v.type,java.util.Collections.emptyList()));
            for (String prefix:new String[]{"k:","r:","c:","a:"}) {
                int count=0; for (String key:content) if (key.startsWith(prefix)) count++;
                for (String key:content) if (key.startsWith(prefix)) t.add(key,weight,recency/count);
            }
        }
        return t;
    }
    private boolean individuallyRated(String imdb) {
        for (Profile person:profiles) if (!person.aggregate()) { Vote vote=person.votes.get(imdb); if (vote!=null&&vote.rating>=0) return true; }
        return false;
    }
    private List<Profile> contributors(Profile p) {
        List<Profile> out = new ArrayList<>();
        for (Profile member : profiles) {
            if (!p.aggregate() && member != p) continue;
            if (p.aggregate()&&member.aggregate()) continue;
            for (Vote vote : member.votes.values()) if (vote.rating >= 0) { out.add(member); break; }
        }
        if (p.aggregate()&&out.isEmpty()) for (Vote vote:p.votes.values()) if (vote.rating>=0) { out.add(p); break; }
        return out;
    }
    private boolean hidden(String imdb, Profile selected, long nowMs) {
        for (Profile p : profiles) {
            if (!selected.aggregate() && p != selected) continue;
            Vote v = p.votes.get(imdb);
            if (v != null && v.watchedAt > 0 && nowMs - v.watchedAt < WATCHED_COOLDOWN_MS) return true;
        }
        return false;
    }
    private static double consensus(List<Double> values) {
        if (values.isEmpty()) return 0;
        double mean = 0; for (double v : values) mean += v / values.size();
        double variance = 0; for (double v : values) variance += (v - mean) * (v - mean) / values.size();
        double worst=java.util.Collections.min(values);
        return mean - .4 * Math.sqrt(variance) + (values.size()>1?.25*Math.min(0,worst):0);
    }
    synchronized List<MediaRepository.MediaItem> rank(List<MediaRepository.MediaItem> base, boolean mixed, int now) {
        return rank(base, mixed, now, System.currentTimeMillis());
    }
    synchronized List<MediaRepository.MediaItem> rank(List<MediaRepository.MediaItem> candidates, boolean mixed, int now, long nowMs) {
        List<MediaRepository.MediaItem> base = unseen(candidates,nowMs);
        return rankForPeople(base,contributors(selected()),selected().aggregate(),mixed,now,nowMs);
    }
    synchronized List<MediaRepository.MediaItem> unseen(List<MediaRepository.MediaItem> candidates,long nowMs) {
        List<MediaRepository.MediaItem> result=new ArrayList<>(); for (MediaRepository.MediaItem item:candidates) if (!hidden(item.imdbId,selected(),nowMs)) result.add(item); return result;
    }
    synchronized List<MediaRepository.MediaItem> revisitCandidates(List<MediaRepository.MediaItem> candidates,boolean mixed,int now,long nowMs) {
        return rankForPeople(candidates,contributors(selected()),selected().aggregate(),mixed,now,nowMs);
    }
    private List<Profile> discoveryPeople(DiscoveryQuery query) {
        if (query.people.contains(GENERAL)) return contributors(find(GENERAL));
        List<Profile> people=new ArrayList<>();
        for (Profile p:profiles) if (query.people.contains(p.id)) for (Vote v:p.votes.values()) if (v.rating>=0) { people.add(p); break; }
        return people;
    }
    synchronized List<MediaRepository.MediaItem> discover(List<MediaRepository.MediaItem> candidates, DiscoveryQuery query, int now) {
        List<MediaRepository.MediaItem> base=new ArrayList<>(); java.util.Set<String> seen=new java.util.HashSet<>();
        for (MediaRepository.MediaItem item:candidates) if (seen.add(item.imdbId)&&acceptsDiscovery(item,query,now)) base.add(item);
        return rankForPeople(base,discoveryPeople(query),query.people.contains(GENERAL),false,now,System.currentTimeMillis());
    }
    synchronized boolean acceptsDiscovery(MediaRepository.MediaItem item,DiscoveryQuery query,int now) {
        if (!query.accepts(item,now)) return false;
        if (!query.includeWatched) for (Profile p:profiles) if ((query.people.contains(GENERAL)||query.people.contains(p.id))&&p.votes.containsKey(item.imdbId)) return false;
        return true;
    }
    private List<MediaRepository.MediaItem> rankForPeople(List<MediaRepository.MediaItem> base,List<Profile> people,boolean aggregate,boolean mixed,int now,long nowMs) {
        List<MediaRepository.MediaItem> ranked = new ArrayList<>(base);
        observeCatalog(base);
        if (people.isEmpty()) return ranked;
        List<Taste> tastes = new ArrayList<>(); for (Profile p : people) tastes.add(taste(p, now,nowMs));
        Taste collective=aggregate&&!people.get(0).aggregate()?taste(find(GENERAL),now,nowMs):null;
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String,Estimate> estimates=new LinkedHashMap<>();
        Map<String,List<String>> candidateGenres=new LinkedHashMap<>();
        for (int i = 0; i < base.size(); i++) {
            MediaRepository.MediaItem item = base.get(i);
            candidateGenres.put(item.imdbId,genres(item.genresPt));
            List<String> content=RecommendationFeatures.of(item);
            List<Double> values = new ArrayList<>(); double confidence=0,worst=0;
            for (int j = 0; j < people.size(); j++) {
                Estimate estimate=tastes.get(j).estimate(item,now,content);
                if (estimate.confidence>0) { values.add(estimate.score); confidence+=estimate.confidence; worst=Math.min(worst,estimate.score); }
            }
            Estimate group=new Estimate(); group.score=consensus(values); group.worst=worst; group.confidence=values.isEmpty()?0:confidence/values.size();
            if (collective!=null) { Estimate prior=collective.estimate(item,now,content); if (prior.confidence>0) { group.score+=.15*prior.score; group.confidence=Math.max(group.confidence,.15*prior.confidence); } }
            double watched=0;
            for (Profile p:profiles) if (aggregate||people.contains(p)) {
                Vote vote=p.votes.get(item.imdbId);
                if (vote!=null) { double age=Math.max(0,(nowMs-vote.watchedAt)/(double)DAY_MS-21); watched=Math.max(watched,.6*Math.exp(-age/90)); }
            }
            scores.put(item.imdbId, group.score-watched + .12 * (1.0 - (double)i / base.size())); estimates.put(item.imdbId,group);
        }
        ranked.sort((a, b) -> Double.compare(scores.get(b.imdbId), scores.get(a.imdbId)));
        List<MediaRepository.MediaItem> result = new ArrayList<>();
        while (!ranked.isEmpty()) {
            int pick=0,size=result.size(); double best=-Double.MAX_VALUE;
            boolean explore=size%5==4;
            boolean safeExploration=false;
            if (explore) for (int i=0;i<Math.min(24,ranked.size());i++) if (estimates.get(ranked.get(i).imdbId).worst>=-.35&&scores.get(ranked.get(i).imdbId)>=scores.get(ranked.get(0).imdbId)-1) { safeExploration=true; break; }
            for (int i=0;i<Math.min(24,ranked.size());i++) {
                MediaRepository.MediaItem item=ranked.get(i); double similarity=0;
                for (int j=Math.max(0,size-3);j<size;j++) similarity=Math.max(similarity,RecommendationFeatures.similarity(candidateGenres.get(item.imdbId),candidateGenres.get(result.get(j).imdbId)));
                Estimate estimate=estimates.get(item.imdbId);
                if (safeExploration&&(estimate.worst<-.35||scores.get(item.imdbId)<scores.get(ranked.get(0).imdbId)-1)) continue;
                double adjusted=scores.get(item.imdbId)-.10*similarity;
                if (explore&&estimate.worst>=-.35&&scores.get(item.imdbId)>=scores.get(ranked.get(0).imdbId)-1) adjusted=.65*scores.get(item.imdbId)-.55*similarity+.20*(1-estimate.confidence);
                if (adjusted>best) { best=adjusted; pick=i; }
            }
            if (mixed&&size%3==2&&result.get(size-1).isSeries()==result.get(size-2).isSeries()) {
                boolean needed=!result.get(size-1).isSeries();
                for (int i=0;i<ranked.size();i++) if (ranked.get(i).isSeries()==needed) { pick=i; break; }
            }
            result.add(ranked.remove(pick));
        }
        return result;
    }

    synchronized String reason(MediaRepository.MediaItem item,java.util.Set<String> audience,int now) {
        DiscoveryQuery query=new DiscoveryQuery(selected); query.people.clear(); query.people.addAll(audience);
        List<Profile> people=discoveryPeople(query); if (people.isEmpty()) return "Descoberta do catálogo · seus gostos ainda estão sendo aprendidos";
        List<Taste> tastes=new ArrayList<>(); for (Profile p:people) tastes.add(taste(p,now));
        List<String> supported=new ArrayList<>();
        List<String> keys=new ArrayList<>(); for (String genre:genres(item.genresPt)) keys.add("g:"+genre); for (String key:RecommendationFeatures.of(item)) if (key.startsWith("k:")) keys.add(key);
        for (String key:keys) { List<Double> evidence=new ArrayList<>(); for (Taste taste:tastes) if (taste.confidence(key)>0) evidence.add(taste.value(key)); if (consensus(evidence)>.08) supported.add(key.substring(2)); }
        if (supported.isEmpty()) return "Para explorar além dos seus gêneros habituais";
        return (audience.contains(GENERAL)||people.size()>1?"Afinidade do grupo com ":"Combina com seu gosto por ")+String.join(" e ",supported.subList(0,Math.min(2,supported.size())));
    }

    static final class TasteOverview {
        int watched, movies, series, rated, liked, people;
        final int[] ratings=new int[RATINGS.length];
        final Map<String,Double> features=new LinkedHashMap<>();
        Map<String,Double> category(String prefix) {
            Map<String,Double> values=new LinkedHashMap<>();
            for (Map.Entry<String,Double> entry:features.entrySet()) if (entry.getKey().startsWith(prefix)) values.put(entry.getKey().substring(2),entry.getValue());
            return values;
        }
    }
    private double combinedFeature(List<Profile> people,List<Taste> tastes,boolean aggregate,String key,int now) {
        List<Double> values=new ArrayList<>();
        for (Taste taste:tastes) if (taste.confidence(key)>0) values.add(key.startsWith("g:")?taste.value(key):taste.preference(key));
        double score=consensus(values);
        if (aggregate&&!people.isEmpty()&&!people.get(0).aggregate()) {
            Taste prior=taste(find(GENERAL),now);
            score+=.15*(key.startsWith("g:")?prior.value(key):prior.preference(key));
        }
        return score;
    }
    synchronized TasteOverview overview(String profileId,int now) {
        Profile profile=find(profileId); if (profile==null) throw new IllegalArgumentException("Perfil inválido");
        TasteOverview overview=new TasteOverview();
        java.util.Set<String> watched=new java.util.HashSet<>(),movies=new java.util.HashSet<>(),series=new java.util.HashSet<>();
        for (Profile person:profiles) if (profile.aggregate()||person==profile) for (Vote vote:person.votes.values()) {
            watched.add(vote.imdb); ("series".equals(vote.type)?series:movies).add(vote.imdb);
            if (vote.rating>=0) { overview.rated++; overview.ratings[vote.rating]++; if (vote.rating>=3) overview.liked++; }
        }
        overview.watched=watched.size(); overview.movies=movies.size(); overview.series=series.size();
        List<Profile> people=contributors(profile); overview.people=people.size(); List<Taste> tastes=new ArrayList<>();
        for (Profile person:people) { Taste taste=taste(person,now); tastes.add(taste); for (String key:taste.features.keySet()) overview.features.putIfAbsent(key,0.0); }
        if (profile.aggregate()) for (String key:taste(profile,now).features.keySet()) overview.features.putIfAbsent(key,0.0);
        for (String key:overview.features.keySet()) {
            overview.features.put(key,combinedFeature(people,tastes,profile.aggregate(),key,now));
        }
        return overview;
    }

    synchronized String summary(String profileId, int now) {
        Profile profile = find(profileId);
        List<Profile> people = contributors(profile);
        int count = 0, watched = 0;
        for (Profile p : profiles) if (profile.aggregate() || p == profile) for (Vote v : p.votes.values()) { watched++; if (v.rating >= 0) count++; }
        if (count == 0) return watched + " registros de assistidos.\n\nAinda sem avaliações. Abra um filme ou série, escolha Avaliar e diga quem assistiu. Os gostos serão aprendidos a partir das notas. Títulos assistidos ficam fora das recomendações por 21 dias.";
        List<Taste> tastes = new ArrayList<>();
        Map<String, Double> features = new LinkedHashMap<>();
        for (Profile p : people) { Taste t = taste(p, now); tastes.add(t); for (String key : t.features.keySet()) features.put(key, 0.0); }
        if (profile.aggregate()) for (String key:taste(profile,now).features.keySet()) features.putIfAbsent(key,0.0);
        for (String key : features.keySet()) features.put(key,combinedFeature(people,tastes,profile.aggregate(),key,now));
        List<String> positive = new ArrayList<>(), negative = new ArrayList<>(), formats = new ArrayList<>(), eras = new ArrayList<>(), decades = new ArrayList<>();
        for (String key : features.keySet()) {
            if (key.startsWith("g:") && features.get(key) > .02) positive.add(key);
            if (key.startsWith("g:") && features.get(key) < -.02) negative.add(key);
            if (key.startsWith("t:")) formats.add(key);
            if (key.startsWith("e:")) eras.add(key);
            if (key.startsWith("d:")) decades.add(key);
        }
        for (List<String> list : java.util.Arrays.asList(positive, formats, eras, decades)) list.sort((a,b) -> Double.compare(features.get(b), features.get(a)));
        negative.sort((a,b) -> Double.compare(features.get(a), features.get(b)));
        String format = "Sem preferência clara";
        if (Math.max(features.getOrDefault("t:movie", 0.0), features.getOrDefault("t:series", 0.0)) > .05
                && Math.abs(features.getOrDefault("t:movie", 0.0) - features.getOrDefault("t:series", 0.0)) > .05)
            format = features.getOrDefault("t:series", 0.0) > features.getOrDefault("t:movie", 0.0) ? "Mais sinais a favor de séries" : "Mais sinais a favor de filmes";
        return count + (count == 1 ? " avaliação · " : " avaliações · ") + watched + (watched == 1 ? " registro de assistido" : " registros de assistidos") + (profile.aggregate() ? "\n" + people.size() + " perfis com avaliações combinadas" : "")
                + (count < 5 ? "\nSinais iniciais: avalie mais títulos para refinar." : "")
                + "\n\nGÊNEROS PREFERIDOS\n" + names(positive, 6) + "\n\nGÊNEROS MENOS APRECIADOS\n" + names(negative, 6)
                + "\n\nFILMES E SÉRIES\n" + format + "\n\nÉPOCAS COM MELHORES SINAIS\n" + namesPositive(eras, features)
                + "\n\nDÉCADAS COM MELHORES SINAIS\n" + namesPositive(decades, features)
                + "\n\nPriorizamos inéditos por 21 dias e buscamos outras opções no catálogo. Quando tudo se esgota, mostramos sugestões identificadas como Para rever. A prioridade menor dos assistidos diminui com o tempo."
                + (profile.aggregate() ? " O perfil Geral favorece afinidades em comum e reduz conflitos entre os gostos." : "");
    }
    private static String namesPositive(List<String> keys, Map<String, Double> scores) {
        List<String> positive = new ArrayList<>(); for (String key : keys) if (scores.get(key) > .02) positive.add(key);
        return names(positive, 3);
    }
    private static String names(List<String> keys, int max) {
        if (keys.isEmpty()) return "Ainda sem sinal suficiente";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(max, keys.size()); i++) { if (i > 0) out.append('\n'); out.append(i + 1).append(". ").append(keys.get(i).substring(2)); }
        return out.toString();
    }
}
