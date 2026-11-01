package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Six poster columns, recycled rows and continuous D-pad scrolling. */
final class DiscoveryGrid extends LinearLayout {
    static final int PAGE_SIZE=12,COLUMNS=6;
    static final float FOCUS_SCALE=1.08f;
    static int[] cellSize(int width,int height,int margin) {
        int maxWidth=Math.max(1,width/COLUMNS-2*margin);
        int maxHeight=Math.max(1,(int)((height-2*margin)/FOCUS_SCALE));
        int posterWidth=Math.max(1,Math.min(maxWidth,(int)(maxHeight/1.5f)));
        int posterHeight=Math.max(1,(int)(posterWidth*1.5f));
        return new int[]{posterWidth,posterHeight,posterHeight};
    }
    private final RecyclerView list;
    private final GridLayoutManager layout;
    private final TextView position;
    private final Activity activity;
    private final MediaRepository repo;
    private final Runnable up,more;
    private final java.util.function.BiConsumer<MediaRepository.MediaItem,Integer> open;
    private final java.util.function.BiConsumer<View,MediaRepository.MediaItem> focused;
    private List<MediaRepository.MediaItem> items=Collections.emptyList();
    private boolean showMore,loading;
    private String emptyMessage="",moreLabel="Descobrir mais resultados";
    private int prefetched=-1,pendingFocus=-1,lastFocusedIndex,focusGeneration,scrollDirection=1;
    private boolean movingFocus;
    private int viewportGeneration;
    private java.util.function.Consumer<Boolean> motion=active -> {};
    void onMotion(java.util.function.Consumer<Boolean> listener) { motion=listener; }
    private Runnable nearingEnd;
    private Runnable left=() -> {};
    void onLeft(Runnable action) { left=action; }
    private java.util.function.Function<MediaRepository.MediaItem,String> annotation;
    void onNearEnd(Runnable action) { nearingEnd=action; }
    private void bindBadge(TextView badge,MediaRepository.MediaItem item) { badge.setText(item.ratingLabel(false)); badge.setVisibility(item.hasRating()?View.VISIBLE:View.GONE); }
    private String caption(MediaRepository.MediaItem item) { String note=annotation==null?"":annotation.apply(item); return note.isEmpty()?item.year+" · "+item.genresPt:note; }
    void annotate(java.util.function.Function<MediaRepository.MediaItem,String> annotation) {
        this.annotation=annotation;
        for (int i=0;i<list.getChildCount();i++) { View card=list.getChildAt(i); int at=list.getChildAdapterPosition(card); if (at>=0&&at<items.size()) { MediaRepository.MediaItem item=items.get(at); TextView badge=card.findViewById(R.id.discoveryBadge); if (badge!=null) bindBadge(badge,item); card.setContentDescription(item.displayTitles()+" · "+caption(item)); } }
    }
    private void nearing(int at) { if (nearingEnd!=null&&!items.isEmpty()&&at>=items.size()-8) list.post(nearingEnd); }
    private final RecyclerView.Adapter<RecyclerView.ViewHolder> adapter=new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) { TitleMarquee marquee=(TitleMarquee)holder.itemView.getTag(R.id.marqueeState); if (marquee!=null) marquee.cancel(); }
        @Override public int getItemCount() { return items.size()+(showMore?1:0); }
        @Override public int getItemViewType(int at) { return at==items.size()?1:0; }
        @Override public long getItemId(int at) { return at==items.size()?Long.MIN_VALUE:Long.parseLong(items.get(at).imdbId.substring(2))*2+(items.get(at).isSeries()?1:0); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) {
            View view;
            if (type==1) {
                LinearLayout footer=new LinearLayout(activity); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(12),0,dp(8));
                TextView button=footerButton(),top=footerButton(); top.setText("Voltar ao topo");
                footer.addView(button,new LayoutParams(-2,dp(44))); button.setOnClickListener(v -> { if (!loading) more.run(); });
                LayoutParams topSize=new LayoutParams(-2,dp(44)); topSize.setMarginStart(dp(12)); footer.addView(top,topSize); top.setOnClickListener(v -> scrollToTop());
                for (TextView action:new TextView[]{button,top}) action.setOnKeyListener((v,key,event) -> {
                    if (event.getAction()!=KeyEvent.ACTION_DOWN) return false;
                    if (key==KeyEvent.KEYCODE_DPAD_UP) { focusCard(items.size()-1); return true; }
                    if (key==KeyEvent.KEYCODE_DPAD_LEFT) { if (v==top&&button.isEnabled()) button.requestFocus(); return true; }
                    if (key==KeyEvent.KEYCODE_DPAD_RIGHT) { if (v==button) top.requestFocus(); return true; }
                    return key==KeyEvent.KEYCODE_DPAD_DOWN;
                });
                view=footer;
            } else {
                view=activity.getLayoutInflater().inflate(R.layout.item_discovery_media,parent,false);
                View surface=view.findViewById(R.id.discoverySurface); surface.setForeground(activity.getDrawable(R.drawable.discovery_grid_focus));
                surface.setOutlineProvider(new ViewOutlineProvider(){ @Override public void getOutline(View view,Outline outline) { outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(12)); }}); surface.setClipToOutline(true);
            }
            RecyclerView.ViewHolder holder=new RecyclerView.ViewHolder(view){};
            if (type==0) {
                view.setOnClickListener(v -> { int at=holder.getAdapterPosition(); if (at>=0&&at<items.size()) open.accept(items.get(at),at); });
                view.setOnFocusChangeListener((v,hasFocus) -> { int at=holder.getAdapterPosition(); styleCardFocus(v,hasFocus,true); if (hasFocus&&at>=0&&at<items.size()) { lastFocusedIndex=at; if (!movingFocus&&list.getScrollState()==RecyclerView.SCROLL_STATE_IDLE) focused.accept(v,items.get(at)); nearing(at); } });
                view.setOnKeyListener((v,key,event) -> {
                    if (event.getAction()!=KeyEvent.ACTION_DOWN) return false;
                    int at=pendingFocus>=0?pendingFocus:holder.getAdapterPosition(); if (at<0) return false;
                    if (key==KeyEvent.KEYCODE_DPAD_UP) { if (at<COLUMNS) { cancelPendingFocus(); up.run(); } else focusCard(at-COLUMNS); return true; }
                    if (key==KeyEvent.KEYCODE_DPAD_DOWN) { if (at+COLUMNS<items.size()) focusCard(at+COLUMNS); else if (showMore) focusMore(); return true; }
                    if (key==KeyEvent.KEYCODE_DPAD_LEFT) { if (at%COLUMNS>0) focusCard(at-1); else { cancelPendingFocus(); left.run(); } return true; }
                    if (key==KeyEvent.KEYCODE_DPAD_RIGHT) { if (at%COLUMNS<COLUMNS-1&&at+1<items.size()) focusCard(at+1); return true; }
                    return false;
                });
            }
            return holder;
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int at) {
            if (at==items.size()) {
                bindFooter(holder.itemView); return;
            }
            View card=holder.itemView; MediaRepository.MediaItem item=items.get(at);
            card.setTag(R.id.marqueeIdentity,item.type+item.imdbId);
            sizeCard(card);
            bindBadge(card.findViewById(R.id.discoveryBadge),item);
            repo.bindTitle(card.findViewById(R.id.discoveryTitle),card.findViewById(R.id.discoveryOriginal),item);
            card.setContentDescription(item.displayTitles()+" · "+caption(item));
            repo.loadGridArtwork(item,card.findViewById(R.id.discoveryArtwork),() -> bindBadge(card.findViewById(R.id.discoveryBadge),item)); styleCardFocus(card,card.hasFocus(),false);
        }
    };
    DiscoveryGrid(Activity activity,MediaRepository repo,java.util.function.BiConsumer<MediaRepository.MediaItem,Integer> open,Runnable more,Runnable up,java.util.function.BiConsumer<View,MediaRepository.MediaItem> focused) {
        super(activity); this.activity=activity; this.repo=repo; this.open=open; this.more=more; this.up=up; this.focused=focused;
        setOrientation(VERTICAL); setPadding(0,dp(8),0,0); setClipChildren(false);
        // XML initializes the scrollbar drawable required by Android 9.
        list=(RecyclerView)activity.getLayoutInflater().inflate(R.layout.view_catalog_list,this,false); list.setClipToPadding(false); list.setClipChildren(false); list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            list.setClipBounds(new android.graphics.Rect(0,0,r-l,b-t));
            int edge=(int)Math.ceil((r-l)/(float)COLUMNS*1.5f*(FOCUS_SCALE-1)/2)+dp(2);
            if (list.getPaddingTop()!=edge||list.getPaddingBottom()!=edge) list.setPadding(0,edge,0,edge);
            if (r-l!=or-ol||b-t!=ob-ot) list.post(() -> { for (int i=0;i<list.getChildCount();i++) {
                View card=list.getChildAt(i); if (card.findViewById(R.id.discoverySurface)!=null) sizeCard(card);
            }
            View focus=list.getFocusedChild(); if (focus!=null) keepCardVisible(focus); });
        }); list.setPadding(0,dp(4),0,dp(8)); list.setItemAnimator(null); list.setItemViewCacheSize(COLUMNS*2); list.setVerticalScrollBarEnabled(true); list.setScrollbarFadingEnabled(false); list.setScrollIndicators(SCROLL_INDICATOR_TOP|SCROLL_INDICATOR_BOTTOM);
        layout=new GridLayoutManager(activity,COLUMNS); layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){ @Override public int getSpanSize(int at) { return at==items.size()?COLUMNS:1; }}); list.setLayoutManager(layout); adapter.setHasStableIds(true); list.setAdapter(adapter); addView(list,new LayoutParams(-1,0,1));
        position=new TextView(activity); position.setTextColor(0xfff2f2f4); position.setShadowLayer(dp(3),0,dp(1),0xff000000); position.setBackgroundResource(R.drawable.grid_position_bg); position.setTextSize(12); position.setSingleLine(true); position.setGravity(Gravity.CENTER); position.setPadding(dp(14),0,dp(14),0); position.setTypeface(getResources().getFont(R.font.outfit)); LayoutParams indicatorSize=new LayoutParams(-2,dp(28)); indicatorSize.gravity=Gravity.CENTER_HORIZONTAL; addView(position,indicatorSize);
        list.getViewTreeObserver().addOnGlobalLayoutListener(() -> { if (pendingFocus>=0) list.post(this::restorePendingFocus); });
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(View view) { if (pendingFocus>=0) list.post(DiscoveryGrid.this::restorePendingFocus); }
            @Override public void onChildViewDetachedFromWindow(View view) { }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(RecyclerView view,int dx,int dy) { if (dy!=0) scrollDirection=dy<0?-1:1; updatePosition(); prefetch(); nearing(layout.findLastVisibleItemPosition()); }
            @Override public void onScrollStateChanged(RecyclerView view,int state) {
                if (state!=RecyclerView.SCROLL_STATE_IDLE) motion.accept(true);
                else list.post(() -> {
                    if (list.getScrollState()==RecyclerView.SCROLL_STATE_IDLE&&!movingFocus) { motion.accept(false); if (list.hasFocus()) refreshBackground(); }
                });
            }
        });
    }
    private void sizeCard(View card) {
        int width=Math.max(list.getWidth(),list.getMeasuredWidth()); if (width<=0) width=getResources().getDisplayMetrics().widthPixels-dp(96);
        int height=Math.max(list.getHeight(),list.getMeasuredHeight()); if (height<=0) height=getResources().getDisplayMetrics().heightPixels-dp(200);
        int[] cell=cellSize(width-list.getPaddingLeft()-list.getPaddingRight(),height-list.getPaddingTop()-list.getPaddingBottom(),dp(6));
        card.setTag(R.id.discoveryArtwork,cell[1]);
        if (card.getLayoutParams().height!=cell[2]) { RecyclerView.LayoutParams size=new RecyclerView.LayoutParams(-1,cell[2]); size.setMargins(dp(6),dp(6),dp(6),dp(6)); card.setLayoutParams(size); }
        View surface=card.findViewById(R.id.discoverySurface);
        if (surface.getLayoutParams().width!=cell[0]||surface.getLayoutParams().height!=cell[1]) surface.setLayoutParams(new android.widget.FrameLayout.LayoutParams(cell[0],cell[1],Gravity.CENTER));
    }
    void animateViewportChange(Runnable change) {
        if (!isShown()||list.getHeight()==0) { change.run(); return; }
        int token=++viewportGeneration; int[] origin=new int[2]; list.getLocationOnScreen(origin); int oldHeight=list.getHeight();
        list.animate().cancel(); change.run(); motion.accept(true);
        list.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw() {
                list.getViewTreeObserver().removeOnPreDrawListener(this); if (token!=viewportGeneration) return true;
                list.setTranslationY(0); int[] target=new int[2]; list.getLocationOnScreen(target); int dy=origin[1]-target[1];
                list.setClipBounds(new android.graphics.Rect(0,0,list.getWidth(),Math.max(list.getHeight(),oldHeight)));
                list.setTranslationY(dy);
                android.graphics.Rect crop=new android.graphics.Rect(0,0,list.getWidth(),oldHeight);
                list.postOnAnimation(() -> { if (token!=viewportGeneration) return;
                list.animate().translationY(0).setUpdateListener(value -> {
                    if (token!=viewportGeneration) return; crop.bottom=Math.round(oldHeight+(list.getHeight()-oldHeight)*value.getAnimatedFraction()); list.setClipBounds(crop);
                }).setDuration(TvSmoothScroller.animationDuration(420,android.animation.ValueAnimator.getDurationScale())).setInterpolator(new android.view.animation.PathInterpolator(.4f,0,.2f,1)).withLayer().withEndAction(() -> {
                    if (token!=viewportGeneration) return; list.animate().setUpdateListener(null); list.setClipBounds(new android.graphics.Rect(0,0,list.getWidth(),list.getHeight())); motion.accept(false);
                }).start(); }); return true;
            }
        });
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void styleCardFocus(View card,boolean focus,boolean animate) {
        View surface=card.findViewById(R.id.discoverySurface); View art=card.findViewById(R.id.discoveryArtwork); if (art==null||!(card.getTag(R.id.discoveryArtwork) instanceof Integer)) return;
        TitleMarquee marquee=(TitleMarquee)card.getTag(R.id.marqueeState);
        if (marquee==null) { marquee=new TitleMarquee(card,card.findViewById(R.id.discoveryTitle),card.findViewById(R.id.discoveryOriginal)); card.setTag(R.id.marqueeState,marquee); }
        marquee.update(card.getTag(R.id.marqueeIdentity),focus);
        for (int id:new int[]{R.id.discoveryCaption,R.id.discoveryScrim}) { View overlay=card.findViewById(id); overlay.animate().cancel(); if (animate) overlay.animate().alpha(focus?1f:0f).setDuration(180).start(); else overlay.setAlpha(focus?1f:0f); }
        surface.animate().cancel();
        if (animate&&card.isLaidOut()) surface.animate().scaleX(focus?FOCUS_SCALE:1f).scaleY(focus?FOCUS_SCALE:1f).setDuration(180).start();
        else { surface.setScaleX(focus?FOCUS_SCALE:1f); surface.setScaleY(focus?FOCUS_SCALE:1f); }
        if (focus) list.post(() -> keepCardVisible(card));
        card.setElevation(focus?dp(8):0);
    }
    private void keepCardVisible(View card) {
        if (!card.hasFocus()||card.getParent()!=list||!list.isShown()||movingFocus||list.getScrollState()!=RecyclerView.SCROLL_STATE_IDLE) return;
        int at=list.getChildAdapterPosition(card);
        if (at>=0&&TvSmoothScroller.verticalOffset(list,card,FOCUS_SCALE)!=0) TvSmoothScroller.to(list,at,false,FOCUS_SCALE,() -> {});
    }
    void refreshBackground() { if (lastFocusedIndex>=0&&lastFocusedIndex<items.size()) focused.accept(list,items.get(lastFocusedIndex)); }
    void retryMissingArtwork() {
        for (int i=0;i<list.getChildCount();i++) {
            View card=list.getChildAt(i); int at=list.getChildAdapterPosition(card); android.widget.ImageView art=card.findViewById(R.id.discoveryArtwork);
            if (art!=null&&at>=0&&at<items.size()) repo.resumeArtwork(art);
        }
    }
    private TextView footerButton() {
        TextView button=new TextView(activity); button.setTextColor(getResources().getColorStateList(R.color.modal_button_text,activity.getTheme())); button.setTextSize(15); button.setTypeface(getResources().getFont(R.font.outfit)); button.setGravity(Gravity.CENTER); button.setPadding(dp(24),0,dp(24),0); button.setBackgroundResource(R.drawable.detail_action_bg); button.setFocusableInTouchMode(true); button.setClickable(true); return button;
    }
    private void bindFooter(View view) {
        LinearLayout footer=(LinearLayout)view; TextView button=(TextView)footer.getChildAt(0);
        button.setText(loading?"Buscando mais títulos…":moreLabel);
        if (loading&&button.hasFocus()) footer.getChildAt(1).requestFocus();
        button.setEnabled(!loading);
    }
    void cancelPendingFocus() { focusGeneration++; movingFocus=false; pendingFocus=-1; list.stopScroll(); motion.accept(false); }
    @Override protected void onVisibilityChanged(View changed,int visibility) { super.onVisibilityChanged(changed,visibility); if (list!=null&&!isShown()) { viewportGeneration++; list.animate().cancel(); list.setTranslationY(0); cancelPendingFocus(); } }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (list!=null&&visibility!=VISIBLE) { viewportGeneration++; list.animate().cancel(); list.setTranslationY(0); cancelPendingFocus(); } }
    private void scrollToTop() { focusCard(0); }
    private void prefetch() {
        int first=layout.findFirstVisibleItemPosition(),last=layout.findLastVisibleItemPosition(); if (first<0||last<0) return;
        int edge=(scrollDirection<0?first:last)/COLUMNS*2+(scrollDirection<0?1:0);
        if (prefetched!=edge) { prefetched=edge; repo.prefetchDiscovery(items,first,last,scrollDirection); }
    }
    private void updatePosition() {
        if (items.isEmpty()) { position.setText(emptyMessage); return; }
        int first=Math.min(items.size()-1,Math.max(0,layout.findFirstVisibleItemPosition())),last=Math.min(items.size()-1,Math.max(first,layout.findLastVisibleItemPosition()));
        String label="Itens "+(first+1)+"–"+(last+1)+" de "+items.size()+" · Grupo "+(first/PAGE_SIZE+1);
        if (!label.contentEquals(position.getText())) { position.setText(label); position.post(position::requestLayout); }
    }
    void reset() { cancelPendingFocus(); pendingFocus=-1; lastFocusedIndex=0; items=Collections.emptyList(); prefetched=-1; scrollDirection=1; adapter.notifyDataSetChanged(); list.scrollToPosition(0); }
    void moreState(boolean available,boolean loading,String label) {
        boolean changed=showMore!=available; this.showMore=available; this.loading=loading; this.moreLabel=label;
        RecyclerView.ViewHolder footer=list.findViewHolderForAdapterPosition(items.size()); if (footer!=null&&footer.getItemViewType()==1) bindFooter(footer.itemView);
        if (changed) adapter.notifyDataSetChanged(); else if (showMore) adapter.notifyItemChanged(items.size());
    }
    void bind(List<MediaRepository.MediaItem> results,int ignoredPage,MediaRepository ignoredRepo,String emptyMessage) {
        int old=items.size(); boolean prefix=results.size()>=old;
        for (int i=0;prefix&&i<old;i++) prefix=items.get(i).imdbId.equals(results.get(i).imdbId)&&items.get(i).type.equals(results.get(i).type);
        this.emptyMessage=emptyMessage;
        if (prefix&&results.size()==old) { updatePosition(); return; }
        if (prefix) {
            items=new ArrayList<>(results);
            if (showMore) adapter.notifyItemRemoved(old);
            adapter.notifyItemRangeInserted(old,items.size()-old+(showMore?1:0));
        } else { items=new ArrayList<>(results); adapter.notifyDataSetChanged(); list.scrollToPosition(0); }
        prefetched=-1; list.post(() -> { updatePosition(); prefetch(); });
    }
    private void restorePendingFocus() {
        if (movingFocus||pendingFocus<0||!list.isShown()) return;
        RecyclerView.ViewHolder target=list.findViewHolderForAdapterPosition(pendingFocus);
        if (target==null) return;
        View focus=pendingFocus==items.size()&&target.itemView instanceof LinearLayout?((LinearLayout)target.itemView).getChildAt(0):target.itemView;
        if (!focus.isEnabled()&&pendingFocus==items.size()) focus=((LinearLayout)target.itemView).getChildAt(1);
        if (focus.requestFocus()) { pendingFocus=-1; if (focus==target.itemView) list.post(() -> keepCardVisible(target.itemView)); }
    }
    private void focusMore() { moveFocus(items.size()); }
    void focusCurrent() { focusCard(lastFocusedIndex); }
    private void moveFocus(int index) {
        pendingFocus=index;
        if (!list.isShown()||list.getWindowVisibility()!=VISIBLE) return;
        if (movingFocus) return;
        final int generation=++focusGeneration;
        RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(index);
        if (holder==null||!TvSmoothScroller.fullyVisible(list,holder.itemView,FOCUS_SCALE)) {
            movingFocus=true;
            Runnable start=() -> { if (generation==focusGeneration) TvSmoothScroller.to(list,index,false,FOCUS_SCALE,() -> list.post(() -> { if (generation==focusGeneration) { movingFocus=false; if (pendingFocus>=0) moveFocus(pendingFocus); } })); };
            int anchor=TvSmoothScroller.nearby(layout.findFirstVisibleItemPosition(),index,COLUMNS);
            if (anchor>=0) {
                layout.scrollToPositionWithOffset(anchor,0);
                // Lay out the nearby row before the smooth scroller looks for its target.
                list.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() { list.getViewTreeObserver().removeOnPreDrawListener(this); list.post(start); return true; }
                });
            } else list.post(start);
        } else { restorePendingFocus(); list.post(this::restorePendingFocus); }
    }
    void focusCard(int index) {
        if (items.isEmpty()) { up.run(); return; }
        moveFocus(Math.max(0,Math.min(index,items.size()-1)));
    }
}
