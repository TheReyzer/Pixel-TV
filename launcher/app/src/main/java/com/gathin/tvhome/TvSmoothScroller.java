package com.gathin.tvhome;

import android.util.DisplayMetrics;
import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

/** Bounded TV motion, with focus restored only after the target is visible. */
final class TvSmoothScroller {
    static long animationDuration(long duration,float systemScale) {
        return systemScale>0&&systemScale<1?Math.round(duration/systemScale):duration;
    }
    static int duration(int distance,int densityDpi) { return Math.max(160,Math.min(300,Math.round(Math.abs((long)distance)*80f/Math.max(1,densityDpi)/.3356f))); }
    static int relatedStart(int top,int bottom,int height,int paddingTop,int paddingBottom,int heading) {
        int spare=Math.max(0,height-paddingTop-paddingBottom-(bottom-top));
        return Math.max(0,top-paddingTop-Math.max(heading,spare/4));
    }
    static int nearby(int current,int target,int span) {
        if (current<0||Math.abs((long)target-current)<=span*4L) return -1;
        return (int)Math.max(0,Math.min(Integer.MAX_VALUE-1L,target+(current>target?span:-span)));
    }
    static boolean fullyVisible(RecyclerView list,View child) {
        return child.getLeft()>=list.getPaddingLeft()&&child.getTop()>=list.getPaddingTop()
            &&child.getRight()<=list.getWidth()-list.getPaddingRight()&&child.getBottom()<=list.getHeight()-list.getPaddingBottom();
    }
    static int verticalOffset(int top,int bottom,int start,int end,int inset) {
        if (top-inset<start) return top-inset-start;
        return Math.max(0,bottom+inset-end);
    }
    static int verticalOffset(RecyclerView list,View child,float focusScale) {
        RecyclerView.LayoutManager manager=list.getLayoutManager();
        RecyclerView.LayoutParams params=(RecyclerView.LayoutParams)child.getLayoutParams();
        int inset=(int)Math.ceil(child.getHeight()*(focusScale-1)/2);
        // The list padding is already reserved for the enlarged poster edges.
        return verticalOffset(manager.getDecoratedTop(child)-params.topMargin,manager.getDecoratedBottom(child)+params.bottomMargin,
                0,list.getHeight(),inset);
    }
    static boolean fullyVisible(RecyclerView list,View child,float focusScale) {
        return child.getLeft()>=list.getPaddingLeft()&&child.getRight()<=list.getWidth()-list.getPaddingRight()&&verticalOffset(list,child,focusScale)==0;
    }
    static void to(RecyclerView list,int target,boolean alignStart,Runnable finished) {
        to(list,target,alignStart,1f,finished);
    }
    static void to(RecyclerView list,int target,boolean alignStart,float focusScale,Runnable finished) {
        RecyclerView.LayoutManager manager=list.getLayoutManager(); if (manager==null) { finished.run(); return; }
        list.stopScroll();
        if (manager instanceof LinearLayoutManager) {
            LinearLayoutManager linear=(LinearLayoutManager)manager; int span=manager instanceof GridLayoutManager?((GridLayoutManager)manager).getSpanCount():3;
            int anchor=nearby(linear.findFirstVisibleItemPosition(),target,span);
            // shortcut: long jumps animate the final row; revisit when intermediate pages can be prefetched cheaply.
            if (anchor>=0) linear.scrollToPositionWithOffset(Math.min(anchor,Math.max(0,manager.getItemCount()-1)),0);
        }
        LinearSmoothScroller scroll=new LinearSmoothScroller(list.getContext()) {
            @Override protected float calculateSpeedPerPixel(DisplayMetrics metrics) { return 80f/metrics.densityDpi; }
            @Override protected int calculateTimeForDeceleration(int distance) { return duration(distance,list.getResources().getDisplayMetrics().densityDpi); }
            @Override protected int calculateTimeForScrolling(int distance) { return Math.min(300,super.calculateTimeForScrolling(distance)); }
            @Override protected int getHorizontalSnapPreference() { return alignStart?SNAP_TO_START:SNAP_TO_ANY; }
            @Override protected int getVerticalSnapPreference() { return SNAP_TO_ANY; }
            @Override public int calculateDyToMakeVisible(View child,int snapPreference) {
                return focusScale==1f?super.calculateDyToMakeVisible(child,snapPreference):-verticalOffset(list,child,focusScale);
            }
            @Override protected void onStop() {
                super.onStop();
                // LinearSmoothScroller stops when it schedules deceleration, before the list arrives.
                list.post(() -> {
                    if (list.getScrollState()==RecyclerView.SCROLL_STATE_IDLE) { finished.run(); return; }
                    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override public void onScrollStateChanged(RecyclerView view,int state) {
                            if (state==RecyclerView.SCROLL_STATE_IDLE) { list.removeOnScrollListener(this); finished.run(); }
                        }
                    });
                });
            }
        };
        scroll.setTargetPosition(target); manager.startSmoothScroll(scroll);
    }
}
