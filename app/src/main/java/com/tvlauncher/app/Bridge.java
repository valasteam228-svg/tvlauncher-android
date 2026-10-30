package com.tvlauncher.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;

import java.util.LinkedHashMap;
import java.util.Map;

/** Мост между интерфейсом (JavaScript, объект AndroidTV) и системой Android. */
public class Bridge {
    private final Activity act;

    Bridge(Activity a) { act = a; }

    /**
     * Адрес сайта -> пакеты официальных приложений для Android TV.
     * Если такое приложение установлено, откроется оно; иначе сайт откроется во встроенном окне браузера.
     * Неверный или отсутствующий пакет ничего не ломает: просто сработает запасной вариант.
     */
    private static final Map<String, String[]> APPS = new LinkedHashMap<>();
    static {
        APPS.put("music.youtube.com", new String[]{"com.google.android.youtube.tvmusic"});
        APPS.put("youtube.com", new String[]{"com.google.android.youtube.tv", "com.liskovsoft.smarttubetv"});
        APPS.put("netflix.com", new String[]{"com.netflix.ninja"});
        APPS.put("twitch.tv", new String[]{"tv.twitch.android.app"});
        APPS.put("spotify.com", new String[]{"com.spotify.tv.android"});
        APPS.put("disneyplus.com", new String[]{"com.disney.disneyplus"});
        APPS.put("primevideo.com", new String[]{"com.amazon.amazonvideo.livingroom"});
        APPS.put("max.com", new String[]{"com.wbd.stream"});
        APPS.put("tv.apple.com", new String[]{"com.apple.atve.androidtv.appletv"});
        APPS.put("kinopoisk.ru", new String[]{"ru.kinopoisk"});
        APPS.put("ivi.ru", new String[]{"ru.ivi.client.tv", "ru.ivi.client"});
        APPS.put("okko.tv", new String[]{"ru.more.play.tv", "ru.more.play"});
        APPS.put("wink.ru", new String[]{"ru.rt.video.app.tv"});
        APPS.put("kion.ru", new String[]{"ru.mts.mtstv"});
        APPS.put("premier.one", new String[]{"ru.premier.tv"});
        APPS.put("rutube.ru", new String[]{"ru.rutube.app"});
        APPS.put("vkvideo.ru", new String[]{"com.vk.vkvideo"});
        APPS.put("music.yandex.ru", new String[]{"ru.yandex.music"});
        APPS.put("soundcloud.com", new String[]{"com.soundcloud.android"});
    }

    /** Открыть адрес: сначала пробуем родное приложение сервиса, потом наш браузер. */
    static void openUrl(Activity a, String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            PackageManager pm = a.getPackageManager();
            for (Map.Entry<String, String[]> e : APPS.entrySet()) {
                if (!(host.equals(e.getKey()) || host.endsWith("." + e.getKey()))) continue;
                for (String pkg : e.getValue()) {
                    Intent view = new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg);
                    if (view.resolveActivity(pm) != null) { a.startActivity(view); return; }
                    Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                    if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                    if (i != null) { a.startActivity(i); return; }
                }
                break;
            }
        } catch (Exception ignored) { }
        Intent b = new Intent(a, BrowserActivity.class);
        b.putExtra("url", url);
        a.startActivity(b);
    }

    @JavascriptInterface
    public void openUrl(final String url) {
        act.runOnUiThread(new Runnable() { public void run() { openUrl(act, url); } });
    }

    /** Системные настройки Android: Wi‑Fi, Bluetooth, звук и т.д. */
    @JavascriptInterface
    public void openSystem(final String key) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                String action;
                switch (key) {
                    case "wifi": action = Settings.ACTION_WIFI_SETTINGS; break;
                    case "network": action = Settings.ACTION_WIRELESS_SETTINGS; break;
                    case "bluetooth":
                    case "btadd": action = Settings.ACTION_BLUETOOTH_SETTINGS; break;
                    case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
                    case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
                    case "time": action = Settings.ACTION_DATE_SETTINGS; break;
                    case "region": action = Settings.ACTION_LOCALE_SETTINGS; break;
                    case "storage": action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS; break;
                    case "location": action = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
                    default: action = Settings.ACTION_SETTINGS;
                }
                try { act.startActivity(new Intent(action)); }
                catch (Exception e) {
                    try { act.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
                }
            }
        });
    }

    private AudioManager audio() { return (AudioManager) act.getSystemService(Context.AUDIO_SERVICE); }

    @JavascriptInterface
    public double getVolume() {
        AudioManager am = audio();
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0 : (double) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max;
    }

    @JavascriptInterface
    public void setVolume(double v) {
        AudioManager am = audio();
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (int) Math.round(Math.max(0, Math.min(1, v)) * max), 0);
    }

    @JavascriptInterface
    public boolean getMute() { return audio().isStreamMute(AudioManager.STREAM_MUSIC); }

    @JavascriptInterface
    public void setMute(boolean on) {
        audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, on ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
    }

    /** «Не отключать экран». */
    @JavascriptInterface
    public void keepAwake(final boolean on) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                if (on) act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }
}
