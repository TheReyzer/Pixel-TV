package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.SearchManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 * Capabilities and local configuration adapt the launcher to the connected Android TV.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private static final long WEATHER_REFRESH_MS = 30 * 60_000L;
    private static final String PREFS_NAME = "tvhome_prefs";

    private DeviceConfig deviceConfig;

    private String liveTvPkg;

    private AppAdapter adapter;
    private DockDecoration dockDecoration;
    private RecyclerView grid;
    private FrameLayout root;
    private View mainContentColumn;
    private View topHeaderBar;
    private View btnScrollDownApps;
    private Drawable dockBackground;
    private View btnScrollUpHome;
    private boolean inAppsDrawer = false;
    private int lastDockColumn = 0;
    private int appFocusGeneration,drawerTransitionGeneration;
    private boolean appsTransitioning;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private TextView dateView;
    private TextView weatherView;
    private final HomeControls homeControls=new HomeControls();
    private TextView btnWifi;
    private TextView btnSettings;
    private ImageView btnProfile;
    private ImageView updateIndicator;
    private UpdateManager.Update availableUpdate;
    private PopupWindow updateNoticePopup;
    private View updateWelcomeOverlay;
    private ProgressBar updateDownloadProgress;
    private TextView updateDownloadStatus;
    private int updateNoticeVersion=-1;
    private ProfileController profiles;
    private WatchFeedbackController watchFeedback;
    private TitleSearchController titleSearch;
    private boolean searchToolbarMoving,mediaContentMoving;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;

    // Media Showcase (‹ [    ] [  ] [  ] ›) & Full Detail View + Inline Embedded Trailer
    private MediaRepository mediaRepo;
    private boolean homeResumed;
    private boolean pendingHomePageAdvance;
    private View mediaSection;
    private boolean mediaSectionVisible = true;
    private ImageView mediaBackdropBg;
    private MediaBackdropController mediaBackground;
    private View mediaBackdropScrim;
    private View normalHomeContainer;
    private View mediaDetailContainer;
    private final View[] filterTabs = new View[11];
    private DiscoveryController discovery;
    private DiscoveryQuery discoveryQuery;
    private List<MediaRepository.MediaItem> discoveryResults;
    private String discoveryNotice="";
    private boolean watchLaterActive,browsingCatalog,gridLoading,searchResultsActive,historyActive;
    private String browseType="all",gridMoreLabel="Descobrir mais resultados";
    private int gridGeneration,catalogSkip;
    private final java.util.concurrent.ExecutorService catalogWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> catalogRequest;
    private DiscoveryGrid discoveryGrid;
    private RecommendationRail recommendationRail,relatedRail;
    private final java.util.concurrent.ExecutorService relatedWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> relatedRequest;
    private int relatedGeneration,relatedContentTop=-1;
    private List<MediaRepository.MediaItem> relatedItems=java.util.Collections.emptyList();
    private final java.util.ArrayDeque<RelatedDetailState> detailParents=new java.util.ArrayDeque<>();
    private static final class RelatedDetailState {
        final MediaRepository.MediaItem item; final List<MediaRepository.MediaItem> related; final int selected,scroll;
        RelatedDetailState(MediaRepository.MediaItem item,List<MediaRepository.MediaItem> related,int selected,int scroll) { this.item=item; this.related=new ArrayList<>(related); this.selected=selected; this.scroll=scroll; }
    }
    private boolean historyWhenReady;
    private final MediaRepository.MediaItem[] currentTrio = new MediaRepository.MediaItem[3];
    private int currentMediaCategory = MediaRepository.CAT_NOVIDADES;
    private int currentMediaType = MediaRepository.TYPE_ALL;
    private int lastFilterTab;
    private final int[] categoryPageOffset = new int[7];
    private String recommendationProfileId;
    private boolean varyProfilePage;
    private long recommendationFeedbackVersion;
    private boolean pendingProfileRecommendations;
    private java.util.Set<String> profilePageAvoid;
    private java.util.Set<String> profilePagePrevious;
    private List<MediaRepository.MediaItem> variedProfileRanking;
    private List<MediaRepository.MediaItem> variedProfileItems;
    private final java.util.Random recommendationRandom=new java.util.Random();
    private final java.util.Map<String,java.util.Set<String>> lastProfileRecommendations=new java.util.HashMap<>();
    private SharedPreferences recommendationExposurePrefs;
    private java.util.Set<String> lastVisibleRecommendations=new java.util.LinkedHashSet<>();
    private boolean gridBackAtOrigin;
    private boolean inMediaDetailView = false;
    private int lastClickedMediaCardIdx = 0;
    private MediaRepository.MediaItem detailMediaItem;
    private View detailPosterCard;
    private ImageView detailPoster;
    private View trailerModalOverlay;
    private View trailerModalPanel;
    private View trailerVideoBox;
    private PlayerView trailerModalPlayerView;
    private MediaRepository.MediaItem activeTrailerItem;
    private boolean trailerYoutubeAttempted;
    private ExoPlayer trailerPlayer;
    private Runnable trailerTimeout;
    private int trailerLanguageMode = 0;
    private boolean trailerLanguageInitialized;
    private String trailerOriginalLanguage = "en";
    private TextView trailerModalTitle;
    private TextView btnModalRewind, btnModalPlayPause, btnModalForward, btnModalLang, btnModalFullscreen;
    private View btnModalClose, trailerModalHeader, trailerModalControls;
    private View trailerProgressRow, trailerFullscreenControls, btnFullscreenClose;
    private LinearLayout trailerFullscreenBottom;
    private LinearLayout.LayoutParams trailerProgressLayout;
    private int trailerProgressIndex, fullscreenRevealKey = -1;
    private ProgressBar trailerProgress;
    private TextView trailerElapsed, trailerDuration, btnFullscreenRewind, btnFullscreenPlayPause, btnFullscreenForward;
    private boolean trailerFullscreen;
    private final Runnable hideFullscreenControls = () -> {
        if (!trailerFullscreen) return;
        trailerFullscreenControls.setVisibility(View.INVISIBLE);
        trailerModalOverlay.requestFocus();
        android.util.Log.d("TVHomeTrailer", "CONTROLS hidden");
    };
    private final Runnable updateTrailerProgress = new Runnable() {
        @Override public void run() {
            if (!trailerModalActive) return;
            long duration = trailerPlayer == null ? 0 : Math.max(0, trailerPlayer.getDuration());
            long position = trailerPlayer == null ? 0 : Math.max(0, trailerPlayer.getCurrentPosition());
            trailerElapsed.setText(android.text.format.DateUtils.formatElapsedTime(position / 1000));
            trailerDuration.setText(android.text.format.DateUtils.formatElapsedTime(duration / 1000));
            trailerProgress.setProgress(duration > 0 ? (int) (Math.min(position, duration) * 1000 / duration) : 0);
            trailerProgress.setSecondaryProgress(trailerPlayer == null || duration == 0 ? 0
                    : (int) (Math.min(trailerPlayer.getBufferedPosition(), duration) * 1000 / duration));
            handler.postDelayed(this, 500);
        }
    };
    private FrameLayout.LayoutParams trailerPanelLayout;
    private LinearLayout.LayoutParams trailerVideoLayout;
    private Drawable trailerPanelBackground;
    private final Rect trailerPanelPadding = new Rect();
    private boolean trailerModalActive = false;
    private boolean trailerPlaying = true;
    private List<NativeTrailerSource.Candidate> currentTrailerCandidates = Collections.emptyList();
    private int currentTrailerIdx = 0;
    private View trailerCover;
    private ImageView trailerCoverImg, trailerCoverIcon;
    private TextView trailerCoverMsg;
    private View trailerLoadingGroup;
    private TextView trailerLoadingText;
    private View trailerSpinner;
    /** True only after the decoder renders its first frame. */
    private boolean trailerStarted = false;
    /** Invalidates callbacks from a previous modal session or candidate. */
    private int trailerLoadToken = 0;
    /** Candidates tried for the current trailer session (stops endless error/timeout fallback loops). */
    private int trailerAttempts = 0;
    private long trailerRequestedAt;
    /** Views hidden behind the modal (replaced by a static snapshot) to cut overdraw while video plays. */
    private final List<View> trailerHiddenViews = new ArrayList<>();
    private static final long TRAILER_LOAD_TIMEOUT_MS = 10000;
    private int currentAmbientColor = 0xFFE50914;
    private ValueAnimator ambientColorAnimator;
    private TextView detailTypeBadge;
    private TextView detailRatingBadge;
    private TextView detailYearGenre;
    private TextView detailTitle;
    private TextView detailSynopsis;
    private TextView btnWatchMedia;
    private TextView btnWatchTrailer;
    private TextView btnCloseDetail;

    private static int lastFocused = 0;
    private static String lastFocusedKey;
    private float density;

    // Weather cache
    private static String cachedWeatherSuffix = "";
    private static long lastWeatherFetchMs = 0L;

    // Last app switcher (2s Back hold) & Auto-Kill on return
    private static String lastLaunchedPkg;
    private static String lastLaunchedLabel;
    private static ComponentName lastLaunchedComponent;
    private String pendingAutoKillPkg;
    private boolean backLongPressHandled = false;
    private boolean menuLongPressHandled = false;

    // Night Volume Leveler (0 = Off, 1 = On, 2 = Auto 22h-07h)
    private static LoudnessEnhancer loudnessEnhancer;
    private static Equalizer nightEqualizer;
    private static boolean nightAudioActive = false;
    private boolean inNightVolumeSubmenu = false;

    // Wi-Fi drop/reconnect tracking
    private boolean netInitialized = false;
    private boolean netOnline = true;

    // HDMI hotplug detection
    private TvInputManager tvInputManager;
    private final Map<String, Integer> hdmiStates = new HashMap<>();
    private final Map<String, Runnable> pendingHdmiChecks = new HashMap<>();
    private PopupWindow hdmiPrompt;
    private String hdmiPromptInputId;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver clockReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (!startupVisible) pendingHomePageAdvance=true;
                if (activityResumed&&mediaRepo!=null&&!gridMode()) refreshMediaRecommendations();
                return;
            }
            updateGreeting();
            updateDateAndWeatherText();
            if (mediaRepo!=null) refreshMediaRecommendations();
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(() -> {
                if (netInitialized && !netOnline) {
                    netOnline = true;
                    showGlassNotice(R.drawable.ic_wifi, "Wi-Fi GFENIX", "Reconectado", true);
                }
                netInitialized = true;
                netOnline = true;
                updateWifiLabel();
                retryVisibleArtwork();
                fetchWeatherIfNeeded();
            });
        }

        @Override public void onCapabilitiesChanged(@NonNull Network network,@NonNull NetworkCapabilities caps) {
            handler.post(() -> updateWifiLabel());
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(() -> {
                if (netInitialized && netOnline) {
                    netOnline = false;
                    showGlassNotice(R.drawable.ic_wifi, "Wi-Fi GFENIX caiu", "Sem conexão", false);
                }
                updateWifiLabel();
            });
        }
    };

    private final TvInputManager.TvInputCallback tvInputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String inputId, int state) {
            if (tvInputManager == null || inputId == null) return;
            Integer prev = hdmiStates.put(inputId, state);
            android.util.Log.d("TVHomeHdmi", "STATE input=" + inputId + " previous=" + prev + " current=" + state);
            if (prev != null && prev == state) return;
            Runnable pending = pendingHdmiChecks.remove(inputId);
            if (pending != null) handler.removeCallbacks(pending);
            if (state != TvInputManager.INPUT_STATE_CONNECTED && inputId.equals(hdmiPromptInputId)) dismissHdmiPrompt();
            TvInputInfo info = tvInputManager.getTvInputInfo(inputId);
            if (info == null || !info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) return;
            if (prev == null || state != TvInputManager.INPUT_STATE_CONNECTED) return;
            Runnable check = () -> {
                pendingHdmiChecks.remove(inputId);
                if (!hasActiveHdmiSource(inputId)) return;
                CharSequence name = info.loadLabel(MainActivity.this);
                String label = name == null ? inputId : name.toString();
                if (!label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                showHdmiConnectedBanner(inputId, label);
            };
            pendingHdmiChecks.put(inputId, check);
            handler.postDelayed(check, 1500);
        }
    };

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateDateAndWeatherText();
            updateWifiLabel();
            applyNightAudioSchedule();
            fetchWeatherIfNeeded();
            if (mediaRepo!=null) refreshMediaRecommendations(true);
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    private long startupBegan,startupFirstFrameAt,startupContentReadyAt,startupDismissedAt;
    private View startupOverlay;
    private boolean homeInitialized,activityResumed,startupVisible=true,startupSearchPreparing,startupSearchPrepared;
    private final Runnable startupCheck=this::checkStartup;
    static boolean startupCanReveal(boolean contentReady,boolean artworkReady,long waitMillis) { return contentReady&&(artworkReady||waitMillis>=4000); }
    private void checkStartup() {
        if (!startupVisible||!homeInitialized||!activityResumed||isFinishing()||isDestroyed()) return;
        boolean contentReady=adapter.readyForStartup()&&recommendationRail!=null&&recommendationRail.readyForStartup(false);
        if (!startupSearchPrepared&&recommendationRail!=null&&recommendationRail.getChildCount()>0) {
            if (!startupSearchPreparing) {
                startupSearchPreparing=true;
                titleSearch.prepareFirstUse(() -> { startupSearchPrepared=true; handler.post(startupCheck); });
            }
            return;
        }
        long now=android.os.SystemClock.elapsedRealtime();
        if (contentReady&&startupContentReadyAt==0) startupContentReadyAt=now;
        if (startupCanReveal(contentReady,recommendationRail!=null&&recommendationRail.readyForStartup(true),startupContentReadyAt==0?0:now-startupContentReadyAt)) {
            startupVisible=false; startupDismissedAt=now; markStartupShown(); startupMark("home-visible");
            startupOverlay.animate().alpha(0).setDuration(180).withEndAction(() -> {
                if (startupOverlay.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)startupOverlay.getParent()).removeView(startupOverlay);
                if (!activityResumed||isDestroyed()) return;
                profiles.ensureFirstProfile();
                if ((profiles.dialog==null||!profiles.dialog.isShowing())&&hasWindowFocus()) getActiveFilterTabView().requestFocus();
                if (watchFeedback!=null) watchFeedback.resume();
            }).start();
        } else handler.postDelayed(startupCheck,80);
    }
    private void startupMark(String stage) { android.util.Log.d("TVHomePerf",stage+" "+(android.os.SystemClock.elapsedRealtime()-startupBegan)+"ms"); }
    private int startupBootCount() {
        try { return Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT); }
        catch (Exception ignored) { return 0; }
    }
    private boolean shouldShowStartup() {
        return getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getInt("welcome_boot_count",Integer.MIN_VALUE)!=startupBootCount();
    }
    private void markStartupShown() {
        getSharedPreferences(PREFS_NAME,MODE_PRIVATE).edit().putInt("welcome_boot_count",startupBootCount()).apply();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        startupBegan=android.os.SystemClock.elapsedRealtime();
        super.onCreate(savedInstanceState);
        startupVisible=shouldShowStartup();
        if (!startupVisible) { initializeHome(); return; }
        startupOverlay=getLayoutInflater().inflate(R.layout.view_home_startup,null);
        int hour=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        ((TextView)startupOverlay.findViewById(R.id.startupGreeting)).setText(hour<5?R.string.good_night:hour<12?R.string.good_morning:hour<18?R.string.good_afternoon:R.string.good_evening);
        setContentView(startupOverlay);
        startupOverlay.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw() {
                startupOverlay.getViewTreeObserver().removeOnPreDrawListener(this);
                startupFirstFrameAt=android.os.SystemClock.elapsedRealtime(); startupMark("first-frame");
                // Let TCL submit the welcome buffer before blocking the UI with initialization.
                startupOverlay.postDelayed(() -> { if (!isDestroyed()&&!isFinishing()) initializeHome(); },120);
                return true;
            }
        });
    }
    private void initializeHome() {
        setContentView(R.layout.activity_main);
        root=findViewById(R.id.root);
        if (startupOverlay!=null && startupOverlay.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)startupOverlay.getParent()).removeView(startupOverlay);
        if (startupVisible && startupOverlay!=null) ((android.view.ViewGroup)root).addView(startupOverlay,new android.view.ViewGroup.LayoutParams(-1,-1));
        startupMark("layout");
        density = getResources().getDisplayMetrics().density;
        COLUMNS = DeviceConfig.columns(getResources().getDisplayMetrics().widthPixels,density,getResources().getInteger(R.integer.columns));
        deviceConfig=DeviceConfig.load(this);
        liveTvPkg = deviceConfig.liveTvPackage(this);

        root = findViewById(R.id.root);
        mainContentColumn = findViewById(R.id.mainContentColumn);
        topHeaderBar = findViewById(R.id.topHeaderBar);
        btnScrollDownApps = findViewById(R.id.btnScrollDownApps);
        btnScrollUpHome = findViewById(R.id.btnScrollUpHome);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        mediaBackdropBg = findViewById(R.id.mediaBackdropBg);
        mediaBackdropScrim = findViewById(R.id.mediaBackdropScrim);
        normalHomeContainer = findViewById(R.id.normalHomeContainer);
        mediaDetailContainer = findViewById(R.id.mediaDetailContainer);

        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        int initC = currentAmbientColor & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x66000000 | initC, 0x2E000000 | initC, 0x00000000 | initC});
        ambient.setBackground(ambientGlow);
        ambient.setAlpha(1f);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        dateView = findViewById(R.id.date);
        weatherView = findViewById(R.id.weatherText);
        btnWifi = findViewById(R.id.btnWifi);
        btnSettings = findViewById(R.id.btnSettings);
        btnProfile = findViewById(R.id.btnProfile);
        profiles = new ProfileController(this, btnProfile, this::onProfilesChanged);
        profiles.syncNow();
        startupMark("profiles");
        recommendationProfileId=profiles.model.selected().id;
        recommendationFeedbackVersion=profiles.model.feedbackVersion();
        recommendationExposurePrefs=getSharedPreferences("home_exposure_v1",MODE_PRIVATE);
        lastVisibleRecommendations=recommendationIds(recommendationExposurePrefs.getString("screen",""),4);
        varyProfilePage=true;
        btnProfile.setOnClickListener(v -> profiles.showProfiles());

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        dockDecoration = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(dockDecoration);

        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);
        dockBackground=new android.graphics.drawable.InsetDrawable(getDrawable(R.drawable.home_control_group),0,0,0,Math.round(18*density));
        findViewById(R.id.gridWrapper).setBackground(dockBackground);
        findViewById(R.id.gridWrapper).setPadding(0,0,0,0);

        if (btnScrollDownApps != null) {
            btnScrollDownApps.setOnClickListener(v -> openAppsDrawer(lastDockColumn));
            btnScrollDownApps.setOnFocusChangeListener((v,focused) -> {
                v.animate().alpha(focused?1f:.5f).setDuration(120).start();
            });
            btnScrollDownApps.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusTile(lastDockColumn);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    openAppsDrawer(lastDockColumn);
                    return true;
                }
                return false;
            });
        }
        if (btnScrollUpHome != null) {
            btnScrollUpHome.setOnClickListener(v -> closeAppsDrawer(lastDockColumn));
            btnScrollUpHome.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusTile(lastDockColumn);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    closeAppsDrawer(lastDockColumn);
                    return true;
                }
                return false;
            });
        }

        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        updateIndicator=findViewById(R.id.updateIndicator);
        if (updateIndicator!=null) updateIndicator.setOnClickListener(v -> showUpdatePrompt());
        for (int id:new int[]{R.id.btnInputs,R.id.btnWifi,R.id.btnSettings}) homeControls.add(findViewById(id),44);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> showWifiDnsMenu());
        btnSettings.setOnClickListener(v -> showSettingsDropdown());

        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if ((v.getId() == R.id.btnSettings || v.getId() == R.id.btnWifi)
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        menuLongPressHandled = false;
                    } else if (event.getRepeatCount() >= 3 && !menuLongPressHandled) {
                        menuLongPressHandled = true;
                        open(new Intent(v.getId() == R.id.btnSettings
                                ? Settings.ACTION_SETTINGS
                                : Settings.ACTION_WIFI_SETTINGS));
                    }
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (!menuLongPressHandled) {
                        if (v.getId() == R.id.btnSettings) showSettingsDropdown();
                        else showWifiDnsMenu();
                    }
                    menuLongPressHandled = false;
                    return true;
                }
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (inMediaDetailView) {
                    btnWatchMedia.requestFocus();
                } else {
                    if (inAppsDrawer) closeAppsDrawer(0);
                    getActiveFilterTabView().requestFocus();
                }
                return true;
            }
            if (keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) {
                View[] controls=updateIndicator!=null&&updateIndicator.getVisibility()==View.VISIBLE
                        ? new View[]{updateIndicator,btnInputs,btnWifi,btnSettings,btnProfile}
                        : new View[]{btnInputs,btnWifi,btnSettings,btnProfile};
                for (int i=0;i<controls.length;i++) if (v==controls[i]) {
                    int next=i+(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);
                    if (next>=0&&next<controls.length) controls[next].requestFocus();
                    return true;
                }
            }
            return false;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        btnProfile.setOnKeyListener(pillKeys);

        int[] mediaFilters={R.id.tabNovidades,R.id.tabEmAlta,R.id.tabTodos,R.id.tabFilmes,R.id.tabSeries,R.id.btnDiscover,R.id.btnEditDiscovery,R.id.btnSearchMedia,R.id.btnWatchLaterFilter,R.id.btnHistoryFilter,R.id.btnTasteFilter};
        for (int i=0;i<mediaFilters.length;i++) { filterTabs[i]=findViewById(mediaFilters[i]); filterTabs[i].setEnabled(false); }
        catalogWorker.execute(() -> {
            MediaRepository repository=new MediaRepository(getApplicationContext());
            handler.post(() -> {
                if (isDestroyed()||isFinishing()) { repository.destroyDiscoveryPrefetch(); return; }
                setupMediaShowcase(repository);
                for (View filter:filterTabs) filter.setEnabled(true);
                if (pendingHomePageAdvance&&!gridMode()) bindMediaTrio(false);
                if (historyWhenReady) { historyWhenReady=false; showWatchHistory(); }
                startupMark("recommendations-ready");
            });
        });
        for (int id:new int[]{R.id.btnSearchMedia,R.id.btnDiscover,R.id.btnHistoryFilter,R.id.btnTasteFilter,R.id.btnWatchLaterFilter}) homeControls.add(findViewById(id),40,128);

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            if (gridBackAtOrigin&&newF!=filterTabs[gridOriginIndex()]) gridBackAtOrigin=false;
            boolean onTileOrMedia = newF != null && newF.getId() == R.id.card;
            if (!onTileOrMedia) hideGlass();
            if (titleSearch==null||!titleSearch.isSwitching()) homeControls.refresh();
        });

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);

        tvInputManager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tvInputManager != null) {
            for (TvInputInfo info : tvInputManager.getTvInputList()) {
                hdmiStates.put(info.getId(), tvInputManager.getInputState(info.getId()));
            }
            tvInputManager.registerCallback(tvInputCallback, handler);
        }

        ensureAutoSearchServiceEnabled();
        restoreLastLaunchedFromPrefs();
        applyNightAudioSchedule();
        refreshSettingsCacheAsync();
        IntentFilter clockChanges=new IntentFilter(Intent.ACTION_TIME_CHANGED);
        clockChanges.addAction(Intent.ACTION_DATE_CHANGED);
        clockChanges.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        clockChanges.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(clockReceiver,clockChanges);
        startupMark("created");
        root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            public boolean onPreDraw(){ root.getViewTreeObserver().removeOnPreDrawListener(this); startupMark("home-layout-frame"); return true; }
        });
        homeInitialized=true;
        UpdateManager.schedule(this);
        if (activityResumed) resumeHome();
    }

    @Override
    protected void onResume() {
        super.onResume(); activityResumed=true;
        if (homeInitialized) { resumeHome(); UpdateManager.checkIfDue(this,this::onUpdateChecked); }
    }
    private void resumeHome() {
        long resumeBegan=android.os.SystemClock.elapsedRealtime();
        deviceConfig=DeviceConfig.load(this); liveTvPkg=deviceConfig.liveTvPackage(this);
        handler.post(this::retryVisibleArtwork);
        if (!startupVisible) root.post(() -> profiles.ensureFirstProfile());
        boolean keepSearch=titleSearch!=null&&titleSearch.isKeepingSearch();
        boolean externalVisit=WatchMonitor.get(this).consumeExternalVisit();
        if (homeResumed&&externalVisit&&!keepSearch) pendingHomePageAdvance=true;
        homeResumed=true;
        if (!keepSearch) { WatchMonitor.get(this).returnedHome(); if (watchFeedback!=null) watchFeedback.resume(); }
        if (titleSearch!=null) titleSearch.resumed();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(1f);

        if (inMediaDetailView) updateDetailActions();
        AutoSearchService.cancelPending(this);

        // Auto-Kill leftovers of the app we just exited when returning to Home
        if (pendingAutoKillPkg != null) {
            final String pkgToKill = pendingAutoKillPkg;
            pendingAutoKillPkg = null;
            if (isAutoKillEnabled() && !pkgToKill.equals(getPackageName())) {
                handler.postDelayed(() -> {
                    try {
                        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                        if (am != null) am.killBackgroundProcesses(pkgToKill);
                    } catch (Exception ignored) {
                    }
                }, 400);
            }
        }

        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reloadIfNeeded();
        updateGreeting();
        updateDateAndWeatherText();
        updateWifiLabel();
        applyNightAudioSchedule();
        fetchWeatherIfNeeded();
        refreshSettingsCacheAsync();
        if (mediaRepo != null) {
            if (!inMediaDetailView&&!inAppsDrawer) {
                if (pendingHomePageAdvance&&!gridMode()) bindMediaTrio(false);
                else bindMediaTrio(false);
            }
            mediaRepo.refreshFromNetworkIfNeeded(this::refreshMediaRecommendations);
        }
        handler.postDelayed(minuteTick, MINUTE_MS);
        if (startupVisible) { handler.removeCallbacks(startupCheck); handler.post(startupCheck); }
        android.util.Log.d("TVHomePerf","resume "+(android.os.SystemClock.elapsedRealtime()-resumeBegan)+"ms");

        if (inMediaDetailView) {
            btnWatchMedia.post(() -> btnWatchMedia.requestFocus());
        } else if (inAppsDrawer) {
            grid.post(() -> {
                int pos = adapter.positionOf(returnKey);
                focusTile(pos >= 0 ? pos : returnSlot);
            });
        } else if (mediaRepo!=null) {
            selectMediaCategory(currentMediaCategory);
            if (filterTabs[lastFilterTab] != null) {
                filterTabs[lastFilterTab].requestFocus();
                filterTabs[lastFilterTab].post(() -> filterTabs[lastFilterTab].requestFocus());
            }
        }
    }

    private int dp(float value) { return Math.round(value*density); }
    private TextView updateAction(String label) {
        TextView v=new TextView(this); v.setText(label); v.setTextColor(Color.WHITE); v.setTextSize(12); v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.create("sans",Typeface.BOLD)); v.setFocusable(true); v.setClickable(true); v.setPadding(dp(18),0,dp(18),0); v.setMinHeight(dp(42)); v.setBackgroundResource(R.drawable.home_top_segment);
        return v;
    }
    private void onUpdateChecked(UpdateManager.Update update) {
        if (update==null||isFinishing()||isDestroyed()) return;
        availableUpdate=update;
        if (updateIndicator!=null) {
            updateIndicator.setVisibility(View.VISIBLE);
            updateIndicator.animate().cancel();
            updateIndicator.setAlpha(1f); updateIndicator.setScaleX(1f); updateIndicator.setScaleY(1f);
            updateIndicator.animate().alpha(.55f).scaleX(1.08f).scaleY(1.08f).setDuration(900).withEndAction(() -> {
                if (updateIndicator!=null&&availableUpdate!=null) updateIndicator.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).start();
            }).start();
        }
        if (!startupVisible&&updateNoticeVersion!=update.versionCode) { updateNoticeVersion=update.versionCode; showUpdatePrompt(); }
    }
    private void showUpdatePrompt() {
        if (availableUpdate==null||root==null||isFinishing()||isDestroyed()) return;
        if (updateNoticePopup!=null&&updateNoticePopup.isShowing()) updateNoticePopup.dismiss();
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(20),dp(16),dp(20),dp(14)); panel.setBackgroundResource(R.drawable.glass_panel);
        TextView title=new TextView(this); title.setText("Nova atualização · v"+availableUpdate.versionName); title.setTextColor(Color.WHITE); title.setTextSize(17); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); panel.addView(title);
        TextView summary=new TextView(this); summary.setText(availableUpdate.summary.isEmpty()?"O PIXEL TV recebeu melhorias para deixar sua experiência mais simples.":availableUpdate.summary); summary.setTextColor(0xffc9c9d2); summary.setTextSize(12); summary.setPadding(0,dp(6),0,dp(12)); panel.addView(summary);
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); TextView later=updateAction("DEPOIS"); TextView now=updateAction("ATUALIZAR"); actions.addView(later,new LinearLayout.LayoutParams(-2,dp(42))); actions.addView(now,new LinearLayout.LayoutParams(-2,dp(42))); panel.addView(actions);
        updateNoticePopup=new PopupWindow(panel,dp(430),ViewGroup.LayoutParams.WRAP_CONTENT,true); updateNoticePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); updateNoticePopup.setOutsideTouchable(true); updateNoticePopup.setElevation(dp(16));
        later.setOnClickListener(v -> updateNoticePopup.dismiss()); now.setOnClickListener(v -> { updateNoticePopup.dismiss(); startUpdateDownload(); });
        updateNoticePopup.setOnDismissListener(() -> updateNoticePopup=null);
        updateNoticePopup.showAtLocation(root,Gravity.BOTTOM|Gravity.END,dp(48),dp(38)); now.requestFocus();
    }
    private void startUpdateDownload() {
        if (availableUpdate==null||updateWelcomeOverlay!=null) return;
        FrameLayout overlay=new FrameLayout(this); overlay.setBackgroundColor(0xe6000000); overlay.setFocusable(true); overlay.setFocusableInTouchMode(true);
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(34),dp(28),dp(34),dp(30)); panel.setBackgroundResource(R.drawable.glass_panel);
        TextView eyebrow=new TextView(this); eyebrow.setText("PIXEL TV  ·  ATUALIZAÇÃO"); eyebrow.setTextColor(0xff87e5a0); eyebrow.setTextSize(11); eyebrow.setLetterSpacing(.12f); panel.addView(eyebrow);
        TextView title=new TextView(this); title.setText("Atualizando para v"+availableUpdate.versionName); title.setTextColor(Color.WHITE); title.setTextSize(25); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setPadding(0,dp(8),0,dp(4)); panel.addView(title);
        TextView summary=new TextView(this); summary.setText(availableUpdate.summary.isEmpty()?"Uma atualização do sistema está pronta.":availableUpdate.summary); summary.setTextColor(0xffd2d2dc); summary.setTextSize(13); summary.setPadding(0,0,0,dp(16)); panel.addView(summary);
        TextView changesTitle=new TextView(this); changesTitle.setText("O que melhorou"); changesTitle.setTextColor(Color.WHITE); changesTitle.setTextSize(14); changesTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); panel.addView(changesTitle);
        LinearLayout changes=new LinearLayout(this); changes.setOrientation(LinearLayout.VERTICAL); changes.setPadding(dp(8),dp(8),0,dp(14));
        for (String change:availableUpdate.changes) { TextView row=new TextView(this); row.setText("•  "+change); row.setTextColor(0xffc9c9d2); row.setTextSize(12); row.setPadding(0,dp(3),0,dp(3)); changes.addView(row); }
        ScrollView scroll=new ScrollView(this); scroll.addView(changes); panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(130)));
        updateDownloadProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); updateDownloadProgress.setMax(100); updateDownloadProgress.setIndeterminate(true); panel.addView(updateDownloadProgress,new LinearLayout.LayoutParams(-1,dp(6)));
        updateDownloadStatus=new TextView(this); updateDownloadStatus.setText("Baixando a atualização…"); updateDownloadStatus.setTextColor(0xffbcbcc7); updateDownloadStatus.setTextSize(12); updateDownloadStatus.setPadding(0,dp(10),0,0); panel.addView(updateDownloadStatus);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(670),dp(410),Gravity.CENTER); overlay.addView(panel,pp); root.addView(overlay,new FrameLayout.LayoutParams(-1,-1)); updateWelcomeOverlay=overlay; overlay.requestFocus();
        UpdateManager.download(this,availableUpdate,new UpdateManager.DownloadCallback() {
            @Override public void progress(int percent) { if (updateDownloadProgress==null) return; if (percent<0) updateDownloadProgress.setIndeterminate(true); else { updateDownloadProgress.setIndeterminate(false); updateDownloadProgress.setProgress(percent); updateDownloadStatus.setText("Baixando a atualização… "+percent+"%"); } }
            @Override public void success(java.io.File apk) { updateDownloadStatus.setText("Download concluído. Abrindo instalação…"); installUpdate(apk); }
            @Override public void error(Exception error) { updateDownloadStatus.setText("Não foi possível baixar agora. Tente novamente mais tarde."); ThemedNotice.show(MainActivity.this,R.drawable.ic_wifi,"Atualização não concluída","Verifique a conexão e tente novamente",false,true); }
        });
    }
    private void installUpdate(java.io.File apk) {
        try {
            if (android.os.Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()) {
                updateDownloadStatus.setText("Permita instalações do PIXEL TV e volte para concluir."); startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))); return;
            }
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive"); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent);
        } catch (Exception e) { if (updateDownloadStatus!=null) updateDownloadStatus.setText("Não foi possível abrir o instalador nesta TV."); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (titleSearch!=null) titleSearch.permissionResult(requestCode,grantResults);
    }

    @Override
    protected void onPause() {
        super.onPause(); activityResumed=false;
        handler.removeCallbacks(startupCheck);
        if (!homeInitialized) return;
        // Native TV menus can cover Home without stopping its Activity.
        if (!startupVisible&&(titleSearch==null||!titleSearch.isKeepingSearch())) pendingHomePageAdvance=true;
        if (mediaBackground!=null) mediaBackground.cancel();
        if (titleSearch==null||!titleSearch.isKeepingSearch()) {
            WatchMonitor.get(this).outside();
            if (titleSearch!=null) titleSearch.pause();
            if (watchFeedback!=null) watchFeedback.pause();
        }
        handler.removeCallbacks(minuteTick);
        dismissHdmiPrompt();
        for (Runnable check : pendingHdmiChecks.values()) handler.removeCallbacks(check);
        pendingHdmiChecks.clear();
        if (trailerModalActive) {
            dismissTrailerModal();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(startupCheck);
        if (!homeInitialized) { catalogWorker.shutdownNow(); super.onDestroy(); return; }
        if (adapter!=null) adapter.destroy();
        if (mediaBackground!=null) mediaBackground.destroy();
        unregisterReceiver(clockReceiver);
        homeControls.destroy();
        cancelGridLoad(); catalogWorker.shutdownNow();
        if (titleSearch!=null) titleSearch.destroy();
        cancelRelated(); relatedWorker.shutdownNow();
        if (watchFeedback!=null) watchFeedback.destroy();
        if (mediaRepo != null) mediaRepo.destroyDiscoveryPrefetch();
        if (discovery != null) discovery.destroy();
        if (profiles != null) profiles.destroy();
        super.onDestroy();
        releaseTrailerPlayer();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        if (tvInputManager != null) tvInputManager.unregisterCallback(tvInputCallback);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (!homeInitialized||startupVisible) {
            if (keyCode==KeyEvent.KEYCODE_BACK||keyCode==KeyEvent.KEYCODE_ENTER||keyCode==KeyEvent.KEYCODE_DPAD_CENTER||keyCode==KeyEvent.KEYCODE_DPAD_UP||keyCode==KeyEvent.KEYCODE_DPAD_DOWN||keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return super.dispatchKeyEvent(event);
        }

        // Back first leaves fullscreen, then closes the modal.
        if (trailerModalActive) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (trailerFullscreen) setTrailerFullscreen(false);
                    else dismissTrailerModal();
                }
                return true;
            }
            if (trailerFullscreen && keyCode == fullscreenRevealKey) {
                if (event.getAction() == KeyEvent.ACTION_UP) fullscreenRevealKey = -1;
                else showFullscreenTrailerControls();
                return true;
            }
            if (trailerFullscreen && event.getAction() == KeyEvent.ACTION_DOWN) {
                boolean hidden = trailerFullscreenControls.getVisibility() != View.VISIBLE;
                showFullscreenTrailerControls();
                if (hidden && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                    fullscreenRevealKey = keyCode;
                    return true;
                }
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    if (event.getRepeatCount() == 0) {
                        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) toggleTrailerPlayPause();
                        else if (trailerPlayer != null) {
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) trailerPlayer.pause();
                            else {
                                if (trailerPlayer.getPlaybackState() == Player.STATE_ENDED) trailerPlayer.seekTo(0);
                                trailerPlayer.play();
                            }
                        }
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                    seekTrailerBy(-10);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    seekTrailerBy(10);
                    return true;
                }
            }
        }

        // The remote Menu/Settings key opens the native TV settings on one click.
        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_SETTINGS
                || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
                || keyCode == KeyEvent.KEYCODE_MEDIA_TOP_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) open(new Intent(Settings.ACTION_SETTINGS));
            return true;
        }

        // Remote BACK key: short press in Detail View / Apps Drawer -> return to Home; 2s hold -> switch back to Last App (Alt+Tab style)
        if (keyCode == KeyEvent.KEYCODE_BACK && !movingMode) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    backLongPressHandled = false;
                } else if (event.getRepeatCount() >= 3 && !backLongPressHandled) {
                    backLongPressHandled = true;
                    switchToLastApp();
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!backLongPressHandled) {
                    onBackPressed();
                }
                backLongPressHandled = false;
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (!homeInitialized||startupVisible) return;
        if (trailerModalActive) {
            if (trailerFullscreen) setTrailerFullscreen(false);
            else dismissTrailerModal();
            return;
        }
        if (inMediaDetailView) {
            closeMediaDetailView();
            return;
        }
        if (movingMode) {
            stopMoving();
            return;
        }
        if (inAppsDrawer) {
            closeAppsDrawer(lastDockColumn);
        } else if (titleSearch!=null&&titleSearch.back()) {
            return;
        } else if (gridMode()) {
            if (gridBackAtOrigin&&filterTabs[gridOriginIndex()].hasFocus()) {
                returnToRecommendations();
            } else { focusGridOrigin(); gridBackAtOrigin=true; }
        }
    }

    // ---- Media Showcase (‹ [    ] [  ] [  ] ›) & Full Detail View -------------------------------

    private void ensureAutoSearchServiceEnabled() {
        try {
            String svcName = getPackageName() + "/" + AutoSearchService.class.getName();
            String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (current == null || current.isEmpty()) {
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, svcName);
            } else if (!current.contains(svcName)) {
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, current + ":" + svcName);
            }
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
        } catch (Exception ignored) {
        }
    }

    private void configureDockLayout(boolean drawer) {
        View wrapper=findViewById(R.id.gridWrapper);
        LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)wrapper.getLayoutParams();
        size.height=drawer?0:Math.round(118*density); size.weight=drawer?1:0; wrapper.setLayoutParams(size);
        wrapper.setBackground(drawer?null:dockBackground);
        wrapper.setPadding(0,0,0,0);
        grid.setPadding(0,Math.round((drawer?18:0)*density),0,Math.round(18*density));
    }

    private void openAppsDrawer(int targetCol) { if (!inAppsDrawer) transitionAppsDrawer(true,targetCol); }
    private void closeAppsDrawer(int targetCol) { if (inAppsDrawer) transitionAppsDrawer(false,targetCol); }
    private void transitionAppsDrawer(boolean drawer,int targetCol) {
        inAppsDrawer=drawer; appsTransitioning=true; final int generation=++drawerTransitionGeneration;
        final int col=Math.max(0,Math.min(targetCol,COLUMNS-1)); lastDockColumn=col;
        hideGlass(); hideHero(); if (mediaBackground!=null) mediaBackground.cancel();
        if (!drawer) {
            if (pendingHomePageAdvance&&!gridMode()) bindMediaTrio(false);
            else refreshMediaRecommendations(true);
        }
        View wrapper=findViewById(R.id.gridWrapper),section=findViewById(R.id.mediaSection);
        wrapper.animate().cancel(); grid.animate().cancel(); topHeaderBar.animate().cancel(); section.animate().cancel();
        if (drawer) {
            topHeaderBar.animate().alpha(0).translationY(-12*density).setDuration(160).start();
            section.animate().alpha(0).translationY(-18*density).setDuration(160).start();
        }
        wrapper.animate().alpha(0).translationY((drawer?-18:18)*density).setDuration(160)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(() -> {
                if (generation!=drawerTransitionGeneration||isFinishing()||isDestroyed()) return;
                configureDockLayout(drawer);
                topHeaderBar.setVisibility(drawer?View.GONE:View.VISIBLE); section.setVisibility(drawer?View.GONE:View.VISIBLE);
                mediaSectionVisible=!drawer; btnScrollDownApps.setVisibility(drawer?View.GONE:View.VISIBLE); btnScrollUpHome.setVisibility(drawer?View.VISIBLE:View.GONE);
                dockDecoration.setEnabled(!drawer); adapter.setDrawerMode(drawer); grid.invalidateItemDecorations(); grid.scrollToPosition(0);
                grid.setAlpha(1); grid.setTranslationY(0); wrapper.setAlpha(0); wrapper.setTranslationY((drawer?18:-18)*density);
                if (!drawer) {
                    topHeaderBar.setAlpha(0); topHeaderBar.setTranslationY(-12*density); section.setAlpha(0); section.setTranslationY(-18*density);
                    topHeaderBar.animate().alpha(1).translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    section.animate().alpha(1).translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                }
                wrapper.animate().alpha(1).translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .withEndAction(() -> {
                        if (generation!=drawerTransitionGeneration||isFinishing()||isDestroyed()) return;
                        appsTransitioning=false;
                        if (hasWindowFocus()&&!drawer&&titleSearch!=null&&titleSearch.isOpen()) titleSearch.navigationAnchor().requestFocus();
                        else if (hasWindowFocus()&&adapter.getItemCount()>0) focusTile(Math.min(col,adapter.getItemCount()-1));
                    }).start();
            }).start();
    }

    private void setupMediaShowcase(MediaRepository repository) {
        mediaRepo = repository;
        mediaBackground=new MediaBackdropController(mediaRepo,mediaBackdropBg,mediaBackdropScrim,this::animateAmbientTo,() -> hasWindowFocus()&&!inMediaDetailView&&!trailerModalActive&&!isFinishing());
        profiles.setMediaRepository(mediaRepo);
        titleSearch=new TitleSearchController(this,profiles,mediaRepo);
        titleSearch.onMotion(active -> { searchToolbarMoving=active; onMediaMotion(mediaContentMoving); });
        titleSearch.onFeedbackLayout(change -> { if (gridMode()) discoveryGrid.animateViewportChange(change); else recommendationRail.animateViewportChange(change); });
        titleSearch.onExplore(this::openCatalog);
        titleSearch.onResults(this::showSearchResults);
        titleSearch.onEditorOpened(() -> { homeControls.finishAnimations(); if (inMediaDetailView) exitMediaDetails(); if (inAppsDrawer) closeAppsDrawer(lastDockColumn); });
        titleSearch.onFocusResults(() -> { if (gridMode()) discoveryGrid.focusCurrent(); else { View card=recommendationRail.getChildAt(0); if (card!=null) card.requestFocus(); } });
        titleSearch.onEditorClosed(() -> { discoveryGrid.cancelPendingFocus(); gridBackAtOrigin=gridMode(); filterTabs[7].requestFocus(); homeControls.finishAnimations(); });
        watchFeedback=new WatchFeedbackController(this,profiles,titleSearch,() -> startupVisible||trailerModalActive||hdmiPrompt!=null||(profiles.dialog!=null&&profiles.dialog.isShowing()));
        mediaRepo.setProfiles(profiles.model);
        mediaRepo.onRecommendationsUpdated(key -> { if (!gridMode()&&key==currentMediaCategory*3+currentMediaType) refreshMediaRecommendations(); });
        mediaSection = findViewById(R.id.mediaSection);

        filterTabs[0] = findViewById(R.id.tabNovidades);
        filterTabs[1] = findViewById(R.id.tabEmAlta);
        filterTabs[2] = findViewById(R.id.tabTodos);
        filterTabs[3] = findViewById(R.id.tabFilmes);
        filterTabs[4] = findViewById(R.id.tabSeries);
        filterTabs[5] = findViewById(R.id.btnDiscover);
        filterTabs[6] = findViewById(R.id.btnEditDiscovery);
        filterTabs[7] = findViewById(R.id.btnSearchMedia);
        filterTabs[8] = findViewById(R.id.btnWatchLaterFilter);
        filterTabs[9] = findViewById(R.id.btnHistoryFilter);
        filterTabs[10] = findViewById(R.id.btnTasteFilter);
        discovery = new DiscoveryController(this,profiles,mediaRepo);
        discoveryGrid=new DiscoveryGrid(this,mediaRepo,this::openMediaDetailView,
                this::loadMoreGrid,
                () -> getActiveFilterTabView().requestFocus(),(view,item) -> {
                    titleSearch.onResultFocused(); hideHero(); hideGlass(); mediaBackground.focus(item,true);
                });
        discoveryGrid.onNearEnd(() -> { if (searchResultsActive) titleSearch.nearEnd(); });
        discoveryGrid.onMotion(this::onMediaMotion);
        discoveryGrid.setVisibility(View.GONE);
        ((LinearLayout)mediaSection).addView(discoveryGrid,new LinearLayout.LayoutParams(-1,0,1));
        ImageView gridReturn=new ImageView(this);
        gridReturn.setId(R.id.btnGridReturn); gridReturn.setImageResource(R.drawable.ic_back);
        gridReturn.setImageTintList(getResources().getColorStateList(R.drawable.pill_text,getTheme()));
        gridReturn.setBackgroundResource(R.drawable.media_next_btn_bg); gridReturn.setPadding(dp(7),dp(7),dp(7),dp(7));
        gridReturn.setContentDescription("Voltar às recomendações"); gridReturn.setFocusableInTouchMode(true); gridReturn.setClickable(true); gridReturn.setVisibility(View.GONE);
        android.widget.FrameLayout root=findViewById(R.id.root);
        root.addView(gridReturn,new android.widget.FrameLayout.LayoutParams(dp(28),dp(28)));
        discoveryGrid.onLeft(() -> gridReturn.requestFocus());
        gridReturn.setOnClickListener(v -> returnToRecommendations());
        gridReturn.setOnKeyListener((v,key,event) -> {
            if (event.getAction()!=KeyEvent.ACTION_DOWN) return false;
            if (key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_DOWN) { discoveryGrid.focusCurrent(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_UP) { focusGridOrigin(); return true; }
            return key==KeyEvent.KEYCODE_DPAD_LEFT;
        });
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            boolean visible=discoveryGrid.isShown()&&!inMediaDetailView&&!inAppsDrawer;
            gridReturn.setVisibility(visible?View.VISIBLE:View.GONE);
            if (visible) {
                int[] gridAt=new int[2],rootAt=new int[2]; discoveryGrid.getLocationInWindow(gridAt); root.getLocationInWindow(rootAt);
                gridReturn.setX(gridAt[0]-rootAt[0]-dp(36));
                gridReturn.setY(gridAt[1]-rootAt[1]+(discoveryGrid.getHeight()-gridReturn.getHeight())/2f);
            }
        });

        recommendationRail=new RecommendationRail(this,mediaRepo,this::openMediaDetailView,
                () -> getActiveFilterTabView().requestFocus(),() -> focusTile(lastDockColumn),
                item -> { hideHero(); hideGlass(); mediaBackground.focus(item); },this::rememberRailExposure);
        recommendationRail.onMotion(this::onMediaMotion);
        ((android.widget.FrameLayout)findViewById(R.id.mediaCardsRow)).addView(recommendationRail,0,new android.widget.FrameLayout.LayoutParams(-1,-1));

        detailPosterCard = findViewById(R.id.detailPosterCard);
        detailPoster = findViewById(R.id.detailPoster);

        detailTypeBadge = findViewById(R.id.detailTypeBadge);
        detailRatingBadge = findViewById(R.id.detailRatingBadge);
        detailYearGenre = findViewById(R.id.detailYearGenre);
        detailTitle = findViewById(R.id.detailTitle);
        detailSynopsis = findViewById(R.id.detailSynopsis);
        btnWatchMedia = findViewById(R.id.btnWatchMedia);
        btnWatchTrailer = findViewById(R.id.btnWatchTrailer);
        btnCloseDetail = findViewById(R.id.btnCloseDetail);
        TextView btnRateMedia = findViewById(R.id.btnRateMedia);
        btnRateMedia.setFocusableInTouchMode(true);
        btnRateMedia.setBackgroundResource(R.drawable.detail_action_bg);
        btnRateMedia.setOnClickListener(v -> { if (detailMediaItem != null) watchFeedback.review(detailMediaItem, btnRateMedia); });
        TextView btnWatchLater=findViewById(R.id.btnWatchLater); btnWatchLater.setFocusableInTouchMode(true); btnWatchLater.setBackgroundResource(R.drawable.detail_action_bg);
        btnWatchLater.setOnClickListener(v -> { if (detailMediaItem==null) return; if (watchLaterActive) { profiles.removeSavedItem(detailMediaItem); closeMediaDetailView(); } else profiles.chooseWatchLater(detailMediaItem,btnWatchLater); });

        final float cardRadius = 18f * density;
        ViewOutlineProvider cardOutline = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadius);
            }
        };
        if (detailPosterCard != null) {
            detailPosterCard.setOutlineProvider(cardOutline);
            detailPosterCard.setClipToOutline(true);
        }

        // Filter Tabs behavior
        for (int i = 0; i < filterTabs.length; i++) {
            final int catIdx = i;
            View tab = filterTabs[i];
            tab.setFocusableInTouchMode(true);
            tab.setOnClickListener(v -> {
                gridBackAtOrigin=false;
                if (catIdx==7) {
                    titleSearch.open("",filterTabs[7],false,false,() -> {});
                } else if (catIdx==9) {
                    showWatchHistory();
                } else if (catIdx==10) {
                    profiles.showTaste(filterTabs[10]);
                } else if (catIdx==8) {
                    leaveSearchGrid(); cancelGridLoad(); browsingCatalog=false; discoveryGrid.reset();
                    watchLaterActive=true; discoveryResults=null; categoryPageOffset[6]=0; lastFilterTab=8;
                    selectMediaCategory(currentMediaCategory);
                } else if (catIdx >= 5) {
                    discovery.open(discoveryQuery,filterTabs[5],(query,items,online,notice) -> {
                        leaveSearchGrid(); cancelGridLoad(); browsingCatalog=false; discoveryGrid.reset(); gridMoreLabel="Descobrir mais resultados";
                        discoveryNotice=notice;
                        watchLaterActive=false; discoveryQuery=query; discoveryResults=items; categoryPageOffset[6]=0; lastFilterTab=5;
                        selectMediaCategory(currentMediaCategory); filterTabs[5].requestFocus();
                        if (!online) ThemedNotice.show(this,R.drawable.ic_wifi,"Sem conexão com o catálogo","Mostrando descobertas dos títulos salvos",false,true);
                        else if (items.size()<MediaRepository.MIN_DISCOVERY_RESULTS) ThemedNotice.show(this,R.drawable.ic_search,"Encontramos "+items.size()+" títulos","Amplie os filtros para encontrar mais",false,true);
                    });
                } else {
                    leaveGrid();
                    if (catIdx < 2) selectMediaCategory(catIdx);
                    else { currentMediaType = catIdx-2; selectMediaCategory(currentMediaCategory); }
                }
            });
            tab.setOnFocusChangeListener((v, focused) -> {
                if (focused) lastFilterTab = catIdx;
            });
            tab.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    findViewById(R.id.btnInputs).requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusMediaCard(0);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    View previous=adjacentFilter(catIdx,-1);
                    if (previous!=null) previous.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    View next=adjacentFilter(catIdx,1);
                    if (next!=null) next.requestFocus();
                    else if (titleSearch.isOpen()) titleSearch.firstControl().requestFocus();
                    return true;
                }
                return false;
            });
        }

        relatedRail=new RecommendationRail(this,mediaRepo,(item,index) -> {
            android.widget.ScrollView scroll=findViewById(R.id.mediaDetailContainer);
            if (detailParents.size()>=20) detailParents.removeLast();
            detailParents.addFirst(new RelatedDetailState(detailMediaItem,relatedItems,relatedRail.selectedIndex(),scroll.getScrollY()));
            openMediaDetailView(item,lastClickedMediaCardIdx);
        },this::focusDetailActions,() -> {},item -> relatedRail.post(() -> positionRelated(true)),items -> {});
        relatedRail.onMotion(this::onMediaMotion);
        ((android.widget.FrameLayout)findViewById(R.id.relatedCardsRow)).addView(relatedRail,new android.widget.FrameLayout.LayoutParams(-1,-1));
        mediaDetailContainer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> sizeDetailViewport());
        mediaDetailContainer.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!inMediaDetailView||!relatedRail.isShown()) return;
            android.graphics.Rect bounds=new android.graphics.Rect(); relatedRail.getDrawingRect(bounds);
            ((android.widget.ScrollView)mediaDetailContainer).offsetDescendantRectToMyCoords(relatedRail,bounds);
            boolean moved=relatedContentTop>=0&&relatedContentTop!=bounds.top; relatedContentTop=bounds.top;
            if (moved&&relatedRail.hasFocus()) positionRelated(false);
        });

        // Detail View action buttons
        btnWatchMedia.setOnClickListener(v -> {
            if (detailMediaItem != null) showWatchAppPicker(detailMediaItem);
        });
        btnWatchTrailer.setOnClickListener(v -> {
            if (detailMediaItem != null) showTrailerModal(detailMediaItem);
        });
        btnCloseDetail.setOnClickListener(v -> closeMediaDetailView());

        View.OnKeyListener detailBtnKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (v.getId()==R.id.btnWatchLater||v.getId()==R.id.btnCloseDetail) { findViewById(v.getId()==R.id.btnWatchLater?R.id.btnWatchMedia:R.id.btnRateMedia).requestFocus(); return true; }
                if (detailSynopsis.isFocusable()) detailSynopsis.requestFocus(); else findViewById(R.id.btnInputs).requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (v.getId()!=R.id.btnWatchLater&&v.getId()!=R.id.btnCloseDetail) findViewById(R.id.btnWatchLater).requestFocus();
                else focusRelated();
                return true;
            }
            if (keyCode==KeyEvent.KEYCODE_DPAD_LEFT&&v.getId()==R.id.btnWatchLater) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnWatchMedia) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnCloseDetail) return true;
            return false;
        };
        btnWatchMedia.setOnKeyListener(detailBtnKeys);
        btnWatchTrailer.setOnKeyListener(detailBtnKeys);
        btnCloseDetail.setOnKeyListener(detailBtnKeys);
        btnRateMedia.setOnKeyListener(detailBtnKeys);
        btnWatchMedia.setNextFocusRightId(R.id.btnWatchTrailer);
        btnWatchTrailer.setNextFocusLeftId(R.id.btnWatchMedia);
        btnWatchTrailer.setNextFocusRightId(R.id.btnRateMedia);
        btnRateMedia.setNextFocusLeftId(R.id.btnWatchTrailer);
        btnRateMedia.setNextFocusRightId(R.id.btnRateMedia);
        btnWatchLater.setOnKeyListener(detailBtnKeys); btnWatchLater.setNextFocusRightId(R.id.btnCloseDetail); btnWatchLater.setNextFocusUpId(R.id.btnWatchMedia);
        btnCloseDetail.setNextFocusUpId(R.id.btnRateMedia);
        for (View action:new View[]{btnWatchMedia,btnWatchTrailer,btnRateMedia}) action.setNextFocusDownId(R.id.btnWatchLater);
        btnCloseDetail.setNextFocusLeftId(R.id.btnWatchLater);

        titleSearch.prepare();
        selectMediaCategory(MediaRepository.CAT_NOVIDADES);
        if (inAppsDrawer) { mediaSection.setVisibility(View.GONE); btnScrollDownApps.setVisibility(View.GONE); }
        mediaRepo.refreshFromNetworkIfNeeded(this::refreshMediaRecommendations);
    }

    private void onMediaMotion(boolean active) {
        mediaContentMoving=active; active=active||searchToolbarMoving;
        if (active) {
            mediaBackground.cancel(); mediaBackdropBg.animate().cancel(); mediaBackdropScrim.animate().cancel();
            if (ambientColorAnimator!=null) ambientColorAnimator.cancel();
        }
    }
    private View getActiveFilterTabView() {
        if (titleSearch!=null&&titleSearch.isOpen()) return titleSearch.navigationAnchor();
        return filterTabs[lastFilterTab].isShown()?filterTabs[lastFilterTab]:filterTabs[5];
    }

    private void onProfilesChanged() {
        if (mediaRepo == null) return;
        String selected=profiles.model.selected().id;
        long feedback=profiles.model.feedbackVersion();
        boolean preferencesChanged=!selected.equals(recommendationProfileId)||feedback!=recommendationFeedbackVersion;
        if (preferencesChanged) {
            recommendationProfileId=selected;
            recommendationFeedbackVersion=feedback;
            varyProfilePage=true;
            pendingProfileRecommendations=true;
        }
        if (historyActive) discoveryResults=profiles.model.history(selected);
        if (discoveryResults != null&&!browsingCatalog&&!searchResultsActive&&!historyActive) {
            MediaRepository.RecommendationBatch batch=mediaRepo.recommendDiscovery(discoveryResults,discoveryQuery);
            discoveryResults=batch.items; discoveryNotice=batch.notice;
        }
        if (detailMediaItem!=null) { updateRecommendationReason(detailMediaItem); updateDetailActions(); }
        if (preferencesChanged&&!inMediaDetailView) java.util.Arrays.fill(categoryPageOffset, 0);
        selectMediaCategory(currentMediaCategory);
    }

    private void selectMediaCategory(int category) {
        currentMediaCategory = category;
        boolean discovering=gridMode();
        hideGlass();
        findViewById(R.id.mediaCardsRow).setVisibility(discovering?View.GONE:View.VISIBLE);
        discoveryGrid.setVisibility(discovering?View.VISIBLE:View.GONE);
        findViewById(R.id.btnGridReturn).setVisibility(discovering&&!inMediaDetailView&&!inAppsDrawer?View.VISIBLE:View.GONE);
        findViewById(R.id.gridWrapper).setVisibility(discovering?View.GONE:View.VISIBLE);
        btnScrollDownApps.setVisibility(discovering?View.GONE:View.VISIBLE);
        LinearLayout.LayoutParams section=(LinearLayout.LayoutParams)mediaSection.getLayoutParams();
        section.height=0; section.weight=1;
        mediaSection.setLayoutParams(section);
        for (int i = 0; i < filterTabs.length; i++) {
            filterTabs[i].setSelected(historyActive?i==9:searchResultsActive?i==7:watchLaterActive?i==8:discoveryResults!=null?i==5:i<2?i==category:i<5&&i-2==currentMediaType);
        }
        boolean editable=discoveryResults!=null&&!browsingCatalog&&!searchResultsActive&&!historyActive;
        filterTabs[6].setVisibility(editable?View.VISIBLE:View.GONE);
        filterTabs[6].setSelected(discoveryResults!=null);
        findViewById(R.id.discoveryEditDivider).setVisibility(editable?View.VISIBLE:View.GONE);
        filterTabs[5].setBackgroundResource(!editable?R.drawable.filter_tab_bg:R.drawable.discovery_segment_start_bg);
        homeControls.setLabel((TextView)filterTabs[5],browsingCatalog?"Catálogo IMDb · "+currentMediaItems().size():editable?getString(R.string.discover)+" · "+currentMediaItems().size():getString(R.string.discover));
        homeControls.setLabel((TextView)filterTabs[7],searchResultsActive?"Busca · "+titleSearch.query():getString(R.string.title_search));
        homeControls.refresh();
        filterTabs[5].setContentDescription(browsingCatalog?"Explorando catálogo IMDb":!editable?getString(R.string.discover):"Descobrir, ativo. "+discoveryQuery.summary(profiles.model));
        bindMediaTrio(false);
    }

    private void leaveSearchGrid() {
        searchResultsActive=false; historyActive=false;
        if (titleSearch!=null) titleSearch.close();
    }
    private void showSearchResults(boolean replace) {
        if (!replace&&!searchResultsActive) return;
        if (replace) {
            if (inMediaDetailView) exitMediaDetails();
            cancelGridLoad(); historyActive=false; browsingCatalog=false; watchLaterActive=false; discoveryQuery=null;
            searchResultsActive=true; discoveryGrid.reset();
        }
        discoveryResults=titleSearch.results(); discoveryNotice=""; lastFilterTab=7;
        selectMediaCategory(currentMediaCategory);
        if (replace&&!titleSearch.focusSuggestions()) discoveryGrid.focusCard(0);
    }
    void showWatchHistory() {
        gridBackAtOrigin=false;
        if (discoveryGrid==null) { historyWhenReady=true; return; }
        if (inMediaDetailView) exitMediaDetails();
        leaveSearchGrid(); cancelGridLoad(); browsingCatalog=false; watchLaterActive=false; historyActive=true; discoveryQuery=null; lastFilterTab=9;
        discoveryResults=profiles.model.history(profiles.model.selected().id); discoveryNotice=""; discoveryGrid.reset();
        selectMediaCategory(currentMediaCategory); discoveryGrid.focusCard(0);
    }
    private int gridOriginIndex() { return searchResultsActive||browsingCatalog?7:historyActive?9:watchLaterActive?8:5; }
    private void leaveGrid() {
        gridBackAtOrigin=false; mediaRepo.cancelDiscoveryPrefetch();
        leaveSearchGrid(); cancelGridLoad(); browsingCatalog=false; discoveryResults=null; watchLaterActive=false;
    }
    private void returnToRecommendations() {
        discoveryGrid.cancelPendingFocus();
        leaveGrid(); selectMediaCategory(currentMediaCategory); filterTabs[currentMediaCategory].requestFocus();
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void focusGridOrigin() {
        discoveryGrid.cancelPendingFocus();
        if (titleSearch!=null&&titleSearch.isOpen()) titleSearch.navigationAnchor().requestFocus();
        else filterTabs[gridOriginIndex()].requestFocus();
    }
    private void updateDetailActions() {
        if (detailMediaItem==null) return;
        String profile=profiles.model.selected().id,id=detailMediaItem.imdbId;
        boolean saved=profiles.model.isSaved(profile,id),rated=profiles.model.isRated(profile,id);
        TextView later=findViewById(R.id.btnWatchLater),rating=findViewById(R.id.btnRateMedia);
        later.setSelected(saved); later.setText(watchLaterActive?"Remover da lista":saved?"Na lista":"Assistir mais tarde");
        rating.setSelected(rated); rating.setText(rated?"Avaliado":getString(R.string.rate_title));
        later.setContentDescription((saved?"Na lista de ":"Salvar para ")+profiles.model.selected().name+". Escolher perfis para assistir mais tarde");
        rating.setContentDescription((rated?"Avaliado. Alterar avaliação":"Avaliar título")+" · "+profiles.model.selected().name);
        TextView notes=findViewById(R.id.detailProfileStatus); String summary=profiles.model.historySummary(profile,id);
        notes.setText(summary); notes.setVisibility(summary.isEmpty()?View.GONE:View.VISIBLE);
    }

    private void cancelGridLoad() {
        gridGeneration++; gridLoading=false;
        if (catalogRequest!=null) catalogRequest.cancel(true);
        catalogRequest=null;
    }
    private void openCatalog(String type) {
        searchResultsActive=false; historyActive=false; cancelGridLoad(); mediaRepo.cancelDiscoveryPrefetch(); browsingCatalog=true; browseType=type; catalogSkip=0;
        watchLaterActive=false; discoveryQuery=null; discoveryResults=new ArrayList<>(); discoveryNotice=""; lastFilterTab=5;
        discoveryGrid.reset(); selectMediaCategory(currentMediaCategory); loadMoreGrid();
    }
    private void loadMoreGrid() {
        if (searchResultsActive) { titleSearch.loadMore(); return; }
        if (historyActive) return;
        if (gridLoading||watchLaterActive||discoveryResults==null||(!browsingCatalog&&discoveryQuery==null)) return;
        final int token=++gridGeneration,skip=catalogSkip,previousSize=discoveryResults.size();
        final boolean catalog=browsingCatalog; final String type=browseType;
        final DiscoveryQuery query=catalog?null:new DiscoveryQuery(discoveryQuery);
        final java.util.Set<String> excluded=new java.util.HashSet<>(); for (MediaRepository.MediaItem item:discoveryResults) excluded.add(item.imdbId);
        gridLoading=true; gridMoreLabel=catalog?"Explorar mais títulos":"Descobrir mais resultados";
        discoveryGrid.moreState(true,true,gridMoreLabel);
        catalogRequest=catalogWorker.submit(() -> {
            try {
                List<MediaRepository.MediaItem> found;
                String notice="";
                if (catalog) found=mediaRepo.browseCatalog(type,skip);
                else {
                    MediaRepository.DiscoveryCatalog fetched=mediaRepo.discover(query,excluded);
                    MediaRepository.RecommendationBatch batch=mediaRepo.recommendDiscovery(fetched.items,query); found=batch.items; notice=batch.notice;
                }
                final List<MediaRepository.MediaItem> additions=found; final String message=notice;
                handler.post(() -> {
                    if (token!=gridGeneration||isFinishing()||discoveryResults==null) return;
                    gridLoading=false; if (catalog) catalogSkip=skip+50;
                    discoveryResults=MediaRepository.appendUnique(discoveryResults,additions);
                    if (!message.isEmpty()) discoveryNotice=message;
                    if (discoveryResults.size()==previousSize) gridMoreLabel="Nenhum título novo. Tentar novamente";
                    selectMediaCategory(currentMediaCategory);
                    if (discoveryResults.size()>previousSize) discoveryGrid.focusCard(previousSize);
                    else showGlassNotice(R.drawable.ic_search,"Nenhum título novo neste lote","Você pode tentar novamente ou alterar os filtros",false);
                });
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) return;
                handler.post(() -> {
                    if (token!=gridGeneration||isFinishing()) return;
                    gridLoading=false; gridMoreLabel="Tentar carregar mais resultados";
                    discoveryGrid.moreState(true,false,gridMoreLabel);
                    showGlassNotice(R.drawable.ic_wifi,"Não foi possível ampliar o catálogo","Os resultados já carregados foram mantidos",false);
                });
            }
        });
    }

    private boolean gridMode() { return watchLaterActive||discoveryResults!=null; }
    private int mediaPageSlot() { return gridMode()?6:currentMediaCategory*3+currentMediaType; }
    private List<MediaRepository.MediaItem> currentMediaItems() {
        if (mediaRepo==null) return java.util.Collections.emptyList();
        if (watchLaterActive) return profiles.model.watchLater(profiles.model.selected().id);
        return discoveryResults==null?mediaRepo.getCategoryItems(currentMediaCategory,currentMediaType)
                :discoveryResults;
    }

    private void refreshMediaRecommendations() {
        refreshMediaRecommendations(false);
    }

    private void refreshMediaRecommendations(boolean preserveCompletePage) {
        if (mediaRepo==null||inMediaDetailView||inAppsDrawer) return;
        if (gridMode()) { if (!preserveCompletePage) bindMediaTrio(false); return; }
        int available=currentMediaItems().size(),shown=0;
        for (MediaRepository.MediaItem item:currentTrio) if (item!=null) shown++;
        // Preserve complete focused pages, but never leave an incomplete loading page stuck.
        if (pendingProfileRecommendations||(!preserveCompletePage&&!findViewById(R.id.mediaCardsRow).hasFocus())||shown<Math.min(3,available)) bindMediaTrio(false);
    }

    private void bindMediaTrio(boolean animateTransition) {
        if (!gridMode()&&pendingHomePageAdvance) { pendingHomePageAdvance=false; varyProfilePage=true; }
        long bindingStarted=android.os.SystemClock.elapsedRealtime();
        MediaRepository.RecommendationBatch batch=gridMode()?null:mediaRepo.getRecommendations(currentMediaCategory,currentMediaType);
        if (batch!=null&&!batch.ready&&!varyProfilePage) {
            if (currentTrio[0]!=null&&currentTrio[1]!=null&&currentTrio[2]!=null) return;
            if (pendingProfileRecommendations) for (MediaRepository.MediaItem item:currentTrio) if (item!=null) return;
        }
        if (batch!=null&&batch.ready) pendingProfileRecommendations=false;
        List<MediaRepository.MediaItem> list = batch==null?currentMediaItems():batch.items;
        TextView recommendationHint=findViewById(R.id.recommendationNotice);
        String notice=historyActive?"Assistidos e avaliações · "+profiles.model.selected().name+" · "+list.size()+" títulos":watchLaterActive||searchResultsActive?"":discoveryResults!=null?discoveryNotice:batch.notice;
        recommendationHint.setTextSize(historyActive?14:11);
        recommendationHint.setText(notice); recommendationHint.setVisibility(notice.isEmpty()?View.GONE:View.VISIBLE);
        if (gridMode()) {
            discoveryGrid.annotate(historyActive?item -> profiles.model.historySummary(profiles.model.selected().id,item.imdbId):null);
            discoveryGrid.moreState(searchResultsActive?titleSearch.hasMore():!watchLaterActive&&!historyActive,searchResultsActive?titleSearch.loading():gridLoading,searchResultsActive?(titleSearch.failed()?"Tentar carregar mais resultados":"Carregar mais resultados"):gridMoreLabel);
            discoveryGrid.bind(list,0,mediaRepo,historyActive?"Nenhum título registrado neste perfil.":searchResultsActive?"Nenhum resultado para esta pesquisa.":watchLaterActive?"Sua lista está vazia. Salve filmes e séries pelos detalhes.":gridLoading?"Buscando títulos do catálogo…":"Nenhuma descoberta com estas escolhas");
            return;
        }
        if (varyProfilePage) {
            profilePageAvoid=new java.util.HashSet<>(recentRecommendations());
            List<String> recent=new java.util.ArrayList<>(recentRecommendations());
            profilePagePrevious=new java.util.HashSet<>(recent.subList(Math.max(0,recent.size()-4),recent.size()));
            profilePageAvoid.addAll(lastVisibleRecommendations); profilePagePrevious.addAll(lastVisibleRecommendations);
            for (MediaRepository.MediaItem item:currentTrio) if (item!=null) { profilePageAvoid.add(item.imdbId); profilePagePrevious.add(item.imdbId); }
            variedProfileRanking=null;
            varyProfilePage=false;
        }
        // Apply the same exclusions when the personalized background ranking replaces the immediate list.
        if (profilePageAvoid!=null&&variedProfileRanking!=list) {
            variedProfileItems=MediaRepository.variedOrder(list,profilePageAvoid,profilePagePrevious,recommendationRandom);
            categoryPageOffset[mediaPageSlot()]=0;
            variedProfileRanking=list;
        }
        if (variedProfileRanking==list&&variedProfileItems!=null) list=variedProfileItems;
        findViewById(R.id.mediaEmpty).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        int totalTrios = Math.max(1, (list.size() + 2) / 3);
        int page = categoryPageOffset[mediaPageSlot()] % totalTrios;
        int baseIdx = page * 3;

        recommendationRail.bind(list,baseIdx,recommendationProfileId+":"+currentMediaCategory+":"+currentMediaType);
        rememberRailExposure(recommendationRail.visibleItems());
    }

    static java.util.Set<String> recommendationIds(String saved,int limit) {
        java.util.Set<String> ids=new java.util.LinkedHashSet<>();
        if (saved!=null) for (String id:saved.split(",")) if (id.matches("tt[0-9]{5,12}")) { ids.remove(id); ids.add(id); }
        while(ids.size()>Math.max(0,limit)) ids.remove(ids.iterator().next());
        return ids;
    }
    private java.util.Set<String> recentRecommendations() {
        return lastProfileRecommendations.computeIfAbsent(recommendationProfileId,id -> recommendationIds(recommendationExposurePrefs.getString("profile_"+id,""),12));
    }
    private void rememberRailExposure(List<MediaRepository.MediaItem> visible) {
        for (int i=0;i<3;i++) currentTrio[i]=i<visible.size()?visible.get(i):null;
        java.util.Set<String> shown=new java.util.LinkedHashSet<>(recentRecommendations());
        lastVisibleRecommendations=new java.util.LinkedHashSet<>();
        for (MediaRepository.MediaItem item:visible) { shown.remove(item.imdbId); shown.add(item.imdbId); lastVisibleRecommendations.add(item.imdbId); }
        while (shown.size()>12) shown.remove(shown.iterator().next());
        lastProfileRecommendations.put(recommendationProfileId,shown);
        recommendationExposurePrefs.edit().putString("profile_"+recommendationProfileId,String.join(",",shown)).putString("screen",String.join(",",lastVisibleRecommendations)).apply();
    }
    private void retryVisibleArtwork() {
        if (mediaRepo==null) return;
        if (inMediaDetailView) { mediaRepo.resumeArtwork(detailPoster); if (relatedRail!=null) relatedRail.retryMissingArtwork(); return; }
        if (gridMode()&&discoveryGrid!=null) { discoveryGrid.retryMissingArtwork(); if (discoveryGrid.hasFocus()) discoveryGrid.refreshBackground(); }
        else if (recommendationRail!=null) recommendationRail.retryMissingArtwork();
    }
    private void focusMediaCard(int requested) {
        if (gridMode()) discoveryGrid.focusCard(requested);
        else if (recommendationRail!=null) recommendationRail.focusCard(requested);
        else focusTile(0);
    }

    private void openMediaDetailView(MediaRepository.MediaItem item,int cardIdx) { openMediaDetailView(item,cardIdx,null); }
    private void openMediaDetailView(MediaRepository.MediaItem item,int cardIdx,RelatedDetailState restoring) {
        if (item == null) return;
        if (!inMediaDetailView) detailParents.clear();
        cancelRelated(); relatedContentTop=-1;
        ((android.widget.ScrollView)mediaDetailContainer).scrollTo(0,0);
        if (titleSearch!=null) titleSearch.suspend();
        mediaBackground.cancel();
        ((TextView)findViewById(R.id.btnWatchLater)).setText(watchLaterActive?"Remover da lista":"Assistir mais tarde");
        lastClickedMediaCardIdx = cardIdx;
        detailMediaItem = item;
        inMediaDetailView = true;
        updateDetailActions();
        hideGlass();
        hideHero();
        dismissTrailerModal();

        detailTypeBadge.setText(item.typeBadgePt());
        detailRatingBadge.setText(item.ratingLabel(true));
        detailYearGenre.setText(item.year + "  •  " + item.genresPt);
        mediaRepo.bindTitle(detailTitle,findViewById(R.id.detailOriginal),item);
        updateRecommendationReason(item);
        loadDetailSynopsis(item);

        mediaRepo.loadArtwork(item,true,detailPoster,340,500);
        mediaRepo.loadImage(item.backdropUrl,item.posterUrl, mediaBackdropBg, 960, 540);
        mediaBackdropBg.animate().alpha(0.58f).setDuration(220).start();
        mediaBackdropScrim.animate().alpha(0.88f).setDuration(220).start();

        // Pre-resolve Dublado PT-BR -> Legendado PT-BR -> Inglês trailer candidates in background
        mediaRepo.resolveTrailerCandidates(item, null);
        // Pre-resolve JustWatch streaming providers in background
        mediaRepo.resolveProvidersAsync(item, null);

        normalHomeContainer.setVisibility(View.GONE);
        mediaDetailContainer.setAlpha(0f);
        mediaDetailContainer.setTranslationY(10 * density);
        mediaDetailContainer.setVisibility(View.VISIBLE);
        mediaDetailContainer.animate().alpha(1f).translationY(0f).setDuration(180).start();
        btnWatchMedia.requestFocus(); mediaDetailContainer.post(this::sizeDetailViewport);
        if (restoring!=null&&!restoring.related.isEmpty()) {
            bindRelated(item,restoring.related,restoring.selected,false);
            mediaDetailContainer.post(() -> { if (detailMediaItem==item) { ((android.widget.ScrollView)mediaDetailContainer).scrollTo(0,restoring.scroll); relatedRail.focusCard(0); } });
        } else loadRelated(item);
    }
    private void updateRecommendationReason(MediaRepository.MediaItem item) {
        java.util.Set<String> people=discoveryQuery==null||browsingCatalog||searchResultsActive||historyActive?java.util.Collections.singleton(profiles.model.selected().id):discoveryQuery.people;
        ((TextView)findViewById(R.id.detailRecommendationReason)).setText(profiles.model.reason(item,people,Calendar.getInstance().get(Calendar.YEAR)));
    }

    private void closeMediaDetailView() {
        if (!inMediaDetailView) return;
        dismissTrailerModal(); cancelRelated();
        if (!detailParents.isEmpty()) { RelatedDetailState previous=detailParents.removeFirst(); openMediaDetailView(previous.item,lastClickedMediaCardIdx,previous); return; }
        inMediaDetailView = false;
        if (pendingHomePageAdvance&&!gridMode()) bindMediaTrio(false);
        else refreshMediaRecommendations(true);
        mediaDetailContainer.setVisibility(View.GONE);
        normalHomeContainer.setAlpha(0f);
        normalHomeContainer.setVisibility(View.VISIBLE);
        normalHomeContainer.animate().alpha(1f).setDuration(150).start();
        int idx = Math.max(0,lastClickedMediaCardIdx);
        focusMediaCard(idx);
        handler.post(this::retryVisibleArtwork);
    }
    private void exitMediaDetails() { detailParents.clear(); closeMediaDetailView(); }
    private void cancelRelated() { relatedGeneration++; if (relatedRequest!=null) relatedRequest.cancel(true); relatedRequest=null; }
    private void sizeDetailViewport() {
        android.widget.ScrollView scroll=(android.widget.ScrollView)mediaDetailContainer; if (scroll.getHeight()==0) return;
        int available=scroll.getHeight()-scroll.getPaddingTop()-scroll.getPaddingBottom();
        View row=findViewById(R.id.relatedCardsRow); int height=Math.max(dp(150),Math.min(dp(260),available-dp(56)));
        if (row.getLayoutParams().height!=height) { ViewGroup.LayoutParams size=row.getLayoutParams(); size.height=height; row.setLayoutParams(size); }
        View content=findViewById(R.id.detailContent); int bottom=Math.max(dp(32),(available-height)*3/4+dp(24));
        if (content.getPaddingBottom()!=bottom) content.setPadding(0,0,0,bottom);
        View main=findViewById(R.id.detailMainContent); int minimum=Math.max(dp(280),scroll.getHeight()-dp(140));
        if (main.getMinimumHeight()!=minimum) main.setMinimumHeight(minimum);
    }
    private void positionRelated(boolean smooth) {
        if (!inMediaDetailView||relatedRail==null||!relatedRail.hasFocus()||!relatedRail.isShown()) return;
        android.widget.ScrollView scroll=(android.widget.ScrollView)mediaDetailContainer;
        android.graphics.Rect bounds=new android.graphics.Rect(); relatedRail.getDrawingRect(bounds); scroll.offsetDescendantRectToMyCoords(relatedRail,bounds);
        android.graphics.Rect heading=new android.graphics.Rect(); View title=findViewById(R.id.relatedHeading); title.getDrawingRect(heading); scroll.offsetDescendantRectToMyCoords(title,heading);
        int minimum=bounds.top-heading.top;
        int target=TvSmoothScroller.relatedStart(bounds.top,bounds.bottom,scroll.getHeight(),scroll.getPaddingTop(),scroll.getPaddingBottom(),Math.max(0,minimum));
        if (Math.abs(target-scroll.getScrollY())<2) return;
        if (smooth) scroll.smoothScrollTo(0,target); else scroll.scrollTo(0,target);
    }
    private void focusDetailActions() {
        ((android.widget.ScrollView)mediaDetailContainer).smoothScrollTo(0,0); findViewById(R.id.btnWatchLater).requestFocus();
    }
    private void focusRelated() {
        if (relatedItems.isEmpty()) {
            TextView status=findViewById(R.id.relatedStatus); status.setFocusableInTouchMode(true); status.requestFocus();
            ((android.widget.ScrollView)mediaDetailContainer).smoothScrollTo(0,findViewById(R.id.relatedHeading).getTop());
        } else relatedRail.focusCard(0);
    }
    private void bindRelated(MediaRepository.MediaItem seed,List<MediaRepository.MediaItem> items,int selected,boolean loading) {
        relatedItems=new ArrayList<>(items); relatedRail.bind(relatedItems,selected,"related:"+seed.type+":"+seed.imdbId);
        TextView status=findViewById(R.id.relatedStatus); boolean waiting=status.hasFocus();
        status.setText(loading?"Buscando continuações e derivados…":items.isEmpty()?"Não foi possível carregar os relacionados. Clique para tentar novamente.":"");
        status.setVisibility(status.length()>0?View.VISIBLE:View.GONE); status.setFocusable(items.isEmpty());
        status.setOnClickListener(items.isEmpty()?v -> loadRelated(seed):null);
        status.setOnKeyListener((v,key,event) -> { if (event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP) { focusDetailActions(); return true; } return false; });
        if (waiting&&!items.isEmpty()) relatedRail.post(() -> relatedRail.focusCard(0));
    }
    private void loadRelated(MediaRepository.MediaItem item) {
        cancelRelated(); int token=relatedGeneration; bindRelated(item,java.util.Collections.emptyList(),0,true);
        relatedRequest=relatedWorker.submit(() -> {
            List<MediaRepository.MediaItem> local=mediaRepo.relatedLocal(item);
            handler.post(() -> { if (token==relatedGeneration&&inMediaDetailView&&detailMediaItem==item) bindRelated(item,local,0,true); });
            try {
                List<MediaRepository.MediaItem> resolved=mediaRepo.relatedTitles(item);
                handler.post(() -> {
                    if (token!=relatedGeneration||!inMediaDetailView||detailMediaItem!=item) return;
                    String focused=relatedRail.hasFocus()&&!relatedItems.isEmpty()?relatedItems.get(relatedRail.selectedIndex()).imdbId:"";
                    List<MediaRepository.MediaItem> next=new ArrayList<>(resolved); int selected=0;
                    if (!focused.isEmpty()) {
                        MediaRepository.MediaItem previous=relatedItems.get(relatedRail.selectedIndex());
                        if (next.stream().noneMatch(value -> value.imdbId.equals(focused))) next.add(previous);
                        for (int i=0;i<next.size();i++) if (next.get(i).imdbId.equals(focused)) { selected=i; break; }
                    }
                    bindRelated(item,next,selected,false);
                });
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) return;
                android.util.Log.w("TVHomeRelated","Relationship lookup unavailable; preserving similar titles",error);
                handler.post(() -> { if (token==relatedGeneration&&inMediaDetailView&&detailMediaItem==item) { TextView status=findViewById(R.id.relatedStatus); status.setText(local.isEmpty()?"Não foi possível carregar. Clique para tentar novamente.":""); status.setVisibility(local.isEmpty()?View.VISIBLE:View.GONE); } });
            }
        });
    }
    private void loadDetailSynopsis(MediaRepository.MediaItem item) {
        String saved=mediaRepo.localizedSynopsis(item);
        detailSynopsis.setText(saved.isEmpty()?"Carregando sinopse em português…":saved);
        detailSynopsis.setOnClickListener(null); detailSynopsis.setClickable(false); detailSynopsis.setFocusable(false); detailSynopsis.setBackground(null); detailSynopsis.setPadding(0,0,0,0); detailSynopsis.setTextColor(0xffe0e0e0);
        mediaRepo.ensureFullSynopsisPt(item,text -> {
            if (detailMediaItem!=item||!inMediaDetailView||isFinishing()) return;
            detailSynopsis.setText(text.isEmpty()?"Sinopse em português indisponível. Tentar novamente":text);
            if (text.isEmpty()) {
                detailSynopsis.setBackgroundResource(R.drawable.pill_bg); int pad=Math.round(12*density); detailSynopsis.setPadding(pad,pad,pad,pad);
                detailSynopsis.setTextColor(getResources().getColorStateList(R.color.modal_button_text,getTheme()));
                detailSynopsis.setFocusableInTouchMode(true); detailSynopsis.setOnClickListener(v -> loadDetailSynopsis(item));
            }
        });
    }

    // ---- Integrated Floating Trailer Menu Modal (Dublado PT-BR -> Legendado PT-BR -> Inglês) ------

    private void ensureTrailerUi() {
        if (trailerModalPlayerView!=null) return;
        ((android.view.ViewStub)findViewById(R.id.trailerStub)).inflate();
        trailerModalOverlay = findViewById(R.id.trailerModalOverlay);
        trailerModalPanel = findViewById(R.id.trailerModalPanel);
        trailerModalHeader = findViewById(R.id.trailerModalHeader);
        trailerModalControls = findViewById(R.id.trailerModalControls);
        trailerProgressRow = findViewById(R.id.trailerProgressRow);
        trailerProgress = findViewById(R.id.trailerProgress);
        trailerElapsed = findViewById(R.id.trailerElapsed);
        trailerDuration = findViewById(R.id.trailerDuration);
        trailerFullscreenControls = findViewById(R.id.trailerFullscreenControls);
        trailerFullscreenBottom = findViewById(R.id.trailerFullscreenBottom);
        btnFullscreenClose = findViewById(R.id.btnFullscreenClose);
        btnFullscreenRewind = findViewById(R.id.btnFullscreenRewind);
        btnFullscreenPlayPause = findViewById(R.id.btnFullscreenPlayPause);
        btnFullscreenForward = findViewById(R.id.btnFullscreenForward);
        trailerVideoBox = findViewById(R.id.trailerVideoBox);
        trailerModalPlayerView = findViewById(R.id.trailerModalPlayerView);
        trailerModalTitle = findViewById(R.id.trailerModalTitle);
        trailerCover = findViewById(R.id.trailerCover);
        trailerCoverImg = findViewById(R.id.trailerCoverImg);
        trailerCoverIcon = findViewById(R.id.trailerCoverIcon);
        trailerCoverMsg = findViewById(R.id.trailerCoverMsg);
        trailerLoadingGroup = findViewById(R.id.trailerLoadingGroup);
        trailerLoadingText = findViewById(R.id.trailerLoadingText);
        trailerSpinner = findViewById(R.id.trailerSpinner);

        btnModalRewind = findViewById(R.id.btnModalRewind);
        btnModalPlayPause = findViewById(R.id.btnModalPlayPause);
        btnModalForward = findViewById(R.id.btnModalForward);
        btnModalLang = findViewById(R.id.btnModalLang);
        btnModalClose = findViewById(R.id.btnModalClose);
        btnModalFullscreen = findViewById(R.id.btnModalFullscreen);

        setupTrailerPlayerView();
        // Keep remote focus inside the modal, including its separate close action.
        btnModalRewind.setOnClickListener(v -> seekTrailerBy(-10));
        btnModalPlayPause.setOnClickListener(v -> toggleTrailerPlayPause());
        btnModalForward.setOnClickListener(v -> seekTrailerBy(10));
        btnModalLang.setOnClickListener(v -> cycleTrailerLanguage());
        btnModalClose.setOnClickListener(v -> dismissTrailerModal());
        btnModalFullscreen.setOnClickListener(v -> setTrailerFullscreen(true));
        btnFullscreenClose.setOnClickListener(v -> dismissTrailerModal());
        btnFullscreenRewind.setOnClickListener(v -> { seekTrailerBy(-10); showFullscreenTrailerControls(); });
        btnFullscreenPlayPause.setOnClickListener(v -> { toggleTrailerPlayPause(); showFullscreenTrailerControls(); });
        btnFullscreenForward.setOnClickListener(v -> { seekTrailerBy(10); showFullscreenTrailerControls(); });
        trailerModalOverlay.setOnClickListener(v -> {
            if (trailerFullscreen) showFullscreenTrailerControls();
            else dismissTrailerModal();
        });

        View.OnKeyListener modalCtrlKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                btnModalClose.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (v == btnModalClose) btnModalFullscreen.requestFocus();
                return true;
            }
            if (v == btnModalClose && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnModalRewind) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == btnModalFullscreen) return true;
            return false;
        };
        for (View control : new View[]{btnModalRewind, btnModalPlayPause, btnModalForward, btnModalLang, btnModalFullscreen, btnModalClose}) {
            control.setFocusableInTouchMode(true);
            control.setOnKeyListener(modalCtrlKeys);
        }
        trailerModalOverlay.setFocusable(false);
        View.OnKeyListener fullscreenKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { btnFullscreenClose.requestFocus(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (v == btnFullscreenClose) btnFullscreenPlayPause.requestFocus();
                return true;
            }
            if (v == btnFullscreenClose && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) return true;
            return (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v == btnFullscreenRewind)
                    || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == btnFullscreenForward);
        };
        for (View control : new View[]{btnFullscreenClose, btnFullscreenRewind, btnFullscreenPlayPause, btnFullscreenForward}) {
            control.setFocusableInTouchMode(true);
            control.setOnKeyListener(fullscreenKeys);
        }

    }

    private void setupTrailerPlayerView() {
        trailerModalPlayerView.setUseController(false);
        trailerModalPlayerView.setFocusable(false);
        trailerModalPlayerView.setFocusableInTouchMode(false);
        trailerModalPlayerView.setKeepScreenOn(false);
    }

    private void cancelTrailerTimeout() {
        if (trailerTimeout != null) handler.removeCallbacks(trailerTimeout);
        trailerTimeout = null;
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void releaseTrailerPlayer() {
        cancelTrailerTimeout();
        if (trailerModalPlayerView != null) {
            trailerModalPlayerView.setPlayer(null);
            trailerModalPlayerView.setKeepScreenOn(false);
        }
        if (trailerPlayer != null) {
            ExoPlayer previous = trailerPlayer;
            trailerPlayer = null;
            androidx.media3.exoplayer.DecoderCounters counters = previous.getVideoDecoderCounters();
            if (counters != null) {
                counters.ensureUpdated();
                android.util.Log.d("TVHomeTrailer", "RELEASE rendered=" + counters.renderedOutputBufferCount
                        + " dropped=" + counters.droppedBufferCount);
            }
            previous.release();
        }
    }

    private void showTrailerUnavailable() {
        setTrailerFullscreen(false);
        trailerLoadToken++;
        releaseTrailerPlayer();
        trailerStarted = false;
        trailerPlaying = false;
        updateTrailerPlayPauseLabels();
        btnModalLang.setText("Indisponível");
        showTrailerCover(0, "Trailer indisponível no momento");
    }

    private void tryNextTrailerCandidate() {
        trailerAttempts++;
        if (trailerAttempts >= currentTrailerCandidates.size()) {
            startYoutubeTrailerFallback();
            return;
        }
        loadTrailerCandidateAt((currentTrailerIdx + 1) % currentTrailerCandidates.size());
    }

    /** Cover and spinner remain visible until the first frame. */
    private void showTrailerLoading() {
        if (trailerCover == null) return;
        trailerCover.animate().cancel();
        trailerCover.setAlpha(1f);
        trailerCover.setVisibility(View.VISIBLE);
        trailerCoverIcon.setVisibility(View.GONE);
        trailerCoverMsg.setVisibility(View.GONE);
        if (trailerLoadingText != null) {
            trailerLoadingText.setText("Procurando trailer...");
        }
        setTrailerSpinner(true);
    }

    /** Native pause, replay and unavailable states. */
    private void showTrailerCover(int iconRes, String msg) {
        if (trailerCover == null) return;
        setTrailerSpinner(false);
        trailerCover.animate().cancel();
        trailerCover.setVisibility(View.VISIBLE);
        trailerCover.animate().alpha(1f).setDuration(120).start();
        if (iconRes != 0) {
            trailerCoverIcon.setImageResource(iconRes);
            trailerCoverIcon.setVisibility(View.VISIBLE);
        } else {
            trailerCoverIcon.setVisibility(View.GONE);
        }
        trailerCoverMsg.setText(msg);
        trailerCoverMsg.setVisibility(msg != null ? View.VISIBLE : View.GONE);
    }

    private void hideTrailerCover() {
        setTrailerSpinner(false);
        if (trailerCover == null || trailerCover.getVisibility() != View.VISIBLE) return;
        trailerCover.animate().cancel();
        trailerCover.animate().alpha(0f).setDuration(220)
                .withEndAction(() -> trailerCover.setVisibility(View.GONE)).start();
    }

    private void setTrailerSpinner(boolean on) {
        if (trailerLoadingGroup != null) {
            trailerLoadingGroup.setVisibility(on ? View.VISIBLE : View.GONE);
        } else if (trailerSpinner != null) {
            trailerSpinner.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Replace everything behind the modal with a static snapshot so the GPU only composites one bitmap
     * plus the panel on every video frame (the live backdrop/cards/ambient layers caused stutter).
     */
    private void freezeBackgroundForTrailer() {
        restoreBackgroundAfterTrailer();
        try {
            int w = root.getWidth() / 2, h = root.getHeight() / 2;
            if (w <= 0 || h <= 0) return;
            Bitmap snap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(snap);
            c.scale(0.5f, 0.5f);
            root.draw(c);
            Drawable bg = new LayerDrawable(new Drawable[]{
                    new BitmapDrawable(getResources(), snap), new ColorDrawable(0xB8000000)});
            trailerModalOverlay.setBackground(bg);
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child == trailerModalOverlay || child.getVisibility() != View.VISIBLE) continue;
                child.setVisibility(View.INVISIBLE);
                trailerHiddenViews.add(child);
            }
        } catch (Throwable t) {
            restoreBackgroundAfterTrailer();
        }
    }

    private void restoreBackgroundAfterTrailer() {
        for (View v : trailerHiddenViews) v.setVisibility(View.VISIBLE);
        trailerHiddenViews.clear();
        if (trailerModalOverlay != null) trailerModalOverlay.setBackgroundColor(0xB8000000);
    }

    private void showTrailerModal(final MediaRepository.MediaItem item) {
        if (item == null) return;
        trailerRequestedAt=android.os.SystemClock.elapsedRealtime();
        ensureTrailerUi();
        releaseTrailerPlayer();
        trailerModalActive = true;
        handler.removeCallbacks(updateTrailerProgress);
        handler.post(updateTrailerProgress);
        activeTrailerItem = item;
        trailerYoutubeAttempted = false;
        trailerPlaying = false;
        trailerStarted = false;
        trailerAttempts = 0;
        currentTrailerCandidates = Collections.emptyList();
        currentTrailerIdx = 0;
        final int requestToken = ++trailerLoadToken;
        trailerModalTitle.setText("Trailer  •  " + item.title);
        btnModalLang.setText("Buscando...");
        updateTrailerPlayPauseLabels();
        hideGlass();
        if (trailerCoverImg != null) {
            trailerCoverImg.setImageDrawable(null);
            mediaRepo.loadImage(item.backdropUrl,item.posterUrl, trailerCoverImg, 960, 540);
        }
        showTrailerLoading();
        freezeBackgroundForTrailer();
        trailerModalOverlay.animate().cancel();
        trailerModalOverlay.setAlpha(0f);
        trailerModalOverlay.setVisibility(View.VISIBLE);
        trailerModalOverlay.animate().alpha(1f).setDuration(160).start();
        btnModalPlayPause.requestFocus();
        trailerTimeout = () -> {
            if (trailerModalActive && requestToken == trailerLoadToken) showTrailerUnavailable();
        };
        handler.postDelayed(trailerTimeout, 30000);
        mediaRepo.resolveTrailerCandidates(item, candidates -> {
            if (!trailerModalActive || requestToken != trailerLoadToken) return;
            cancelTrailerTimeout();
            currentTrailerCandidates = candidates;
            if (candidates.isEmpty()) {
                showTrailerUnavailable();
            } else {
                loadTrailerCandidateAt(0);
            }
        });
    }

    private void startYoutubeTrailerFallback() {
        if (!trailerModalActive) return;
        if (trailerYoutubeAttempted || activeTrailerItem == null) {
            showTrailerUnavailable();
            return;
        }
        trailerYoutubeAttempted = true;
        releaseTrailerPlayer();
        final int token = ++trailerLoadToken;
        trailerStarted = false;
        trailerPlaying = false;
        updateTrailerPlayPauseLabels();
        showTrailerLoading();
        trailerLoadingText.setText("Buscando outra fonte do trailer...");
        trailerTimeout = () -> {
            if (trailerModalActive && token == trailerLoadToken) showTrailerUnavailable();
        };
        handler.postDelayed(trailerTimeout, 25000);
        mediaRepo.resolveYoutubeTrailers(activeTrailerItem, candidates -> {
            if (!trailerModalActive || token != trailerLoadToken) return;
            cancelTrailerTimeout();
            currentTrailerCandidates = candidates;
            trailerAttempts = 0;
            if (candidates.isEmpty()) showTrailerUnavailable();
            else loadTrailerCandidateAt(0);
        });
    }

    private void showFullscreenTrailerControls() {
        if (!trailerFullscreen) return;
        boolean hidden = trailerFullscreenControls.getVisibility() != View.VISIBLE;
        trailerFullscreenControls.setVisibility(View.VISIBLE);
        if (hidden) {
            btnFullscreenPlayPause.requestFocus();
            android.util.Log.d("TVHomeTrailer", "CONTROLS visible");
        }
        handler.removeCallbacks(hideFullscreenControls);
        handler.postDelayed(hideFullscreenControls, 3000);
    }

    private void setTrailerFullscreen(boolean fullscreen) {
        if (trailerFullscreen == fullscreen || (fullscreen && !trailerModalActive)) return;
        trailerFullscreen = fullscreen;
        if (fullscreen) {
            trailerPanelLayout = (FrameLayout.LayoutParams) trailerModalPanel.getLayoutParams();
            trailerVideoLayout = (LinearLayout.LayoutParams) trailerVideoBox.getLayoutParams();
            trailerPanelBackground = trailerModalPanel.getBackground();
            trailerPanelPadding.set(trailerModalPanel.getPaddingLeft(), trailerModalPanel.getPaddingTop(),
                    trailerModalPanel.getPaddingRight(), trailerModalPanel.getPaddingBottom());
            trailerModalHeader.setVisibility(View.GONE);
            trailerModalControls.setVisibility(View.GONE);
            LinearLayout panel = (LinearLayout) trailerModalPanel;
            trailerProgressIndex = panel.indexOfChild(trailerProgressRow);
            trailerProgressLayout = (LinearLayout.LayoutParams) trailerProgressRow.getLayoutParams();
            panel.removeView(trailerProgressRow);
            LinearLayout.LayoutParams progressLayout = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (24 * density));
            progressLayout.bottomMargin = (int) (12 * density);
            trailerFullscreenBottom.addView(trailerProgressRow, 0, progressLayout);
            trailerModalPanel.setPadding(0, 0, 0, 0);
            trailerModalPanel.setBackgroundColor(Color.BLACK);
            trailerModalPanel.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            trailerVideoBox.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            trailerModalOverlay.setFocusableInTouchMode(true);
            showFullscreenTrailerControls();
        } else {
            handler.removeCallbacks(hideFullscreenControls);
            fullscreenRevealKey = -1;
            trailerFullscreenControls.setVisibility(View.GONE);
            trailerFullscreenBottom.removeView(trailerProgressRow);
            ((LinearLayout) trailerModalPanel).addView(trailerProgressRow, trailerProgressIndex, trailerProgressLayout);
            trailerModalPanel.setLayoutParams(trailerPanelLayout);
            trailerVideoBox.setLayoutParams(trailerVideoLayout);
            trailerModalPanel.setBackground(trailerPanelBackground);
            trailerModalPanel.setPadding(trailerPanelPadding.left, trailerPanelPadding.top,
                    trailerPanelPadding.right, trailerPanelPadding.bottom);
            trailerModalHeader.setVisibility(View.VISIBLE);
            trailerModalControls.setVisibility(View.VISIBLE);
            trailerModalOverlay.setFocusableInTouchMode(false);
            trailerModalOverlay.setFocusable(false);
            btnModalFullscreen.requestFocus();
        }
        android.util.Log.d("TVHomeTrailer", "FULLSCREEN " + fullscreen);
    }

    private void dismissTrailerModal() {
        if (!trailerModalActive) return;
        setTrailerFullscreen(false);
        trailerModalActive = false;
        handler.removeCallbacks(updateTrailerProgress);
        handler.removeCallbacks(hideFullscreenControls);
        trailerPlaying = false;
        trailerLoadToken++;
        releaseTrailerPlayer();
        // Bring the live UI back before moving focus into it (invisible views can't take focus).
        restoreBackgroundAfterTrailer();
        trailerModalOverlay.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            trailerModalOverlay.setVisibility(View.GONE);
        }).start();

        if (inMediaDetailView && btnWatchTrailer != null) {
            btnWatchTrailer.requestFocus();
        } else {
            focusMediaCard(lastClickedMediaCardIdx);
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void loadTrailerCandidateAt(int idx) {
        if (!trailerModalActive || currentTrailerCandidates.isEmpty()) return;
        releaseTrailerPlayer();
        currentTrailerIdx = Math.max(0, Math.min(idx, currentTrailerCandidates.size() - 1));
        NativeTrailerSource.Candidate candidate = currentTrailerCandidates.get(currentTrailerIdx);
        if (candidate.youtube) trailerYoutubeAttempted = true;
        final int token = ++trailerLoadToken;
        trailerStarted = false;
        trailerLanguageInitialized = false;
        trailerLanguageMode = 0;
        trailerOriginalLanguage = candidate.originalLanguage;
        showTrailerLoading();
        trailerLoadingText.setText("Carregando trailer...");
        btnModalLang.setText("Idioma...");
        long startedAt = android.os.SystemClock.elapsedRealtime();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("TVHome/2.0")
                .setConnectTimeoutMs(5000).setReadTimeoutMs(8000);
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10000, 30000, 1000, 2500)
                        .setTargetBufferBytes(16 * 1024 * 1024).build())
                .build();
        trailerPlayer = player;
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setMaxVideoSize(1280, 720).setMaxVideoBitrate(3500000)
                .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
                .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC)
                .setMaxAudioChannelCount(2)
                .setPreferredAudioLanguages("pt-BR", "pt", candidate.originalLanguage)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
        player.addListener(new Player.Listener() {
            private boolean isCurrent() {
                return trailerModalActive && token == trailerLoadToken && player == trailerPlayer;
            }

            @Override public void onRenderedFirstFrame() {
                if (!isCurrent() || trailerStarted) return;
                trailerStarted = true;
                cancelTrailerTimeout();
                hideTrailerCover();
                android.util.Log.d("TVHomeTrailer", "FIRST_FRAME token=" + token + " ms="
                        + (android.os.SystemClock.elapsedRealtime() - startedAt)+" modalMs="
                        + (android.os.SystemClock.elapsedRealtime() - trailerRequestedAt));
            }

            @Override public void onPlaybackStateChanged(int state) {
                if (!isCurrent()) return;
                android.util.Log.d("TVHomeTrailer", "STATE token=" + token + " state=" + state);
                if (state == Player.STATE_BUFFERING && trailerStarted) {
                    trailerLoadingText.setText("Carregando...");
                    setTrailerSpinner(true);
                } else if (state == Player.STATE_READY && trailerStarted) {
                    setTrailerSpinner(false);
                } else if (state == Player.STATE_ENDED) {
                    setTrailerFullscreen(false);
                    trailerPlaying = false;
                    updateTrailerPlayPauseLabels();
                    showTrailerCover(R.drawable.ic_play, "Trailer finalizado  •  OK em Play para rever");
                }
            }

            @Override public void onIsPlayingChanged(boolean playing) {
                if (!isCurrent()) return;
                trailerPlaying = playing;
                trailerModalPlayerView.setKeepScreenOn(playing);
                updateTrailerPlayPauseLabels();
                if (playing && trailerStarted) hideTrailerCover();
                else if (trailerStarted && player.getPlaybackState() == Player.STATE_READY
                        && !player.getPlayWhenReady()) showTrailerCover(R.drawable.ic_pause, "Pausado");
            }

            @Override public void onTracksChanged(Tracks tracks) {
                if (!isCurrent() || trailerLanguageInitialized || tracks.isEmpty()) return;
                trailerLanguageInitialized = true;
                if (trailerOriginalLanguage.isEmpty()) {
                    for (Tracks.Group group : tracks.getGroups()) {
                        if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
                        for (int i = 0; i < group.length; i++) {
                            androidx.media3.common.Format format = group.getTrackFormat(i);
                            if (group.isTrackSupported(i) && format.language != null
                                    && (trailerOriginalLanguage.isEmpty() || (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0)) {
                                trailerOriginalLanguage = format.language;
                            }
                        }
                    }
                }
                trailerLanguageMode = hasTrailerLanguage(C.TRACK_TYPE_AUDIO, "pt") || candidate.languagePriority == 0 ? 0
                        : hasTrailerLanguage(C.TRACK_TYPE_TEXT, "pt") || candidate.languagePriority == 1 ? 1 : 2;
                applyTrailerLanguage();
                android.util.Log.d("TVHomeTrailer", "TRACKS ptAudio="
                        + hasTrailerLanguage(C.TRACK_TYPE_AUDIO, "pt") + " ptText="
                        + hasTrailerLanguage(C.TRACK_TYPE_TEXT, "pt"));
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (!isCurrent()) return;
                android.util.Log.w("TVHomeTrailer", "NATIVE_ERROR " + error.getErrorCodeName(), error);
                tryNextTrailerCandidate();
            }
        });
        trailerModalPlayerView.setPlayer(player);
        androidx.media3.common.MediaItem.Builder media = new androidx.media3.common.MediaItem.Builder()
                .setUri(candidate.streamUrl).setMimeType(candidate.mimeType);
        if (!candidate.portugueseSubtitles.isEmpty()) {
            media.setSubtitleConfigurations(Collections.singletonList(new androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(
                    Uri.parse(candidate.portugueseSubtitles)).setMimeType(MimeTypes.TEXT_VTT).setLanguage("pt-BR").build()));
        }
        if (candidate.youtube) {
            DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(
                    (androidx.media3.datasource.DataSource.Factory)() -> new TrailerDataSource(http.createDataSource()));
            androidx.media3.exoplayer.source.MediaSource video = factory.createMediaSource(media.build());
            if (candidate.audioStreamUrl.isEmpty()) player.setMediaSource(video);
            else player.setMediaSource(new MergingMediaSource(video, factory.createMediaSource(
                    new androidx.media3.common.MediaItem.Builder().setUri(candidate.audioStreamUrl)
                            .setMimeType(MimeTypes.AUDIO_MP4).build())));
        } else {
            player.setMediaSource(new HlsMediaSource.Factory(http)
                    .setPlaylistParserFactory(new TrailerPlaylistParserFactory(candidate.originalLanguage))
                    .setAllowChunklessPreparation(true).createMediaSource(media.build()));
        }
        trailerTimeout = () -> {
            if (trailerModalActive && token == trailerLoadToken && !trailerStarted) tryNextTrailerCandidate();
        };
        handler.postDelayed(trailerTimeout, TRAILER_LOAD_TIMEOUT_MS);
        android.util.Log.d("TVHomeTrailer", "LOAD token=" + token + " candidate=" + currentTrailerIdx
                + " source=" + (candidate.youtube ? "youtube-native" : "apple")
                + " separateAudio=" + !candidate.audioStreamUrl.isEmpty() + " title=" + candidate.title);
        player.prepare();
        player.play();
    }

    private boolean hasTrailerLanguage(int type, String language) {
        if (trailerPlayer == null) return false;
        for (Tracks.Group group : trailerPlayer.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                String actual = group.getTrackFormat(i).language;
                if (group.isTrackSupported(i) && actual != null
                        && (actual.equalsIgnoreCase(language) || actual.toLowerCase(java.util.Locale.ROOT)
                        .startsWith(language.toLowerCase(java.util.Locale.ROOT) + "-"))) return true;
            }
        }
        return false;
    }

    private void cycleTrailerLanguage() {
        if (!trailerModalActive || trailerPlayer == null || !trailerLanguageInitialized) return;
        NativeTrailerSource.Candidate candidate = currentTrailerCandidates.get(currentTrailerIdx);
        if (candidate.youtube && (candidate.languagePriority == 1 && candidate.portugueseSubtitles.isEmpty()
                || candidate.mimeType.equals("video/mp4") && candidate.languagePriority == 0)) return;
        String original = trailerOriginalLanguage;
        for (int i = 1; i <= 3; i++) {
            int mode = (trailerLanguageMode + i) % 3;
            if (mode == 0 && !hasTrailerLanguage(C.TRACK_TYPE_AUDIO, "pt")) continue;
            if (mode == 1 && (!hasTrailerLanguage(C.TRACK_TYPE_TEXT, "pt") || original.startsWith("pt"))) continue;
            if (mode == 2 && original.startsWith("pt")) continue;
            trailerLanguageMode = mode;
            applyTrailerLanguage();
            break;
        }
    }

    private void applyTrailerLanguage() {
        if (trailerPlayer == null) return;
        String original = trailerOriginalLanguage;
        String audio = trailerLanguageMode == 0 ? "pt-BR"
                : hasTrailerLanguage(C.TRACK_TYPE_AUDIO, "en") ? "en" : original;
        trailerPlayer.setTrackSelectionParameters(trailerPlayer.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguages(audio, trailerLanguageMode == 0 ? "pt" : "en", original)
                .setPreferredTextLanguages("pt-BR", "pt")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, trailerLanguageMode != 1).build());
        NativeTrailerSource.Candidate candidate = currentTrailerCandidates.get(currentTrailerIdx);
        btnModalLang.setText(trailerLanguageMode == 0 ? (original.startsWith("pt") && candidate.languagePriority != 0 ? "Original PT-BR" : "Dublado PT-BR")
                : trailerLanguageMode == 1 ? "Legendado PT-BR"
                : hasTrailerLanguage(C.TRACK_TYPE_AUDIO, "en") || original.startsWith("en") || candidate.languagePriority == 2 ? "Inglês" : "Original");
        android.util.Log.d("TVHomeTrailer", "LANGUAGE " + btnModalLang.getText());
    }

    private void toggleTrailerPlayPause() {
        if (!trailerModalActive || trailerPlayer == null || !trailerStarted) return;
        if (trailerPlayer.getPlaybackState() == Player.STATE_ENDED) {
            trailerPlayer.seekTo(0);
            trailerPlayer.play();
        } else if (trailerPlayer.getPlayWhenReady()) {
            trailerPlayer.pause();
        } else {
            trailerPlayer.play();
        }
    }

    private void seekTrailerBy(int deltaSeconds) {
        if (!trailerModalActive || trailerPlayer == null || !trailerStarted) return;
        long duration = trailerPlayer.getDuration();
        long position = Math.max(0, trailerPlayer.getCurrentPosition() + deltaSeconds * 1000L);
        if (duration != C.TIME_UNSET) position = Math.min(position, duration);
        trailerPlayer.seekTo(position);
        android.util.Log.d("TVHomeTrailer", "SEEK position=" + position);
    }

    private void updateTrailerPlayPauseLabels() {
        for (TextView button : new TextView[]{btnModalPlayPause, btnFullscreenPlayPause}) {
            if (button == null) continue;
            button.setText(trailerPlaying ? "Pausar" : "Play");
            button.setCompoundDrawablesWithIntrinsicBounds(
                    trailerPlaying ? R.drawable.ic_pause : R.drawable.ic_play, 0, 0, 0);
        }
    }

    // ---- Choose an installed app using current Brazil offers and exact content links -----------

    private View adjacentFilter(int current,int direction) {
        int[] order={0,1,2,3,4,7,5,6,9,10,8};
        for (int i=0;i<order.length;i++) if (order[i]==current) {
            for (int j=i+direction;j>=0&&j<order.length;j+=direction) if (filterTabs[order[j]].isShown()) return filterTabs[order[j]];
            break;
        }
        return null;
    }

    private void showWatchAppPicker(final MediaRepository.MediaItem item) { showWatchAppPicker(item,btnWatchMedia); }
    private void showWatchAppPicker(final MediaRepository.MediaItem item,View returnFocus) {
        if (item == null) return;
        if (trailerModalActive) dismissTrailerModal();
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout panelRoot = (LinearLayout) panel;
        LinearLayout rows = panel.findViewById(R.id.rows);
        ScrollView scroll = (ScrollView) rows.getParent();
        TextView heading = new TextView(this);
        heading.setText("Onde Assistir");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(17);
        heading.setTypeface(getResources().getFont(R.font.outfit));
        heading.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (4 * density));
        panelRoot.addView(heading, 0);
        TextView status = new TextView(this);
        status.setText("Consultando serviços no Brasil...");
        status.setTextColor(0xB3FFFFFF);
        status.setTextSize(12);
        status.setPadding((int) (16 * density), 0, (int) (16 * density), (int) (10 * density));
        panelRoot.addView(status, 1);
        int width = (int) (390 * density);
        int rowHeight = (int) (56 * density);
        PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(20f);
        String[][] apps = {
                {"com.stremio.one", "Stremio", "stremio"},
                {"com.global.unitviptv", "UniTV", "unitv"},
                {"com.netflix.ninja", "Netflix", "netflix"},
                {"com.amazon.amazonvideo.livingroom", "Prime Video", "prime"},
                {"com.apple.atve.androidtv.appletv", "Apple TV", "apple"},
                {"com.globo.globotv", "Globoplay", "globoplay"},
                {"com.wbd.stream", "HBO Max", "max"},
                {"com.disney.disneyplus", "Disney+", "disney"}
        };
        java.util.function.Consumer<List<WatchAvailability.Offer>> populate = offers -> {
            String focusedPackage = null;
            for (int i = 0; i < rows.getChildCount(); i++) {
                if (rows.getChildAt(i).hasFocus()) focusedPackage = (String) rows.getChildAt(i).getTag();
            }
            rows.removeAllViews();
            for (String[] app : apps) {
                WatchAvailability.Offer match = null;
                if (offers != null) for (WatchAvailability.Offer offer : offers) {
                    if (offer.provider.equals(app[2])) { match = offer; break; }
                }
                boolean thirdParty = app[2].equals("stremio") || app[2].equals("unitv");
                boolean installed = isPackageInstalled(getPackageManager(), app[0]);
                if (!thirdParty && (!installed || match == null)) continue;
                String subtitle = !installed ? "Não instalado" : match != null ? match.description()
                        : app[2].equals("stremio") ? "Escolher fonte no app" : "Buscar título no app";
                final WatchAvailability.Offer target = match;
                View row = getLayoutInflater().inflate(R.layout.item_watch_app, rows, false);
                row.setTag(app[0]);
                row.setFocusableInTouchMode(true);
                ((TextView) row.findViewById(R.id.title)).setText(app[1]);
                ((TextView) row.findViewById(R.id.status)).setText(subtitle);
                ImageView icon = row.findViewById(R.id.icon);
                try { icon.setImageDrawable(getPackageManager().getApplicationIcon(app[0])); }
                catch (Exception ignored) { icon.setImageResource(R.drawable.ic_video); }
                row.setAlpha(installed ? 1f : 0.45f);
                row.setOnClickListener(v -> {
                    if (!installed) { showGlassNotice(R.drawable.ic_settings, "App não instalado", app[1], false); return; }
                    popup.dismiss();
                    launchMediaInApp(app[0], app[1], item, target);
                });
                rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
                if (app[0].equals(focusedPackage)) row.requestFocus();
            }
            scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.min(5, rows.getChildCount()) * rowHeight));
            if (popup.isShowing()) {
                if (rows.findFocus() == null && rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
                panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
                popup.update(width, panel.getMeasuredHeight());
            }
        };
        populate.accept(mediaRepo.getAvailableProviders(item));
        popup.setOnDismissListener(() -> { if (returnFocus!=null&&returnFocus.isShown()) returnFocus.requestFocus(); });
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        popup.setHeight(panel.getMeasuredHeight());
        popup.showAtLocation(root, Gravity.CENTER, 0, 0);
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
        mediaRepo.resolveProvidersAsync(item, offers -> {
            if (!popup.isShowing() || isFinishing()) return;
            status.setText(offers == null ? "Não foi possível consultar os serviços."
                    : offers.isEmpty() ? "Nenhuma oferta confirmada no Brasil." : "Disponibilidade no Brasil");
            populate.accept(offers);
        });
    }

    private static boolean isPackageInstalled(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationInfo(pkg, 0).enabled;
        } catch (Exception e) { return false; }
    }

    private void launchMediaInApp(String pkg, String label, MediaRepository.MediaItem item, WatchAvailability.Offer offer) {
        WatchMonitor.get(this).launched(pkg,item);
        AutoSearchService.cancelPending(this);
        pendingAutoKillPkg = pkg;
        lastLaunchedPkg = pkg;
        lastLaunchedLabel = label;
        String link = pkg.equals("com.stremio.one") ? WatchAvailability.stremioLink(item.imdbId, item.isSeries())
                : offer == null ? "" : offer.url;
        if (!link.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Netflix TV needs the external-discovery source as a String to route the title.
            if (pkg.equals("com.netflix.ninja")) intent.putExtra("source", "30");
            if (intent.resolveActivity(getPackageManager()) != null) {
                if (!pkg.equals("com.stremio.one")) {
                    ensureAutoSearchServiceEnabled();
                    AutoSearchService.enqueueDeepLink(this, pkg, item.title, item.year, link);
                }
                try {
                    android.util.Log.d("TVHomeWatch", "OPEN imdb=" + item.imdbId + " package=" + pkg + " link=" + link);
                    startActivity(intent);
                    return;
                } catch (Exception e) {
                    AutoSearchService.cancelPending(this);
                    android.util.Log.w("TVHomeWatch", "Content link unavailable", e);
                }
            }
        }
        ensureAutoSearchServiceEnabled();
        AutoSearchService.enqueueSearch(this, pkg, item.title, item.year);
        showGlassNotice(R.drawable.ic_livetv, "Buscando no " + label, item.title, true);
        launchPackageLeanback(pkg);
    }

    private void launchPackageLeanback(String pkg) {
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (launch.getComponent() != null) {
                lastLaunchedComponent = launch.getComponent();
            }
            try {
                startActivity(launch);
            } catch (Exception ignored) {
            }
        } else {
            showGlassNotice(R.drawable.ic_settings, "App não encontrado", pkg, false);
        }
    }

    private boolean movingMode = false;

    // ---- Last App Switcher & Auto-Kill -----------------------------------------------------------

    @Override
    public void onAppLaunched(String pkg, String label, ComponentName component) {
        WatchMonitor.get(this).appOpened(pkg);
        lastLaunchedPkg = pkg;
        lastLaunchedLabel = label;
        lastLaunchedComponent = component;
        pendingAutoKillPkg = pkg;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("last_pkg", pkg)
                .putString("last_label", label)
                .putString("last_cls", component.getClassName())
                .apply();
    }

    private void restoreLastLaunchedFromPrefs() {
        if (lastLaunchedComponent != null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String pkg = prefs.getString("last_pkg", null);
        String cls = prefs.getString("last_cls", null);
        String lbl = prefs.getString("last_label", null);
        if (pkg != null && cls != null) {
            lastLaunchedPkg = pkg;
            lastLaunchedLabel = lbl != null ? lbl : pkg;
            lastLaunchedComponent = new ComponentName(pkg, cls);
        }
    }

    private void switchToLastApp() {
        restoreLastLaunchedFromPrefs();
        if (lastLaunchedComponent == null) {
            showGlassNotice(R.drawable.ic_input, "Último Aplicativo", "Nenhum aberto ainda", false);
            return;
        }
        showGlassNotice(R.drawable.ic_input, "Voltando para " + lastLaunchedLabel, "Alternando", true);
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(lastLaunchedComponent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            pendingAutoKillPkg = lastLaunchedPkg;
            startActivity(i);
        } catch (Exception e) {
            Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(lastLaunchedPkg);
            if (fallback != null) startActivity(fallback);
        }
    }

    private boolean isAutoKillEnabled() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("auto_kill", true);
    }

    private void toggleAutoKill() {
        boolean next = !isAutoKillEnabled();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("auto_kill", next).apply();
        showGlassNotice(R.drawable.ic_input, "Auto-Kill em 2º Plano", next ? "Ativado" : "Desativado", next);
    }

    private void cleanRamNow() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        for (String pkg : adapter.getAllPackages()) {
            if (!pkg.equals(getPackageName())) {
                try {
                    am.killBackgroundProcesses(pkg);
                } catch (Exception ignored) {
                }
            }
        }
        handler.postDelayed(() -> {
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            long freeMb = after.availMem / (1024 * 1024);
            long gainedMb = Math.max(0, (after.availMem - before.availMem) / (1024 * 1024));
            String title = gainedMb > 5
                    ? "Memória RAM limpa (+" + gainedMb + " MB)"
                    : "Memória RAM otimizada";
            showGlassNotice(R.drawable.ic_av, title, freeMb + " MB livres", true);
        }, 300);
    }

    private long getFreeRamMb() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    // ---- Weather for the optional configured location -----------------------------------------------------

    private void updateDateAndWeatherText() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d 'DE' MMMM", new Locale("pt", "BR"));
        String d = sdf.format(new Date()).toUpperCase(new Locale("pt", "BR"))
                .replace("-FEIRA", "");
        if (dateView != null) {
            dateView.setText(d);
        }
        if (weatherView != null) {
            if (!cachedWeatherSuffix.isEmpty()) {
                weatherView.setText(cachedWeatherSuffix);
                weatherView.setVisibility(View.VISIBLE);
            } else {
                weatherView.setVisibility(View.GONE);
            }
        }
    }

    private void fetchWeatherIfNeeded() {
        if (deviceConfig==null||!deviceConfig.hasWeather()) return;
        long now = System.currentTimeMillis();
        if (!cachedWeatherSuffix.isEmpty() && (now - lastWeatherFetchMs) < WEATHER_REFRESH_MS) {
            return;
        }
        new Thread(() -> {
            try {
                String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + deviceConfig.latitude
                        + "&longitude=" + deviceConfig.longitude
                        + "&current=temperature_2m,precipitation,weather_code"
                        + "&hourly=precipitation_probability"
                        + "&timezone=auto&forecast_days=1";
                JSONObject json = new JSONObject(NativeTrailerSource.request(urlStr,null));
                JSONObject current = json.getJSONObject("current");
                final String finalSuffix = WeatherForecast.summary(current,json.optJSONObject("hourly"));
                lastWeatherFetchMs = System.currentTimeMillis();
                handler.post(() -> {
                    cachedWeatherSuffix = finalSuffix;
                    updateDateAndWeatherText();
                });
            } catch (Exception ignored) {
                handler.postDelayed(this::fetchWeatherIfNeeded, 5000);
            }
        }).start();
    }

    // ---- Night Volume Leveler (Realtek / AudioFX Compressor) -------------------------------------

    private int getNightVolumeMode() {
        // 0 = Desligado (Manual), 1 = Ligado (Manual), 2 = Automático por Horário
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("night_vol_mode", 0);
    }

    private int getNightStartMins() {
        // Default 22:00 (1320 mins)
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("night_vol_start_mins", 22 * 60);
    }

    private int getNightEndMins() {
        // Default 07:00 (420 mins)
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("night_vol_end_mins", 7 * 60);
    }

    private void stepNightStartMins(int deltaMins) {
        int m = (((getNightStartMins() + deltaMins) % 1440) + 1440) % 1440;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt("night_vol_start_mins", m).apply();
        applyNightAudioSchedule();
    }

    private void stepNightEndMins(int deltaMins) {
        int m = (((getNightEndMins() + deltaMins) % 1440) + 1440) % 1440;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt("night_vol_end_mins", m).apply();
        applyNightAudioSchedule();
    }

    private static String formatTimeMins(int mins) {
        int m = ((mins % 1440) + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    private String getNightVolumeModeLabel() {
        int m = getNightVolumeMode();
        if (m == 1) return "Ligado (Manual)";
        if (m == 2) return "Auto " + formatTimeMins(getNightStartMins()) + "–" + formatTimeMins(getNightEndMins());
        return "Desligado";
    }

    private void setNightVolumeMode(int mode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt("night_vol_mode", mode).apply();
        applyNightAudioSchedule();
        if (mode == 1) {
            showGlassNotice(R.drawable.ic_livetv, "Volume Noturno", "Ligado (Manual)", true);
        } else if (mode == 2) {
            showGlassNotice(R.drawable.ic_livetv, "Volume Noturno Automático",
                    formatTimeMins(getNightStartMins()) + " às " + formatTimeMins(getNightEndMins()), true);
        } else {
            showGlassNotice(R.drawable.ic_livetv, "Volume Noturno", "Desligado", false);
        }
    }

    private void applyNightAudioSchedule() {
        int mode = getNightVolumeMode();
        boolean shouldEnable;
        if (mode == 1) {
            shouldEnable = true;
        } else if (mode == 2) {
            Calendar cal = Calendar.getInstance();
            int nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            int start = getNightStartMins();
            int end = getNightEndMins();
            if (start == end) {
                shouldEnable = true;
            } else if (start > end) {
                shouldEnable = (nowMins >= start || nowMins < end);
            } else {
                shouldEnable = (nowMins >= start && nowMins < end);
            }
        } else {
            shouldEnable = false;
        }
        setNightAudioEnabled(shouldEnable);
    }

    private void setNightAudioEnabled(boolean enable) {
        if (nightAudioActive == enable) return;
        nightAudioActive = enable;
        try {
            if (enable) {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = new LoudnessEnhancer(0);
                }
                loudnessEnhancer.setTargetGain(350); // +3.5 dB dialogue boost
                loudnessEnhancer.setEnabled(true);

                if (nightEqualizer == null) {
                    nightEqualizer = new Equalizer(0, 0);
                }
                short bands = nightEqualizer.getNumberOfBands();
                short[] range = nightEqualizer.getBandLevelRange();
                if (bands > 0 && range != null && range.length == 2) {
                    short min = range[0], max = range[1];
                    for (short b = 0; b < bands; b++) {
                        int freqHz = nightEqualizer.getCenterFreq(b) / 1000;
                        if (freqHz < 160) {
                            // Attenuate heavy bass/explosions at night
                            nightEqualizer.setBandLevel(b, (short) (min / 2));
                        } else if (freqHz >= 900 && freqHz <= 3500) {
                            // Clear speech frequencies
                            nightEqualizer.setBandLevel(b, (short) (max / 3));
                        } else {
                            nightEqualizer.setBandLevel(b, (short) 0);
                        }
                    }
                    nightEqualizer.setEnabled(true);
                }
            } else {
                if (loudnessEnhancer != null) loudnessEnhancer.setEnabled(false);
                if (nightEqualizer != null) nightEqualizer.setEnabled(false);
            }
        } catch (Exception ignored) {
        }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    private void focusGlassOnView(View card, float scale, float baseRadius) {
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 5f * density;
        float w = card.getWidth() * scale + 2 * pad;
        float h = card.getHeight() * scale + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(baseRadius * scale);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(160).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
    }

    @Override
    public void onTileFocused(int position, View card) {
        if (appsTransitioning) return;
        if (mediaBackground!=null) mediaBackground.cancel();
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);

        focusGlassOnView(card, AppAdapter.FOCUS_SCALE, 18f * density);

        if (mediaBackdropBg != null && mediaBackdropBg.getAlpha() > 0f && !inMediaDetailView) {
            mediaBackdropBg.animate().alpha(0f).setDuration(240).start();
            mediaBackdropScrim.animate().alpha(0f).setDuration(240).start();
        }

        int color = adapter.ambientColorAt(position);
        animateAmbientTo(color);

        View item = (View) card.getParent();
        float pad = 5f * density;
        if (!inAppsDrawer) {
            lastDockColumn = Math.max(0, Math.min(position, COLUMNS - 1));
            heroShown = true;
        } else {
            hideHero();
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        int color = adapter.ambientColorAt(position);
        animateAmbientTo(color);
        heroShown = true;
    }

    private void animateAmbientTo(int targetColor) {
        if (targetColor == 0) targetColor = 0xFFE50914;
        if (ambientColorAnimator != null) {
            ambientColorAnimator.cancel();
        }
        int fromColor = currentAmbientColor;
        ambientColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, targetColor);
        ambientColorAnimator.setDuration(360);
        ambientColorAnimator.addUpdateListener(anim -> {
            currentAmbientColor = (int) anim.getAnimatedValue();
            int c = currentAmbientColor & 0x00FFFFFF;
            ambientGlow.setColors(new int[]{0x66000000 | c, 0x2E000000 | c, 0x00000000 | c});
            ambient.invalidate();
        });
        ambientColorAnimator.start();
        if (ambient.getAlpha() < 0.99f) {
            ambient.animate().alpha(1f).setDuration(360).start();
        }
    }

    private void setAmbient(int color) {
        animateAmbientTo(color);
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation & app organization -----------------------------------------------------------

    @Override
    public void onTileClicked(int position) {
        if (movingMode) {
            stopMoving();
            return;
        }
        adapter.launchAt(position);
    }

    @Override
    public void onTileLongPressed(int position, View card) {
        if (movingMode) {
            stopMoving();
            return;
        }
        showAppMenu(position, card);
    }

    private void startMoving(int position) {
        movingMode = true;
        greeting.setText(getString(R.string.moving_hint, adapter.labelAt(position)));
        greeting.setTextColor(0xFFFFFFFF);
        if (inAppsDrawer) {
            showGlassNotice(R.drawable.ic_input, "Movendo " + adapter.labelAt(position), "OK para fixar", true);
        }
        focusTile(position);
    }

    private void stopMoving() {
        if (!movingMode) return;
        movingMode = false;
        greeting.setTextColor(0x8CFFFFFF);
        updateGreeting();
        showGlassNotice(R.drawable.ic_input, getString(R.string.order_saved), "Salvo", true);
    }

    private void moveSelectedTile(int fromPos, int toPos) {
        adapter.swapItems(fromPos, toPos);
        greeting.setText(getString(R.string.moving_hint, adapter.labelAt(toPos)));
        grid.postDelayed(() -> focusTile(toPos), 60);
    }

    private void showAppMenu(int position, View card) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (260 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(16f);

        addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.menu_move), -1,
                () -> startMoving(position));

        if (inAppsDrawer) {
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.menu_pin_dock), -1, () -> {
                adapter.pinToDock(position);
                showGlassNotice(R.drawable.ic_livetv, getString(R.string.order_saved), "Favorito", true);
                closeAppsDrawer(0);
            });
        }

        addSeparator(rows);
        addInputRow(rows, popup, R.drawable.ic_settings, getString(R.string.menu_app_info), -1,
                () -> adapter.openAppInfo(position));

        addInputRow(rows, popup, R.drawable.ic_av, getString(R.string.menu_reset_order), -1, () -> {
            adapter.resetOrder();
            showGlassNotice(R.drawable.ic_av, getString(R.string.order_saved), "Padrão", true);
            if (inAppsDrawer) closeAppsDrawer(0);
            else grid.post(() -> focusTile(0));
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int pw = Math.max(width, panel.getMeasuredWidth());
        int ph = panel.getMeasuredHeight();
        popup.setWidth(pw);

        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        int x = Math.max((int) (24 * density), Math.min(r.centerX() - pw / 2, root.getWidth() - pw - (int) (24 * density)));
        int y = r.top - ph - (int) (14 * density);
        if (y < (int) (24 * density)) {
            y = Math.min(r.bottom + (int) (14 * density), root.getHeight() - ph - (int) (24 * density));
        }

        panel.setAlpha(0f);
        panel.setScaleY(0.92f);
        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        panel.animate().alpha(1f).scaleY(1f).setDuration(180).start();
        rows.getChildAt(0).requestFocus();
    }

    @Override
    public boolean onTileKey(int position, int keyCode, KeyEvent event) {
        int count = adapter.getItemCount();
        if (movingMode) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (position > 0) moveSelectedTile(position, position - 1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (position < count - 1) moveSelectedTile(position, position + 1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (position >= COLUMNS) moveSelectedTile(position, position - COLUMNS);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (position + COLUMNS < count) moveSelectedTile(position, position + COLUMNS);
                else if (position < count - 1) moveSelectedTile(position, count - 1);
                return true;
            }
            return false;
        }

        if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        if (!inAppsDrawer) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                int cardIdx = (position <= 1) ? 0 : (position == 2 ? 1 : 2);
                focusMediaCard(cardIdx);
                return true;
            } else {
                if (btnScrollDownApps != null && btnScrollDownApps.getVisibility() == View.VISIBLE) {
                    btnScrollDownApps.requestFocus();
                } else {
                    openAppsDrawer(position);
                }
                return true;
            }
        } else {
            int target;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (position < COLUMNS) {
                    if (btnScrollUpHome != null && btnScrollUpHome.getVisibility() == View.VISIBLE) {
                        btnScrollUpHome.requestFocus();
                    } else {
                        closeAppsDrawer(position);
                    }
                    return true;
                }
                target = position - COLUMNS;
            } else {
                int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
                if (position >= lastRowStart) return true;
                target = Math.min(position + COLUMNS, count - 1);
            }
            focusTile(target);
            return true;
        }
    }

    private void focusTile(int position) {
        if (adapter.getItemCount()==0) return;
        int at=Math.max(0,Math.min(position,adapter.getItemCount()-1)),generation=++appFocusGeneration;
        RecyclerView.ViewHolder holder=grid.findViewHolderForAdapterPosition(at);
        if (holder!=null&&TvSmoothScroller.fullyVisible(grid,holder.itemView)) {
            View card=holder.itemView.findViewById(R.id.card);
            if (card.isFocused()) onTileFocused(at,card); else card.requestFocus();
            return;
        }
        grid.post(() -> { if (generation==appFocusGeneration) TvSmoothScroller.to(grid,at,false,() -> {
            if (generation!=appFocusGeneration) return;
            RecyclerView.ViewHolder target=grid.findViewHolderForAdapterPosition(at);
            if (target!=null) target.itemView.findViewById(R.id.card).requestFocus();
        }); });
    }

    // ---- top bar & dropdowns ---------------------------------------------------------------------

    private void updateGreeting() {
        if (movingMode) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = WifiRecentNetworks.clean(info != null ? info.getSSID() : null);
                if (!ssid.isEmpty()) {
                    text = ssid;
                    if (info.getSupplicantState()==android.net.wifi.SupplicantState.COMPLETED) rememberWifi(ssid);
                }
            }
        } catch (Exception ignored) {
        }
        homeControls.setLabel(btnWifi,text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            showGlassNotice(R.drawable.ic_settings, getString(R.string.not_available), null, false);
        }
    }

    /** Glass-styled notification alert matching the dropdown menu design. */
    public void showGlassNotice(final int iconRes, final String title, final String statusText, final boolean showGreenDot) {
        ThemedNotice.show(this,iconRes,title,statusText,showGreenDot,false);
    }

    private String currentWifi() {
        try {
            ConnectivityManager cm=getSystemService(ConnectivityManager.class);
            Network active=cm==null?null:cm.getActiveNetwork();
            NetworkCapabilities caps=active==null?null:cm.getNetworkCapabilities(active);
            if (caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "";
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo info=wm==null?null:wm.getConnectionInfo();
            return info!=null&&info.getSupplicantState()==android.net.wifi.SupplicantState.COMPLETED?WifiRecentNetworks.clean(info.getSSID()):"";
        } catch (Exception ignored) { return ""; }
    }
    private void rememberWifi(String ssid) {
        android.content.SharedPreferences prefs=getSharedPreferences("wifi_recent_v1",MODE_PRIVATE);
        String before=prefs.getString("networks","[]"),after=WifiRecentNetworks.connected(before,ssid);
        if (!before.equals(after)) prefs.edit().putString("networks",after).apply();
    }
    private String dnsLabel() {
        String mode=Settings.Global.getString(getContentResolver(),"private_dns_mode"),host=Settings.Global.getString(getContentResolver(),"private_dns_specifier");
        if ("hostname".equals(mode)) {
            if ("one.one.one.one".equalsIgnoreCase(host)) return "Cloudflare · 1.1.1.1";
            if ("dns.google".equalsIgnoreCase(host)) return "Google · 8.8.8.8";
            if ("dns.adguard-dns.com".equalsIgnoreCase(host)) return "AdGuard";
            return host==null||host.isEmpty()?"DNS personalizado":host;
        }
        return "off".equals(mode)?"DNS privado desativado":"Padrão do roteador";
    }
    private void stepDns(int direction,View row) {
        String mode=Settings.Global.getString(getContentResolver(),"private_dns_mode"),host=Settings.Global.getString(getContentResolver(),"private_dns_specifier");
        String[] hosts={"","one.one.one.one","dns.google","dns.adguard-dns.com"};
        int current=0;
        if ("hostname".equals(mode)) for (int i=1;i<hosts.length;i++) if (hosts[i].equalsIgnoreCase(host)) current=i;
        int next=(current+direction+hosts.length)%hosts.length;
        setPrivateDns(next==0?"opportunistic":"hostname",hosts[next],next==0?"DNS do roteador":hosts[next]);
        updateRowInPlace(row,"DNS", "‹  "+dnsLabel()+"  ›",false);
        row.setContentDescription("DNS atual: "+dnsLabel()+". Use esquerda ou direita para alterar.");
    }
    /** Current DNS and the last five successful Wi-Fi connections. */
    private void showWifiDnsMenu() {
        View panel=getLayoutInflater().inflate(R.layout.popup_inputs,root,false);
        LinearLayout rows=panel.findViewById(R.id.rows);
        int width=Math.round(330*density);
        PopupWindow popup=new PopupWindow(panel,width,ViewGroup.LayoutParams.WRAP_CONTENT,true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); popup.setOutsideTouchable(true); popup.setAnimationStyle(0); popup.setElevation(12f);
        final View[] dns={null};
        dns[0]=buildInteractiveRow(rows,R.drawable.ic_wifi,"DNS","‹  "+dnsLabel()+"  ›",false,() -> stepDns(1,dns[0]),() -> stepDns(-1,dns[0]),() -> stepDns(1,dns[0]));
        dns[0].setContentDescription("DNS atual: "+dnsLabel()+". Use esquerda ou direita para alterar."); rows.addView(dns[0]);
        addSeparator(rows);
        String active=currentWifi(); if (!active.isEmpty()) rememberWifi(active);
        List<String> recent=WifiRecentNetworks.read(getSharedPreferences("wifi_recent_v1",MODE_PRIVATE).getString("networks","[]"));
        for (String ssid:recent) addCustomRow(rows,popup,R.drawable.ic_wifi,ssid,ssid.equals(active)?"Conectada":null,ssid.equals(active),() -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        if (recent.isEmpty()) { TextView empty=new TextView(this); empty.setText("Nenhuma conexão Wi-Fi registrada"); empty.setTextColor(0xffdddddf); empty.setTextSize(13); empty.setPadding(Math.round(12*density),Math.round(10*density),Math.round(12*density),Math.round(10*density)); rows.addView(empty); }
        addSeparator(rows);
        addCustomRow(rows,popup,R.drawable.ic_settings,"Configurações de Wi-Fi",null,false,() -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        showDropdownPanel(popup,panel,btnWifi,width);
    }

    private void setPrivateDns(String mode, String hostname, String label) {
        try {
            Settings.Global.putString(getContentResolver(), "private_dns_mode", mode);
            if (!hostname.isEmpty()) {
                Settings.Global.putString(getContentResolver(), "private_dns_specifier", hostname);
            }
            showGlassNotice(R.drawable.ic_wifi, label, "Ativado", true);
        } catch (Exception e) {
            showGlassNotice(R.drawable.ic_wifi, "Permissão DNS pendente", "Abrindo Rede", false);
            open(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }

    /** Custom Settings Dropdown (Night Volume, 1-Click Bluetooth Audio, Auto-Kill, RAM Cleaner, Native Settings). */
    private void showSettingsDropdown() {
        inNightVolumeSubmenu = false;
        View anchor = btnSettings;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        final LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (320 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(12f);

        popup.setOnDismissListener(() -> {
            if (inNightVolumeSubmenu) {
                inNightVolumeSubmenu = false;
                handler.post(this::showSettingsDropdown);
            } else {
                refreshSettingsCacheAsync();
            }
        });

        populateSettingsRows(rows, popup, 0);
        showDropdownPanel(popup, panel, anchor, width);
    }

    private static final class CachedBtItem {
        final BluetoothDevice device;
        final String name;
        final boolean connected;
        CachedBtItem(BluetoothDevice device, String name, boolean connected) {
            this.device = device;
            this.name = name;
            this.connected = connected;
        }
    }

    private volatile List<CachedBtItem> cachedBtItems = Collections.emptyList();
    private volatile long cachedFreeRamMb = 750;

    private void refreshSettingsCacheAsync() {
        new Thread(() -> {
            try {
                long free = getFreeRamMb();
                List<CachedBtItem> list = new ArrayList<>();
                BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
                if (bta != null && bta.isEnabled()) {
                    Set<BluetoothDevice> bonded = bta.getBondedDevices();
                    if (bonded != null) {
                        for (BluetoothDevice dev : bonded) {
                            String name = dev.getName();
                            if (name == null || name.isEmpty()) continue;
                            if (dev.getBluetoothClass()!=null&&dev.getBluetoothClass().getMajorDeviceClass()==BluetoothClass.Device.Major.PERIPHERAL) continue;
                            BluetoothClass bc = dev.getBluetoothClass();
                            boolean isAudio = bc == null
                                    || bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO
                                    || bc.hasService(BluetoothClass.Service.RENDER)
                                    || bc.hasService(BluetoothClass.Service.AUDIO);
                            if (!isAudio) continue;
                            list.add(new CachedBtItem(dev, name, isBtDeviceConnected(dev)));
                        }
                    }
                }
                cachedFreeRamMb = free;
                cachedBtItems = list;
            } catch (Exception ignored) {
            }
        }).start();
    }

    private int bluetoothAudioIcon(BluetoothDevice device) {
        BluetoothClass kind=device.getBluetoothClass();
        int type=kind==null?0:kind.getDeviceClass();
        return type==BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER||type==BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO
                ?R.drawable.ic_speaker:R.drawable.ic_headphones;
    }

    private void populateSettingsRows(final LinearLayout rows, final PopupWindow popup, int focusIdx) {
        inNightVolumeSubmenu = false;
        rows.removeAllViews();

        // 1. Night Volume Leveler (opens submenu in-place inside the same dropdown)
        int nvMode = getNightVolumeMode();
        View rNv = buildInteractiveRow(rows, R.drawable.ic_livetv, "Volume Noturno",
                getNightVolumeModeLabel() + " ▸", nvMode > 0,
                () -> populateNightVolumeRows(rows, popup, 0), null, null);
        rows.addView(rNv);

        // 2. Auto-Kill toggle (toggles in-place + shows glass notice without blinking)
        boolean ak = isAutoKillEnabled();
        final View[] rAkHolder = new View[1];
        rAkHolder[0] = buildInteractiveRow(rows, R.drawable.ic_input, "Auto-Kill em 2º Plano",
                ak ? "Ativado" : "Desativado", ak, () -> {
                    toggleAutoKill();
                    boolean nowAk = isAutoKillEnabled();
                    updateRowInPlace(rAkHolder[0], "Auto-Kill em 2º Plano", nowAk ? "Ativado" : "Desativado", nowAk);
                }, null, null);
        rows.addView(rAkHolder[0]);

        // 3. Clean RAM Now (uses pre-cached RAM so menu opens in 0ms)
        addCustomRow(rows, popup, R.drawable.ic_av, "Limpar Memória RAM",
                cachedFreeRamMb + " MB livres", false, () -> {
                    cleanRamNow();
                    refreshSettingsCacheAsync();
                });

        // 4. Paired Bluetooth Audio Devices (uses pre-cached list so menu opens in 0ms)
        addSeparator(rows);
        List<CachedBtItem> btList = cachedBtItems;
        boolean addedBtDevice = !btList.isEmpty();
        for (final CachedBtItem item : btList) {
            addCustomRow(rows, popup, bluetoothAudioIcon(item.device), item.name,
                    item.connected ? "Conectado" : "Conectar", item.connected,
                    () -> toggleBluetoothAudioDevice(item.device));
        }
        addCustomRow(rows, popup, R.drawable.ic_headphones,
                addedBtDevice ? "Parear Outro Fone Bluetooth" : "Conectar Fone Bluetooth",
                null, false, () -> {
                    inNightVolumeSubmenu = false;
                    openBluetoothSettings();
                });

        // 5. Native Android TV Settings
        addSeparator(rows);
        addCustomRow(rows, popup, R.drawable.ic_settings, "Menu Nativo da TV",
                "Segure 2s", false, () -> {
                    inNightVolumeSubmenu = false;
                    open(new Intent(Settings.ACTION_SETTINGS));
                });

        if (focusIdx >= 0 && focusIdx < rows.getChildCount()) {
            rows.getChildAt(focusIdx).requestFocus();
        }
    }

    private void populateNightVolumeRows(final LinearLayout rows, final PopupWindow popup, int focusIdx) {
        inNightVolumeSubmenu = true;
        rows.removeAllViews();

        final View[] rowRefs = new View[4]; // 0 = rManual, 1 = rAuto, 2 = rStart, 3 = rEnd
        final Runnable refreshInPlace = () -> {
            int mode = getNightVolumeMode();
            String startFmt = formatTimeMins(getNightStartMins());
            String endFmt = formatTimeMins(getNightEndMins());

            boolean manualActive = (mode == 1);
            updateRowInPlace(rowRefs[0],
                    manualActive ? "Desativar Manualmente" : "Ativar Manualmente",
                    manualActive ? "Ligado" : "Desligado",
                    manualActive);

            boolean autoActive = (mode == 2);
            String autoRange = startFmt + " às " + endFmt;
            updateRowInPlace(rowRefs[1],
                    "Automático por Horário",
                    autoActive ? "Ativo (" + autoRange + ")" : autoRange,
                    autoActive);

            updateRowInPlace(rowRefs[2],
                    "Hora de Início (Ligar)",
                    "◀ " + startFmt + " ▶",
                    false);

            updateRowInPlace(rowRefs[3],
                    "Hora de Fim (Desligar)",
                    "◀ " + endFmt + " ▶",
                    false);
        };

        // Row 0: Single Manual Toggle Option (updates in-place without blinking)
        rowRefs[0] = buildInteractiveRow(rows, R.drawable.ic_livetv, "", "", false, () -> {
            setNightVolumeMode(getNightVolumeMode() == 1 ? 0 : 1);
            refreshInPlace.run();
        }, null, null);
        rows.addView(rowRefs[0]);

        // Row 1: Automatic by Schedule Toggle (updates in-place without blinking)
        rowRefs[1] = buildInteractiveRow(rows, R.drawable.ic_livetv, "", "", false, () -> {
            setNightVolumeMode(getNightVolumeMode() == 2 ? 0 : 2);
            refreshInPlace.run();
        }, null, null);
        rows.addView(rowRefs[1]);

        addSeparator(rows); // Child index 2

        // Row 3: Start Time in 30-min steps (updates in-place without blinking)
        rowRefs[2] = buildInteractiveRow(rows, R.drawable.ic_settings, "", "", false,
                () -> {
                    stepNightStartMins(30);
                    refreshInPlace.run();
                },
                () -> {
                    stepNightStartMins(-30);
                    refreshInPlace.run();
                },
                () -> {
                    stepNightStartMins(30);
                    refreshInPlace.run();
                });
        rows.addView(rowRefs[2]);

        // Row 4: End Time in 30-min steps (updates in-place without blinking)
        rowRefs[3] = buildInteractiveRow(rows, R.drawable.ic_settings, "", "", false,
                () -> {
                    stepNightEndMins(30);
                    refreshInPlace.run();
                },
                () -> {
                    stepNightEndMins(-30);
                    refreshInPlace.run();
                },
                () -> {
                    stepNightEndMins(30);
                    refreshInPlace.run();
                });
        rows.addView(rowRefs[3]);

        addSeparator(rows); // Child index 5

        // Row 6: Back to Settings (iconRes = 0 -> no cable icon, only arrow + text)
        View rBack = buildInteractiveRow(rows, 0, "← Voltar aos Ajustes",
                null, false, () -> populateSettingsRows(rows, popup, 0), null, null);
        rows.addView(rBack);

        refreshInPlace.run();

        if (focusIdx >= 0 && focusIdx < rows.getChildCount()) {
            rows.getChildAt(focusIdx).requestFocus();
        }
    }

    private static void updateRowInPlace(View row, String title, String statusText, boolean showGreenDot) {
        if (row == null) return;
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (statusText != null && !statusText.isEmpty()) {
            status.setText(statusText);
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
        dot.setVisibility(showGreenDot ? View.VISIBLE : View.GONE);
    }

    private View buildInteractiveRow(LinearLayout rows, int iconRes, String title, String statusText,
                                     boolean showGreenDot, Runnable onClick, Runnable onLeft, Runnable onRight) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ImageView icon = row.findViewById(R.id.icon);
        TextView titleView = row.findViewById(R.id.title);
        if (iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) titleView.getLayoutParams();
            lp.setMarginStart(0);
            titleView.setLayoutParams(lp);
        }
        titleView.setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (statusText != null && !statusText.isEmpty()) {
            status.setText(statusText);
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
        dot.setVisibility(showGreenDot ? View.VISIBLE : View.GONE);
        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
        }
        if (onLeft != null || onRight != null) {
            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && onLeft != null) {
                    onLeft.run();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && onRight != null) {
                    onRight.run();
                    return true;
                }
                return false;
            });
        }
        return row;
    }

    private boolean isBtDeviceConnected(BluetoothDevice dev) {
        try {
            Method m = dev.getClass().getMethod("isConnected");
            Object res = m.invoke(dev);
            return res instanceof Boolean && (Boolean) res;
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleBluetoothAudioDevice(final BluetoothDevice device) {
        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
        if (bta == null) return;
        final boolean currentlyConnected = isBtDeviceConnected(device);
        showGlassNotice(R.drawable.ic_livetv, "🎧 " + device.getName(),
                currentlyConnected ? "Desconectando..." : "Conectando...", !currentlyConnected);
        bta.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                try {
                    Method m = proxy.getClass().getMethod(currentlyConnected ? "disconnect" : "connect", BluetoothDevice.class);
                    m.invoke(proxy, device);
                } catch (Exception e) {
                    openBluetoothSettings();
                } finally {
                    bta.closeProfileProxy(profile, proxy);
                    refreshSettingsCacheAsync();
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.A2DP);
    }

    private void openBluetoothSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            open(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean hasActiveHdmiSource(String inputId) {
        if (tvInputManager == null) return false;
        TvInputInfo selected = tvInputManager.getTvInputInfo(inputId);
        if (selected == null || selected.getType() != TvInputInfo.TYPE_HDMI || !selected.isPassthroughInput()) return false;
        int ports = 0, connected = 0;
        for (TvInputInfo input : tvInputManager.getTvInputList()) {
            if (input.getType() != TvInputInfo.TYPE_HDMI || !input.isPassthroughInput()) continue;
            ports++;
            if (tvInputManager.getInputState(input.getId()) == TvInputManager.INPUT_STATE_CONNECTED) connected++;
        }
        // shortcut: some firmware reports every port connected; suppress prompts until it reports a reliable state.
        boolean active = HdmiConnectionPolicy.hasActiveSource(tvInputManager.getInputState(inputId), connected, ports);
        android.util.Log.d("TVHomeHdmi", "CHECK input=" + inputId + " active=" + active + " connected=" + connected + "/" + ports);
        return active;
    }

    private void dismissHdmiPrompt() {
        if (hdmiPrompt != null) hdmiPrompt.dismiss();
    }

    /** Ask only after a stable HDMI connection; the default action never changes input. */
    private void showHdmiConnectedBanner(final String inputId, final String label) {
        if (isFinishing() || !hasWindowFocus() || !hasActiveHdmiSource(inputId)) return;
        dismissHdmiPrompt();
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (380 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(16f);

        TextView question = new TextView(this);
        question.setText(getString(R.string.hdmi_switch_question, label));
        question.setTextColor(Color.WHITE);
        question.setTextSize(16);
        question.setTypeface(getResources().getFont(R.font.outfit));
        int inset = (int) (12 * density);
        question.setPadding(inset, inset, inset, inset);
        ((LinearLayout) panel).addView(question, 0);
        addCustomRow(rows, popup, R.drawable.ic_close_x, getString(R.string.hdmi_not_now), null, false,
                () -> android.util.Log.d("TVHomeHdmi", "CANCEL input=" + inputId));
        rows.getChildAt(0).setFocusableInTouchMode(true);
        addCustomRow(rows, popup, R.drawable.ic_input, getString(R.string.hdmi_switch, label), null, true, () -> {
            if (hasActiveHdmiSource(inputId)) {
                android.util.Log.d("TVHomeHdmi", "CONFIRMED_SWITCH input=" + inputId);
                open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                        TvContract.buildChannelUriForPassthroughInput(inputId), "vnd.android.cursor.item/channel"));
            }
        });
        View previousFocus = getCurrentFocus();
        popup.setOnDismissListener(() -> {
            if (hdmiPrompt == popup) {
                hdmiPrompt = null;
                hdmiPromptInputId = null;
            }
            if (previousFocus != null && previousFocus.isShown()) previousFocus.requestFocus();
        });
        hdmiPrompt = popup;
        hdmiPromptInputId = inputId;
        showDropdownPanel(popup, panel, findViewById(R.id.btnInputs), width);
        android.util.Log.d("TVHomeHdmi", "PROMPT input=" + inputId);
    }

    private void showDropdownPanel(PopupWindow popup, View panel, View anchor, int minWidth) {
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(minWidth, panel.getMeasuredWidth()));

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.96f);
        panel.setTranslationY(-4 * density);
        if (anchor != null && anchor.isShown()) {
            popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.END);
        } else {
            popup.showAtLocation(root, Gravity.TOP | Gravity.END, (int) (48 * density), (int) (26 * density));
        }
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(90)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
        LinearLayout rows = panel.findViewById(R.id.rows);
        if (rows != null && rows.getChildCount() > 0) {
            rows.getChildAt(0).requestFocus();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            showGlassNotice(R.drawable.ic_input, getString(R.string.no_inputs), null, false);
            return;
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addCustomRow(LinearLayout rows, PopupWindow popup, int iconRes, String title,
                              String statusText, boolean showGreenDot, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (statusText != null && !statusText.isEmpty()) {
            status.setText(statusText);
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
        dot.setVisibility(showGreenDot ? View.VISIBLE : View.GONE);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
