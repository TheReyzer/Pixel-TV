package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.*;

/** Shared catalog search; provider selection and playback keep the existing Assistir flow. */
final class TitleSearchController {
    private static final int VOICE_REQUEST=7041;
    private final Activity activity;
    private final ProfileController ui;
    private final MediaRepository repository;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> search;
    private int generation;
    private boolean searchOpen,permissionPending,listening,pairingOpen;
    private SpeechRecognizer recognizer;
    private VoiceCaptureWindow voiceWindow;
    private int voiceGeneration;
    private LinearLayout voiceAlternatives;
    private android.widget.FrameLayout toolbar;
    private View normalActions,spacer;
    private SearchCapsuleView toolbarChrome;
    private android.animation.ValueAnimator toolbarAnimation;
    private java.util.function.Consumer<Runnable> feedbackLayout=Runnable::run;
    private int feedbackHeight=-1,feedbackAnimationGeneration;
    void onFeedbackLayout(java.util.function.Consumer<Runnable> action) { feedbackLayout=action; }
    private int toolbarAnimationGeneration;
    private java.util.function.Consumer<Boolean> toolbarMotion=active -> {};
    void onMotion(java.util.function.Consumer<Boolean> action) { toolbarMotion=action; }
    private LinearLayout editor,feedback,statusRow;
    private TextView language,format;
    private ImageView submit;
    private boolean bindingInput,switching,preparing;
    boolean isSwitching() { return switching; }
    View firstControl() { return language; }
    void prepare() { mount(); }
    void prepareFirstUse(Runnable ready) {
        preparing=true; open("",null,false,false,null);
        afterDraw(editor,() -> afterMotion(() -> {
            close(); afterDraw(normalActions,() -> afterMotion(() -> { preparing=false; ready.run(); }));
        }));
    }
    private void afterMotion(Runnable action) {
        if (toolbarAnimation==null) action.run();
        else handler.postDelayed(() -> afterMotion(action),32);
    }
    private void afterDraw(View view,Runnable action) {
        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this); view.postOnAnimation(action); return true;
            }
        });
    }
    private Runnable editorOpened,focusResults;
    private final java.util.ArrayList<String> spokenTitles=new java.util.ArrayList<>();
    void onEditorOpened(Runnable action) { editorOpened=action; }
    void onFocusResults(Runnable action) { focusResults=action; }
    private boolean suggestionsHidden;
    private java.util.ArrayList<String> partialTitles=new java.util.ArrayList<>();
    private int voiceLanguage;
    private String searchType="all";
    private android.animation.ObjectAnimator micPulse;
    private EditText voiceInput;
    private TextView voiceStatus;
    private View voiceMic;
    private TextView pairButton;
    private Runnable voiceSearch,cancelled;
    private Runnable editorClosed;
    void finishRating() { cancelled=null; ratingMode=false; }
    void onEditorClosed(Runnable action) { editorClosed=action; }
    private boolean ratingMode;
    private final TitleSearchResults state=new TitleSearchResults();
    private java.util.function.Consumer<Boolean> resultsChanged;
    void onResults(java.util.function.Consumer<Boolean> changed) { resultsChanged=changed; }
    List<MediaRepository.MediaItem> results() { return new java.util.ArrayList<>(state.items); }
    String query() { return state.query; }
    boolean hasMore() { return state.more; }
    boolean loading() { return state.loading; }
    boolean failed() { return state.failed; }
    void loadMore() { loadMore(false); }
    void nearEnd() { loadMore(true); }
    private void loadMore(boolean automatically) {
        if (state.loading||!state.more||automatically&&state.failed) return;
        state.loading=true; state.failed=false; int token=generation; String query=state.query,type=state.type,cursor=state.cursor;
        if (resultsChanged!=null) resultsChanged.accept(false);
        search=worker.submit(() -> {
            try {
                MediaRepository.SearchPage page=repository.searchPage(query,type,cursor);
                handler.post(() -> { if (token==generation) { state.append(page); if (resultsChanged!=null) resultsChanged.accept(false); } });
            } catch (Exception error) {
                handler.post(() -> { if (token==generation) { state.loading=false; state.failed=true; if (resultsChanged!=null) resultsChanged.accept(false); } });
            }
        });
    }
    private java.util.function.Consumer<String> explore;
    void onExplore(java.util.function.Consumer<String> explore) { this.explore=explore; }
    void ratingDetails(MediaRepository.MediaItem item,java.util.function.Consumer<MediaRepository.MediaItem> ready) {
        if (!item.genresPt.trim().isEmpty()) { ready.accept(item); return; }
        worker.submit(() -> {
            MediaRepository.MediaItem detail=item;
            try { detail=repository.ratingDetails(item); } catch (Exception error) { android.util.Log.d("TVHomeSearch","Extra rating metadata unavailable"); }
            final MediaRepository.MediaItem resolved=detail; handler.post(() -> ready.accept(resolved));
        });
    }

    TitleSearchController(Activity activity,ProfileController ui,MediaRepository repository) { this.activity=activity; this.ui=ui; this.repository=repository; }
    private void cancelSearch() { state.loading=false; generation++; if (search!=null) search.cancel(true); search=null; }
    void close() {
        if (!searchOpen) return;
        switching=true; suspend(); searchOpen=false; cancelled=null;
        int previous=toolbarVisibleWidth(); activity.findViewById(R.id.btnSearchMedia).setActivated(false);
        editor.setVisibility(View.INVISIBLE); normalActions.setVisibility(View.VISIBLE);
        refreshFeedback(); animateToolbar(previous,normalActions); switching=false;
    }
    void suspend() {
        boolean interrupted=state.loading||listening;
        cancelSearch(); stopVoice(); hideKeyboard();
        if (interrupted&&voiceStatus!=null) voiceStatus.setText("Busca interrompida. Pressione a lupa ou o microfone para tentar novamente.");
    }
    void pause() { if (searchOpen) suspend(); }
    void destroy() { close(); toolbarAnimationGeneration++; if (toolbarAnimation!=null) toolbarAnimation.cancel(); toolbarMotion.accept(false); handler.removeCallbacksAndMessages(null); worker.shutdownNow(); }
    boolean back() {
        if (!searchOpen) return false;
        if (listening) { stopVoice(); voiceStatus.setText(R.string.title_voice_stopped); return true; }
        Runnable action=cancelled; close();
        if (action!=null) action.run();
        if (editorClosed!=null) editorClosed.run();
        return true;
    }
    boolean isOpen() { return searchOpen; }
    boolean isKeepingSearch() { return permissionPending||pairingOpen; }
    void resumed() {
        if (pairingOpen) { pairingOpen=false; if (searchOpen) { voiceStatus.setText(R.string.title_voice_retry); voiceMic.requestFocus(); } }
    }
    private void openPairing() {
        stopVoice();
        Intent pairing=new Intent().setClassName("com.android.tv.settings","com.android.tv.settings.accessories.AddAccessoryActivity");
        if (pairing.resolveActivity(activity.getPackageManager())==null) pairing=new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        if (pairing.resolveActivity(activity.getPackageManager())==null) pairing=new Intent(android.provider.Settings.ACTION_SETTINGS);
        pairingOpen=true;
        try { activity.startActivity(pairing); }
        catch (RuntimeException e) { pairingOpen=false; voiceStatus.setText(R.string.title_voice_pair_manually); }
    }
    static boolean needsPairingHelp(int error) { return error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT||error==SpeechRecognizer.ERROR_AUDIO; }
    void permissionResult(int request,int[] grants) {
        if (request!=VOICE_REQUEST) return;
        permissionPending=false;
        if (!searchOpen) return;
        if (grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) handler.post(this::startVoice);
        else voiceStatus.setText(R.string.title_voice_permission);
    }
    private void stopVoice() {
        releaseVoice(true);
    }
    private void releaseVoice(boolean cancel) {
        listening=false;
        if (micPulse!=null) { micPulse.cancel(); micPulse=null; }
        if (voiceMic!=null) { voiceMic.setAlpha(1f); voiceMic.setSelected(false); voiceMic.setContentDescription("Pesquisar por voz em português ou inglês"); }
        releaseRecognizer(cancel);
    }
    private void releaseRecognizer(boolean cancel) {
        voiceGeneration++;
        if (recognizer!=null) { if (cancel) recognizer.cancel(); recognizer.destroy(); recognizer=null; }
    }
    private void startVoice() {
        if (!searchOpen) return;
        if (listening) { stopVoice(); voiceStatus.setText(R.string.title_voice_stopped); return; }
        if (activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionPending=true;
            activity.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},VOICE_REQUEST);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) { voiceStatus.setText(R.string.title_voice_unavailable); return; }
        cancelSearch();
        android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (ime!=null) ime.hideSoftInputFromWindow(voiceInput.getWindowToken(),0);
        pairButton.setVisibility(View.GONE); ((View)voiceAlternatives.getParent()).setVisibility(View.GONE);
        partialTitles.clear();
        listening=true; voiceWindow=new VoiceCaptureWindow(android.os.SystemClock.elapsedRealtime());
        voiceMic.setSelected(true); voiceMic.setContentDescription("Parar pesquisa por voz"); voiceStatus.setText(R.string.title_voice_preparing);
        micPulse=android.animation.ObjectAnimator.ofFloat(voiceMic,View.ALPHA,1f,.45f); micPulse.setDuration(650); micPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE); micPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE); micPulse.start();
        listenForTitle();
    }
    private void listenForTitle() {
        if (!searchOpen||!listening) return;
        int voiceToken=++voiceGeneration;
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,10)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,12_000)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1800)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1200)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,activity.getPackageName());
        // shortcut: Android 9 cannot deliver the segmented results used by the provider's newer language switching.
        if (voiceLanguage!=0) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,voiceLanguage==1?"en-US":"pt-BR");
        else if (android.os.Build.VERSION.SDK_INT>=34) {
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,new java.util.ArrayList<>(java.util.Arrays.asList("pt-BR","en-US")));
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,new java.util.ArrayList<>(java.util.Arrays.asList("pt-BR","en-US")));
        }
        try {
            recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { if (listening&&voiceToken==voiceGeneration) voiceStatus.setText(R.string.title_voice_listening); }
                @Override public void onBeginningOfSpeech() { if (listening&&voiceToken==voiceGeneration) { voiceWindow.speechStarted(); voiceStatus.setText(R.string.title_voice_listening); } }
                @Override public void onRmsChanged(float rms) {}
                @Override public void onBufferReceived(byte[] data) {}
                @Override public void onEndOfSpeech() { if (listening&&voiceToken==voiceGeneration) voiceStatus.setText(R.string.title_voice_processing); }
                @Override public void onError(int error) {
                    if (!listening||!searchOpen||voiceToken!=voiceGeneration) return;
                    if ((error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)&&!partialTitles.isEmpty()) { onResults(new Bundle()); return; }
                    if ((error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)&&voiceWindow.retrySilence(android.os.SystemClock.elapsedRealtime())) {
                        releaseRecognizer(false); int retryToken=voiceGeneration;
                        handler.postDelayed(() -> { if (listening&&searchOpen&&retryToken==voiceGeneration) listenForTitle(); },200);
                        return;
                    }
                    android.util.Log.w("TVHomeVoice","Recognition error="+error);
                    releaseVoice(false);
                    if (needsPairingHelp(error)) { voiceStatus.setText(R.string.title_voice_pair_hint); pairButton.setVisibility(View.VISIBLE); refreshFeedback(); pairButton.requestFocus(); }
                    else voiceStatus.setText(error==SpeechRecognizer.ERROR_NETWORK||error==SpeechRecognizer.ERROR_NETWORK_TIMEOUT?R.string.title_voice_network:R.string.title_voice_empty);
                }
                @Override public void onResults(Bundle data) {
                    if (!listening||!searchOpen||voiceToken!=voiceGeneration) return;
                    java.util.ArrayList<String> alternatives=VoiceTitleQuery.results(data==null?null:data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),partialTitles);
                    releaseVoice(false);
                    if (alternatives.isEmpty()) { voiceStatus.setText(R.string.title_voice_pair_hint); pairButton.setVisibility(View.VISIBLE); refreshFeedback(); }
                    else {
                        spokenTitles.clear(); spokenTitles.addAll(alternatives);
                        setQuery(alternatives.get(0)); voiceSearch.run();
                    }
                }
                @Override public void onPartialResults(Bundle data) {
                    if (!listening||!searchOpen||voiceToken!=voiceGeneration) return;
                    java.util.ArrayList<String> titles=VoiceTitleQuery.alternatives(data==null?null:data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                    String title=VoiceTitleQuery.first(titles);
                    if (!titles.isEmpty()) partialTitles=titles;
                    if (!title.isEmpty()) { voiceWindow.speechStarted(); setQuery(title); }
                }
                @Override public void onEvent(int type,Bundle params) {}
                @Override public void onSegmentResults(Bundle data) { onResults(data); }
            });
            recognizer.startListening(intent);
        } catch (RuntimeException e) { stopVoice(); voiceStatus.setText(R.string.title_voice_unavailable); android.util.Log.w("TVHomeVoice","Recognition unavailable",e); }
    }
    private void refreshSuggestions() { refreshFeedback(); }
    private void refreshFeedback() {
        if (feedback==null) return;
        boolean status=voiceStatus.length()>0;
        boolean pills=!status&&!suggestionsHidden&&voiceAlternatives.getChildCount()>0;
        boolean visible=searchOpen&&(status||pills);
        int available=Math.max(ui.dp(160),editor.getWidth()+toolbar.getPaddingLeft()+toolbar.getPaddingRight());
        if (visible&&status) statusRow.measure(View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int height=visible?(status?statusRow.getMeasuredHeight():ui.dp(28)):0;
        Runnable apply=() -> {
            boolean entering=visible&&(feedback.getVisibility()!=View.VISIBLE||height!=feedbackHeight);
            feedbackHeight=height; statusRow.setVisibility(status?View.VISIBLE:View.GONE);
            ((View)voiceAlternatives.getParent()).setVisibility(pills?View.VISIBLE:View.GONE);
            ViewGroup.LayoutParams size=feedback.getLayoutParams(); if (size.height!=height) { size.height=height; feedback.setLayoutParams(size); }
            feedback.setVisibility(visible?View.VISIBLE:View.GONE);
            if (entering&&!preparing) {
                int token=++feedbackAnimationGeneration; feedback.animate().cancel(); feedback.setAlpha(0); feedback.setTranslationY(-ui.dp(6));
                feedback.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                    @Override public boolean onPreDraw() {
                        feedback.getViewTreeObserver().removeOnPreDrawListener(this);
                        feedback.postOnAnimation(() -> { if (token==feedbackAnimationGeneration&&searchOpen&&feedback.isShown()) feedback.animate().alpha(1).translationY(0).setStartDelay(90).setDuration(TvSmoothScroller.animationDuration(180,android.animation.ValueAnimator.getDurationScale())).start(); });
                        return true;
                    }
                });
            } else if (!visible) { feedbackAnimationGeneration++; feedback.animate().cancel(); feedback.setAlpha(1); feedback.setTranslationY(0); }
        };
        if (height!=feedbackHeight) feedbackLayout.accept(apply); else apply.run();
    }
    void onResultFocused() { if (searchOpen) { suggestionsHidden=true; refreshFeedback(); } }
    View navigationAnchor() {
        suggestionsHidden=false; refreshFeedback();
        return voiceStatus.length()==0&&voiceAlternatives.getChildCount()>0?voiceAlternatives.getChildAt(0):voiceInput;
    }
    boolean focusSuggestions() {
        if (!searchOpen||voiceStatus.length()>0||voiceAlternatives.getChildCount()==0) return false;
        navigationAnchor().requestFocus(); return true;
    }
    private void hideKeyboard() {
        if (voiceInput==null) return;
        android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (ime!=null) ime.hideSoftInputFromWindow(voiceInput.getWindowToken(),0);
    }
    private void setQuery(String text) {
        bindingInput=true; voiceInput.setText(text); voiceInput.setSelection(voiceInput.length()); bindingInput=false;
    }
    private int toolbarVisibleWidth() {
        return toolbarChrome.isLaidOut()?Math.round(toolbarChrome.revealedWidth()):toolbar.getWidth();
    }
    private void animateToolbar(int previous,View incoming) {
        final int token=++toolbarAnimationGeneration;
        if (toolbarAnimation!=null) toolbarAnimation.cancel();
        editor.animate().cancel(); normalActions.animate().cancel(); editor.setClipBounds(null); normalActions.setClipBounds(null);
        editor.setAlpha(1); normalActions.setAlpha(1); editor.setTranslationX(0); normalActions.setTranslationX(0);
        if (!toolbar.isLaidOut()||!toolbar.isShown()) { toolbarChrome.reveal(incoming.getWidth()+toolbar.getPaddingLeft()+toolbar.getPaddingRight()); toolbarMotion.accept(false); return; }
        incoming.setAlpha(0);
        toolbar.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                toolbar.getViewTreeObserver().removeOnPreDrawListener(this);
                if (token!=toolbarAnimationGeneration) return true;
                int width=incoming.getWidth()+toolbar.getPaddingLeft()+toolbar.getPaddingRight(); toolbarChrome.reveal(previous); toolbarMotion.accept(true);
                android.graphics.Rect clip=new android.graphics.Rect();
                android.animation.ValueAnimator animation=android.animation.ValueAnimator.ofFloat(previous,width); toolbarAnimation=animation;
                animation.setDuration(TvSmoothScroller.animationDuration(420,android.animation.ValueAnimator.getDurationScale())); animation.setInterpolator(new android.view.animation.PathInterpolator(.4f,0,.2f,1));
                animation.addUpdateListener(value -> {
                    if (token!=toolbarAnimationGeneration) return;
                    float revealed=(Float)value.getAnimatedValue(); toolbarChrome.reveal(revealed);
                    clip.set(Math.max(0,Math.round(toolbar.getWidth()-revealed)-incoming.getLeft()),0,incoming.getWidth(),incoming.getHeight()); incoming.setClipBounds(clip);
                });
                animation.addListener(new android.animation.AnimatorListenerAdapter(){ @Override public void onAnimationEnd(android.animation.Animator ended) {
                    if (token!=toolbarAnimationGeneration) return;
                    toolbarAnimation=null; toolbarChrome.reveal(width); incoming.setClipBounds(null); toolbarMotion.accept(false);
                }});
                toolbar.postOnAnimation(() -> {
                    if (token!=toolbarAnimationGeneration) return;
                    animation.start(); incoming.animate().alpha(1).setStartDelay(45).setDuration(TvSmoothScroller.animationDuration(200,android.animation.ValueAnimator.getDurationScale())).start();
                });
                return true;
            }
        });
    }
    static int editorWidth(int available,int fixedControls) {
        return Math.max(0,available-Math.round(Math.max(0,available-fixedControls)*.1f));
    }
    private TextView textControl(String text,int width,String description) {
        TextView view=ui.text(text,12); view.setId(View.generateViewId()); view.setBackgroundResource(R.drawable.filter_tab_bg); view.setTextColor(activity.getResources().getColorStateList(R.drawable.pill_text,null));
        view.setSingleLine(true); view.setIncludeFontPadding(false); view.setGravity(Gravity.CENTER);
        view.setFocusableInTouchMode(true); view.setClickable(true); view.setContentDescription(description);
        editor.addView(view,new LinearLayout.LayoutParams(ui.dp(width),-1)); return view;
    }
    private ImageView iconControl(LinearLayout parent,int icon,String description) {
        ImageView view=new ImageView(activity); view.setId(View.generateViewId()); view.setImageResource(icon);
        view.setImageTintList(activity.getResources().getColorStateList(R.drawable.pill_text,null));
        view.setBackgroundResource(R.drawable.filter_tab_bg); view.setPadding(ui.dp(9),ui.dp(9),ui.dp(9),ui.dp(9));
        view.setFocusableInTouchMode(true); view.setClickable(true); view.setContentDescription(description);
        parent.addView(view,new LinearLayout.LayoutParams(ui.dp(36),-1)); return view;
    }
    private void mount() {
        if (editor!=null) return;
        toolbar=activity.findViewById(R.id.contentActions); normalActions=activity.findViewById(R.id.contentActionButtons);
        spacer=activity.findViewById(R.id.filterActionsSpacer); feedback=activity.findViewById(R.id.inlineSearchFeedback);
        toolbar.setBackground(null); toolbar.setClipChildren(false); toolbar.setClipToPadding(false);
        toolbarChrome=new SearchCapsuleView(activity); toolbarChrome.setId(R.id.inlineSearchChrome);
        toolbar.addView(toolbarChrome,0,new android.widget.FrameLayout.LayoutParams(0,0));
        toolbar.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int left=0,top=0;
            int right=toolbar.getWidth(),bottom=toolbar.getHeight();
            if (toolbarChrome.getLeft()!=left||toolbarChrome.getTop()!=top||toolbarChrome.getRight()!=right||toolbarChrome.getBottom()!=bottom) toolbarChrome.layout(left,top,right,bottom);
            if (!searchOpen&&toolbarAnimation==null) toolbarChrome.reveal(normalActions.getWidth()+toolbar.getPaddingLeft()+toolbar.getPaddingRight());
        });
        editor=new LinearLayout(activity); editor.setId(R.id.inlineSearchEditor); editor.setGravity(Gravity.CENTER_VERTICAL); editor.setVisibility(View.INVISIBLE);
        android.widget.FrameLayout.LayoutParams editorSize=new android.widget.FrameLayout.LayoutParams(-1,-1,Gravity.END|Gravity.CENTER_VERTICAL); toolbar.addView(editor,editorSize);
        language=textControl("AUTO",48,"Idioma da pesquisa: automático. Clique para alternar"); language.setId(R.id.inlineSearchLanguage);
        format=textControl("Tudo",64,"Tipo de título: tudo. Clique para alternar"); format.setId(R.id.inlineSearchFormat);
        LinearLayout queryGroup=new LinearLayout(activity); queryGroup.setId(R.id.inlineSearchQueryGroup); queryGroup.setGravity(Gravity.CENTER_VERTICAL); queryGroup.setAddStatesFromChildren(true); queryGroup.setBackgroundResource(R.drawable.inline_search_field_bg);
        LinearLayout.LayoutParams querySize=new LinearLayout.LayoutParams(0,-1,1); querySize.setMarginStart(ui.dp(10)); editor.addView(queryGroup,querySize);
        voiceInput=new EditText(activity); voiceInput.setId(R.id.inlineSearchInput); voiceInput.setSingleLine(true);
        voiceInput.setTextColor(Color.WHITE); voiceInput.setHintTextColor(0xffbbbbbb); voiceInput.setTextSize(14);
        voiceInput.setTypeface(activity.getResources().getFont(R.font.outfit)); voiceInput.setBackground(null);
        voiceInput.setPadding(ui.dp(10),0,ui.dp(6),0); voiceInput.setHint("Filme ou série");
        voiceInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN); voiceInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        voiceInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        voiceInput.setContentDescription("Título do filme ou série. Clique para digitar");
        queryGroup.addView(voiceInput,new LinearLayout.LayoutParams(0,-1,1));
        voiceMic=iconControl(queryGroup,R.drawable.ic_mic,"Pesquisar por voz em português ou inglês"); voiceMic.setId(R.id.inlineSearchMic);
        voiceMic.setBackgroundResource(R.drawable.inline_voice_bg); ((ImageView)voiceMic).setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        submit=iconControl(queryGroup,R.drawable.ic_search,"Pesquisar ou explorar catálogo"); submit.setId(R.id.inlineSearchSubmit); submit.setBackgroundResource(R.drawable.inline_search_control_bg); submit.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        ImageView back=iconControl(editor,R.drawable.ic_back,"Voltar"); back.setId(R.id.inlineSearchBack);
        ((LinearLayout.LayoutParams)back.getLayoutParams()).setMarginStart(ui.dp(8));
        statusRow=new LinearLayout(activity); statusRow.setGravity(Gravity.CENTER_VERTICAL); feedback.addView(statusRow,new LinearLayout.LayoutParams(-1,-2));
        voiceStatus=ui.text("",12); voiceStatus.setId(R.id.inlineSearchStatus); voiceStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        voiceStatus.setPadding(ui.dp(8),ui.dp(3),ui.dp(8),ui.dp(3)); statusRow.addView(voiceStatus,new LinearLayout.LayoutParams(0,-2,1));
        pairButton=ui.text("Parear controle",12); ui.styleButton(pairButton); pairButton.setGravity(Gravity.CENTER); pairButton.setFocusableInTouchMode(true); pairButton.setClickable(true); pairButton.setVisibility(View.GONE);
        statusRow.addView(pairButton,new LinearLayout.LayoutParams(-2,ui.dp(30))); pairButton.setOnClickListener(v -> openPairing());
        android.widget.HorizontalScrollView pills=new android.widget.HorizontalScrollView(activity); pills.setId(R.id.inlineSearchSuggestions); pills.setHorizontalScrollBarEnabled(false);
        pills.setContentDescription("Sugestões de títulos. Seta para baixo para ver os resultados");
        voiceAlternatives=new LinearLayout(activity); voiceAlternatives.setGravity(Gravity.CENTER_VERTICAL); pills.addView(voiceAlternatives);
        feedback.addView(pills,new LinearLayout.LayoutParams(-1,ui.dp(28)));
        toolbar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> layoutEditor());
        voiceStatus.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { refreshFeedback(); }
            public void afterTextChanged(android.text.Editable s) {}
        });
        voiceInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if (bindingInput) return;
                cancelSearch(); spokenTitles.clear(); voiceAlternatives.removeAllViews(); pairButton.setVisibility(View.GONE);
                if (!listening&&!android.text.TextUtils.equals(voiceStatus.getText(),draftHint())) voiceStatus.setText(draftHint());
                refreshFeedback();
            }
            public void afterTextChanged(android.text.Editable s) {}
        });
        Runnable keyboard=() -> { voiceInput.requestFocus(); voiceInput.setSelection(voiceInput.length());
            android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (ime!=null) voiceInput.post(() -> ime.showSoftInput(voiceInput,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)); };
        voiceInput.setOnClickListener(v -> keyboard.run());
        voiceInput.setOnKeyListener((v,key,event) -> {
            if (key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) { if (event.getAction()==KeyEvent.ACTION_UP) keyboard.run(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_DOWN&&event.getAction()==KeyEvent.ACTION_DOWN) { if (!focusSuggestions()&&focusResults!=null) focusResults.run(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_RIGHT) { if (event.getAction()==KeyEvent.ACTION_DOWN) voiceMic.requestFocus(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_LEFT) { if (event.getAction()==KeyEvent.ACTION_DOWN) format.requestFocus(); return true; }
            return false;
        });
        language.setOnClickListener(v -> {
            stopVoice(); voiceLanguage=(voiceLanguage+1)%3; language.setText(voiceLanguage==0?"AUTO":voiceLanguage==1?"EN":"PT");
            language.setContentDescription(activity.getString(voiceLanguage==0?R.string.title_voice_language_auto:voiceLanguage==1?R.string.title_voice_language_en:R.string.title_voice_language_pt));
            voiceStatus.setText(draftHint());
        });
        format.setOnClickListener(v -> { stopVoice(); searchType=searchType.equals("all")?"movie":searchType.equals("movie")?"series":"all";
            format.setText(searchType.equals("all")?"Tudo":searchType.equals("movie")?"Filme":"Série"); format.setContentDescription("Tipo de título: "+format.getText()+". Clique para alternar");
            if (voiceInput.length()>=2) runSearch(); else voiceStatus.setText(draftHint());
        });
        voiceSearch=this::runSearch; voiceMic.setOnClickListener(v -> startVoice()); submit.setOnClickListener(v -> runSearch()); back.setOnClickListener(v -> back());
        voiceInput.setOnEditorActionListener((v,action,event) -> { if (action==EditorInfo.IME_ACTION_SEARCH) { runSearch(); return true; } return false; });
        View[] controls={language,format,voiceInput,voiceMic,submit,back};
        for (int i=0;i<controls.length;i++) {
            View child=controls[i]; child.setNextFocusLeftId(i==0?R.id.tabSeries:controls[i-1].getId()); child.setNextFocusRightId(controls[Math.min(controls.length-1,i+1)].getId());
            if (child!=voiceInput) child.setOnKeyListener((v,key,event) -> {
                if (key==KeyEvent.KEYCODE_DPAD_DOWN&&event.getAction()==KeyEvent.ACTION_DOWN) { if (!focusSuggestions()&&focusResults!=null) focusResults.run(); return true; } return false;
            });
        }
    }
    private void layoutEditor() {
        if (toolbar.getWidth()<=0) return;
        int available=toolbar.getWidth()-toolbar.getPaddingLeft()-toolbar.getPaddingRight();
        int width=editorWidth(available,ui.dp(238));
        android.widget.FrameLayout.LayoutParams size=(android.widget.FrameLayout.LayoutParams)editor.getLayoutParams();
        if (size.width!=width) { size.width=width; editor.setLayoutParams(size); }
        int start=toolbar.getLeft()+toolbar.getWidth()-width-toolbar.getPaddingLeft()-toolbar.getPaddingRight();
        if (feedback.getPaddingLeft()!=start) feedback.setPadding(start,0,0,0);
    }
    private String draftHint() {
        if (voiceInput.length()==0) return ratingMode?"Digite ou fale o título que você assistiu.":"Digite ou fale um título. A lupa sem texto explora o catálogo IMDb.";
        return voiceInput.getText().toString().trim().length()<2?activity.getString(R.string.watch_search_minimum):"Pressione a lupa para pesquisar ou use o microfone.";
    }
    private void buildSuggestions(String query) {
        voiceAlternatives.removeAllViews(); suggestionsHidden=false;
        for (String title:SearchSuggestions.titles(query,spokenTitles,state.items)) {
            TextView pill=ui.text(title,11); ui.styleButton(pill); pill.setSingleLine(true); pill.setIncludeFontPadding(false); pill.setPadding(ui.dp(10),0,ui.dp(10),0);
            pill.setGravity(Gravity.CENTER); pill.setFocusableInTouchMode(true); pill.setClickable(true); pill.setContentDescription("Pesquisar: "+title);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,ui.dp(24)); size.setMargins(0,0,ui.dp(6),0); voiceAlternatives.addView(pill,size);
            pill.setOnClickListener(v -> { setQuery(title); runSearch(); });
            pill.setOnKeyListener((v,key,event) -> {
                if (event.getAction()!=KeyEvent.ACTION_DOWN) return false;
                if (key==KeyEvent.KEYCODE_DPAD_UP) { voiceInput.requestFocus(); return true; }
                if (key==KeyEvent.KEYCODE_DPAD_DOWN) { onResultFocused(); if (focusResults!=null) focusResults.run(); return true; }
                return false;
            });
        }
    }
    private void runSearch() {
        if (!searchOpen) return;
        stopVoice(); String query=voiceInput.getText().toString().trim(); hideKeyboard();
        pairButton.setVisibility(View.GONE);
        if (query.isEmpty()&&explore!=null) { cancelSearch(); voiceAlternatives.removeAllViews(); voiceStatus.setText(""); explore.accept(searchType); return; }
        if (query.length()<2) { voiceStatus.setText(R.string.watch_search_minimum); voiceInput.requestFocus(); return; }
        cancelSearch(); int token=generation; String type=searchType; state.loading=true; voiceStatus.setText(R.string.watch_search_loading);
        search=worker.submit(() -> {
            try {
                MediaRepository.SearchPage page=repository.searchPage(query,type,"");
                handler.post(() -> {
                    if (!searchOpen||token!=generation) return;
                    state.reset(query,type); state.append(page); buildSuggestions(query);
                    voiceStatus.setText(state.items.isEmpty()&&voiceAlternatives.getChildCount()==0?"Nenhum título encontrado. Tente outro nome.":"");
                    if (resultsChanged!=null) resultsChanged.accept(true);
                });
            } catch (Exception error) {
                android.util.Log.w("TVHomeSearch","Title search unavailable",error);
                handler.post(() -> { if (token==generation&&searchOpen) { state.loading=false; voiceStatus.setText("Não foi possível buscar. Os resultados anteriores foram mantidos."); } });
            }
        });
    }
    void open(String title,View anchor,boolean automatically,boolean forRating,Runnable cancelled) {
        mount(); stopVoice(); cancelSearch(); this.cancelled=cancelled; ratingMode=forRating;
        if (editorOpened!=null) editorOpened.run();
        if (!title.trim().isEmpty()) { setQuery(title); spokenTitles.clear(); voiceAlternatives.removeAllViews(); }
        switching=true;
        if (!searchOpen) {
            int previous=toolbarVisibleWidth(); searchOpen=true; activity.findViewById(R.id.btnSearchMedia).setActivated(true);
            normalActions.setVisibility(View.INVISIBLE); editor.setVisibility(View.VISIBLE); animateToolbar(previous,editor);
        }
        suggestionsHidden=false; pairButton.setVisibility(View.GONE);
        voiceStatus.setText(voiceAlternatives.getChildCount()>0?"":draftHint()); refreshFeedback();
        voiceInput.requestFocus(); switching=false;
        if (automatically&&!title.trim().isEmpty()) handler.post(this::runSearch);
    }
}
