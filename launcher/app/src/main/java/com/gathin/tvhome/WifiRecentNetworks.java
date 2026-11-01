package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/** Only successful connections observed by the launcher, newest first. */
final class WifiRecentNetworks {
    // shortcut: prior connection order is unknown; import it only if the firmware exposes a history API.
    static String clean(String ssid) {
        if (ssid==null) return "";
        String name=ssid.trim();
        if (name.startsWith("\"")&&name.endsWith("\"")&&name.length()>=2) name=name.substring(1,name.length()-1);
        return name.isEmpty()||name.equalsIgnoreCase("<unknown ssid>")||name.equals("0x")?"":name;
    }
    static List<String> read(String json) {
        List<String> result=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(json);
            for (int i=0;i<array.length()&&result.size()<5;i++) { String name=clean(array.optString(i,"")); if (!name.isEmpty()&&!result.contains(name)) result.add(name); }
        } catch (Exception ignored) { }
        return result;
    }
    static String connected(String json,String ssid) {
        List<String> recent=read(json); String name=clean(ssid);
        if (!name.isEmpty()) { recent.remove(name); recent.add(0,name); }
        while (recent.size()>5) recent.remove(recent.size()-1);
        return new JSONArray(recent).toString();
    }
}
