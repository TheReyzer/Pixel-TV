package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks the public GitHub manifest and keeps update state local to this TV. */
final class UpdateManager {
    static final String MANIFEST_URL="https://raw.githubusercontent.com/TheReyzer/Pixel-TV/main/update.json";
    private static final String PREFS="tvhome_updates";
    private static final String JSON="available_json";
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());

    static final class Update {
        final int versionCode; final String versionName,apkUrl,summary; final List<String> changes;
        Update(int code,String name,String apk,String summary,List<String> changes) {
            versionCode=code; versionName=name; apkUrl=apk; this.summary=summary; this.changes=changes;
        }
        static Update from(String raw) throws Exception {
            JSONObject o=new JSONObject(raw); JSONArray a=o.optJSONArray("changelog");
            List<String> c=new ArrayList<>();
            if (a!=null) for (int i=0;i<a.length();i++) c.add(a.optString(i));
            return new Update(o.getInt("versionCode"),o.optString("versionName"),o.optString("apkUrl"),o.optString("summary"),c);
        }
    }

    interface Callback { void done(Update update); }

    static void schedule(Context context) {
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (alarm==null) return;
        for (int hour:new int[]{9,21}) {
            Calendar next=Calendar.getInstance(); next.set(Calendar.HOUR_OF_DAY,hour); next.set(Calendar.MINUTE,0); next.set(Calendar.SECOND,0); next.set(Calendar.MILLISECOND,0);
            if (next.getTimeInMillis()<=System.currentTimeMillis()) next.add(Calendar.DATE,1);
            Intent intent=new Intent(context,UpdateCheckReceiver.class).setAction("com.gathin.tvhome.UPDATE_CHECK_"+hour);
            PendingIntent pending=PendingIntent.getBroadcast(context,9000+hour,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),AlarmManager.INTERVAL_DAY,pending);
        }
    }

    static void checkIfDue(Context context,Callback callback) {
        String windowKey=Calendar.getInstance().get(Calendar.YEAR)+"-"+Calendar.getInstance().get(Calendar.DAY_OF_YEAR)+"-"+(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)<14?"morning":"night");
        android.content.SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if (windowKey.equals(p.getString("last_window",""))) { if (callback!=null) main.post(() -> callback.done(available(context))); return; }
        p.edit().putString("last_window",windowKey).apply(); checkNow(context,callback);
    }

    static void checkNow(Context context,Callback callback) {
        worker.execute(() -> {
            Update found=null;
            try {
                android.util.Log.d("TVHomeUpdate","checking "+MANIFEST_URL);
                HttpURLConnection c=(HttpURLConnection)new URL(MANIFEST_URL).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setUseCaches(false);
                InputStream in=c.getInputStream(); StringBuilder b=new StringBuilder(); byte[] buf=new byte[4096]; int n;
                while ((n=in.read(buf))!=-1) b.append(new String(buf,0,n,java.nio.charset.StandardCharsets.UTF_8));
                in.close(); c.disconnect();
                Update candidate=Update.from(b.toString());
                if (candidate.versionCode>BuildConfig.VERSION_CODE && !candidate.apkUrl.isEmpty()) {
                    context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(JSON,b.toString()).apply(); found=candidate;
                } else context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(JSON).apply();
                android.util.Log.d("TVHomeUpdate","remote="+candidate.versionCode+" local="+BuildConfig.VERSION_CODE+" available="+(found!=null));
            } catch (Exception error) { android.util.Log.e("TVHomeUpdate","check failed",error); found=available(context); }
            Update result=found; if (callback!=null) main.post(() -> callback.done(result));
        });
    }

    static Update available(Context context) {
        String raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(JSON,null);
        if (raw==null) return null;
        try { Update u=Update.from(raw); return u.versionCode>BuildConfig.VERSION_CODE?u:null; } catch (Exception ignored) { return null; }
    }

    static void download(Context context,Update update,DownloadCallback callback) {
        worker.execute(() -> {
            File target=new File(context.getCacheDir(),"pixel-tv-update.apk");
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(update.apkUrl).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(20000); c.connect();
                int total=c.getContentLength(); int done=0; byte[] buf=new byte[8192];
                InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(target,false); int n;
                while ((n=in.read(buf))!=-1) { out.write(buf,0,n); done+=n; int percent=total>0?done*100/total:-1; int p=percent; main.post(() -> callback.progress(p)); }
                out.close(); in.close(); c.disconnect(); main.post(() -> callback.success(target));
            } catch (Exception e) { target.delete(); main.post(() -> callback.error(e)); }
        });
    }

    interface DownloadCallback { void progress(int percent); void success(File apk); void error(Exception error); }
}
