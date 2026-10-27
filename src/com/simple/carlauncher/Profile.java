package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/**
 * Per-user settings so the launcher can be shared: driver name, own car photo and which
 * reminders to speak. Also detects whether the head unit is an NWD one (factory radio,
 * Bluetooth and sound services available) so NWD-only features can be hidden elsewhere.
 */
final class Profile {

    private Profile() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("profile", Context.MODE_PRIVATE);
    }

    /** Driver of this head unit; used until another name is saved in the setup screen. */
    static final String DEFAULT_NAME = "Eddy";

    static String name(Context c) {
        return prefs(c).getString("name", DEFAULT_NAME).trim();
    }

    static boolean voiceEnabled(Context c) {
        return prefs(c).getBoolean("voice", true);
    }

    static boolean isSetupDone(Context c) {
        return prefs(c).getBoolean("setup_done", false);
    }

    /** "¡Bienvenido, EDDY!" (name in capitals) or just "¡Bienvenido!". */
    static String welcome(Context c) {
        String n = name(c);
        return n.isEmpty() ? "¡Bienvenido!" : "¡Bienvenido, " + n.toUpperCase(new java.util.Locale("es")) + "!";
    }

    // ---------- Car picture ----------

    static File carPhotoFile(Context c) {
        return new File(c.getFilesDir(), "car.png");
    }

    /** The user's own car photo, if they picked one. */
    static Bitmap carPhoto(Context c) {
        File f = carPhotoFile(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /** Bundled car pictures: the owner's Kia Sorento, lights off / on (0 when absent). */
    static int bundledCar(Context c, boolean lights) {
        return c.getResources().getIdentifier(lights ? "sorento_lights" : "sorento", "drawable", c.getPackageName());
    }

    // ---------- Device ----------

    private static Boolean nwd;

    /** NWD head unit: factory radio, Bluetooth module and sound services are installed. */
    static boolean isNwd(Context c) {
        if (nwd == null) {
            PackageManager pm = c.getPackageManager();
            nwd = installed(pm, "com.nwd.radio.service") && installed(pm, "com.bt.bc03")
                    && installed(pm, "com.nwd.setting.service");
        }
        return nwd;
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
