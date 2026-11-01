package com.gathin.tvhome;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional evidence from catalog metadata; absent fields never imply a preference. */
final class RecommendationFeatures {
    private static final java.util.regex.Pattern FEATURE_KEY=java.util.regex.Pattern.compile("[krac]:[^\\p{Cntrl}]{1,80}");
    private static final String[][] THEMES={
        {"Investigação","investigacao","investigation","detective","detetive","serial killer"},
        {"Tribunais","tribunal","courtroom","advogad","lawyer","julgamento"},
        {"Sobrevivência","sobreviver","sobrevivencia","survival","survive","naufrag"},
        {"Espaço","astronaut","espacial","outer space","space mission","interplanet"},
        {"Viagem no tempo","viagem no tempo","time travel","volta no tempo"},
        {"Distopia","distopic","dystopi","totalitari","regime opressor"},
        {"Política","politic","ditadura","dictatorship","eleicao","election"},
        {"Medicina","hospital","medic","surgeon","cirurgi"},
        {"Espionagem","espion","spy","espiao","secret agent","agente secreto"},
        {"Sobrenatural","sobrenatural","supernatural","fantasma","ghost","assombr"},
        {"Zumbis","zumbi","zombie","undead"},
        {"Amadurecimento","coming of age","amadurecimento","adolescen"},
        {"Psicológico","psicologic","psychological"},
        {"Humor sombrio","humor negro","dark comedy","black comedy"},
        {"Ritmo contemplativo","slow burn","slow paced","ritmo lento","contemplativ"},
        {"Ritmo acelerado","fast paced","ritmo acelerado","frenetic","frantic"},
        {"Tom leve","lighthearted","light hearted","tom leve"},
        {"Tom sombrio","tom sombrio","atmosfera sombria","dark atmosphere"}
    };
    static List<String> of(MediaRepository.MediaItem item) {
        Set<String> result=new LinkedHashSet<>(item.contentFeatures);
        String text=" "+WatchNavigation.normalize(item.synopsisPt==null?"":item.synopsisPt);
        for (String[] theme:THEMES) for (int i=1;i<theme.length;i++) if (text.contains(" "+theme[i])) { result.add("k:"+theme[0]); break; }
        return new ArrayList<>(result);
    }
    static List<String> read(JSONArray values) {
        Set<String> result=new LinkedHashSet<>();
        if (values!=null) for (int i=0;i<Math.min(24,values.length());i++) {
            String key=values.optString(i,"");
            if (FEATURE_KEY.matcher(key).matches()) result.add(key);
        }
        return new ArrayList<>(result);
    }
    static void fromCatalog(MediaRepository.MediaItem item,JSONObject meta) {
        List<String> result=new ArrayList<>();
        String runtime=meta.optString("runtime","").trim();
        java.util.regex.Matcher minutes=java.util.regex.Pattern.compile("^(\\d{1,3})(?:\\s*(?:min|minutes|minutos))?$",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(runtime);
        if (minutes.matches()) {
            int value=Integer.parseInt(minutes.group(1));
            if (value>0&&value<=600) result.add("r:"+(item.isSeries()?(value<=30?"Episódios até 30 min":value<=60?"Episódios de 31 a 60 min":"Episódios acima de 60 min"):(value<=95?"Filmes até 95 min":value<=130?"Filmes de 96 a 130 min":"Filmes acima de 130 min")));
        }
        people(result,meta.optJSONArray("director"),"c:",2);
        people(result,meta.optJSONArray("cast"),"a:",3);
        item.contentFeatures=result;
    }
    private static void people(List<String> result,JSONArray values,String prefix,int limit) {
        if (values==null) return;
        for (int i=0;i<Math.min(limit,values.length());i++) {
            String name=values.optString(i,"").trim();
            if (!name.isEmpty()&&name.length()<=80&&!name.matches(".*[\\p{Cntrl}].*")) result.add(prefix+WatchNavigation.normalize(name));
        }
    }
    static double similarity(List<String> first,List<String> second) {
        if (first.isEmpty()||second.isEmpty()) return 0;
        int common=0; for (String genre:first) if (second.contains(genre)) common++;
        return common/(double)(first.size()+second.size()-common);
    }
}
