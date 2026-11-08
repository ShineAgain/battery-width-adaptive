package com.batteryadaptive.lsp;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Toast;

import io.github.libxposed.service.XposedService;

/**
 * 配置读写工具。
 * Hook 侧（SystemUI 进程）只读远程配置 getRemotePreferences("config")；
 * App 侧通过 libxposed service 写入同一组远程配置，本地 SharedPreferences 仅作界面显示留存。
 */
public final class PrefsUtil {

    public static final String PREF_NAME = "config";

    private PrefsUtil() {
    }

    public static SharedPreferences local(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveConfig(Context context, XposedService service, int hollowHeightDp, int meterWidthDp) {
        // 本地留存（UI 显示用）
        local(context).edit()
                .putBoolean("enabled", true)
                .putInt("hollow_height_dp", hollowHeightDp)
                .putInt("meter_width_dp", meterWidthDp)
                .commit();

        // 同步到 LSPosed 远程配置，SystemUI 内的 hook 从这里读取
        boolean synced = false;
        if (service != null) {
            try {
                service.getRemotePreferences(PREF_NAME).edit()
                        .putBoolean("enabled", true)
                        .putInt("hollow_height_dp", hollowHeightDp)
                        .putInt("meter_width_dp", meterWidthDp)
                        .apply();
                synced = true;
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(context,
                synced ? R.string.saved : R.string.saved_no_service,
                Toast.LENGTH_LONG).show();
    }

    public static int parse(EditText et, int fallback) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
