package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compact icon segments reveal their labels on focus or while their mode is active. */
final class HomeControls {
    private static final class Segment {
        String label;
        int compactWidth, padding, targetWidth,maxLabelWidth;
        boolean expanded;
        ValueAnimator animation;
    }
    private final Map<TextView,Segment> segments=new LinkedHashMap<>();

    void add(TextView button,int compactDp) {
        add(button,compactDp,150);
    }
    void add(TextView button,int compactDp,int maxLabelDp) {
        Segment segment=new Segment(); segment.label=button.getText().toString();
        float density=button.getResources().getDisplayMetrics().density;
        segment.compactWidth=Math.round(compactDp*density);
        segment.maxLabelWidth=Math.round(maxLabelDp*density);
        int iconSize=Math.round(20*density); segment.padding=(segment.compactWidth-iconSize)/2;
        button.setPaddingRelative(segment.padding,0,segment.padding,0);
        button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        button.setSingleLine(true); button.setEllipsize(TextUtils.TruncateAt.END);
        Drawable[] icons=button.getCompoundDrawablesRelative();
        if (icons[0]!=null) icons[0].setBounds(0,0,iconSize,iconSize);
        button.setCompoundDrawablesRelative(icons[0],null,null,null);
        segments.put(button,segment); setLabel(button,segment.label);
    }

    void setLabel(TextView button,String label) {
        Segment segment=segments.get(button);
        if (segment==null) { button.setText(label); return; }
        segment.label=label; button.setContentDescription(label); update(button,segment);
    }

    void refresh() { for (Map.Entry<TextView,Segment> entry:segments.entrySet()) update(entry.getKey(),entry.getValue()); }

    private void update(TextView button,Segment segment) {
        boolean expanded=button.hasFocus()||button.isSelected()||button.isActivated();
        int gap=Math.round(8*button.getResources().getDisplayMetrics().density);
        int width=segment.compactWidth+(expanded?gap+Math.min(Math.round(button.getPaint().measureText(segment.label)),segment.maxLabelWidth):0);
        button.setCompoundDrawablePadding(expanded?gap:0);
        String text=expanded?segment.label:"";
        if (!TextUtils.equals(button.getText(),text)) button.setText(text);
        if (segment.targetWidth==width&&segment.expanded==expanded) return;
        segment.targetWidth=width; segment.expanded=expanded;
        if (segment.animation!=null) segment.animation.cancel();
        ViewGroup.LayoutParams params=button.getLayoutParams();
        if (!button.isLaidOut()||!button.isShown()) { params.width=width; button.setLayoutParams(params); return; }
        segment.animation=ValueAnimator.ofInt(button.getWidth(),width);
        segment.animation.setDuration(180);
        segment.animation.addUpdateListener(animation -> {
            ViewGroup.LayoutParams current=button.getLayoutParams(); int animated=(int)animation.getAnimatedValue(); if (current.width!=animated) { current.width=animated; button.setLayoutParams(current); }
        });
        segment.animation.start();
    }

    void finishAnimations() { for (Segment segment:segments.values()) if (segment.animation!=null&&segment.animation.isStarted()) segment.animation.end(); }

    void destroy() { for (Segment segment:segments.values()) if (segment.animation!=null) segment.animation.cancel(); }
}
