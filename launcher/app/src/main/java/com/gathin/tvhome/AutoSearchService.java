package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * AccessibilityService that automatically opens the search screen, injects the movie/series title
 * into the search field, and presses OK/Search in UniTV (com.global.unitviptv) and other apps.
 */
public class AutoSearchService extends AccessibilityService {

    private static final String PREFS = "pending_watch_request";
    private static volatile String pendingPkg, pendingQuery, pendingYear, pendingLink;
    private static volatile long expireAtMs;
    private static volatile boolean profileSeen;
    private static volatile AutoSearchService instance;
    private boolean textInjected, searchSubmitted, pollQueued;
    private long lastSearchClick, profileLeftAt;
    private String targetWindow="";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable watchTick=new Runnable() {
        @Override public void run() {
            try { observeUniTv(); } catch (RuntimeException e) { android.util.Log.w("TVHomePlayback","UniTV observation unavailable",e); }
            handler.postDelayed(this,10_000);
        }
    };
    private static String visibleText(AccessibilityNodeInfo root,String id) {
        List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(id);
        try { for (AccessibilityNodeInfo node:nodes) if (node.isVisibleToUser()&&node.getText()!=null) { String text=node.getText().toString().trim(); if (!text.isEmpty()&&!text.matches("[\\d\\s-]+")) return PlaybackJournal.clean(text); } }
        finally { for (AccessibilityNodeInfo node:nodes) node.recycle(); }
        return "";
    }
    private void observeUniTv() {
        if (pendingQuery!=null) return;
        AccessibilityNodeInfo root=getRootInActiveWindow(); if (root==null) return;
        try {
            if (!"com.global.unitviptv".contentEquals(root.getPackageName())) return;
            String title=visibleText(root,"com.global.unitviptv:id/mTextTitle");
            if (title.isEmpty()) return;
            List<AccessibilityNodeInfo> players=root.findAccessibilityNodeInfosByViewId("com.global.unitviptv:id/mPlayerWindow");
            boolean visible=false; try { for (AccessibilityNodeInfo player:players) if (player.isVisibleToUser()) { visible=true; break; } } finally { for (AccessibilityNodeInfo player:players) player.recycle(); }
            String episode=visibleText(root,"com.global.unitviptv:id/mTextUpdate").replace('\u00a0',' ');
            android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
            // shortcut: UniTV exposes no playback state; player visibility plus audio is a hint requiring user confirmation.
            WatchMonitor.get(this).observe("com.global.unitviptv",title,episode.matches("(?i).*S\\s*\\d+.*E\\s*\\d+.*")?"series":"movie",visible&&audio!=null&&audio.isMusicActive(),0,"screen");
        } finally { root.recycle(); }
    }
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            pollQueued=false;
            if (pendingQuery == null) return;
            if (pendingLink != null && !pendingLink.isEmpty() && !profileSeen
                    && System.currentTimeMillis() > expireAtMs - 4 * 60_000L) { cancelPending(AutoSearchService.this); return; }
            if (System.currentTimeMillis() > expireAtMs) { cancelPending(AutoSearchService.this); return; }
            try { attemptSearchStep(); }
            catch (Exception e) { android.util.Log.w("TVHomeWatch", "Navigation step unavailable", e); }
            if (pendingQuery != null) schedulePoll("com.global.unitviptv".equals(pendingPkg)?180:450);
        }
    };
    private void schedulePoll(long delay) {
        if (pollQueued) return;
        pollQueued=true; handler.postDelayed(pollTask,delay);
    }

    public static void enqueueSearch(android.content.Context context, String pkg, String title, String year) {
        enqueue(context, pkg, title, year, "");
    }

    public static void enqueueDeepLink(android.content.Context context, String pkg, String title, String year, String link) {
        if (!WatchAvailability.allowedLink(pkg, link)) return;
        enqueue(context, pkg, title, year, link);
    }

    private static void enqueue(android.content.Context context, String pkg, String title, String year, String link) {
        cancelPending(context);
        if (title == null || title.trim().isEmpty()) return;
        pendingPkg = pkg;
        pendingQuery = title.trim();
        pendingYear = year == null ? "" : year;
        pendingLink = link;
        profileSeen = false;
        expireAtMs = System.currentTimeMillis() + 5 * 60_000L;
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("pkg", pkg)
                .putString("title", pendingQuery).putString("year", pendingYear).putString("link", link)
                .putLong("expires", expireAtMs).putBoolean("profile", false).apply();
        AutoSearchService svc = instance;
        if (svc != null) svc.schedulePoll(0);
        android.util.Log.d("TVHomeWatch", "ENQUEUED package=" + pkg + " nativeLink=" + !link.isEmpty() + " service=" + (svc != null));
    }

    public static void cancelPending(android.content.Context context) {
        pendingQuery = pendingPkg = pendingYear = pendingLink = null;
        profileSeen = false;
        AutoSearchService svc = instance;
        if (svc != null) {
            svc.handler.removeCallbacks(svc.pollTask);
            svc.pollQueued=false;
            svc.textInjected = svc.searchSubmitted = false;
            svc.lastSearchClick = svc.profileLeftAt = 0;
            svc.targetWindow="";
        }
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        handler.removeCallbacks(watchTick); handler.post(watchTick);
        android.util.Log.d("TVHomeWatch", "NAVIGATION_SERVICE connected");
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (pendingQuery == null && prefs.getLong("expires", 0) > System.currentTimeMillis()) {
            pendingPkg = prefs.getString("pkg", "");
            pendingQuery = prefs.getString("title", null);
            pendingYear = prefs.getString("year", "");
            pendingLink = prefs.getString("link", "");
            expireAtMs = prefs.getLong("expires", 0);
            profileSeen = prefs.getBoolean("profile", false);
            if (!pendingLink.isEmpty() && !WatchAvailability.allowedLink(pendingPkg, pendingLink)) cancelPending(this);
        }
        if (pendingQuery != null) schedulePoll(0);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event!=null&&event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) WatchMonitor.get(this).foreground(String.valueOf(event.getPackageName()));
        if (pendingQuery == null || event == null || !pendingPkg.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        // Coalesce content events without postponing an already queued attempt.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String name=String.valueOf(event.getClassName());
            if ("com.global.unitviptv".equals(pendingPkg)&&(name.endsWith(".HomeActivity")||name.endsWith(".VodSearchActivity")||name.endsWith(".WelcomeActivity"))) {
                targetWindow=name; android.util.Log.d("TVHomeWatch","UNITV_WINDOW "+name);
            }
            handler.removeCallbacks(pollTask); pollQueued=false; schedulePoll(0);
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) schedulePoll(80);
    }

    @Override public void onInterrupt() { }


    private void attemptSearchStep() {
        final String title = pendingQuery, pkg = pendingPkg;
        if (title == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        try {
            if (!pkg.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return;
            if (pkg.equals("com.global.unitviptv") && fastUniTvStep(root,title)) return;
            collectNodes(root, nodes);
            if (!pkg.equals("com.global.unitviptv") && !pkg.equals("com.stremio.one") && isProfileSelectionScreen(nodes)) {
                profileSeen = true;
                profileLeftAt = 0;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("profile", true).apply();
                return;
            }
            if (isAccountGate(nodes)) return;
            if (pendingLink != null && !pendingLink.isEmpty()) {
                if (!profileSeen) return;
                long now = android.os.SystemClock.elapsedRealtime();
                if (profileLeftAt == 0) { profileLeftAt = now; return; }
                if (now - profileLeftAt < 900) return;
                String link = pendingLink;
                cancelPending(this);
                android.util.Log.d("TVHomeWatch", "AFTER_PROFILE package=" + pkg);
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                        .setPackage(pkg).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pkg.equals("com.netflix.ninja")) intent.putExtra("source", "30");
                startActivity(intent);
                return;
            }
            if (textInjected) {
                if (pkg.equals("com.global.unitviptv") && findSearchInputNode(nodes) == null) { cancelPending(this); return; }
                AccessibilityNodeInfo result = findMatchingResult(nodes, title, pendingYear);
                if (result != null) {
                    if (clickNodeOrParent(result)) {
                        android.util.Log.d("TVHomeWatch", "MATCHED_RESULT package=" + pkg + " title=" + title);
                        cancelPending(this);
                    }
                    return;
                }
                if (!searchSubmitted) {
                    AccessibilityNodeInfo submit = findSearchConfirmButton(nodes);
                    if (submit != null) {
                        searchSubmitted = clickNodeOrParent(submit);
                        android.util.Log.d("TVHomeWatch", "SEARCH_SUBMITTED success=" + searchSubmitted);
                    }
                }
                return;
            }
            AccessibilityNodeInfo input = findSearchInputNode(nodes);
            if (input != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title);
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    textInjected = true;
                    android.util.Log.d("TVHomeWatch", "SEARCH_INJECTED package=" + pkg + " title=" + title);
                }
                return;
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastSearchClick < 1800) return;
            AccessibilityNodeInfo search = findHomeSearchTrigger(nodes);
            if (search != null) {
                lastSearchClick = now;
                android.util.Log.d("TVHomeWatch", "SEARCH_TRIGGER id=" + search.getViewIdResourceName());
                clickNodeOrParent(search);
            }
        } finally {
            for (AccessibilityNodeInfo node : nodes) if (node != root) node.recycle();
            root.recycle();
        }
    }

    /** Direct known-ID lookups avoid hundreds of remote accessibility calls on UniTV's live home. */
    private boolean fastUniTvStep(AccessibilityNodeInfo root,String title) {
        if (!textInjected&&targetWindow.endsWith(".WelcomeActivity")) return true;
        if (!textInjected&&targetWindow.endsWith(".HomeActivity")) { clickUniTvHomeSearch(root); return true; }
        List<AccessibilityNodeInfo> inputs=root.findAccessibilityNodeInfosByViewId("com.global.unitviptv:id/etSearchInput");
        try {
            for (AccessibilityNodeInfo input:inputs) if (input.isVisibleToUser()&&!input.isPassword()) {
                if (!textInjected) {
                    Bundle args=new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,title);
                    textInjected=input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
                    if (textInjected) android.util.Log.d("TVHomeWatch","SEARCH_INJECTED package=com.global.unitviptv title="+title);
                    return true;
                }
                if (!searchSubmitted) {
                    List<AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByViewId("com.global.unitviptv:id/mBtKeySearch");
                    try { for (AccessibilityNodeInfo button:buttons) if (button.isVisibleToUser()&&clickNodeOrParent(button)) { searchSubmitted=true; android.util.Log.d("TVHomeWatch","SEARCH_SUBMITTED success=true"); break; } }
                    finally { for (AccessibilityNodeInfo button:buttons) button.recycle(); }
                    if (!searchSubmitted) return false;
                }
                List<AccessibilityNodeInfo> results=root.findAccessibilityNodeInfosByViewId("com.global.unitviptv:id/tvVideoName");
                try {
                    AccessibilityNodeInfo match=findMatchingResult(results,title,pendingYear);
                    if (match!=null&&clickNodeOrParent(match)) { android.util.Log.d("TVHomeWatch","MATCHED_RESULT package=com.global.unitviptv title="+title); cancelPending(this); }
                } finally { for (AccessibilityNodeInfo result:results) result.recycle(); }
                return true;
            }
        } finally { for (AccessibilityNodeInfo input:inputs) input.recycle(); }
        if (textInjected) { cancelPending(this); return true; }
        return clickUniTvHomeSearch(root);
    }
    private boolean clickUniTvHomeSearch(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByViewId("com.global.unitviptv:id/mIvSearch");
        try {
            for (AccessibilityNodeInfo button:buttons) if (button.isVisibleToUser()) {
                long now=android.os.SystemClock.elapsedRealtime();
                if (now-lastSearchClick<600) return true;
                if (clickNodeOrParent(button)) { lastSearchClick=now; android.util.Log.d("TVHomeWatch","SEARCH_TRIGGER id="+button.getViewIdResourceName()); }
                return true;
            }
        } finally { for (AccessibilityNodeInfo button:buttons) button.recycle(); }
        return false;
    }

    private static void collectNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> nodes) {
        if (nodes.size() >= 600) { if (node != null) node.recycle(); return; }
        nodes.add(node);
        if (!node.isVisibleToUser()) return;
        for (int i = 0; i < node.getChildCount() && nodes.size() < 600; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectNodes(child, nodes);
        }
    }

    private static boolean isProfileSelectionScreen(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isVisibleToUser() && (WatchNavigation.isProfilePrompt(String.valueOf(node.getText()))
                    || WatchNavigation.isProfilePrompt(String.valueOf(node.getContentDescription())))) return true;
        }
        return false;
    }

    private static boolean isAccountGate(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isVisibleToUser() && (node.isPassword() || WatchNavigation.isAccountGate(String.valueOf(node.getText()))
                    || WatchNavigation.isAccountGate(String.valueOf(node.getContentDescription())))) return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo findMatchingResult(List<AccessibilityNodeInfo> nodes, String title, String year) {
        AccessibilityNodeInfo match = null;
        android.graphics.Rect matchedBounds = new android.graphics.Rect();
        for (AccessibilityNodeInfo node : nodes) {
            String id = viewId(node);
            if (!node.isVisibleToUser() || node.isEditable()
                    || !(id.endsWith("tvvideoname") || id.contains("result") || id.contains("title"))) continue;
            if (!WatchNavigation.titleMatches(String.valueOf(node.getText()), title, year)
                    && !WatchNavigation.titleMatches(String.valueOf(node.getContentDescription()), title, year)) continue;
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);
            if (match != null && !matchedBounds.equals(bounds)) return null;
            match = node;
            matchedBounds.set(bounds);
        }
        return match;
    }

    private static boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; current != null && i < 6; i++) {
            boolean owned = current != node;
            if (current.isClickable() && current.isVisibleToUser() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (owned) current.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (owned) current.recycle();
            current = parent;
        }
        if (current != null && current != node) current.recycle();
        return false;
    }

    private static String viewId(AccessibilityNodeInfo node) {
        return node.getViewIdResourceName() == null ? "" : node.getViewIdResourceName().toLowerCase(java.util.Locale.ROOT);
    }

    private static AccessibilityNodeInfo findSearchInputNode(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            String id = viewId(node);
            if (node.isVisibleToUser() && !node.isPassword() && (id.endsWith("etsearchinput")
                    || id.endsWith("meditsearch") || id.endsWith("search_src_text") || id.endsWith("lb_search_text_editor")
                    || id.endsWith("bg_vod_search_edit") || node.isEditable() && id.contains("search"))) return node;
        }
        return null;
    }

    private static AccessibilityNodeInfo findHomeSearchTrigger(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            if (!node.isVisibleToUser()) continue;
            String id = viewId(node);
            String text = WatchNavigation.normalize(String.valueOf(node.getText()));
            String description = WatchNavigation.normalize(String.valueOf(node.getContentDescription()));
            if (id.endsWith("mivsearch") || id.endsWith("mllsearchfocus")
                    || id.endsWith("tvsearch") || id.endsWith("ivsearchhint") || id.endsWith("searchicon")
                    || id.endsWith("live_tab_search") || id.endsWith("search_orb") || id.contains("home_search")
                    || text.matches("pesquisar|buscar|busca|search") || description.matches("pesquisar|buscar|search")) return node;
        }
        return null;
    }

    private static AccessibilityNodeInfo findSearchConfirmButton(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            if (!node.isVisibleToUser()) continue;
            String id = viewId(node);
            String text = WatchNavigation.normalize(String.valueOf(node.getText()));
            if (id.endsWith("mbtkeysearch") || id.endsWith("bg_vod_search_search")
                    || id.endsWith("search_go_btn") || id.endsWith("search_button")
                    || text.matches("buscar|pesquisar")) return node;
        }
        return null;
    }
}
