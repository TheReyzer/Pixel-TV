package com.gathin.tvhome;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import java.util.ArrayList;
import java.util.List;

/** Notification access is used only to observe media sessions; notification contents are never read. */
public final class PlaybackMonitorService extends NotificationListenerService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers=new ArrayList<>();
    private MediaSessionManager manager;
    private final MediaSessionManager.OnActiveSessionsChangedListener changed=this::bind;
    private final MediaController.Callback callback=new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { read(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { read(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };
    private final Runnable tick=new Runnable() { @Override public void run() { read(); handler.postDelayed(this,10_000); } };
    @Override public void onListenerConnected() {
        super.onListenerConnected(); manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        if (manager==null) return;
        try { manager.addOnActiveSessionsChangedListener(changed,new ComponentName(this,PlaybackMonitorService.class),handler); refresh(); handler.removeCallbacks(tick); handler.post(tick); android.util.Log.d("TVHomePlayback","OBSERVER connected"); }
        catch (SecurityException e) { android.util.Log.w("TVHomePlayback","Media session access unavailable",e); }
    }
    private void refresh() { if (manager!=null) try { bind(manager.getActiveSessions(new ComponentName(this,PlaybackMonitorService.class))); } catch (SecurityException e) { android.util.Log.w("TVHomePlayback","Media access revoked",e); } }
    private void bind(List<MediaController> sessions) {
        for (MediaController controller:controllers) controller.unregisterCallback(callback); controllers.clear();
        if (sessions!=null) for (MediaController controller:sessions) if (PlaybackJournal.APPS.containsKey(controller.getPackageName())) { controllers.add(controller); controller.registerCallback(callback,handler); }
        read();
    }
    private void read() {
        for (MediaController controller:new ArrayList<>(controllers)) try {
            MediaMetadata metadata=controller.getMetadata(); PlaybackState state=controller.getPlaybackState();
            String title="",id=""; long duration=0;
            if (metadata!=null) {
                title=metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE); if (title==null||title.trim().isEmpty()) title=metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                id=metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID); duration=metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (id!=null&&id.matches(".*tt\\d+:\\d+:\\d+.*")) { String album=metadata.getString(MediaMetadata.METADATA_KEY_ALBUM); if (album!=null&&!album.isEmpty()) title=album; }
            }
            boolean playing=state!=null&&state.getState()==PlaybackState.STATE_PLAYING&&state.getPlaybackSpeed()>0;
            WatchMonitor.get(this).observe(controller.getPackageName(),title,id,playing,duration,"session");
        } catch (RuntimeException e) { android.util.Log.w("TVHomePlayback","Session observation unavailable",e); }
    }
    @Override public void onListenerDisconnected() { stopObserving(); super.onListenerDisconnected(); }
    @Override public void onDestroy() { stopObserving(); super.onDestroy(); }
    private void stopObserving() { handler.removeCallbacksAndMessages(null); for (MediaController controller:controllers) controller.unregisterCallback(callback); controllers.clear(); if (manager!=null) manager.removeOnActiveSessionsChangedListener(changed); }
}
