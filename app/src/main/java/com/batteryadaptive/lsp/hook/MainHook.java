package com.batteryadaptive.lsp.hook;

import android.app.Application;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "BatteryWidthAdaptive";
    private static final String TARGET_PACKAGE = "com.android.systemui";
    private static final String MODULE_PACKAGE = "com.batteryadaptive.lsp";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, "config");
        prefs.makeWorldReadable();

        XposedBridge.log("[" + TAG + "] hooked into " + TARGET_PACKAGE
                + ", prefs readable=" + prefs.getFile().canRead());

        BatteryHooks.hook(lpparam, prefs);
    }
}
