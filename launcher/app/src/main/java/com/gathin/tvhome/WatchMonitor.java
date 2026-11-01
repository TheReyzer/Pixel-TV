package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Process-wide journal, shared by the launcher and its read-only playback observer. */
final class WatchMonitor {
    private static WatchMonitor instance;
    private final SharedPreferences prefs;
    private final android.os.PowerManager power;
    private final android.content.pm.PackageManager packages;
    private final java.util.Map<String,Boolean> homeApps=new java.util.HashMap<>();
    private boolean externalVisit;
    final PlaybackJournal journal;
    private boolean home;
    private final java.util.Map<String,Long> sessionPlaying=new java.util.HashMap<>();
    static synchronized WatchMonitor get(Context context) { if (instance==null) instance=new WatchMonitor(context.getApplicationContext()); return instance; }
    private WatchMonitor(Context context) {
        power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);
        packages=context.getPackageManager();
        homeApps.put(context.getPackageName(),false);
        homeApps.put("android",false);
        homeApps.put("com.android.systemui",false);
        homeApps.put("com.tcl.framework.custom",false);
        android.view.inputmethod.InputMethodManager keyboards=(android.view.inputmethod.InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboards!=null) for (android.view.inputmethod.InputMethodInfo keyboard:keyboards.getInputMethodList()) homeApps.put(keyboard.getPackageName(),false);
        prefs=context.getSharedPreferences("watch_feedback_v1",Context.MODE_PRIVATE);
        PlaybackJournal loaded;
        try { loaded=PlaybackJournal.fromJson(prefs.getString("journal","")); }
        catch (Exception e) { prefs.edit().putString("recovery",prefs.getString("journal","")).apply(); loaded=new PlaybackJournal(); android.util.Log.w("TVHomePlayback","Journal preserved for recovery",e); }
        journal=loaded;
    }
    synchronized void save() { prefs.edit().putString("journal",journal.toJson()).apply(); }
    synchronized void launched(String pkg,MediaRepository.MediaItem item) { externalVisit=true; journal.rememberLaunch(pkg,item,System.currentTimeMillis()); save(); }
    synchronized void appOpened(String pkg) { externalVisit=true; journal.clearLaunch(pkg); save(); }
    synchronized boolean consumeExternalVisit() { boolean visited=externalVisit; externalVisit=false; return visited; }
    synchronized void foreground(String pkg) {
        Boolean app=homeApps.get(pkg);
        if (app==null) {
            app="com.android.tv.settings".equals(pkg)||"com.tcl.tv".equals(pkg)||packages.getLeanbackLaunchIntentForPackage(pkg)!=null||packages.getLaunchIntentForPackage(pkg)!=null;
            homeApps.put(pkg,app);
        }
        if (app) externalVisit=true;
        if (PlaybackJournal.APPS.containsKey(pkg)) home=false;
        else journal.pauseAll(System.currentTimeMillis());
    }
    synchronized void outside() { home=false; }
    synchronized void returnedHome() { home=true; journal.home(System.currentTimeMillis()); save(); }
    synchronized void observe(String pkg,String title,String mediaId,boolean playing,long duration,String source) {
        if (home) return;
        if (power!=null&&!power.isInteractive()) { journal.pauseAll(System.currentTimeMillis()); save(); return; }
        long now=System.currentTimeMillis();
        if (source.equals("session")&&playing) sessionPlaying.put(pkg,now);
        if (source.equals("screen")&&now-sessionPlaying.getOrDefault(pkg,0L)<20_000) return;
        journal.sample(pkg,title,mediaId,playing,duration,now,source); save();
    }
}
