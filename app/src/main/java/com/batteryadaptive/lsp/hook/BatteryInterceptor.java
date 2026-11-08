package com.batteryadaptive.lsp.hook;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 电池宽度自适应核心逻辑（libxposed API 102 Hooker）。
 *
 * 对应教程（思路来自 @Mari0us）：
 *  - isHollow=true  ：MiuiHollowBatteryMeterIconView（数字电池）
 *  - isHollow=false ：MiuiBatteryMeterIconView（无数字电池）
 *
 * 原 smali 通过 resource.arsc 的 battery_hollow_height / battery_meter_width
 * 读取尺寸，这里改为从远程配置（SharedPreferences）读取，免改 arsc。
 */
public class BatteryInterceptor implements XposedInterface.Hooker {

    private static volatile Method sSetMeasuredDimension;

    private final boolean isHollow;
    private final SharedPreferences prefs;

    public BatteryInterceptor(boolean isHollow, SharedPreferences prefs) {
        this.isHollow = isHollow;
        this.prefs = prefs;
    }

    @Override
    public Object intercept(XposedInterface.Chain chain) throws Throwable {
        if (!prefs.getBoolean("enabled", true)) {
            // 配置里关闭了模块时，走原始逻辑
            return chain.proceed();
        }
        Object obj = chain.getThisObject();
        if (!(obj instanceof ImageView)) {
            return chain.proceed();
        }
        ImageView view = (ImageView) obj;
        try {
            doMeasure(view, (Integer) chain.getArg(0), (Integer) chain.getArg(1));
        } catch (Throwable t) {
            // 兜底：异常时回退原始实现，避免 SystemUI 崩溃
            return chain.proceed();
        }
        // onMeasure 是 void 方法，返回值被框架忽略
        return null;
    }

    /**
     * 复刻教程 smali onMeasure：
     *   高度 = battery_hollow_height（配置，默认 17dp），不超过父容器给定的高度 spec；
     *   宽度 = 素材（drawable / firstBitmap）宽高比 × 高度，即电池宽度跟随素材宽度；
     *   宽度 spec 为 EXACTLY 时以父容器给定宽度为准。
     */
    private void doMeasure(ImageView view, int widthSpec, int heightSpec) throws Exception {
        android.content.res.Resources res = view.getResources();

        // v1 = MeasureSpec.getSize(p2)
        int height = View.MeasureSpec.getSize(heightSpec);

        // v3 = battery_hollow_height dimen；if (v3 > 0) v1 = v3
        int dimenH = dpToPx(res, prefs.getInt("hollow_height_dp", 17));
        if (dimenH > 0) {
            height = dimenH;
        }

        // if (specSize > 0 && v1 >= specSize) v1 = specSize
        int specH = View.MeasureSpec.getSize(heightSpec);
        if (specH > 0 && height >= specH) {
            height = specH;
        }

        int width;

        if (isHollow) {
            // v0 = this.batteryWidth（兜底初值）
            width = intField(view, "batteryWidth");

            // 优先用 drawable 的宽高比
            boolean done = false;
            Drawable d = view.getDrawable();
            if (d != null) {
                int w = d.getIntrinsicWidth();
                int h = d.getIntrinsicHeight();
                if (w > 0 && h > 0 && height > 0) {
                    width = w * height / h;   // v0 = w * v1 / h
                    done = true;
                }
            }
            // drawable 无效时回退 firstBitmap
            if (!done) {
                Bitmap bmp = bitmapField(view, "firstBitmap");
                if (bmp != null && !bmp.isRecycled()) {
                    int w = bmp.getWidth();
                    int h = bmp.getHeight();
                    if (w > 0 && h > 0 && height > 0) {
                        width = w * height / h;
                    }
                }
            }
        } else {
            // 无数字电池：v0 = battery_meter_width dimen（初值/兜底）
            // 配置 0 表示完全由素材比例决定
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

        // 宽度 spec 处理：EXACTLY 直接取父容器值；UNSPECIFIED 不超过父容器值
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
        setMeasuredDimensionMethod().invoke(view, width, height);
    }

    private static Method setMeasuredDimensionMethod() throws Exception {
        Method m = sSetMeasuredDimension;
        if (m == null) {
            synchronized (BatteryInterceptor.class) {
                m = sSetMeasuredDimension;
                if (m == null) {
                    m = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
                    m.setAccessible(true);
                    sSetMeasuredDimension = m;
                }
            }
        }
        return m;
    }

    private static int intField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(obj);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Bitmap bitmapField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object o = f.get(obj);
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
