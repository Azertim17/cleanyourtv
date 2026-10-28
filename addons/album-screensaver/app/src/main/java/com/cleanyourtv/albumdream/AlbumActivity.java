package com.cleanyourtv.albumdream;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * What setup.sh starts over ADB: stores the album link and the refresh interval, syncs once and shows how it went.
 * Protected by WRITE_SECURE_SETTINGS, which only the adb shell holds, so no other app can point the screensaver at a
 * different album.
 *
 *   am start -n com.cleanyourtv.albumdream/.AlbumActivity --es url 'https://host/share/KEY' --ei hours 12
 */
public class AlbumActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pad = Math.round(48 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(R.string.album_setup_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, pad / 2, 0, 0);
        box.addView(status);
        setContentView(box);

        SharedPreferences.Editor ed = Sync.prefs(this).edit();
        String url = getIntent().getStringExtra("url");
        if (url != null) {
            if (Album.parse(url) == null) {
                status.setText(R.string.album_bad_link);
                main.postDelayed(this::finish, 6000);
                return;
            }
            // A new album: forget when the last one was synced, so this one is fetched now.
            ed.putString(Sync.URL_KEY, url.trim()).putLong(Sync.AT_KEY, 0);
        }
        int hours = getIntent().getIntExtra("hours", 0);
        if (hours >= 1 && hours <= 24 * 30) ed.putInt(Sync.HOURS_KEY, hours);
        ed.apply();

        Sync.start(this, s -> main.post(() -> status.setText(s)), () -> main.postDelayed(this::finish, 5000));
    }
}
