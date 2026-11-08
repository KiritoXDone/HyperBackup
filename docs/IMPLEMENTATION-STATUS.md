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

仅使用 `fixtures/android` 的可丢弃应用及专用目录。用户要求先完成代码，再连接手机测试。当前未报告任何 HyperBackup 真机备份/恢复成功，也未把编译与契约测试当成业务覆盖完成。
