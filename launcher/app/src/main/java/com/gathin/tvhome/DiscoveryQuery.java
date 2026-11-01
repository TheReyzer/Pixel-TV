package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Explicit discovery constraints, independent of the currently active home profile. */
final class DiscoveryQuery {
    private static final java.util.regex.Pattern YEAR=java.util.regex.Pattern.compile("(?:19|20)\\d{2}");
    static final String[] GENRES = {"Action","Adventure","Animation","Biography","Comedy","Crime","Documentary","Drama","Family","Fantasy","History","Horror","Mystery","Romance","Sci-Fi","Sport","Thriller","War","Western"};
    static final String[] GENRE_NAMES = {"Ação","Aventura","Animação","Biografia","Comédia","Policial","Documentário","Drama","Família","Fantasia","História","Terror","Mistério","Romance","Ficção Científica","Esporte","Suspense","Guerra","Faroeste"};
    static final String[] ERAS = {"Lançamentos","Gerais","Clássicos"};
    final Set<String> people = new LinkedHashSet<>();
    final Set<String> genres = new LinkedHashSet<>();
    String type = "movie";
    int era=1;
    boolean includeWatched;

    DiscoveryQuery(String profile) { people.add(profile); }
    DiscoveryQuery(DiscoveryQuery other) {
        people.addAll(other.people); genres.addAll(other.genres); type=other.type; era=other.era; includeWatched=other.includeWatched;
    }
    void togglePerson(String id) {
        ViewerProfiles.togglePerson(people,id);
    }
    static int releaseYear(String value) {
        java.util.regex.Matcher m=YEAR.matcher(value==null?"":value);
        return m.find()?Integer.parseInt(m.group()):0;
    }
    boolean accepts(MediaRepository.MediaItem item,int now) {
        if (!type.equals(item.type)) return false;
        int y=releaseYear(item.year),age=now-y;
        if (era!=1 && (y==0 || age<0 || (era==0?age>2:age<=15))) return false;
        if (genres.isEmpty()) return true;
        for (String selected:genres) {
            int index=java.util.Arrays.asList(GENRES).indexOf(selected);
            if (index<0) continue;
            for (String actual:item.genresPt.split(","))
                if (WatchNavigation.normalize(actual).equals(WatchNavigation.normalize(GENRE_NAMES[index]))
                        || WatchNavigation.normalize(actual).equals(WatchNavigation.normalize(selected))) return true;
        }
        return false;
    }
    static List<Integer> alphabeticalGenres() {
        List<Integer> indices=new ArrayList<>(); for (int i=0;i<GENRES.length;i++) indices.add(i);
        java.text.Collator collator=java.text.Collator.getInstance(new java.util.Locale("pt","BR"));
        indices.sort((a,b) -> collator.compare(GENRE_NAMES[a],GENRE_NAMES[b])); return indices;
    }
    List<MediaRepository.MediaItem> filter(List<MediaRepository.MediaItem> candidates,int now) {
        List<MediaRepository.MediaItem> result=new ArrayList<>(); Set<String> seen=new LinkedHashSet<>();
        for (MediaRepository.MediaItem item:candidates) if (accepts(item,now)&&seen.add(item.imdbId)) result.add(item);
        return result;
    }
    static List<MediaRepository.MediaItem> avoidRepeatedOrder(List<MediaRepository.MediaItem> ranked,List<String> previous) {
        List<MediaRepository.MediaItem> result=new ArrayList<>(ranked);
        if (result.size()<2||previous.isEmpty()) return result;
        int count=Math.min(3,Math.min(result.size(),previous.size())); boolean same=true;
        for (int i=0;i<count;i++) if (!result.get(i).imdbId.equals(previous.get(i))) { same=false; break; }
        if (same) java.util.Collections.swap(result,0,1);
        return result;
    }
    String summary(ViewerProfiles model) {
        List<String> names=new ArrayList<>();
        for (String id:people) { ViewerProfiles.Profile p=model.find(id); if (p!=null) names.add(p.aggregate()?"Geral · todos":p.name); }
        List<String> selectedGenres=new ArrayList<>();
        for (int i:alphabeticalGenres()) if (genres.contains(GENRES[i])) selectedGenres.add(GENRE_NAMES[i]);
        return String.join(", ",names)+" · "+("movie".equals(type)?"Filmes":"Séries")+"\n"
                +(selectedGenres.isEmpty()?"Todos os gêneros":String.join(", ",selectedGenres))+" · "+ERAS[era]
                +"\n"+(includeWatched?"Inclui assistidos":"Sem títulos já assistidos");
    }
}
