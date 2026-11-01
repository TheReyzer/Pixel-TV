package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Animate the capsule bounds without stretching its corners or measuring the controls. */
final class SearchCapsuleView extends View {
    private final Drawable capsule;
    private float revealed=Float.NaN;
    SearchCapsuleView(Context context) { super(context); capsule=context.getDrawable(R.drawable.home_control_group); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    float revealedWidth() { return Float.isNaN(revealed)?getWidth():revealed; }
    void reveal(float width) { revealed=Math.max(0,width); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        capsule.setBounds(Math.round(getWidth()-revealedWidth()),0,getWidth(),getHeight()); capsule.draw(canvas);
    }
}
