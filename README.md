# HyperBackup

<img src="ui/branding/hyperbackup-orange-v1.png" width="128" alt="HyperBackup 橙白图标设计稿" />

基于 [backup_script](https://github.com/YAWAsau/backup_script) 执行能力设计的 Android 备份恢复应用，使用 Miuix UI，参考 Android-DataBackup 的产品流程。

**当前阶段：设计 v1 已冻结，App 尚未实现。** 本仓库包含架构、功能边界、数据契约、UI 和验收计划。设计基线确认于 2026-11-08；后续改变关键边界时，应更新对应文档与 ADR。

核心方案：**独立 Android APK + Miuix 0.9.4 + libsu daemon RootService + 固定版本 backup_script 适配层**。面向刷机前后普通应用备份恢复；通过新的多批次仓库与结构化任务协议组织已有底层能力。

## 阅读入口

| 文档 | 内容 |
| --- | --- |
| [架构](docs/ARCHITECTURE.md) | 5 模块边界、Root IPC、任务协议、执行所有权、生命周期与升级 |
| [范围与行为](docs/BOUNDARIES.md) | 首版包含/排除、原脚本应用保护与 APK 策略、存储与恢复故障边界 |
| [模型和格式](docs/DATA-FORMAT.md) | 身份、不可变备份版本、提交记录、hash、索引重建与删除 |
| [UI 规范](docs/UI-SPEC.md) | 已确认视觉方向、完整路由、各页行为、状态文案与无障碍 |
| [项目图标](docs/BRANDING.md) | 已确认橙白方案、原始图稿与 Android 图标资源制作边界 |
| [开发与验收](docs/VALIDATION-PLAN.md) | P0–P4、核心案例、故障注入、设备/视觉验证标准 |
| [术语](GLOSSARY.md) | 备份、版本、批次、来源/目标用户、恢复前副本 |
| [证据](docs/SOURCES.md) | 固定 SHA 源码、官方约束与 ADB 观察范围 |

## UI

以 **KernelSU Miuix** 为视觉基准，一级入口为 **首页 / 应用 / 备份库 / 设置**；任务通过首页时钟、最近任务、运行状态与通知进入。

![已确认的首页、应用与备份库方向](ui/concept-v3-approved.png)

![恢复确认、执行任务与深色结果页](ui/restore-task-states.png)

概念图采用内置 imagegen，完整提示词保存在 ui/。实际布局、状态与组件以 UI 规范为准，图片不代表编译或交互验证。

## 已确认的产品边界

1. 核心场景是刷机前备份、刷机后按应用恢复。
2. 没有必须导入的历史备份；允许定义新格式。设计使用本应用仓库容器，内部复用 tar/zstd 等底层归档。
3. 设备支持范围沿用本次核查的 backup_script 基线：Android 9+、arm64、Root；上游声明 Magisk / KernelSU。其他 Root 方案不自动列入已验证支持。
4. 交付可独立安装、桌面启动、在 App 内申请 Root 的 APK，复用并适配 backup_script 引擎。
5. 首版聚焦普通应用的 APK、私有数据和可选外部数据。系统应用、共享文件夹、Wi-Fi 不属于首版核心范围。
6. 首版存储目的地包含本地目录、SMB、WebDAV。
7. 使用 Miuix 设计与真实组件，参考 Android-DataBackup 的产品流程。
8. 每次备份保留独立批次，支持查看多次备份并手动清理；首版不自动覆盖旧版本或做块级增量去重。
9. 支持多用户，明确选择来源和目标；应用身份不能只按包名区分。
10. 远端恢复逐应用先下载、校验，再写入，完成后清理临时文件；空间不足时阻止，不自动切换串流恢复。
11. 覆盖已安装应用前，默认创建恢复前备份，成功后才继续；用户可以明确关闭。这份副本用于手动恢复，不是自动回滚事务。
12. 首版不提供归档口令加密；UI 明示内容未加密，连接凭据仍需要受到保护。
13. 备份时记录可读取的 SSAID、运行时权限、AppOps 等应用状态；恢复放进高级选项、默认关闭，支持与结果逐项显示。
14. 可明确判定的单应用失败可记录后继续；安装结果不明、Root 丢失、存储故障等使整批停止。异常重启先核对结果，不自动重放恢复。
15. 首版不做定时备份、自动清理、块级增量去重、远端直接串流恢复、系统应用 / Wi-Fi / 共享文件夹、旧格式导入。
16. 运行中应用保护、版本/签名冲突按固定版脚本行为；不凭建议另设一律强停或一律阻断策略。源码确认的细节见边界文档。
17. WebDAV 默认 HTTPS，HTTP 显式可选并标示未加密传输；证书错误不自动忽略。

## 决策树收口

- 产品：刷机恢复 → 普通应用 / 多用户 / 高级应用状态 → 已确认。
- 存储：本地 + SMB/WebDAV → 多批次 / 手动清理 / 无归档加密 → 已确认；具体格式与单写者机制已随整体方案确认。
- 恢复：逐应用暂存校验 → 默认保护副本 / 沿用脚本安装策略 / 不确定时停下 → 已确认。
- UI：KernelSU Miuix → 四主入口 / 二级选择与执行 / 明暗状态 → 已确认。
- 架构：独立 App → Root daemon/AIDL / 版本化桥接 / 持久对账 → 已确认。
- 最后一轮：用户已确认共同理解并冻结 v1；可行性测试是实施门槛，不是已有通过证据。

GitHub 仓库为 `KiritoXDone/HyperBackup`。App 展示名、正式 packageName 与签名密钥在开始工程时确定；UI 的“备份”是暂定标题。正式包名须在自我保护和 daemon 身份实现前锁定。

## 核查基线

| 参考 | 固定版本 | 与本项目的关系 |
| --- | --- | --- |
| backup_script | [v1117 / 4d85ef6](https://github.com/YAWAsau/backup_script/tree/4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5) | 复用备份执行能力，新增独立应用控制边界 |
| Android-DataBackup | [c306d75](https://github.com/XayahSuSuSu/Android-DataBackup/tree/c306d7539a1ceed8ef42b3936dcfa03bb9fdcdac) | 参考交互与职责划分；区分已发布 2.x 与开发中的 source-next |
| Miuix | [v0.9.4 / 39c40f9](https://github.com/compose-miuix-ui/miuix/tree/39c40f99844227b853f0049a0933b1f3ae6c00ba) | UI 设计与组件核对基线 |
| libsu | [6.0.0 / 8c3e80f](https://github.com/topjohnwu/libsu/tree/8c3e80ffe466b89ff875471e542d77e6a1b480c5) | daemon RootService、AIDL 控制与重连 |

上述为源码核查，不代表已完成编译、设备测试或恢复成功验证。上游已有 Miuix 原生 App，但需要从 Root 终端启动，现有菜单会话协议也不是稳定的独立备份作业 API。

## 架构决策记录

- [ADR 0001：独立应用与引擎边界](docs/adr/0001-independent-app-engine-boundary.md)
- [ADR 0002：独立备份批次](docs/adr/0002-immutable-backup-batches.md)
- [ADR 0003：恢复前校验和保护副本](docs/adr/0003-staged-restore-and-safety-copy.md)
- [ADR 0004：沿用脚本应用保护和安装策略](docs/adr/0004-inherit-engine-app-policies.md)
- [ADR 0005：Root daemon 与对账](docs/adr/0005-root-daemon-and-reconciliation.md)

最重要的新增工程工作是非交互桥接、独立 APK 引擎生命周期、新格式清单与提交、逐归档 hash 和故障对账；这些不只是 UI 换肤。编译/实机尚未验证，已确认的产品决定不等于这些能力已实现。
