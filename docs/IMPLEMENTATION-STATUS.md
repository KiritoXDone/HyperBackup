# 实施记录

当前是开发中的预览实现，**不是已通过 38 组完整覆盖验收的正式首版**。

2026-11-08：已同步设计 r2（0250e48）。r1 的 Kotlin 备份/安装/恢复重实现已从执行路径移除；原生产物不使用 r1 的 `batches/apps/record.json` 容器。

当前代码包含独立 Kotlin/Compose/Miuix 宿主、libsu daemon/AIDL、持久任务与不重放语义、Room/DataStore/凭据库，以及固定上游主流程的窄桥接。上游选择器继续负责名单、配置、Timeline 选择与原子保存；Miuix 页面通过私有宿主交接访问原接口。Rust 宿主构建通过版本能力标志与来源摘要校验，缺少适配产物时禁止运行。

尚在实施/验证：完整能力证据登记、原生集索引与 metadata bundle 浏览、所有维护/辅助操作的 UI 映射、故障收尾与跨工具并发、双向实机恢复、明暗/大字体/长列表与发布支持矩阵。功能入口存在不表示对应设备测试通过。

## 本地构建

需要 JDK 21、Android SDK 37.0 / build-tools 37.0.0、NDK 30.0.16248370、Rust 1.99.1 的 aarch64-linux-android target，以及 Python 3。Rust 依赖使用上游 vendor，不从运行时下载工具。

```sh
git submodule update --init --recursive
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/30.0.16248370
python3 engine/patches/build_native.py
python3 ci/verify_native.py
python3 -m unittest discover -s engine/tests -v
./gradlew :core:domain:test :core:engine:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK 位于 `app/build/outputs/apk/debug/`；当前仅为预览签名。不要用未完成设备验证的产物覆盖真实个人数据。

原始工具摘要在 `engine/upstream/SHA256SUMS`；派生 native 产物附有 `provenance.json`；APK 内的 `PAYLOAD-SHA256SUMS` 覆盖最终资源。代码、适配与自动测试应在同一提交上验证。

## 设备试验范围

仅使用 `fixtures/android` 的可丢弃应用及专用目录。用户要求先完成代码，再连接手机测试。当前未报告任何 HyperBackup 真机备份/恢复成功，也未把编译与契约测试当成业务覆盖完成。2026-11-08 已安装过 R8 调试包；后续 UI 回归因 USB 断开未完成。

2026-11-08 更新：主页面分组、首页状态卡、设置选择项、关于页和蓝紫启动图标已落地；debug/release 均启用 R8。新增推送检查、模拟器显示场景、签名发布和正式版本覆盖门禁，详见 [CI/CD](CI-CD.md)。

恢复前副本当前可对已选择的已安装应用执行独立新集备份，Wi-Fi 使用其原始单独备份入口。文件夹恢复的原始选择器在执行阶段才确定范围，尚未冻结该范围；开启副本时预检阻止执行，不能静默跳过保护。用户可先手动备份目标文件夹后明确关闭本次副本。此缺口属于正式版验收未完成项。

后续设置更新：35 个公开配置键接入持久默认值，11 个由位置或任务范围管理，7 个由宿主运行方式、通知、主题与 APK 更新管理。新增应用列表显示/排序、启动检查、任务亮屏、完成通知及设置导入导出。补充原脚本远端备份管理入口；简繁转换使用原始 CCUtil 生成本次任务的独立脚本副本，不修改固定载荷。

此轮本地检查：18 项领域测试、7 项引擎单测、debug/release R8 构建和两种 lint 通过；固定 Dex 的 CCUtil 自测在专用设备临时目录通过。已提交基线 `8460a22` 的云端 Check 全部通过。最新设置版本的实机 UI 自动化在窗口识别阶段失败，尚未记作通过，正在排查；并不据此登记完整业务覆盖完成。
