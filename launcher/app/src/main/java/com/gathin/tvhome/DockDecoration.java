package com.gathin.tvhome;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Space for the focused favourites inside the shared dock capsule. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private boolean enabled = true;

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        if (!enabled) return;
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (4 * density);
            outRect.bottom = (int) (4 * density);
        }
    }

}
