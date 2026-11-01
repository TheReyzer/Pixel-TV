package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Quality upgrades within one artwork layer; format changes fade the two ImageViews. */
final class HomeArtworkFade extends Drawable {
    static final int DURATION_MS=280;
    private Bitmap previous;
    private final Bitmap next;
    private final float initialMix;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF target=new RectF();
    private final long started=SystemClock.uptimeMillis();
    private int opacity=255;
    private final Runnable frame=this::invalidateSelf;
    HomeArtworkFade(Bitmap previous,Bitmap next) { this(previous,next,0); }
    private HomeArtworkFade(Bitmap previous,Bitmap next,float initialMix) { this.previous=previous; this.next=next; this.initialMix=initialMix; }
    static Bitmap bitmapOf(Drawable drawable) { return drawable instanceof HomeArtworkFade?((HomeArtworkFade)drawable).next:drawable instanceof BitmapDrawable?((BitmapDrawable)drawable).getBitmap():null; }
    static float fraction(long elapsed) { float value=Math.max(0,Math.min(1,elapsed/(float)DURATION_MS)); return value*value*(3-2*value); }
    static float blend(float initial,float progress) { return initial+(1-initial)*progress; }
    private float currentMix() { return previous==null?1:blend(initialMix,fraction(Math.round((SystemClock.uptimeMillis()-started)/Math.max(.01f,1-initialMix)))); }
    static Drawable transition(Drawable drawable,Bitmap bitmap) {
        if (drawable instanceof HomeArtworkFade) {
            HomeArtworkFade fade=(HomeArtworkFade)drawable;
            if (bitmap==fade.next) return fade;
            float mix=fade.currentMix();
            if (bitmap==fade.previous) return new HomeArtworkFade(fade.next,bitmap,1-mix);
            Bitmap visible=fade.previous!=null&&mix<.5f?fade.previous:fade.next;
            return new HomeArtworkFade(visible==bitmap?null:visible,bitmap);
        }
        Bitmap previous=bitmapOf(drawable); return new HomeArtworkFade(previous==bitmap?null:previous,bitmap);
    }
    static float artworkScale(float width,float height,int bitmapWidth,int bitmapHeight,boolean fill) { return fill?Math.max(width/bitmapWidth,height/bitmapHeight):Math.min(width/bitmapWidth,height/bitmapHeight); }
    @Override public int getIntrinsicWidth() { return next.getWidth(); }
    @Override public int getIntrinsicHeight() { return next.getHeight(); }
    private void drawWhole(Canvas canvas,Bitmap bitmap,int alpha,boolean fill) {
        if (bitmap==null||bitmap.isRecycled()||alpha<=0) return;
        float scale=artworkScale(getBounds().width(),getBounds().height(),bitmap.getWidth(),bitmap.getHeight(),fill);
        float width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale;
        float left=getBounds().exactCenterX()-width/2,top=getBounds().exactCenterY()-height/2;
        target.set(left,top,left+width,top+height); paint.setAlpha(alpha); canvas.drawBitmap(bitmap,null,target,paint);
    }
    @Override public void draw(Canvas canvas) {
        float progress=currentMix();
        int save=canvas.save(); canvas.clipRect(getBounds());
        drawWhole(canvas,previous,opacity,true); drawWhole(canvas,next,Math.round(opacity*progress),previous!=null&&progress<1);
        canvas.restoreToCount(save);
        if (progress<1) scheduleSelf(frame,SystemClock.uptimeMillis()+16); else previous=null;
    }
    @Override public void setAlpha(int alpha) { opacity=alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
