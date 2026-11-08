# 领域模型、备份格式与发布规则

状态：2026-11-08 已确认的 v1 格式设计，实施前需通过格式验证。新格式不承诺被原 backup_script 或 DataBackup 读取；内部可复用 tar / zstd 和上游 AppState 载荷。以下 hash、提交标记、仓库身份和多批次索引均为新增能力。

## 模型

| 模型 | 身份与关键内容 |
| --- | --- |
| DeviceOrigin | 随机来源 ID、用户可读设备标签、Android 版本；不使用硬件标识充当可移植主键 |
| AndroidUser | 当前设备用户 ID、类型、名称、解锁/可访问状态；不同设备的相同数字不代表同一用户 |
| AppInstance | 当前设备上的 `(userId, packageName)`，安装版本、签名信息、UID、是否允许操作 |
| BackupDestination | 本机配置 ID、协议、地址/路径、凭据引用；不是仓库本身 |
| BackupRepository | repoId、格式版本；同一仓库地址变化不应创建新的备份身份 |
| BackupBatch | batchId、来源设备、开始/结束、用户标签、各条目结果；不是全局同一时刻快照 |
| AppBackup | recordId、batchId、来源用户/包、安装版本、数据组成、归档引用、校验记录 |
| DataPart | APK、CE、DE、EXTERNAL_DATA、OBB、MEDIA、APP_STATE，独立包含状态与结果 |
| OperationPlan | 冻结的操作内容、目的地、目标用户映射、恢复策略、预检摘要和版本 |
| Task / TaskItem | 作业与条目执行记录、事件序号、阶段、核验结果；与备份产物身份分开 |

一次任务可以涉及多个来源用户，但一个恢复计划中每个 `(targetUserId, packageName)` 只允许一个 recordId。用户不能在同一计划中把微信三个版本同时写到同一个目标。跨用户选择需明确显示“来源用户 → 目标用户”，不按数值静默匹配。

## 存储布局

```text
<chosen-root>/
  repository.json
  writer-claim.json
  batches/
    <batch-id>/
      batch.json
      apps/
        <record-id>/
          record.json
          apk.tar.zst
          user.tar.zst
          user_de.tar.zst
          data.tar.zst       # 仅选中且包含时存在
          obb.tar.zst
          media.tar.zst
          app-state.json     # 带上游载荷版本，不改写未知字段含义
          COMMITTED.json
      FINALIZED.json
  trash/
    <deletion-id>/           # 删除账本，不是无限保留的回收站
```

ID 使用随机唯一标识，不以应用显示名称或精确到秒的时间戳做主键。相对路径由写入端生成；包名与用户仅为清单字段。上游可用唯一 Backup_suffix 产生隔离临时产物，随后由适配层发布为 App 容器，不能把上游默认目录直接当不可变历史仓库。

`repository.json` 包含 `format / schemaVersion / repoId / createdAt / writerCompatibility`；陌生根目录不会被自动接管。创建仓库和连接已有仓库是不同动作；不把清单丢失的目录当成空白可覆盖空间。

## AppBackup 清单示意

```json
{
  "schemaVersion": 1,
  "recordId": "record-uuid",
  "batchId": "batch-uuid",
  "origin": {"deviceId": "source-uuid", "userId": 0, "userLabel": "主用户"},
  "app": {"packageName": "org.example.app", "versionCode": "123", "signingCertificateDigests": []},
  "createdAt": "2026-11-08T06:32:00Z",
  "completion": "COMPLETE",
  "engine": {"upstreamCommit": "4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5", "adapterVersion": 1},
  "parts": [
    {"kind": "APK", "status": "INCLUDED", "path": "apk.tar.zst", "compressedBytes": 123456, "sha256": "64-hex-digest"},
    {"kind": "EXTERNAL_DATA", "status": "NOT_SELECTED"}
  ],
  "exclusionPolicy": {"id": "upstream-v1117", "detailsPath": null},
  "verification": {"archiveStructure": "PASSED", "remoteReadback": "NOT_PERFORMED"}
}
```

这是字段示意，不是可以直接验收的 schema 文件；真实 schema 要约束 UUID、hash 长度、枚举、相对路径和整数范围。versionCode 使用可无损表示的类型，不经浮点转换。包名、版本号、来源身份和签名缺失时记为未知，不伪造默认值。

APK 集必须包含 base + 所需 splits 的完整清单、版本/签名一致性及设备兼容元数据。每个数据归档记解包大小/条目数预算和排除清单。缺少可选数据与损坏所选数据必须可区分。

## 备份发布

