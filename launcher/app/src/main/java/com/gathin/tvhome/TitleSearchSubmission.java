package com.gathin.tvhome;

/** A successful query needs another submit only after its text or format changes. */
final class TitleSearchSubmission {
    private String text="",type="";
    void completed(String query,String format) { text=query.trim(); type=format; }
    boolean changed(String query,String format) { return query.trim().length()>=2&&(!text.equals(query.trim())||!type.equals(format)); }
}
