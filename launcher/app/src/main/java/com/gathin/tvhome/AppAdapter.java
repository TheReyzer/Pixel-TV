package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for key presses on a tile. Return true when handled. */
        boolean onTileKey(int position, int keyCode, KeyEvent event);

        /** Called when a tile is clicked (OK pressed). */
        void onTileClicked(int position);

        /** Called when OK is long-pressed on a tile. */
        void onTileLongPressed(int position, View card);

        /** Called right when an app is launched. */
        void onAppLaunched(String pkg, String label, ComponentName component);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    private static final String PREFS_NAME = "tvhome_prefs";
    private static final String KEY_ORDER = "ordered_pkgs";

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Default dock order (res/values/config.xml, dock_apps). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();
    private boolean drawerMode = false;
    private static final List<Entry> processCache=new ArrayList<>();
    private static long lastScan;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService loader=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean loading,closed,reloadPending;
    private final android.content.BroadcastReceiver packages=new android.content.BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent) { reload(); }
    };

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.dockSize = dockSize;
        DeviceConfig config=DeviceConfig.load(ctx);
        this.favourites = config.dockApps.isEmpty()?Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)):config.dockApps;
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        this.hidden.addAll(config.hiddenApps);
        String liveTv=config.liveTvPackage(ctx); if (!liveTv.isEmpty()) hidden.add(liveTv);
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
        items.addAll(processCache);
        android.content.IntentFilter changes=new android.content.IntentFilter();
        changes.addAction(Intent.ACTION_PACKAGE_ADDED); changes.addAction(Intent.ACTION_PACKAGE_REMOVED); changes.addAction(Intent.ACTION_PACKAGE_CHANGED); changes.addDataScheme("package");
        this.ctx.registerReceiver(packages,changes);
    }

    void setDrawerMode(boolean drawerMode) {
        if (this.drawerMode == drawerMode) return;
        this.drawerMode = drawerMode;
        notifyDataSetChanged();
    }

    boolean isDrawerMode() {
        return drawerMode;
    }

    private int toRealPos(int adapterPos) {
        return drawerMode ? (adapterPos + dockSize) : adapterPos;
    }

    private List<String> getSavedOrder() {
        String raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ORDER, null);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        return Arrays.asList(raw.split(","));
    }

    private void saveOrder() {
        processCache.clear(); processCache.addAll(items);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(items.get(i).pkg);
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ORDER, sb.toString())
                .apply();
    }

    void resetOrder() {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ORDER)
                .apply();
        reload();
    }

    void swapItems(int fromPos, int toPos) {
        int rf = toRealPos(fromPos);
        int rt = toRealPos(toPos);
        if (rf < 0 || rf >= items.size() || rt < 0 || rt >= items.size() || rf == rt) return;
        Collections.swap(items, rf, rt);
        saveOrder();
        notifyDataSetChanged();
    }

    void pinToDock(int fromPos) {
        int rf = toRealPos(fromPos);
        if (rf < 0 || rf >= items.size()) return;
        Entry e = items.remove(rf);
        items.add(0, e);
        saveOrder();
        notifyDataSetChanged();
    }

    void reloadIfNeeded() {
        if (items.isEmpty()||android.os.SystemClock.elapsedRealtime()-lastScan>60000) reload();
    }
    boolean readyForStartup() { return !items.isEmpty()||!loading; }
    void destroy() { closed=true; main.removeCallbacksAndMessages(null); loader.shutdownNow(); ctx.unregisterReceiver(packages); }
    void reload() {
        if (closed) return;
        if (loading) { reloadPending=true; return; }
        loading=true;
        loader.execute(() -> {
            List<Entry> fresh;
            try { fresh=scanApps(); }
            catch (RuntimeException error) { main.post(() -> { loading=false; if (reloadPending) { reloadPending=false; reload(); } }); return; }
            main.post(() -> {
                if (closed) return;
                loading=false; applyApps(fresh); lastScan=android.os.SystemClock.elapsedRealtime();
                processCache.clear(); processCache.addAll(items);
                if (reloadPending) { reloadPending=false; reload(); }
            });
        });
    }
    private List<Entry> scanApps() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        return fresh;
    }
    private void applyApps(List<Entry> fresh) {
        final List<String> customOrder = new ArrayList<>(getSavedOrder());
        Collections.sort(fresh, (a, b) -> {
            if (!customOrder.isEmpty()) {
                int ca = customOrder.indexOf(a.pkg), cb = customOrder.indexOf(b.pkg);
                if (ca >= 0 || cb >= 0) {
                    if (ca < 0) return 1;
                    if (cb < 0) return -1;
                    return Integer.compare(ca, cb);
                }
            }
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        int real = toRealPos(position);
        Entry e = items.get(real);
        h.name.setText(e.label);
        h.name.setVisibility(!drawerMode ? View.GONE : View.VISIBLE);   // the dock has no labels
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onTileClicked(pos);
            }
        });
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onTileLongPressed(pos, v);
            }
            return true;
        });
    }

    void launchAt(int position) {
        int real = toRealPos(position);
        if (real >= 0 && real < items.size()) {
            launch(items.get(real));
        }
    }

    void openAppInfo(int position) {
        int real = toRealPos(position);
        if (real < 0 || real >= items.size()) return;
        Entry e = items.get(real);
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            if (listener != null) listener.onAppLaunched(e.pkg, e.label, e.component);
            ctx.startActivity(i);
        } catch (Exception ex) {
            ThemedNotice.show(ctx,R.drawable.ic_settings,ctx.getString(R.string.cant_open,e.label),null,false,false);
        }
    }

    List<String> getAllPackages() {
        List<String> pkgs = new ArrayList<>(items.size());
        for (Entry e : items) pkgs.add(e.pkg);
        return pkgs;
    }

    @Override
    public int getItemCount() {
        if (drawerMode) {
            return Math.max(0, items.size() - dockSize);
        }
        return Math.min(dockSize, items.size());
    }

    @Override
    public long getItemId(int position) {
        int real = toRealPos(position);
        return items.get(real).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        int real = toRealPos(position);
        return real >= 0 && real < items.size() ? items.get(real).component.flattenToShortString() : null;
    }

    /** Adapter position of the app with this key in the current mode, or -1 when not in current mode. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < getItemCount(); i++) {
            int real = toRealPos(i);
            if (real >= 0 && real < items.size() && items.get(real).component.flattenToShortString().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    String labelAt(int position) {
        int real = toRealPos(position);
        return real >= 0 && real < items.size() ? items.get(real).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        int real = toRealPos(position);
        if (real < 0 || real >= items.size()) return 0xFFE50914;
        Entry e = items.get(real);
        if (e.ambient != 0) return e.ambient;

        String pkg = (e.component != null && e.component.getPackageName() != null)
                ? e.component.getPackageName().toLowerCase() : "";
        if (pkg.contains("netflix")) e.ambient = 0xFFE50914; // Netflix Red
        else if (pkg.contains("youtube") || pkg.contains("smarttube")) e.ambient = 0xFFFF0000; // YouTube Red
        else if (pkg.contains("amazon") || pkg.contains("primevideo")) e.ambient = 0xFF00A8E1; // Prime Cyan
        else if (pkg.contains("wbd.stream") || pkg.contains("hbo") || pkg.contains("max")) e.ambient = 0xFF6C20E0; // Max Purple
        else if (pkg.contains("globo")) e.ambient = 0xFFFF5500; // Globoplay Orange
        else if (pkg.contains("stremio")) e.ambient = 0xFF7B52F2; // Stremio Violet
        else if (pkg.contains("unitv")) e.ambient = 0xFFFF7A00; // UniTV Orange
        else if (pkg.contains("spotify")) e.ambient = 0xFF1DB954; // Spotify Green
        else if (pkg.contains("disney")) e.ambient = 0xFF113CCF; // Disney Blue
        else if (pkg.contains("apple")) e.ambient = 0xFF00D2FF; // Apple Cyan
        else if (pkg.contains("twitch")) e.ambient = 0xFF9146FF; // Twitch Purple
        else if (pkg.contains("pluto")) e.ambient = 0xFFFFCC00; // Pluto Yellow
        else {
            e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        }
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFFE50914;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 20;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFFE50914;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        int validCount = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 120) continue; // Ignore transparent pixels
                validCount++;
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                // Score colorful pixels (high saturation, good brightness)
                float score = hsv[1] * 1.5f + (hsv[2] > 0.35f ? 0.6f : 0f);
                if (hsv[1] > 0.20f && score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f && validCount > 0) {
            int avg = Color.rgb((int) (r / validCount), (int) (g / validCount), (int) (b / validCount));
            Color.colorToHSV(avg, hsv);
            hsv[1] = Math.max(hsv[1], 0.65f);
            hsv[2] = Math.max(hsv[2], 0.60f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.max(hsv[1], 0.70f);
        hsv[2] = Math.max(0.60f, Math.min(0.95f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = density * 18f;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileKey(pos, keyCode, event);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s)
                        .setDuration(focused ? 260 : 180)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                        .start();
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
