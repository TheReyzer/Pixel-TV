package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Native, lightweight charts using the same preference scores as the recommendations. */
final class TasteChartView extends View {
    private static final int POSITIVE=0xffb9e4d4,NEGATIVE=0xffe9acb5;
    private static final int[] COLORS={0xffc97b8c,0xffdcacb5,0xffa7a9bc,0xff8abbb0,0xffc8f0df};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final float density;
    private final List<Map.Entry<String,Double>> scores;
    private final int[] ratings;
    private final float height;
    private final boolean radar;

    public TasteChartView(Context context) { this(context,java.util.Collections.emptyMap()); }

    TasteChartView(Context context,Map<String,Double> values) {
        this(context,values,false);
    }
    TasteChartView(Context context,Map<String,Double> values,boolean radar) {
        super(context); density=getResources().getDisplayMetrics().density; ratings=null;
        scores=new ArrayList<>(values.entrySet());
        if (radar) { scores.sort((a,b) -> Double.compare(Math.abs(b.getValue()),Math.abs(a.getValue()))); if (scores.size()>6) scores.subList(6,scores.size()).clear(); }
        scores.sort((a,b) -> Double.compare(b.getValue(),a.getValue()));
        this.radar=radar&&scores.size()>=3;
        height=this.radar?220:Math.max(36,scores.size()*42);
        StringBuilder description=new StringBuilder("Afinidade de 0 a 100%. 50% indica neutralidade. ");
        for (Map.Entry<String,Double> score:scores) description.append(score.getKey()).append(": ").append(percentage(score.getValue())).append(". ");
        setContentDescription(description); init();
    }
    TasteChartView(Context context,int[] counts) {
        super(context); density=getResources().getDisplayMetrics().density; scores=null; ratings=counts.clone(); height=220; radar=false;
        StringBuilder description=new StringBuilder("Distribuição das avaliações. ");
        for (int i=0;i<ratings.length;i++) description.append(ViewerProfiles.RATINGS[i]).append(": ").append(ratings[i]).append(". ");
        setContentDescription(description); init();
    }
    private void init() { paint.setTypeface(getResources().getFont(R.font.outfit)); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    private static String percentage(double value) { return ViewerProfiles.affinityPercent(value)+"%"; }
    private float dp(float value) { return value*density; }
    @Override protected void onMeasure(int width,int heightSpec) { setMeasuredDimension(MeasureSpec.getSize(width),resolveSize(Math.round(dp(height)),heightSpec)); }
    private void label(Canvas canvas,String text,float x,float y,int size,int color,Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(dp(size)); paint.setTextAlign(align); canvas.drawText(text,x,y,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (ratings!=null) { drawRatings(canvas); return; }
        if (radar) { drawRadar(canvas); return; }
        if (scores.isEmpty()) { label(canvas,"Ainda sem sinais suficientes",0,dp(22),13,0xffbcbcc7,Paint.Align.LEFT); return; }
        float width=getWidth(),center=width/2;
        for (int i=0;i<scores.size();i++) {
            Map.Entry<String,Double> score=scores.get(i); int percent=ViewerProfiles.affinityPercent(score.getValue()); float slot=getHeight()/(float)scores.size(),y=i*slot+Math.max(0,(slot-dp(42))/2);
            label(canvas,score.getKey(),0,y+dp(13),13,Color.WHITE,Paint.Align.LEFT);
            label(canvas,percent+"%",width,y+dp(13),11,percent==50?0xffb0b0bc:percent>50?POSITIVE:NEGATIVE,Paint.Align.RIGHT);
            paint.setColor(0xff34343e); rect.set(0,y+dp(23),width,y+dp(30)); canvas.drawRoundRect(rect,dp(4),dp(4),paint);
            float length=(center-dp(4))*Math.abs(percent-50)/50f;
            if (percent!=50) { paint.setColor(percent>50?POSITIVE:NEGATIVE); rect.set(percent<50?center-length:center,y+dp(23),percent<50?center:center+length,y+dp(30)); canvas.drawRoundRect(rect,dp(4),dp(4),paint); }
            paint.setColor(0xff83838e); canvas.drawLine(center,y+dp(20),center,y+dp(33),paint);
        }
    }
    private void drawRatings(Canvas canvas) {
        float width=getWidth(),cx=width*.22f,cy=getHeight()/2f,radius=Math.min(dp(58),width*.18f);
        int total=0; for (int count:ratings) total+=count;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(12)); paint.setColor(0xff34343e); canvas.drawCircle(cx,cy,radius,paint);
        rect.set(cx-radius,cy-radius,cx+radius,cy+radius); float angle=-90;
        if (total>0) for (int i=0;i<ratings.length;i++) {
            float sweep=ratings[i]*360f/total;
            if (sweep>0) { paint.setColor(COLORS[i]); canvas.drawArc(rect,angle+1.5f,Math.max(.5f,sweep-3),false,paint); } angle+=sweep;
        }
        label(canvas,String.valueOf(total),cx,cy,28,Color.WHITE,Paint.Align.CENTER);
        label(canvas,"avaliações",cx,cy+dp(20),11,0xffbcbcc7,Paint.Align.CENTER);
        float x=width*.48f;
        for (int i=0;i<ratings.length;i++) {
            float y=cy+dp(-50+i*27); paint.setColor(COLORS[i]); canvas.drawCircle(x,y-dp(4),dp(4),paint);
            label(canvas,ViewerProfiles.RATINGS[i],x+dp(13),y,12,0xffddddE4,Paint.Align.LEFT);
            label(canvas,String.valueOf(ratings[i]),width-dp(4),y,13,Color.WHITE,Paint.Align.RIGHT);
        }
    }
    static float radarFraction(double value) {
        return ViewerProfiles.affinityPercent(value)/100f;
    }
    private void drawRadar(Canvas canvas) {
        float width=getWidth(),cx=width/2,cy=getHeight()/2f-dp(7),radius=Math.min(dp(70),width*.23f),labelRadius=radius+dp(19);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0x16e9acb5); canvas.drawCircle(cx,cy,radius*.5f,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
        for (int ring=1;ring<=4;ring++) { paint.setColor(ring==2?0xff777783:0xff393942); canvas.drawCircle(cx,cy,radius*ring/4,paint); }
        android.graphics.Path shape=new android.graphics.Path(); float[] xs=new float[scores.size()],ys=new float[scores.size()];
        for (int i=0;i<scores.size();i++) {
            double angle=-Math.PI/2+2*Math.PI*i/scores.size(); float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
            paint.setColor(0xff45454f); canvas.drawLine(cx,cy,cx+dx*radius,cy+dy*radius,paint);
            float distance=radius*radarFraction(scores.get(i).getValue()); xs[i]=cx+dx*distance; ys[i]=cy+dy*distance;
            if (i==0) shape.moveTo(xs[i],ys[i]); else shape.lineTo(xs[i],ys[i]);
        }
        shape.close(); paint.setStyle(Paint.Style.FILL);
        paint.setShader(new android.graphics.LinearGradient(0,cy-radius,0,cy+radius,0x88b9e4d4,0x12b9e4d4,android.graphics.Shader.TileMode.CLAMP)); canvas.drawPath(shape,paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(POSITIVE); paint.setStrokeWidth(dp(2)); canvas.drawPath(shape,paint);
        for (int i=0;i<scores.size();i++) {
            Map.Entry<String,Double> score=scores.get(i); double angle=-Math.PI/2+2*Math.PI*i/scores.size(); float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
            int percent=ViewerProfiles.affinityPercent(score.getValue());
            int tint=percent==50?0xffb0b0bc:percent<50?NEGATIVE:POSITIVE;
            paint.setStyle(Paint.Style.FILL); paint.setColor(tint); canvas.drawCircle(xs[i],ys[i],dp(3.5f),paint);
            float x=cx+dx*labelRadius,y=cy+dy*labelRadius; Paint.Align align=dx<-.2?Paint.Align.RIGHT:dx>.2?Paint.Align.LEFT:Paint.Align.CENTER;
            paint.setTextSize(dp(12)); float available=align==Paint.Align.LEFT?width-x:align==Paint.Align.RIGHT?x:width;
            String name=android.text.TextUtils.ellipsize(score.getKey(),new android.text.TextPaint(paint),Math.max(dp(30),available),android.text.TextUtils.TruncateAt.END).toString();
            label(canvas,name,x,y,12,Color.WHITE,align);
            label(canvas,percentage(score.getValue()),x,y+dp(14),10,tint,align);
        }
    }
}
