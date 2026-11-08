package com.batteryadaptive.lsp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

public class PrefsUtil {

    private PrefsUtil() {
    }

    /**
     * 把 config.xml 及其目录 chmod 成 SystemUI 可读，
     * 否则 XSharedPreferences 读不到模块配置。
     */
    public static void makeWorldReadable(Context context) {
        try {
            File dataDir = new File(context.getApplicationInfo().dataDir);
            chmod(dataDir, 0711);
            File prefsDir = new File(dataDir, "shared_prefs");
            chmod(prefsDir, 0771);
            File prefsFile = new File(prefsDir, "config.xml");
            chmod(prefsFile, 0664);
        } catch (Throwable ignored) {
        }
    }

    private static void chmod(File file, int mode) throws Exception {
        if (file.exists()) {
            Os.chmod(file.getAbsolutePath(), mode);
        }
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    public static void saveConfig(Activity activity, int hollowHeightDp, int meterWidthDp) {
        prefs(activity).edit()
                .putBoolean("enabled", true)
                .putInt("hollow_height_dp", hollowHeightDp)
                .putInt("meter_width_dp", meterWidthDp)
                .commit();
        makeWorldReadable(activity);
        Toast.makeText(activity, R.string.saved, Toast.LENGTH_SHORT).show();
    }

    public static int parse(EditText et, int fallback) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
