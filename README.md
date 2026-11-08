# BatteryWidthAdaptive —— 电池宽度自适应 LSPosed 模块

将「HyperOS4 电池宽度自适应」教程（原思路来自 **@Mari0us**）从 *改 dex + 改 resource.arsc* 的方式，改写为免修改系统的 **LSPosed 模块**。

## 原理对照

| 教程原方法 | 本模块 |
|---|---|
| DEX 编辑器替换 `MiuiHollowBatteryMeterIconView.onMeasure`（数字电池） | XC_MethodReplacement 直接替换该方法，Java 逻辑逐行对应 smali |
| DEX 编辑器替换 `MiuiBatteryMeterIconView.onMeasure` + 静态字段（无数字电池） | 同上替换 onMeasure；不再需要 `$r8$clinit` / `sHollowHeightId` / `sMeterWidthId` 补丁 |
| Arsc 编辑器添加 `battery_hollow_height=17dp`（0x7f073001）与 type-info 条目 | 模块配置项「电池高度（dp）」，通过 XSharedPreferences 注入，免改 arsc |
| `battery_meter_width` | 模块配置项「无数字电池初始宽度」，0 表示完全跟随素材 |
| 挂载修改后的系统界面 | LSPosed 勾选作用域「系统界面」即可 |

测宽逻辑保持不变：高度取配置值（默认 17dp，且不超过父容器给定高度），宽度 = 素材（drawable，无效时回退 firstBitmap）宽高比 × 高度，即**电池宽度跟随素材宽度**；宽度 spec 为 EXACTLY 时以父容器为准。

## 使用方法

1. 编译得到 APK（见下文「构建」），安装到手机。
2. 打开 LSPosed → 模块 → 启用「电池宽度自适应」，勾选作用域 **系统界面（com.android.systemui）**。
3. 打开模块 App，按需调整：
   - **电池高度**：对应 `battery_hollow_height`，默认 17dp，可按素材调节；
   - **无数字电池初始宽度**：对应 `battery_meter_width`，填 0 即完全跟随素材。
4. 保存后**重启 SystemUI**（或直接重启手机）生效。
5. 之后搭配主题素材（如 ROOT 特别版主题）即可，电池不会再被缩小。

> 注意：改动配置后都需要重启系统界面才生效；模块关闭（LSPosed 中停用）或出现异常时会自动回退系统原逻辑，不会导致 SystemUI 崩溃。

## 构建

**方式一：Android Studio** —— 直接用 Studio 打开工程根目录，Sync 后 `Build > Build APK(s)`。

**方式二：命令行**（需 JDK 17 + Android SDK）：

```bash
gradle assembleRelease
```

release 包使用 debug 签名，可直接安装。

**方式三：GitHub Actions** —— 工程已附带 `.github/workflows/build.yml`，推送到 GitHub 仓库后会自动构建并上传 APK 产物。

## 工程结构

```
app/src/main/
├── AndroidManifest.xml          # xposedmodule / xposedscope 声明
├── assets/xposed_init           # 入口类声明
├── java/com/batteryadaptive/lsp/
│   ├── hook/MainHook.java       # 加载入口，仅作用于 com.android.systemui
│   ├── hook/BatteryHooks.java   # 两个 onMeasure 的替换实现（对应教程 smali）
│   ├── MainActivity.java        # 配置界面
│   └── PrefsUtil.java           # 保存配置并 chmod 供 SystemUI 读取
└── res/...
```

## 免责声明

仅供学习研究，修改系统界面行为存在风险，请自行承担。方法原思路来自 @Mari0us，本项目仅为其 LSPosed 模块化实现。
