package com.gathin.tvhome;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Variable-width, single-line chips wrapping between items, never inside their labels. */
final class DiscoveryFlowLayout extends ViewGroup {
    private final int gap;
    private int[] lines=new int[0];
    private int rowHeight;
    DiscoveryFlowLayout(Context context) { super(context); gap=Math.round(8*getResources().getDisplayMetrics().density); }
    static int[] wrapLines(int[] widths,int available,int gap) {
        int[] lines=new int[widths.length]; int line=0,x=0;
        for (int i=0;i<widths.length;i++) {
            if (x>0&&x+widths[i]>available) { line++; x=0; }
            lines[i]=line; x+=widths[i]+gap;
        }
        return lines;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec),available=Math.max(1,width-getPaddingLeft()-getPaddingRight());
        int[] widths=new int[getChildCount()]; rowHeight=0;
        for (int i=0;i<getChildCount();i++) {
            View child=getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(child.getLayoutParams().height,MeasureSpec.EXACTLY));
            widths[i]=child.getMeasuredWidth(); rowHeight=Math.max(rowHeight,child.getMeasuredHeight());
        }
        lines=wrapLines(widths,available,gap);
        int count=lines.length==0?0:lines[lines.length-1]+1;
        setMeasuredDimension(width,resolveSize(getPaddingTop()+getPaddingBottom()+count*rowHeight+Math.max(0,count-1)*gap,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int x=getPaddingLeft();
        for (int i=0;i<getChildCount();i++) {
            if (i>0&&lines[i]!=lines[i-1]) x=getPaddingLeft();
            View child=getChildAt(i); int y=getPaddingTop()+lines[i]*(rowHeight+gap);
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight()); x+=child.getMeasuredWidth()+gap;
        }
    }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.WRAP_CONTENT,Math.round(48*getResources().getDisplayMetrics().density)); }
}
