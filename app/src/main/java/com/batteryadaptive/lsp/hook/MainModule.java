package com.batteryadaptive.lsp.hook;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * libxposed API 102 模块入口。
 * 作用域：com.android.systemui（见 META-INF/xposed/scope.list）。
 */
public class MainModule extends XposedModule {

    private static final String TAG = "BatteryWidthAdaptive";
    private static final String TARGET_PACKAGE = "com.android.systemui";
    private static final String HOLLOW_VIEW =
            "com.android.systemui.statusbar.views.MiuiHollowBatteryMeterIconView";
    private static final String METER_VIEW =
            "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView";

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            // 远程配置：模块 App 通过 libxposed service 写入，此处只读
            SharedPreferences prefs = getRemotePreferences("config");
            ClassLoader cl = param.getDefaultClassLoader();

            // 1. 数字（空心）电池
            Class<?> hollow = Class.forName(HOLLOW_VIEW, true, cl);
            hook(hollow.getDeclaredMethod("onMeasure", int.class, int.class))
                    .intercept(new BatteryInterceptor(true, prefs));

            // 2. 无数字电池
            Class<?> meter = Class.forName(METER_VIEW, true, cl);
            hook(meter.getDeclaredMethod("onMeasure", int.class, int.class))
                    .intercept(new BatteryInterceptor(false, prefs));

            log(Log.INFO, TAG, "battery adaptive hooks installed");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "install hooks failed", t);
        }
    }
}
