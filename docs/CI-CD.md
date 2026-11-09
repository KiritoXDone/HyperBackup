# CI/CD

每次分支推送、PR 和手动 Check 运行：

- 固定 Rust/NDK 构建原始引擎宿主，验证来源 SHA、补丁指纹和载荷摘要。
- 引擎桥接与发布规则测试；任务幂等、未知结果、中断、归档与协议契约测试。
- 真实 loopback WebDAV 和 Samba 仓库读写。
- Android 28 / 36 模拟器：四入口、首页状态卡隔离、缺少位置弹窗、主题选项、关于页及大字体/暗色场景。模拟器不运行 arm64 Root 业务引擎。
- AvdLite Android 17 / API 37.2 / x86_64：固定 KernelSU 镜像摘要，冷启动与重启后检查 Root、SELinux Enforcing，执行固定上游 CCUtil Dex 自检及相同 UI 场景；保存镜像 manifest、日志和结果。
- debug / release 均开启 R8 和资源压缩，构建 APK、lint，保存报告与 mapping。
- `required-checks` 只有全部工作成功才通过；失败、取消和跳过均不能放行。

`Release` 复用完整 Check。推送 `v<versionName>` 标签后校验唯一 changelog 版本段、签名证书、APK 包名/版本/ABI、引擎来源，再一次性发布 APK、SHA-256、provenance 与说明。手动执行只生成已签名产物，不创建 Release。

签名使用仓库 secrets `HYPERBACKUP_KEYSTORE_BASE64` 和 `HYPERBACKUP_KEYSTORE_PASSWORD`，alias 为 `hyperbackup`；证书公钥摘要固定在 `ci/signing-cert.sha256`。私钥只写入 runner 临时目录，并在 always 收尾中删除；PR 检查不使用签名 secrets。

`ci/coverage.json` 对应上游能力矩阵，新增能力或未映射 operation 会使检查失败。没有运行证据的能力保持 pending。正式版本标签（无 prerelease 后缀）在所有能力 verified 且证据存在前禁止发布；预览版本不能代表完整业务验收。

真实 Root 与跨工具恢复通过明确启用的 `RootFixtureTest` 验证，只允许专用 fixture 应用和 `HyperBackupFixture` 目录。默认云端测试不运行个人设备 Root 操作。

AvdLite 来源固定在 `ci/avdlite.lock.json`，更新需审查版本与摘要，不自动追随 latest。其 shell Root 检查不等于应用 UID 的 Root 授权或业务恢复通过。固定上游的 speednative、busybox、tar、find、zstd、smbclient 和 keycheck 为 arm64；当前 x86_64 镜像不能直接执行这些载荷。备份/恢复、源脚本双向互操作和故障注入仍需要 arm64 Root 环境；OEM 分身、系统数据、Keystore 与特殊应用兼容还需对应实机。不能用跨架构替代工具或模拟返回值解除正式发布门禁。
