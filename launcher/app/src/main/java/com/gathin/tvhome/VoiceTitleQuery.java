package com.gathin.tvhome;

import java.util.List;

/** Treat recognition output like typed input: ignore blanks and bound the catalog query. */
final class VoiceTitleQuery {
    static java.util.ArrayList<String> results(List<String> complete,List<String> partial) {
        java.util.ArrayList<String> finalTitles=alternatives(complete);
        return finalTitles.isEmpty()?alternatives(partial):finalTitles;
    }
    static java.util.ArrayList<String> alternatives(List<String> hypotheses) {
        java.util.ArrayList<String> titles=new java.util.ArrayList<>();
        if (hypotheses!=null) for (String hypothesis:hypotheses) {
            String title=first(java.util.Collections.singletonList(hypothesis));
            if (!title.isEmpty()&&titles.stream().noneMatch(value -> value.equalsIgnoreCase(title))) titles.add(title);
            if (titles.size()==5) break;
        }
        return titles;
    }
    static String first(List<String> hypotheses) {
        if (hypotheses!=null) for (String value:hypotheses) {
            if (value==null) continue;
            String title=value.trim();
            if (title.length()>=2) return title.substring(0,Math.min(100,title.length()));
        }
        return "";
    }
}
