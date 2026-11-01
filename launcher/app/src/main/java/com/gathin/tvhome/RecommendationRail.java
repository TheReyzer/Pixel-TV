package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A recycled, circular rail; returning from details keeps the same virtual position. */
final class RecommendationRail extends RecyclerView {
    private final Activity activity;
    private final MediaRepository repo;
    private final LinearLayoutManager layout;
    private final Runnable up,down;
    private java.util.function.Consumer<Boolean> onMotion=active -> {};
    private final java.util.function.BiConsumer<MediaRepository.MediaItem,Integer> open;
    private final java.util.function.Consumer<MediaRepository.MediaItem> focused;
    private final java.util.function.Consumer<List<MediaRepository.MediaItem>> exposed;
    private List<MediaRepository.MediaItem> items=Collections.emptyList();
    private String context="";
    private int start=-1,current=-1,pending=-1,firstPosition,expandedPosition,initialPosition;
    private boolean movingWindow,viewportMotion;
    private int artworkHeight;
    private android.animation.ValueAnimator motion;
    private List<MotionFrame> motionFrames;
    static float rebasedStart(float from,float to,float next,float progress) { return progress>=1?next:(from+(to-from)*progress-next*progress)/(1-progress); }
    static float availableBlend(float requested,boolean horizontalReady,boolean verticalReady) {
        if (horizontalReady&&!verticalReady) return 1;
        if (verticalReady&&!horizontalReady) return 0;
        return Math.max(0,Math.min(1,requested));
    }
    static int cardWidth(int available,int gap,int position,int first) {
        int leading=Math.round(available*.45f)+gap;
        int preview=Math.max(gap,Math.round(available*.03f));
        int smaller=Math.max(1,(available-preview-leading)/2);
        return position==first?leading:smaller;
    }
    static int[] artworkSize(int slotWidth,int maxHeight,boolean landscape) {
        int width=landscape?slotWidth:Math.min(slotWidth,Math.round(maxHeight*2f/3f));
        return new int[]{Math.max(1,width),Math.max(1,maxHeight)};
    }
    static int slotWidth(int available,int gap,int position,int first,int maxHeight) {
        int width=cardWidth(available,gap,position,first);
        return position==first?width:Math.min(width,Math.max(1,Math.round(maxHeight*2f/3f))+gap);
    }
    static int windowStart(int requested,int first) { return requested<first?requested:requested>first+2?requested-2:first; }
    static int slotLeft(int position,int first,int expanded,int wide,int small) {
        return (position-first)*small+(position>expanded?wide-small:0)-(first>expanded?wide-small:0);
    }
    private int windowOffset() { return firstPosition==initialPosition?0:dp(28); }
    private int slotWidth(int position) {
        int width=getWidth()>0?getWidth():getResources().getDisplayMetrics().widthPixels-dp(96);
        return slotWidth(width-getPaddingLeft()-getPaddingRight(),dp(12),position,expandedPosition,Math.max(1,getHeight()-dp(20)));
    }
    private final Adapter<Holder> cards=new Adapter<Holder>() {
        @Override public int getItemCount() { return items.size()>3?Integer.MAX_VALUE:items.size(); }
        @Override public long getItemId(int at) { return at; }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type) {
            View card=activity.getLayoutInflater().inflate(R.layout.item_discovery_media,parent,false);
            return new Holder(card);
        }
        @Override public void onViewRecycled(Holder holder) {
            holder.marquee.cancel(); stopLayerFade(holder); holder.bound=null; holder.horizontalReady=false; holder.verticalReady=false; holder.targetBlend=-1;
            for (ImageView image:new ImageView[]{holder.horizontal,holder.vertical}) { repo.cancelHomeArtwork(image); image.setImageDrawable(null); image.setTag(R.id.discoveryArtwork,null); image.setTag(R.id.artworkLoaded,null); }
        }
        @Override public void onBindViewHolder(Holder holder,int at) {
            MediaRepository.MediaItem item=item(at); View card=holder.itemView; holder.landscape=at==expandedPosition;
            boolean different=holder.bound!=item; holder.bound=item;
            if (different) { holder.blend=holder.landscape?1:0; holder.horizontalReady=false; holder.verticalReady=false; }
            card.setLayoutParams(new RecyclerView.LayoutParams(slotWidth(at),ViewGroup.LayoutParams.MATCH_PARENT));
            holder.title.setTextSize(19); holder.original.setTextSize(14);
            View caption=card.findViewById(R.id.discoveryCaption); caption.setPadding(dp(14),dp(10),dp(14),dp(14));
            holder.badge.setText(item.ratingLabel(false)); holder.badge.setVisibility(item.hasRating()?View.VISIBLE:View.GONE);
            repo.bindTitle(holder.title,holder.original,item); card.setContentDescription(item.displayTitles());
            loadArtwork(holder);
            fitArtwork(holder); showBlend(holder,holder.blend,false); style(holder,card.hasFocus());
        }
    };
    final class Holder extends ViewHolder {
        final View surface;
        final ImageView horizontal,vertical;
        final TextView title,original,badge;
        final TitleMarquee marquee;
        boolean landscape;
        MediaRepository.MediaItem bound;
        float blend,targetBlend=-1;
        boolean horizontalReady,verticalReady;
        android.animation.ValueAnimator layerFade;
        Holder(View card) {
            super(card); surface=card.findViewById(R.id.discoverySurface); horizontal=card.findViewById(R.id.discoveryArtwork);
            vertical=new ImageView(activity); vertical.setId(R.id.homePortraitArtwork); ((FrameLayout)surface).addView(vertical,1,new FrameLayout.LayoutParams(1,1,Gravity.RIGHT|Gravity.CENTER_VERTICAL));
            title=card.findViewById(R.id.discoveryTitle); original=card.findViewById(R.id.discoveryOriginal); badge=card.findViewById(R.id.discoveryBadge);
            horizontal.setScaleType(ImageView.ScaleType.CENTER_CROP); vertical.setScaleType(ImageView.ScaleType.CENTER_CROP);
            marquee=new TitleMarquee(card,title,original);
            surface.setForeground(new android.graphics.drawable.InsetDrawable(activity.getDrawable(R.drawable.discovery_grid_focus),0) {
                @Override public void draw(android.graphics.Canvas canvas) { android.graphics.Rect crop=surface.getClipBounds(); getDrawable().setBounds(crop==null?getBounds():crop); getDrawable().draw(canvas); }
            });
            surface.setOutlineProvider(new ViewOutlineProvider(){ @Override public void getOutline(View view,Outline outline) { android.graphics.Rect crop=view.getClipBounds(); if (crop!=null) outline.setRoundRect(crop,dp(14)); else outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(14)); }}); surface.setClipToOutline(true);
            card.setOnClickListener(v -> { int at=getAdapterPosition(); if (!movingWindow&&at>=0&&!items.isEmpty()) open.accept(item(at),at); });
            card.setOnFocusChangeListener((v,focus) -> { style(this,focus); if (focus&&getAdapterPosition()>=0) { current=getAdapterPosition(); focused.accept(item(current)); reportVisible(); } });
            card.setOnKeyListener((v,key,event) -> {
                if (event.getAction()!=KeyEvent.ACTION_DOWN) return false;
                int at=pending>=0?pending:getAdapterPosition(); if (at<0) return false;
                if (key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT) { focusPosition(Math.max(0,Math.min(cards.getItemCount()-1,at+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)))); return true; }
                if (key==KeyEvent.KEYCODE_DPAD_UP) { cancelMotion(); up.run(); return true; }
                if (key==KeyEvent.KEYCODE_DPAD_DOWN) { cancelMotion(); down.run(); return true; }
                return false;
            });
        }
    }
    RecommendationRail(Activity activity,MediaRepository repo,java.util.function.BiConsumer<MediaRepository.MediaItem,Integer> open,Runnable up,Runnable down,java.util.function.Consumer<MediaRepository.MediaItem> focused,java.util.function.Consumer<List<MediaRepository.MediaItem>> exposed) {
        super(activity); this.activity=activity; this.repo=repo; this.open=open; this.up=up; this.down=down; this.focused=focused; this.exposed=exposed;
        layout=new LinearLayoutManager(activity,LinearLayoutManager.HORIZONTAL,false) {
            @Override protected void calculateExtraLayoutSpace(RecyclerView.State state,int[] space) {
                int height=Math.max(1,getHeight()-dp(20));
                int small=slotWidth(Math.max(1,getWidth()),dp(12),1,0,height);
                space[0]=movingWindow&&!viewportMotion?getWidth():small;
                space[1]=small;
            }
        }; layout.setInitialPrefetchItemCount(5); setLayoutManager(layout); setHasFixedSize(true); setItemViewCacheSize(6);
        cards.setHasStableIds(true); setAdapter(cards);
        for (int i=0;i<2;i++) getRecycledViewPool().putRecycledView(cards.createViewHolder(this,0));
        setItemAnimator(null); setClipToPadding(false); setClipChildren(false); setPadding(dp(2),0,dp(2),0); setOverScrollMode(OVER_SCROLL_NEVER);
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            setClipBounds(new android.graphics.Rect(0,0,r-l,b-t));
            if (r-l!=or-ol||b-t!=ob-ot) {
                for (int i=0;i<getChildCount();i++) {
                    Holder holder=(Holder)getChildViewHolder(getChildAt(i)); int at=holder.getAdapterPosition(); if (at<0) continue;
                    ViewGroup.LayoutParams size=holder.itemView.getLayoutParams(); int width=slotWidth(at);
                    if (size.width!=width) { size.width=width; holder.itemView.setLayoutParams(size); }
                    fitArtwork(holder);
                }
                if (!movingWindow) post(this::restoreFocus);
            }
        });
        addOnScrollListener(new OnScrollListener(){ @Override public void onScrollStateChanged(RecyclerView view,int state) { if (state==SCROLL_STATE_IDLE) { restoreFocus(); reportVisible(); } }});
        addOnChildAttachStateChangeListener(new OnChildAttachStateChangeListener(){ public void onChildViewAttachedToWindow(View view) { if (pending>=0) post(RecommendationRail.this::restoreFocus); } public void onChildViewDetachedFromWindow(View view) {} });
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private MediaRepository.MediaItem item(int at) { return items.get(Math.floorMod(at,items.size())); }
    private int[] layerSize(Holder holder,boolean landscape) {
        int available=Math.max(getWidth(),layout.getWidth())-getPaddingLeft()-getPaddingRight(),height=Math.max(1,Math.max(getHeight(),layout.getHeight())-dp(20));
        int width=slotWidth(available,dp(12),landscape?0:1,0,height)-dp(12);
        return artworkSize(width,height,landscape);
    }
    private void sizeLayers(Holder holder) {
        for (boolean landscape:new boolean[]{true,false}) {
            ImageView image=landscape?holder.horizontal:holder.vertical;
            artworkHeight=Math.max(artworkHeight,Math.max(1,getHeight()-dp(20)));
            int available=Math.max(getWidth(),layout.getWidth())-getPaddingLeft()-getPaddingRight();
            int[] size=artworkSize(slotWidth(available,dp(12),landscape?0:1,0,artworkHeight)-dp(12),artworkHeight,landscape);
            ViewGroup.LayoutParams previous=image.getLayoutParams();
            if (previous.width!=size[0]||previous.height!=size[1]) image.setLayoutParams(new FrameLayout.LayoutParams(size[0],size[1],Gravity.RIGHT|Gravity.CENTER_VERTICAL));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
    }
    private void loadArtwork(Holder holder) {
        int at=holder.getAdapterPosition(); if (at<0||items.isEmpty()) return;
        int available=Math.max(getWidth(),layout.getWidth())-getPaddingLeft()-getPaddingRight(),height=Math.max(1,Math.max(getHeight(),layout.getHeight())-dp(20));
        MediaRepository.MediaItem item=holder.bound;
        for (boolean landscape:new boolean[]{true,false}) {
            ImageView image=landscape?holder.horizontal:holder.vertical;
            int width=slotWidth(available,dp(12),landscape?0:1,0,height)-dp(12);
            repo.loadHomeArtwork(item,image,width,height,landscape,() -> {
                if (holder.bound!=item) return;
                boolean horizontalReady=holder.horizontal.getTag(R.id.discoveryArtwork)==item&&Boolean.TRUE.equals(holder.horizontal.getTag(R.id.artworkLoaded)),verticalReady=holder.vertical.getTag(R.id.discoveryArtwork)==item&&Boolean.TRUE.equals(holder.vertical.getTag(R.id.artworkLoaded));
                boolean changed=horizontalReady!=holder.horizontalReady||verticalReady!=holder.verticalReady;
                holder.horizontalReady=horizontalReady; holder.verticalReady=verticalReady;
                holder.horizontal.setScaleType(ImageView.ScaleType.CENTER_CROP); holder.vertical.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.badge.setText(item.ratingLabel(false)); holder.badge.setVisibility(item.hasRating()?View.VISIBLE:View.GONE);
                if (movingWindow&&motionFrames!=null) {
                    for (MotionFrame frame:motionFrames) if (frame.holder==holder) frame.retarget(changed);
                } else { fitArtwork(holder); showBlend(holder,holder.landscape?1:0,true); }
            });
        }
    }
    private void fitArtwork(Holder holder) {
        if (holder.getAdapterPosition()<0) return;
        if (movingWindow&&motionFrames!=null) { for (MotionFrame frame:motionFrames) if (frame.holder==holder) frame.retarget(false); return; }
        sizeLayers(holder);
        int[] size=layerSize(holder,holder.landscape);
        if (movingWindow) {
            int available=getWidth()-getPaddingLeft()-getPaddingRight(),height=Math.max(artworkHeight,Math.max(1,getHeight()-dp(20)));
            size=new int[]{slotWidth(available,dp(12),0,0,height)-dp(12),height};
        }
        ViewGroup.LayoutParams previous=holder.surface.getLayoutParams();
        if (previous.width!=size[0]||previous.height!=size[1]) {
            FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(size[0],size[1],Gravity.CENTER); bounds.setMargins(dp(6),dp(10),dp(6),dp(10)); holder.surface.setLayoutParams(bounds);
        }
    }
    private void stopLayerFade(Holder holder) {
        android.animation.ValueAnimator previous=holder.layerFade; holder.layerFade=null; if (previous!=null) previous.cancel();
    }
    private void setLayerOpacity(Holder holder,float blend) {
        holder.horizontal.setAlpha(blend>0?1f:0f); holder.vertical.setAlpha(1-blend);
    }
    private void showBlend(Holder holder,float requested,boolean animate) {
        holder.blend=requested;
        float blend=availableBlend(requested,holder.horizontalReady,holder.verticalReady);
        if (animate&&holder.targetBlend==blend&&holder.layerFade!=null) return;
        stopLayerFade(holder); holder.targetBlend=blend;
        float visible=1-holder.vertical.getAlpha();
        if (animate&&Math.abs(visible-blend)>.001f) {
            android.animation.ValueAnimator fade=android.animation.ValueAnimator.ofFloat(visible,blend); holder.layerFade=fade; fade.setDuration(180);
            fade.addUpdateListener(value -> { if (holder.layerFade==fade) setLayerOpacity(holder,(Float)value.getAnimatedValue()); });
            fade.addListener(new android.animation.AnimatorListenerAdapter(){ @Override public void onAnimationEnd(android.animation.Animator animation) { if (holder.layerFade==fade) { holder.layerFade=null; setLayerOpacity(holder,blend); } }});
            fade.start();
        } else setLayerOpacity(holder,blend);
    }
    private void style(Holder holder,boolean focus) {
        int at=holder.getAdapterPosition(); holder.marquee.update(at>=0&&!items.isEmpty()?item(at).type+item(at).imdbId:null,focus);
        caption(holder);
        holder.surface.animate().cancel(); holder.surface.setScaleX(1); holder.surface.setScaleY(1);
        holder.itemView.setElevation(focus?dp(8):0);
    }
    private void caption(Holder holder) {
        for (int id:new int[]{R.id.discoveryCaption,R.id.discoveryScrim}) {
            View layer=holder.itemView.findViewById(id); float target=holder.landscape?1f:0f;
            if (layer.getAlpha()==target) continue;
            layer.animate().cancel();
            if (holder.itemView.isLaidOut()) layer.animate().alpha(target).setDuration(140).start();
            else layer.setAlpha(target);
        }
    }
    void bind(List<MediaRepository.MediaItem> results,int start,String context) {
        results=MediaRepository.appendUnique(Collections.emptyList(),results);
        boolean same=this.context.equals(context)&&this.start==start&&items.size()==results.size();
        for (int i=0;same&&i<items.size();i++) same=items.get(i).imdbId.equals(results.get(i).imdbId)&&items.get(i).type.equals(results.get(i).type);
        if (same) { post(this::reportVisible); return; }
        boolean refocus=hasFocus(); cancelMotion(); this.items=new ArrayList<>(results); this.start=start; this.context=context; pending=-1;
        current=items.isEmpty()?-1:items.size()>3?(Integer.MAX_VALUE/2/items.size())*items.size()+start:Math.min(start,items.size()-1);
        firstPosition=Math.max(0,current); expandedPosition=firstPosition; initialPosition=firstPosition;
        cards.notifyDataSetChanged(); if (current>=0) { layout.scrollToPositionWithOffset(current,0); if (refocus) focusPosition(current); }
    }
    void focusCard(int requested) {
        if (items.isEmpty()) { down.run(); return; }
        if (requested>2) { focusPosition(requested); return; }
        focusPosition(Math.min(cards.getItemCount()-1,firstPosition+requested));
    }
    int selectedIndex() { return items.isEmpty()?0:Math.floorMod(expandedPosition,items.size()); }
    void animateViewportChange(Runnable change) {
        if (!isShown()||getHeight()==0||getChildCount()==0) { change.run(); return; }
        int[] origin=new int[2]; getLocationOnScreen(origin); int oldHeight=getHeight();
        android.util.SparseArray<android.graphics.RectF> before=new android.util.SparseArray<>(); android.util.SparseArray<Float> roots=new android.util.SparseArray<>();
        for (int i=0;i<getChildCount();i++) {
            Holder holder=(Holder)getChildViewHolder(getChildAt(i)); int at=holder.getAdapterPosition();
            if (at>=0) { before.put(at,surfaceRect(holder)); roots.put(at,holder.itemView.getX()); }
        }
        cancelMotion(); movingWindow=true; viewportMotion=true;
        for (int i=0;i<getChildCount();i++) fitArtwork((Holder)getChildViewHolder(getChildAt(i)));
        change.run(); onMotion.accept(true);
        android.animation.ValueAnimator animation=android.animation.ValueAnimator.ofFloat(0,1); motion=animation;
        animation.setDuration(TvSmoothScroller.animationDuration(420,android.animation.ValueAnimator.getDurationScale())); animation.setInterpolator(new android.view.animation.PathInterpolator(.4f,0,.2f,1));
        animation.addUpdateListener(value -> { if (motion==animation&&motionFrames!=null) for (MotionFrame frame:motionFrames) frame.apply((Float)value.getAnimatedValue()); });
        animation.addListener(new android.animation.AnimatorListenerAdapter(){ @Override public void onAnimationEnd(android.animation.Animator ended) {
            if (motion!=animation) return; motion=null; movingWindow=false; viewportMotion=false; clearMotionFrames(); settleWindow();
            setClipBounds(new android.graphics.Rect(0,0,getWidth(),getHeight())); onMotion.accept(false);
            post(() -> { if (!movingWindow&&pending>=0) focusPosition(pending); });
        }});
        getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw() {
                if (motion!=animation) { getViewTreeObserver().removeOnPreDrawListener(this); return true; }
                getViewTreeObserver().removeOnPreDrawListener(this); int[] target=new int[2]; getLocationOnScreen(target); int dy=origin[1]-target[1];
                setClipBounds(new android.graphics.Rect(0,Math.min(0,dy),getWidth(),Math.max(getHeight(),oldHeight+dy)));
                motionFrames=new ArrayList<>();
                for (int i=0;i<getChildCount();i++) { Holder holder=(Holder)getChildViewHolder(getChildAt(i)); int at=holder.getAdapterPosition(); if (at<0) continue;
                    android.graphics.RectF rect=before.get(at); if (rect==null) rect=surfaceRect(holder); else { rect=new android.graphics.RectF(rect); rect.offset(0,dy); }
                    MotionFrame frame=new MotionFrame(holder,roots.get(at,holder.itemView.getX()),rect); frame.apply(0); motionFrames.add(frame);
                }
                postOnAnimation(() -> { if (motion==animation) animation.start(); }); return true;
            }
        });
    }
    private void cancelMotion() {
        boolean interrupted=movingWindow||motion!=null;
        android.animation.ValueAnimator previous=motion; motion=null; if (previous!=null) previous.cancel();
        movingWindow=false; viewportMotion=false; pending=-1; stopScroll();
        if (interrupted) { clearMotionFrames(); settleWindow(); onMotion.accept(false); }
    }
    private void settleWindow() {
        for (int i=0;i<getChildCount();i++) {
            Holder holder=(Holder)getChildViewHolder(getChildAt(i)); int at=holder.getAdapterPosition(); if (at<0) continue;
            holder.landscape=at==expandedPosition;
            caption(holder);
            ViewGroup.LayoutParams bounds=holder.itemView.getLayoutParams(); int width=slotWidth(at); if (bounds.width!=width) { bounds.width=width; holder.itemView.setLayoutParams(bounds); } fitArtwork(holder); showBlend(holder,holder.landscape?1:0,false);
        }
        if (!items.isEmpty()) layout.scrollToPositionWithOffset(firstPosition,windowOffset());
    }
    private void focusPosition(int at) {
        pending=at;
        if (movingWindow) return;
        int leading=windowStart(at,firstPosition);
        if (leading!=firstPosition||at!=expandedPosition) {
            if (Math.abs((long)leading-firstPosition)>4) {
                // shortcut: distant programmatic jumps restore directly; D-pad steps always animate.
                cancelMotion(); firstPosition=leading; expandedPosition=at; pending=at; cards.notifyDataSetChanged(); layout.scrollToPositionWithOffset(leading,windowOffset()); post(this::restoreFocus); return;
            }
            animateWindow(leading,at);
            return;
        }
        View target=layout.findViewByPosition(at);
        if (target==null) { layout.scrollToPositionWithOffset(firstPosition,windowOffset()); post(this::restoreFocus); }
        else restoreFocus();
    }
    private void animateWindow(int leading,int expanded) {
        int previous=firstPosition,available=getWidth()-getPaddingLeft()-getPaddingRight(),height=Math.max(1,getHeight()-dp(20));
        int previousExpanded=expandedPosition,previousOffset=windowOffset();
        int wide=slotWidth(available,dp(12),previous,previous,height),small=slotWidth(available,dp(12),previous+1,previous,height);
        android.util.SparseArray<android.graphics.RectF> before=new android.util.SparseArray<>();
        android.util.SparseArray<Float> roots=new android.util.SparseArray<>();
        for (int i=0;i<getChildCount();i++) {
            Holder holder=(Holder)getChildViewHolder(getChildAt(i)); int at=holder.getAdapterPosition(); if (at<0) continue;
            before.put(at,surfaceRect(holder)); roots.put(at,holder.itemView.getX()); holder.surface.animate().cancel(); holder.marquee.cancel();
        }
        android.animation.ValueAnimator previousMotion=motion; motion=null; if (previousMotion!=null) previousMotion.cancel(); clearMotionFrames();
        firstPosition=leading; expandedPosition=expanded; movingWindow=true; onMotion.accept(true);
        // Lay out once, then animate transforms; measuring text and the rail every frame stalls TV hardware.
        android.animation.ValueAnimator animation=android.animation.ValueAnimator.ofFloat(0,1); motion=animation;
        float systemScale=android.animation.ValueAnimator.getDurationScale();
        animation.setDuration(TvSmoothScroller.animationDuration(280,systemScale)); animation.setInterpolator(new android.view.animation.PathInterpolator(.2f,0,.2f,1));
        for (int i=0;i<getChildCount();i++) {
            Holder holder=(Holder)getChildViewHolder(getChildAt(i)); int at=holder.getAdapterPosition(); if (at<0) continue;
            holder.landscape=at==expandedPosition;
            caption(holder);
            ViewGroup.LayoutParams bounds=holder.itemView.getLayoutParams(); int width=slotWidth(at); if (bounds.width!=width) { bounds.width=width; holder.itemView.setLayoutParams(bounds); } fitArtwork(holder);
        }
        layout.scrollToPositionWithOffset(firstPosition,windowOffset());
        animation.addUpdateListener(value -> { if (motion==animation&&motionFrames!=null) for (MotionFrame frame:motionFrames) frame.apply((Float)value.getAnimatedValue()); });
        animation.addListener(new android.animation.AnimatorListenerAdapter(){ @Override public void onAnimationEnd(android.animation.Animator ended) {
            if (motion!=animation) return; motion=null; movingWindow=false; viewportMotion=false; clearMotionFrames(); settleWindow(); onMotion.accept(false);
            post(() -> { if (!movingWindow&&pending>=0) focusPosition(pending); reportVisible(); });
        }});
        getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){ @Override public boolean onPreDraw() {
            getViewTreeObserver().removeOnPreDrawListener(this); if (motion!=animation) return true;
            motionFrames=new ArrayList<>();
            for (int i=0;i<getChildCount();i++) {
                Holder holder=(Holder)getChildViewHolder(getChildAt(i)); int at=holder.getAdapterPosition(); if (at<0) continue;
                float oldX=roots.get(at,Float.NaN);
                if (Float.isNaN(oldX)) oldX=getPaddingLeft()+previousOffset+slotLeft(at,previous,previousExpanded,wide,small);
                android.graphics.RectF start=before.get(at);
                if (start==null) {
                    int[] size=layerSize(holder,at==previousExpanded); int slot=at==previousExpanded?wide:small;
                    float left=oldX+(slot-size[0])/2f,top=(getHeight()-size[1])/2f;
                    start=new android.graphics.RectF(left,top,left+size[0],top+size[1]);
                }
                motionFrames.add(new MotionFrame(holder,oldX,start));
            }
            for (MotionFrame frame:motionFrames) frame.apply(0);
            animation.start(); return true;
        }});
    }
    private android.graphics.RectF surfaceRect(Holder holder) {
        View surface=holder.surface;
        float x=holder.itemView.getX()+surface.getX()+surface.getPivotX()*(1-surface.getScaleX()),y=surface.getY()+surface.getPivotY()*(1-surface.getScaleY());
        android.graphics.Rect crop=surface.getClipBounds();
        if (crop!=null) return new android.graphics.RectF(x+crop.left*surface.getScaleX(),y+crop.top*surface.getScaleY(),x+crop.right*surface.getScaleX(),y+crop.bottom*surface.getScaleY());
        return new android.graphics.RectF(x,y,x+surface.getWidth()*surface.getScaleX(),y+surface.getHeight()*surface.getScaleY());
    }
    private final class MotionFrame {
        final Holder holder; final float rootFrom,rootTo; final android.graphics.RectF from,to;
        final View caption,badge; final android.graphics.Rect crop=new android.graphics.Rect();
        float progress,fromBlend; final float toBlend;
        MotionFrame(Holder holder,float rootFrom,android.graphics.RectF from) {
            this.holder=holder; this.rootFrom=rootFrom; rootTo=holder.itemView.getLeft(); this.from=from;
            int[] size=layerSize(holder,holder.landscape);
            to=new android.graphics.RectF(rootTo+(holder.itemView.getWidth()-size[0])/2f,(getHeight()-size[1])/2f,rootTo+(holder.itemView.getWidth()+size[0])/2f,(getHeight()+size[1])/2f);
            caption=holder.itemView.findViewById(R.id.discoveryCaption); badge=holder.badge;
            fromBlend=1-holder.vertical.getAlpha(); toBlend=holder.landscape?1:0;
            stopLayerFade(holder);
            holder.surface.setPivotX(0); holder.surface.setPivotY(0);
        }
        void apply(float progress) {
            this.progress=progress; View surface=holder.surface;
            float root=rootFrom+(rootTo-rootFrom)*progress,x=from.left+(to.left-from.left)*progress,y=from.top+(to.top-from.top)*progress;
            float width=from.width()+(to.width()-from.width())*progress,height=from.height()+(to.height()-from.height())*progress;
            holder.itemView.setTranslationX(root-rootTo); surface.setScaleX(1); surface.setScaleY(1);
            surface.setTranslationX(x+width-root-surface.getLeft()-surface.getWidth()); surface.setTranslationY(y+height/2-surface.getTop()-surface.getHeight()/2f);
            crop.set(Math.round(surface.getWidth()-width),Math.round((surface.getHeight()-height)/2),surface.getWidth(),Math.round((surface.getHeight()+height)/2)); surface.setClipBounds(crop); surface.invalidateOutline(); if (holder.itemView.hasFocus()) surface.invalidate();
            caption.setTranslationX(crop.left); caption.setTranslationY(crop.bottom-surface.getHeight()); badge.setTranslationX(crop.left); badge.setTranslationY(crop.top);
            showBlend(holder,fromBlend+(toBlend-fromBlend)*progress,false);
        }
        void retarget(boolean readyChanged) {
            if (progress>=1) return;
            if (readyChanged) { float visible=1-holder.vertical.getAlpha(); fromBlend=rebasedStart(visible,visible,toBlend,progress); }
            int[] size=layerSize(holder,holder.landscape);
            float left=rootTo+(holder.itemView.getWidth()-size[0])/2f,top=(getHeight()-size[1])/2f,right=left+size[0],bottom=top+size[1];
            if (Math.abs(to.left-left)<.1f&&Math.abs(to.top-top)<.1f&&Math.abs(to.right-right)<.1f&&Math.abs(to.bottom-bottom)<.1f) return;
            from.set(rebasedStart(from.left,to.left,left,progress),rebasedStart(from.top,to.top,top,progress),rebasedStart(from.right,to.right,right,progress),rebasedStart(from.bottom,to.bottom,bottom,progress)); to.set(left,top,right,bottom);
        }
        void clear() {
            holder.itemView.setTranslationX(0); View surface=holder.surface;
            surface.setPivotX(surface.getWidth()/2f); surface.setPivotY(surface.getHeight()/2f); surface.setTranslationX(0); surface.setTranslationY(0);
            surface.setScaleX(1); surface.setScaleY(1); surface.setClipBounds(null); surface.invalidateOutline();
            caption.setTranslationX(0); caption.setTranslationY(0); badge.setTranslationX(0); badge.setTranslationY(0);
        }
    }
    private void clearMotionFrames() { if (motionFrames!=null) { for (MotionFrame frame:motionFrames) frame.clear(); motionFrames=null; } }
    void onMotion(java.util.function.Consumer<Boolean> listener) { onMotion=listener; }
    private void restoreFocus() {
        if (movingWindow||pending<0||!isShown()) return; View target=layout.findViewByPosition(pending); boolean same=target!=null&&target.hasFocus();
        if (target!=null&&target.requestFocus()) { if (same) { style((Holder)getChildViewHolder(target),true); focused.accept(item(pending)); } pending=-1; }
    }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (visibility!=VISIBLE&&layout!=null) cancelMotion(); }
    List<MediaRepository.MediaItem> visibleItems() {
        List<MediaRepository.MediaItem> visible=new ArrayList<>();
        for (int i=0;i<Math.min(4,items.size());i++) visible.add(item(firstPosition+i));
        return visible;
    }
    private void reportVisible() { if (!items.isEmpty()) exposed.accept(visibleItems()); }
    void retryMissingArtwork() {
        for (int i=0;i<getChildCount();i++) { View child=getChildAt(i); Holder holder=(Holder)getChildViewHolder(child); int at=holder.getAdapterPosition(); if (at<0||items.isEmpty()) continue; loadArtwork(holder); if (child.hasFocus()) focused.accept(item(at)); }
    }
    boolean readyForStartup(boolean requireArtwork) {
        if (items.isEmpty()) return false;
        for (int i=0;i<Math.min(3,items.size());i++) {
            View card=layout.findViewByPosition(firstPosition+i); if (card==null) return false;
            Holder holder=(Holder)getChildViewHolder(card);
            if (requireArtwork&&!(i==0?holder.horizontalReady:holder.verticalReady)) return false;
        }
        return true;
    }
}
