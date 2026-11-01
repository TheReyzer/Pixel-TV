package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A transient question, never a second watched-history database. */
final class WatchFeedbackController {
    private final Activity activity;
    private final ProfileController ui;
    private final TitleSearchController titleSearch;
    private final WatchMonitor monitor;
    private final BooleanSupplier busy;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PopupWindow popup;
    private boolean resumed,flowActive;
    private WatchReviewSession session;
    private final Runnable expire=() -> { if (popup!=null) popup.dismiss(); };
    private final Runnable offer=new Runnable() { @Override public void run() {
        if (!resumed||session==null||session.first()==null) return;
        if (busy.getAsBoolean()||!activity.hasWindowFocus()) { handler.postDelayed(this,600); return; }
        showQuestion(session.first());
    }};

    WatchFeedbackController(Activity activity,ProfileController ui,TitleSearchController titleSearch,BooleanSupplier busy) {
        this.activity=activity; this.ui=ui; this.titleSearch=titleSearch; this.busy=busy; monitor=WatchMonitor.get(activity);
    }
    void resume() {
        resumed=true;
        List<PlaybackJournal.Entry> suggestions=monitor.journal.pending();
        // Consume before presenting: Back, timeout and No cannot resurrect a suggestion.
        monitor.journal.discardSuggestions(); monitor.save();
        session=suggestions.isEmpty()?null:new WatchReviewSession(suggestions);
        handler.removeCallbacks(offer); if (session!=null) handler.postDelayed(offer,800);
    }
    void pause() {
        resumed=false; session=null; handler.removeCallbacksAndMessages(null);
        if (popup!=null) popup.dismiss();
        boolean close=flowActive; flowActive=false;
        if (close&&ui.dialog!=null) ui.dialog.dismiss();
    }
    void destroy() { pause(); }
    void review(MediaRepository.MediaItem item,View anchor) {
        if (!flowActive||session==null) { session=new WatchReviewSession(java.util.Collections.emptyList()); }
        flowActive=true; rateItem(item,anchor);
    }
    private void rateItem(MediaRepository.MediaItem item,View anchor) {
        WatchReviewSession current=session;
        if (item.genresPt.trim().isEmpty()) {
            LinearLayout panel=ui.panel("Avaliar "+item.title,"Preparando as informações do título…");
            View cancel=ui.row(ui.rows(panel),"Voltar","Cancelar avaliação",R.drawable.ic_back,this::finish); ui.show(panel,cancel,anchor);
            ui.dialog.setOnCancelListener(d -> finish());
        }
        titleSearch.ratingDetails(item,detail -> {
            if (!resumed||session!=current||current==null) return;
            ui.chooseRater(detail,anchor,changed -> {
                if (!resumed||session!=current) return;
                current.completed(detail,changed); finish();
            });
        });
    }

    private void startEntry(PlaybackJournal.Entry entry,View anchor) {
        if (entry.item!=null) rateItem(entry.item,anchor); else showSearch(entry.title,anchor,true);
    }
    private void finish() {
        int saved=session==null?0:session.saved; boolean handled=session!=null&&session.handled>0;
        session=null; flowActive=false; titleSearch.finishRating();
        if (ui.dialog!=null) ui.dialog.dismiss();
        if (handled) ui.savedNotice(saved>0?saved==1?"Avaliação salva":"Avaliações salvas":"Avaliações anteriores mantidas");
    }
    private void showQuestion(PlaybackJournal.Entry entry) {
        View anchor=activity.getWindow().getDecorView(); View previous=activity.getCurrentFocus();
        LinearLayout panel=new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xff242329); bg.setCornerRadius(ui.dp(18)); bg.setStroke(ui.dp(1),0xff77727f); panel.setBackground(bg);
        boolean generic=session.multipleTitles||entry.item==null;
        TextView heading=ui.text(generic?"Você assistiu algo em "+session.apps+"?":"O que achou de "+entry.item.title+"?",18);
        panel.addView(heading);
        TextView hint=ui.text(generic?"Confirme e escolha o filme ou a série.":"Escolha quem assistiu e dê uma nota por pessoa.",12); hint.setPadding(0,ui.dp(8),0,ui.dp(12)); panel.addView(hint);
        LinearLayout buttons=new LinearLayout(activity); panel.addView(buttons);
        TextView yes=ui.text(generic?"Sim":"Responder",15),no=ui.text(generic?"Não":"Agora não",15);
        for (TextView button:new TextView[]{yes,no}) { ui.styleButton(button); button.setGravity(Gravity.CENTER); button.setFocusableInTouchMode(true); button.setClickable(true); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(44),1); p.setMargins(ui.dp(3),0,ui.dp(3),0); buttons.addView(button,p); }
        int maximumWidth=Math.min(ui.dp(350),activity.getResources().getDisplayMetrics().widthPixels-ui.dp(56));
        panel.measure(View.MeasureSpec.makeMeasureSpec(maximumWidth,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        PopupWindow question=new PopupWindow(panel,panel.getMeasuredWidth(),ViewGroup.LayoutParams.WRAP_CONTENT,true); popup=question;
        question.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); question.setElevation(ui.dp(12)); question.setOutsideTouchable(true);
        question.setOnDismissListener(() -> { handler.removeCallbacks(expire); if (popup==question) popup=null; if (!flowActive) session=null; if (previous!=null&&previous.isShown()) previous.requestFocus(); });
        yes.setOnClickListener(v -> { flowActive=true; question.dismiss(); if (entry.item!=null||session.multipleTitles) startEntry(entry,previous); else { showSearch(entry.title,previous,false); } });
        no.setOnClickListener(v -> question.dismiss());
        View.OnKeyListener reset=(v,key,event) -> { if (event.getAction()==KeyEvent.ACTION_DOWN) { handler.removeCallbacks(expire); handler.postDelayed(expire,20_000); } return false; };
        yes.setOnKeyListener(reset); no.setOnKeyListener(reset);
        question.showAtLocation(anchor,Gravity.BOTTOM|Gravity.END,ui.dp(28),ui.dp(28)); yes.requestFocus(); handler.postDelayed(expire,20_000);
    }
    private void showSearch(String title,View anchor,boolean automatically) {
        titleSearch.open(title,anchor,automatically,true,this::finish);
    }
}

