package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.content.pm.ResolveInfo;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optional per-device values live in private app storage, never in shared source defaults. */
final class DeviceConfig {
    final String liveTv;
    final double latitude,longitude;
    final List<String> dockApps,hiddenApps;
    private DeviceConfig(String liveTv,double latitude,double longitude,List<String> dock,List<String> hidden) { this.liveTv=liveTv; this.latitude=latitude; this.longitude=longitude; dockApps=Collections.unmodifiableList(dock); hiddenApps=Collections.unmodifiableList(hidden); }
    static DeviceConfig parse(String json) throws Exception {
        JSONObject root=new JSONObject(json); java.util.Iterator<String> keys=root.keys();
        while(keys.hasNext()) if (!java.util.Arrays.asList("liveTvPackage","weather","dockApps","hiddenApps").contains(keys.next())) throw new IllegalArgumentException("Unknown device configuration field");
        String live=root.optString("liveTvPackage",""); if (!live.isEmpty()&&!packageName(live)) throw new IllegalArgumentException("Invalid Live TV package");
        JSONObject weather=root.optJSONObject("weather"); double lat=Double.NaN,lon=Double.NaN;
        if (weather!=null) { lat=weather.getDouble("latitude"); lon=weather.getDouble("longitude"); if (!validCoordinates(lat,lon)) throw new IllegalArgumentException("Invalid weather coordinates"); }
        return new DeviceConfig(live,lat,lon,packages(root.optJSONArray("dockApps")),packages(root.optJSONArray("hiddenApps")));
    }
    static boolean packageName(String value) { return value!=null&&value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"); }
    static boolean validCoordinates(double latitude,double longitude) { return Double.isFinite(latitude)&&Double.isFinite(longitude)&&Math.abs(latitude)<=90&&Math.abs(longitude)<=180; }
    static int columns(int width,float density,int preferred) { return Math.max(1,Math.min(preferred,(int)((width/density-96)/164))); }
    private static List<String> packages(JSONArray array) throws Exception {
        List<String> result=new ArrayList<>(); if (array==null) return result;
        if (array.length()>50) throw new IllegalArgumentException("Too many configured apps");
        for (int i=0;i<array.length();i++) { String value=array.getString(i); if (!packageName(value)) throw new IllegalArgumentException("Invalid app package"); if(!result.contains(value)) result.add(value); }
        return result;
    }
    static DeviceConfig load(Context context) {
        java.io.File file=new java.io.File(context.getFilesDir(),"device-config.json");
        try {
            if (file.exists()&&file.length()<=16384) try (java.io.InputStream input=new java.io.FileInputStream(file); java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()) {
                byte[] data=new byte[1024]; int count; while((count=input.read(data))!=-1) output.write(data,0,count);
                return parse(output.toString("UTF-8"));
            }
        } catch (Exception error) { android.util.Log.w("TVHomeConfig","Ignoring invalid local configuration"); }
        try { return parse("{}"); } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    boolean hasWeather() { return validCoordinates(latitude,longitude); }
    String liveTvPackage(Context context) {
        if (!liveTv.isEmpty()) return liveTv;
        String resource=context.getString(R.string.live_tv_package).trim(); if (packageName(resource)) return resource;
        ResolveInfo resolved=context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_VIEW,TvContract.Channels.CONTENT_URI),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved!=null&&resolved.activityInfo!=null&&!resolved.activityInfo.packageName.equals("android")&&!resolved.activityInfo.packageName.equals(context.getPackageName())) return resolved.activityInfo.packageName;
        for(String candidate:new String[]{"com.android.tv","com.google.android.tv","com.tcl.tv"}) if(context.getPackageManager().getLaunchIntentForPackage(candidate)!=null||context.getPackageManager().getLeanbackLaunchIntentForPackage(candidate)!=null) return candidate;
        return "";
    }
}
