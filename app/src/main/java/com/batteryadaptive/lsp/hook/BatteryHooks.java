package com.batteryadaptive.lsp.hook;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 电池宽度自适应核心逻辑。
 *
 * 对应教程（思路来自 @Mari0us）：
 *  1. MiuiHollowBatteryMeterIconView.onMeasure —— 空心电池（数字电池图标）
 *  2. MiuiBatteryMeterIconView.onMeasure       —— 无数字电池图标
 *
 * 原 smali 中通过 resource.arsc 的 battery_hollow_height / battery_meter_width
 * 读取尺寸，这里改为从模块配置（XSharedPreferences）读取，免改 arsc。
 */
public final class BatteryHooks {

    private static final String HOLLOW_VIEW =
            "com.android.systemui.statusbar.views.MiuiHollowBatteryMeterIconView";
    private static final String METER_VIEW =
            "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView";

    private BatteryHooks() {
    }

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        // 1. 空心（数字）电池
        XposedHelpers.findAndHookMethod(HOLLOW_VIEW, lpparam.classLoader,
                "onMeasure", int.class, int.class,
                newOnMeasureReplacement(true, prefs));

        // 2. 无数字电池
        XposedHelpers.findAndHookMethod(METER_VIEW, lpparam.classLoader,
                "onMeasure", int.class, int.class,
                newOnMeasureReplacement(false, prefs));
    }

    private static de.robv.android.xposed.XC_MethodReplacement newOnMeasureReplacement(
            final boolean isHollow, final XSharedPreferences prefs) {
        return new de.robv.android.xposed.XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                prefs.reload();
                if (!prefs.getBoolean("enabled", true)) {
                    // 用户关闭模块时走原逻辑
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }
                try {
                    doMeasure((ImageView) param.thisObject,
                            (Integer) param.args[0], (Integer) param.args[1], isHollow, prefs);
                } catch (Throwable t) {
                    // 兜底：异常时回退原始实现，避免 SystemUI 崩溃
                    XposedBridge.log("[BatteryWidthAdaptive] onMeasure failed, fallback: " + t);
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }
                return null;
            }
        };
    }

    /**
     * 复刻 smali onMeasure：
     *   高度 = battery_hollow_height（模块配置，默认 17dp），不超过父容器给定的高度 spec；
     *   宽度 = 按 drawable / firstBitmap 的宽高比随高度等比缩放（素材多宽，电池就多宽）；
     *   若宽度 spec 为 EXACTLY 则以父容器给定宽度为准。
     */
    private static void doMeasure(ImageView view, int widthSpec, int heightSpec,
                                  boolean isHollow, XSharedPreferences prefs) {
        android.content.res.Resources res = view.getContext().getResources();

        // v1 = MeasureSpec.getSize(p2)
        int height = View.MeasureSpec.getSize(heightSpec);

        // v3 = battery_hollow_height dimen；if (v3 > 0) v1 = v3
        int dimenH = dpToPx(res, prefs.getInt("hollow_height_dp", 17));
        if (dimenH > 0) {
            height = dimenH;
        }

        // if (specSize > 0 && v1 >= specSize) v1 = specSize —— 不超过父容器给的实测高度
        int specH = View.MeasureSpec.getSize(heightSpec);
        if (specH > 0 && height >= specH) {
            height = specH;
        }

        int width;

        if (isHollow) {
            // v0 = this.batteryWidth（兜底初值）
            width = getIntFieldSafe(view, "batteryWidth");

            // 优先用 drawable 的宽高比
            boolean done = false;
            Drawable d = view.getDrawable();
            if (d != null) {
                int w = d.getIntrinsicWidth();
                int h = d.getIntrinsicHeight();
                if (w > 0 && h > 0 && height > 0) {
                    width = w * height / h;   // mul-int/div-int：v0 = w * v1 / h
                    done = true;
                }
            }
            // drawable 无效时回退 firstBitmap
            if (!done) {
                Bitmap bmp = getFirstBitmap(view);
                if (bmp != null && !bmp.isRecycled()) {
                    int w = bmp.getWidth();
                    int h = bmp.getHeight();
                    if (w > 0 && h > 0 && height > 0) {
                        width = w * height / h;
                    }
                }
            }
        } else {
            // 无数字电池：v0 = battery_meter_width dimen（教程里作为初值/兜底）
            // 配置 0 表示不指定，完全由素材比例决定
            width = dpToPx(res, prefs.getInt("meter_width_dp", 0));

            Drawable d = view.getDrawable();
            if (d != null) {
                int w = d.getIntrinsicWidth();
                int h = d.getIntrinsicHeight();
                if (w > 0 && h > 0 && height > 0) {
                    width = w * height / h;
                }
            }
            // 该类原 smali 无 firstBitmap 回退分支，保持一致
        }

        // 宽度 spec 处理：
        // mode == EXACTLY -> 直接取父容器给定宽度
        // mode == UNSPECIFIED(0) -> 宽度不超过父容器给定值
        int mode = View.MeasureSpec.getMode(widthSpec);
        if (mode == View.MeasureSpec.EXACTLY) {
            width = View.MeasureSpec.getSize(widthSpec);
        } else if (mode == View.MeasureSpec.UNSPECIFIED) {
            int s = View.MeasureSpec.getSize(widthSpec);
            if (width > s) {
                width = s;
            }
        }

        // setMeasuredDimension(v0, v1)
        XposedHelpers.callMethod(view, "setMeasuredDimension", width, height);
    }

    private static int getIntFieldSafe(Object obj, String name) {
        try {
            return XposedHelpers.getIntField(obj, name);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Bitmap getFirstBitmap(ImageView view) {
        try {
            java.lang.reflect.Field f = XposedHelpers.findFieldIfExists(
                    view.getClass(), "firstBitmap");
            if (f == null) {
                return null;
            }
            Object o = f.get(view);
            return o instanceof Bitmap ? (Bitmap) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int dpToPx(android.content.res.Resources res, int dp) {
        if (dp <= 0) {
            return 0;
        }
        return Math.round(dp * res.getDisplayMetrics().density);
    }
}
