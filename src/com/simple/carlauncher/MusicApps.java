package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the apps on the head unit that play music: apps in the music category, apps that
 * offer a media browser (what Android Auto style players expose) and well-known players by
 * name (YouTube, YouTube Music, Spotify, the factory USB player…). Bluetooth music comes first
 * on NWD units.
 */
final class MusicApps {

    private MusicApps() { }

    static final class App {
        final String label;
        final Drawable icon;
        final Intent launch;

        App(String label, Drawable icon, Intent launch) {
            this.label = label;
            this.icon = icon;
            this.launch = launch;
        }
    }

    /** Preferred order; anything else found follows alphabetically. */
    private static final String[] FAVOURITES = {
            "com.google.android.apps.youtube.music", "com.google.android.youtube", "com.spotify.music",
            "com.nwd.android.music.ui", "deezer.android.app", "com.amazon.mp3", "com.aspiro.tidal",
    };

    private static final String[] KEYWORDS = {
            "music", "música", "musica", "youtube", "spotify", "deezer", "tidal", "soundcloud",
            "player", "reproductor", "radio online", "podcast",
    };

    static List<App> find(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(launcher, 0);

        java.util.Set<String> media = new java.util.HashSet<>();
        for (ResolveInfo ri : pm.queryIntentServices(new Intent("android.media.browse.MediaBrowserService"), 0)) {
            media.add(ri.serviceInfo.packageName);
        }
        java.util.Set<String> category = new java.util.HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC), 0)) {
            category.add(ri.activityInfo.packageName);
        }

        for (ResolveInfo ri : all) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || found.containsKey(pkg)) continue;
            String text = (ri.loadLabel(pm) + " " + pkg).toLowerCase();
            boolean match = media.contains(pkg) || category.contains(pkg);
            for (String k : KEYWORDS) match |= text.contains(k);
            if (match) found.put(pkg, ri);
        }

        List<App> apps = new ArrayList<>();
        if (Profile.isNwd(c)) {
            apps.add(new App("Bluetooth", c.getDrawable(R.drawable.ic_bluetooth), new Intent(c, BtActivity.class)));
        }
        for (String pkg : FAVOURITES) {
            ResolveInfo ri = found.remove(pkg);
            if (ri != null) apps.add(toApp(pm, ri));
        }
        List<App> rest = new ArrayList<>();
        for (ResolveInfo ri : found.values()) rest.add(toApp(pm, ri));
        java.util.Collections.sort(rest, (a, b) -> a.label.compareToIgnoreCase(b.label));
        apps.addAll(rest);
        return apps;
    }

    private static App toApp(PackageManager pm, ResolveInfo ri) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
        return new App(ri.loadLabel(pm).toString(), ri.loadIcon(pm), i);
    }

    static void launch(Context c, App a) {
        try {
            c.startActivity(new Intent(a.launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.widget.Toast.makeText(c, "No se pudo abrir " + a.label, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** List of every music app to pick from. */
    static void choose(Activity a, List<App> apps) {
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i).label;
        new AlertDialog.Builder(a)
                .setTitle("Elige la música")
                .setItems(names, (d, which) -> launch(a, apps.get(which)))
                .show();
    }
}
