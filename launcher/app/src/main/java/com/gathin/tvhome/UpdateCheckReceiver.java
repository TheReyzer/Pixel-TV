package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs the morning and evening manifest check even when Home is not visible. */
public final class UpdateCheckReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        final PendingResult pending=goAsync();
        UpdateManager.checkNow(context,update -> pending.finish());
    }
}
