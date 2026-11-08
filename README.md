# BatteryWidthAdaptive —— 电池宽度自适应 LSPosed 模块（libxposed API 102）

将「HyperOS4 电池宽度自适应」教程（原思路来自 **@Mari0us**）从 *改 dex + 改 resource.arsc* 的方式，改写为免修改系统的 **LSPosed 模块**，采用 **libxposed 现代 API（io.github.libxposed:api:102）**。

## 原理对照

| 教程原方法 | 本模块 |
|---|---|
| DEX 编辑器替换 `MiuiHollowBatteryMeterIconView.onMeasure`（数字电池） | `hook(onMeasure).intercept(...)` 全量替换，Java 逻辑逐行对应 smali |
| DEX 编辑器替换 `MiuiBatteryMeterIconView.onMeasure` + 静态字段（无数字电池） | 同上；不再需要 `$r8$clinit` / `sHollowHeightId` / `sMeterWidthId` 补丁 |
| Arsc 编辑器添加 `battery_hollow_height=17dp`（0x7f073001）与 type-info 条目 | 模块配置项「电池高度（dp）」，通过 libxposed RemotePreferences 注入，免改 arsc |
| `battery_meter_width` | 模块配置项「无数字电池初始宽度」，0 表示完全跟随素材 |
| 挂载修改后的系统界面 | LSPosed 勾选作用域「系统界面」即可（staticScope 预置） |

测宽逻辑保持不变：高度取配置值（默认 17dp，且不超过父容器给定高度），宽度 = 素材（drawable，无效时回退 firstBitmap）宽高比 × 高度，即**电池宽度跟随素材宽度**；宽度 spec 为 EXACTLY 时以父容器为准。

## 现代模块规范要点

- Manifest 中**不含**任何 `xposedmodule` / `xposedminversion` 等 legacy meta-data；
- 模块元数据全部位于 `app/src/main/resources/META-INF/xposed/`：
  - `module.prop`（minApiVersion=101 / targetApiVersion=102 / staticScope）
  - `java_init.list`（入口类 `com.batteryadaptive.lsp.hook.MainModule`）
  - `scope.list`（com.android.systemui）
- 依赖：`compileOnly io.github.libxposed:api:102.0.0`（不可打包进 APK）、`implementation io.github.libxposed:service:102.0.1`（App 侧写远程配置）；
- 配置走 `getRemotePreferences("config")`：Hook 进程只读，App 通过 XposedServiceHelper 绑定服务后写入，无需 chmod / XSharedPreferences。

## 使用方法

1. 构建得到 APK（见下文），安装到手机。
2. 打开 LSPosed → 模块 → 启用「电池宽度自适应」，确认作用域包含 **系统界面（com.android.systemui）**。
3. 打开模块 App，按需调整：
   - **电池高度**：对应 `battery_hollow_height`，默认 17dp，可按素材调节；
   - **无数字电池初始宽度**：对应 `battery_meter_width`，填 0 即完全跟随素材。
4. 保存。提示已同步后，若状态栏未变化，重启 SystemUI 即可。
5. 之后搭配主题素材（如 ROOT 特别版主题）即可，电池不会再被缩小。

> 异常兜底：hook 出错或配置里关闭模块时自动回退系统原始测量逻辑，不会导致 SystemUI 崩溃。

## 构建

**方式一：Android Studio**（需 JDK 17）—— 打开工程根目录，Sync 后 `Build > Build APK(s)`。

**方式二：命令行**：

```bash
gradle assembleRelease
```

release 包使用 debug 签名，可直接安装。

**方式三：GitHub Actions** —— 推送到 GitHub 后自动构建并上传 APK 产物（`.github/workflows/build.yml`）。

## 工程结构

```
app/src/main/
├── AndroidManifest.xml                      # 无任何 xposed meta-data（现代模块）
├── resources/META-INF/xposed/               # 模块元数据
│   ├── module.prop / java_init.list / scope.list
├── java/com/batteryadaptive/lsp/
│   ├── hook/MainModule.java                 # 入口：extends XposedModule，onPackageLoaded 安装 hook
│   ├── hook/BatteryInterceptor.java         # 两个 onMeasure 的替换实现（对应教程 smali）
│   ├── MainActivity.java                    # 配置界面（XposedServiceHelper 绑定服务）
│   └── PrefsUtil.java                       # 本地留存 + 远程配置同步
└── res/...
```

## 免责声明

仅供学习研究，修改系统界面行为存在风险，请自行承担。方法原思路来自 @Mari0us，本项目仅为其 LSPosed 模块化实现。
