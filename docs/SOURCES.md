# 研究来源与证据范围

核查日期：2026-11-08。以下固定提交用于设计，后续升级应重新比较行为。源码检查、README 声明、设备 UI 观察、设计新增要求分开记录。

## backup_script

固定 [v1117 / 4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5](https://github.com/YAWAsau/backup_script/tree/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5)，提交时间 2026-11-08 13:15:27 +08:00。

| 事实 | 来源 |
| --- | --- |
| 已有原生 Miuix UI、库版本 0.9.4；最低 API 28 | [webview/app/build.gradle.kts](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/webview/app/build.gradle.kts#L7-L24) |
| Activity 无普通桌面入口，仍由 root start.sh 引导 | [Manifest](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/webview/AndroidManifest.xml#L1-L13)、[start.sh](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/start.sh#L41-L90) |
| Root 脚本、Dex、Rust 分工；Android 9+/arm64/Magisk/KernelSU 为声明基线 | [README](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/README.md)、[Rust 构建](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/rust/build.ps1#L21-L25) |
| 现有控制是菜单/编辑器/控制台会话，认证 loopback | [NativeApi.java](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/webview/app/src/main/java/NativeApi.java#L22-L77)、[web_menu.rs](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/rust/src/web_menu.rs#L1-L65) |
| 取消区分 graceful/forced/failed，不能视为事务回滚 | [web_console.rs](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/rust/src/web_console.rs#L332-L345) |
| 默认后台应用/黑名单设置及保护流程 | [tools.sh 设置](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L2431-L2469)、[kill_app](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L31684-L31776) |
| guard 收尾不等于恢复所有第三方冻结状态 | [收尾顺序](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L10048-L10081) |
| 同版/本机新版/不可比较跳过 APK；明确更新失败可能继续数据 | [安装与恢复分支](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L38873-L38982) |
| 安装提交未知结果为 75；不包含 -d | [pm_session.rs](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/rust/src/pm_session.rs#L156-L221) |
| 数据排除范围不只缓存 | [归档参数](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L7487-L7515) |
| Backup_suffix 可构造目录，但非完整多批次仓库协议 | [备份目录名](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L23557-L23575) |
| 恢复核验字段并非全文件内容 hash | [restore_manifest.rs](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/rust/src/restore_manifest.rs#L42-L76) |

未找到通用归档 SHA-256 清单生成、断电后安全续作、与 DataBackup 格式互读、整个恢复原子回滚的充分证据。新项目需要这些能力时，明确列为新增或不支持，而非从宣传文案推导。

## Android-DataBackup

固定 [c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac](https://github.com/XayahSuSuSu/Android-DataBackup/tree/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac)。手机实际安装的是 **2.0.12 Premium**；仓库 `source-next` 为开发中的 3.x，不能混为同一发布版。

值得参考的边界：[RootService 与 AIDL](https://github.com/XayahSuSuSu/Android-DataBackup/blob/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac/source-next/app/src/main/aidl/com/xayah/databackup/rootservice/IRemoteRootService.aidl#L11-L52)、[用户/应用来源模型](https://github.com/XayahSuSuSu/Android-DataBackup/blob/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac/source-next/app/src/main/java/com/xayah/databackup/entity/backup/BackupSource.kt#L8-L42)、[恢复请求单次消费](https://github.com/XayahSuSuSu/Android-DataBackup/blob/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac/source-next/app/src/main/java/com/xayah/databackup/data/RestoreProcessRepository.kt#L96-L115)。

不能直接当作保证：[内存会话中断处理](https://github.com/XayahSuSuSu/Android-DataBackup/blob/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac/source-next/app/src/main/java/com/xayah/databackup/data/RestoreProcessRepository.kt#L42-L81)、[原地恢复和收尾](https://github.com/XayahSuSuSu/Android-DataBackup/blob/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac/source-next/app/src/main/java/com/xayah/databackup/service/restore/RestoreInternalDataHelper.kt#L44-L113)。其 README 的完整性宣传不作为本项目验收结论。

## Miuix 与 Android

Root IPC 另核对 [libsu 6.0.0 / 8c3e80f](https://github.com/topjohnwu/libsu/tree/8c3e80ffe466b89ff875471e542d77e6a1b480c5)。[RootService 生命周期](https://github.com/topjohnwu/libsu/blob/8c3e80ffe466b89ff875471e542d77e6a1b480c5/service/src/main/java/com/topjohnwu/superuser/ipc/RootService.java#L41-L94)明确默认/daemon 区别及跨用户行为；[服务端更新处理](https://github.com/topjohnwu/libsu/blob/8c3e80ffe466b89ff875471e542d77e6a1b480c5/service/src/main/java/com/topjohnwu/superuser/internal/RootServiceServer.java#L298-L338)说明不能依赖回调完成所有退出清理。采用 daemon 是本设计的选择，不是照搬 DataBackup 当前默认绑定方式。

- [Miuix v0.9.4](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4)，固定 `39c40f99844227b853f0049a0933b1f3ae6c00ba`。[组件文档](https://github.com/compose-miuix-ui/miuix/tree/39c40f99844227b853f0049a0933b1f3ae6c00ba/docs/components)用于控件选型；源码/API 参数需要实现时针对固定版本再次核对。
- [Miuix 导航边界](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/docs/guide/miuix-nav.md)：导航外观和返回栈是不同职责，不能推断未提供的跨页结果 API。
- [模糊能力](https://github.com/compose-miuix-ui/miuix/blob/39c40f99844227b853f0049a0933b1f3ae6c00ba/docs/guide/blur.md)：Android 支持条件不覆盖最低 API 28；首版视觉不依赖该模块。
- [dataSync FGS 类型](https://developer.android.com/develop/background-work/services/fgs/service-types#data-sync)、[FGS 超时](https://developer.android.com/develop/background-work/services/fgs/timeout)：备份恢复可以归入该类型，但存在版本/target 对应的生命周期限制。
- [长 Worker 限制](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)、[用户停止应用](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)：WorkManager 或通知均不能替代中断对账。

## 实机视觉观察

经用户要求，用 ADB 观察 DataBackup 2.0.12 的首页、应用选择、设置确认、恢复入口、备份应用详情，以及 KernelSU v3.3.1 的首页、Superuser、设置。KernelSU 设置明确显示 UI Style=Miuix。

这些观察仅验证页面结构和当时 UI，不验证本设计的组件实现或备份能力。未点击执行备份/恢复/删除或修改 Root 权限开关。原始截图留在工作资料目录，最终设计包只含虚构样例的概念图。

## r2 补充核查：完整业务与长期更新

完整源码盘点补充了 README 主功能表未充分列出的能力：SMS/MMS/通话记录、Google Maps Timeline、thanox/HMA-OSS 特殊路径、低 target SDK 安装选项、仅恢复未安装应用、完成后远程关机、亮屏伪装、低电量策略和特定清理/保留行为。逐项源位置见 [UPSTREAM-PARITY.md](UPSTREAM-PARITY.md)。

- [菜单与 commands_data](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L40528-L40677)：真实分发必须与菜单变化一起核查。菜单 ID 按行号递增，不是稳定业务 API。
- [Timeline](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L21308-L21600) 与 [communications](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L21666-L21876)：特殊格式、账号/合并与保留不能由普通应用备份代替。
- [原生目录扫描](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/rust/src/restore_catalog.rs#L13-L29)：不能递归识别 r1 的 App 多层专用容器，故 r2 撤销该容器作为正式格式。
- [配置同步](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L2704-L2731)：字段、顺序、注释、旧值和废弃键按源规则处理；不是仅添加缺项。
- [原始工具更新](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/tools/tools.sh#L22705-L22802) 会裁剪/替换工具及包装入口；[applist_web APK 安装](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/rust/src/applist_web.rs#L882-L893) 及 Dex AppListReturn 的原包身份需要宿主适配，不能把原始更新包直接覆盖进 HyperBackup。
- [versions.properties](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/versions.properties)、[仓库检查](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/ci/check_repository.py#L133-L185)、source-package.json、settings-options/labels 和 capabilities 共同作为变更检测来源，任何单一文件都不足以证明能力覆盖。

仍未证实独立联系人导入导出、用户定时备份、通用归档口令加密、块级去重、任意断电续传或整批回滚；不能从同名权限、内部 timer、会话加密或清单去重推导这些产品能力。

## 来源与分发（许可）

两个参考仓库均带 GPL-3.0 许可证；backup_script 的 Rust 配置标注 GPL-3.0-only。复用时保留原作者、许可证和来源，记录固定 SHA、修改与第三方组件；分发前整理对应源码、构建方式及必要 notice。相关原文：[backup_script LICENSE](https://github.com/YAWAsau/backup_script/blob/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5/LICENSE)、[DataBackup LICENSE](https://github.com/XayahSuSuSu/Android-DataBackup/blob/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac/LICENSE)。
