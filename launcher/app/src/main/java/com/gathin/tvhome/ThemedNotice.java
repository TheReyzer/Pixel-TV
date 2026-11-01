package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** One house style for launcher notices, including callers without an Activity. */
final class ThemedNotice {
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static Toast active;

    static void show(Context context,int icon,String title,String status,boolean dot,boolean longer) {
        Context app=context.getApplicationContext();
        handler.post(() -> {
            if (active!=null) active.cancel();
            View panel=LayoutInflater.from(app).inflate(R.layout.toast_notice,new android.widget.FrameLayout(app),false);
            ImageView image=panel.findViewById(R.id.icon); image.setImageResource(icon); image.setColorFilter(0xfff2f2f2);
            TextView heading=panel.findViewById(R.id.title); heading.setText(title);
            TextView detail=panel.findViewById(R.id.status); detail.setText(status);
            panel.findViewById(R.id.noticeStatus).setVisibility(status==null||status.isEmpty()?View.GONE:View.VISIBLE);
            panel.findViewById(R.id.dot).setVisibility(dot?View.VISIBLE:View.GONE);
            float d=app.getResources().getDisplayMetrics().density;
            int width=Math.min(Math.round(480*d),app.getResources().getDisplayMetrics().widthPixels-Math.round(48*d));
            heading.setMaxWidth(Math.max(1,width-Math.round(64*d)));
            detail.setMaxWidth(Math.max(1,width-Math.round((dot?79:64)*d)));
            active=new Toast(app); active.setDuration(longer?Toast.LENGTH_LONG:Toast.LENGTH_SHORT);
            active.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,Math.round(36*d)); active.setView(panel); active.show();
        });
    }
}
