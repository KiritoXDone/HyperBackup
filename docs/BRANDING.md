# HyperBackup 项目图标

2026-11-08 选定蓝紫色三层数据堆叠方案，替代橙白存储卡。原始选定稿保存在 [hyperbackup-violet-selected.png](../ui/branding/hyperbackup-violet-selected.png)。

![HyperBackup](../ui/branding/hyperbackup.svg)

- 背景 `#6264D9`，上层和下层白色，中层 `#BEBFF6`；上层三条数据线为镂空。
- Adaptive 前景和背景分层；monochrome 独立使用白色轮廓和镂空线条；legacy 与仓库 SVG 共用同一主体路径。
- 主体居中，完整 108dp 前景画布中的宽度为 39.26dp、高度约 39.52dp，保持原图比例。系统负责 adaptive mask，前景不重复绘制圆角底或外部白画布。
- 尺寸基准为 Miaopu 2.3.8 / 45 的实际 APK 与源码（`a6fdaef02d2ffbc103d55a9f42795977ccc21629`）：白色猫爪主体缩放后约 42.44 × 46.67dp。新图形接近正方形，先按相同主体宽度适配；随后按用户反馈将主体再缩小 7.5%。保持图形比例，不直接照搬其 0.729 缩放系数。

页面配色继续遵循 [UI 规范](UI-SPEC.md)。旧橙白稿仅作为设计历史保留。