1. 获得本机修改作业锁和目的地写声明；读取 repoId/格式，再核验目标路径。
2. 固定计划，写批次草稿；每个应用独立捕获并暂存或流式上传，不覆盖其他 recordId。
3. 数据写到唯一 `.partial` 名称。计算压缩归档 SHA-256、字节数与结构信息，保存元数据的 hash；明确定义零字节数据组成。
4. 归档完成后关闭并检查写入结果；有可靠重命名能力时转为最终名。没有该能力时仍以最后的有效提交标记作为可见性依据，不能宣称目录操作原子。
5. 写 record.json，再写 `COMMITTED.json`，包含 record hash 与全部实际 INCLUDED 载荷的文件名/大小/hash。清单必须可完整解析并通过 schema 校验；读者仅展示已提交条目为可选择内容。提交意味着记录已经确定，不代表所有请求内容成功：所选组成有缺失则 `completion=PARTIAL`，失败组成不可选，成功子集仍可按明确范围使用；损坏 split 集不能作为完整 APK 子集恢复。
6. 各项完成后写批次最终汇总和 `FINALIZED.json`。部分成功批次如实保留可用子集，不以最终标记强行宣布所有项目成功。

临时文件不等于备份；提交后记录不可原位修改。修复或重新备份产生新 recordId。网络断开时先检查原 ID 是否已成功提交；发现匹配提交记录可以对账，不重传覆盖。内容不符或结果不明则保留诊断并停止。

## 校验的准确含义

| 证据 | 可显示 | 不可声称 |
| --- | --- | --- |
| 写入端计算了归档 hash，远端大小正确 | 已写入；记录内容摘要，尚未回读 | 远端内容已逐字节验证 |
| 下载后 hash 匹配且结构检查通过 | 归档校验通过 | 目标应用一定正常、一定免登录 |
| 恢复后路径/类型/大小/属主等检查通过 | 所列恢复检查通过 | 每个文件内容、所有 ACL/SELinux 细节均验证过 |
| 只找到合法清单 | 已识别备份 | 全部归档完整可恢复 |

本地归档在完成写入后读取计算摘要；远端备份可边传边计算摘要并校验长度，但若未回读，不显示完整内容已核验。远端恢复必须在临时文件上计算实际 hash，与清单匹配后才写入目标。SHA-256 提供完整性比较，首版没有额外签名体系，不等于能证明备份来源可信。

全库深度校验作为用户手动发起的检查操作，结果记录对象 ID、大小/修改身份、检查时间；对象变化后历史结论失效。正常列表扫描只读清单，不为了显示列表把全部归档下载一遍。

## 恢复前副本

副本也是普通 AppBackup，但批次 `purpose=PRE_RESTORE`，关联 restoreTaskId 和目标应用；不会与用户原有来源备份混淆。默认放在用户确认的可写目的地；原来源只读时必须选另一个可写位置或显式关闭保护，不自动退化。

副本需覆盖实际将被修改的数据组成，不能拿“只备份 APK”作为数据覆盖的保护。对应归档提交失败/状态未知时阻止该项恢复。空间估算包括保护副本占用；保护关闭不作为下次恢复的隐式长期默认。

## 扫描、删除与并发

- Room 保存应用列表、目的地配置、批次索引、Task/TaskItem/EventCursor、检查结果和草稿；重新扫描从清单重建备份索引，不恢复凭据或旧设备 UID。
- 已缓存但目的地不可达的项目显示“上次扫描结果”，不能标为当前在线存在。通过 repoId 合并路径变化；repoId 不一致不能接着对旧对象发删除。
- 删除先展示将删除的明确批次/应用版本和大小，再创建删除计划。运行任务引用的源备份、临时记录和恢复前副本在任务结束前不能删除。
- 删除使用 tombstone/删除账本先使条目从可恢复集合退出，再逐文件清理；失败显示剩余空间和待清理项，避免半个目录看起来仍完整。首版不承诺系统级回收站；最终确认写明不可撤销。
- 写声明使用服务端支持的独占创建/条件写能力；记录 owner/session/boot/task 与心跳。若目的地不能证明单写者，禁止同时修改。首版不支持多设备同时写同一个仓库。
- 超时不等于锁持有者已死亡；锁状态未知时阻止新修改并进入检查流程，不自动抢锁。远端被其他软件修改属于外部变更，提交和恢复时重新校验对象。
- App schema 与上游 metadata schema 分开版本化。遇到未来主版本只读列出“不支持”，禁止写入降级；格式迁移必须另开设计，不隐式改全部历史备份。

## 首个格式验证里程碑

实现一个本地仓库和一个 WebDAV/SMB 测试仓库：写 2 个批次、保留同一应用 2 个版本；中断上传/清单写入不会出现可恢复假条目；重新安装 App 后能重建索引；删除一个版本不会影响另一个；损坏任意所选归档会在覆盖数据之前阻断。通过后才扩展批量性能与界面功能。
