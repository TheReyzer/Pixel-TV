package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Keep the current artwork until the settled selection has a cached or downloaded backdrop. */
final class MediaBackdropController {
    private final MediaRepository repo;
    private final ImageView image;
    private final View scrim;
    private final IntConsumer ambient;
    private final BooleanSupplier allowed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable pending;
    private int generation;
    private String requested="",applied="";
    private Drawable current;

    MediaBackdropController(MediaRepository repo,ImageView image,View scrim,IntConsumer ambient,BooleanSupplier allowed) {
        this.repo=repo; this.image=image; this.scrim=scrim; this.ambient=ambient; this.allowed=allowed;
    }
    void focus(MediaRepository.MediaItem item) {
        focus(item,false);
    }
    static int width(boolean grid) { return grid?640:960; }
    void focus(MediaRepository.MediaItem item,boolean grid) {
        int width=width(grid),fade=grid?240:300;
        String key=item.type+":"+item.imdbId+":"+width;
        if (requested.equals(key)) return;
        cancel(); requested=key; int token=generation; Object request=new Object(); image.setTag(request);
        pending=() -> {
            pending=null;
            if (!allowed.getAsBoolean()) { if (token==generation) requested=""; return; }
            repo.loadBackdrop(item,width,width*9/16,bitmap -> {
                if (token!=generation||image.getTag()!=request||!allowed.getAsBoolean()) return;
                if (bitmap==null) { requested=""; return; }
                Drawable next=new BitmapDrawable(image.getResources(),bitmap),previous=image.getDrawable();
                if (previous instanceof TransitionDrawable) previous=current;
                current=next; applied=key;
                if (previous==null) image.setImageDrawable(next);
                else {
                    TransitionDrawable transition=new TransitionDrawable(new Drawable[]{previous,next}); transition.setCrossFadeEnabled(true);
                    image.setImageDrawable(transition); transition.startTransition(fade);
                    handler.postDelayed(() -> { if (image.getDrawable()==transition) image.setImageDrawable(next); },fade);
                }
                image.animate().cancel(); image.animate().alpha(.40f).setDuration(fade).start();
                scrim.animate().alpha(.65f).setDuration(fade).start();
                int[] samples=new int[9]; int i=0;
                for (int y=1;y<=3;y++) for (int x=1;x<=3;x++) samples[i++]=bitmap.getPixel(bitmap.getWidth()*x/4,bitmap.getHeight()*y/4);
                ambient.accept(meanColor(samples));
            });
        };
        handler.postDelayed(pending,grid?340:220);
    }
    void cancel() { generation++; requested=""; if (pending!=null) handler.removeCallbacks(pending); pending=null; }
    void destroy() { cancel(); handler.removeCallbacksAndMessages(null); }
    static int meanColor(int[] colors) {
        int r=0,g=0,b=0,n=0;
        for (int color:colors) if ((color>>>24)>=128) { r+=(color>>>16)&255; g+=(color>>>8)&255; b+=color&255; n++; }
        return n==0?0xff121216:0xff000000|((r/n)<<16)|((g/n)<<8)|(b/n);
    }
}
