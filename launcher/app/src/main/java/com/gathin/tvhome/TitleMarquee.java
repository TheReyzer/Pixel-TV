package com.gathin.tvhome;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

/** Recycled cards always show the beginning before starting their title animation. */
final class TitleMarquee {
    static final long DELAY_MS=1000;
    private final View card;
    private final TextView[] titles;
    private Object identity;
    private boolean focused;
    private final Runnable start;
    TitleMarquee(View card,TextView... titles) {
        this.card=card; this.titles=titles;
        start=() -> {
        if (!focused||!card.hasFocus()) return;
        for (TextView title:titles) { title.setEllipsize(TextUtils.TruncateAt.MARQUEE); title.setMarqueeRepeatLimit(-1); title.setSelected(true); }
        };
    }
    void update(Object identity,boolean focus) {
        if (java.util.Objects.equals(this.identity,identity)&&focused==focus) return;
        this.identity=identity; focused=focus; card.removeCallbacks(start);
        for (TextView title:titles) { title.setSelected(false); title.setEllipsize(TextUtils.TruncateAt.END); title.scrollTo(0,0); title.setHorizontalFadingEdgeEnabled(false); }
        if (focus) card.postDelayed(start,DELAY_MS);
    }
    void cancel() { update(null,false); }
}
