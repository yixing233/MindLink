# 在 Linux/ARM（含手机 proot）上构建

本文件记录把 `scripts/build.ps1` 移植为 `scripts/build.sh` 时踩到的坑，供 PC 端复用时对照。

## 环境

- JDK 21、Gradle 9.x、AGP 9.x、Kotlin 2.x（`ui-compose` 工程按此版本线构建）
- Android SDK：`platforms/android-37.0`、`build-tools/36.0.0`
- apktool 2.11+、R8（d8）、`zipalign` / `apksigner`（系统 ARM 版，SDK 自带的是 x86）

## 构建

```sh
MINDLINK_BUILD_ROOT=/tmp/build/out bash scripts/build.sh
```

可用环境变量覆盖工具路径：`MINDLINK_JDK/SDK/GRADLE/APKTOOL/D8/AAPT2/ZIPALIGN/APKSIGNER`，
以及签名用的 `MINDLINK_KEYSTORE/KS_PASS/KEY_PASS/KEY_ALIAS`（设置后第 8 步直接签名）。

## 两个必须守住的约束

1. **Compose 载荷不能开 R8 裁剪。** 裁剪后载荷只剩约 2700 个类，Compose 运行时与
   Material3 被剥掉，微信内设置面板/侧栏加载时报 `IllegalStateException`。
   `scripts/build.sh` 已默认 `MINDLINK_PAYLOAD_MINIFY=false`。完整载荷约 16500 个类。
2. **清单必须保留 `wex.ui.core.MainActivity` 的 MAIN/LAUNCHER 声明。**
   微信的包可见性查询只匹配启动器类应用；没有它，微信进程
   `createPackageContext("dev.mindlink")` 直接失败，侧栏与设置面板都会报
   `Installed MindLink resources are unavailable`。`scripts/check_compose_resources.py`
   的启动项校验已放宽为「零个或恰好 MainActivity」，但仍不允许其它启动项。

## ARM 上的 aapt2

Gradle 默认下载的 aapt2 是 x86_64，在 ARM 上会用 qemu 包装执行；
`scripts/build.sh` 通过命令行 `-Pandroid.aapt2FromMavenOverride` 注入包装后的 aapt2，
因此**不需要**把本机绝对路径写进 `gradle.properties`。
