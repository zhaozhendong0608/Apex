<!-- 【三层记忆金字塔 - 第一层：极简快照名片 (<30行)】 -->
# ⚡ tier1_snapshot.md · 极简工作区名片

- **项目空间**: NET-SIM (全景工业网络仿真与拓扑排版系统)
- **当前聚焦需求**: REQ-002
<!-- TIER1-ACTIVE:START -->
- **当前焦点**：无（没有 ACTIVE 任务）
- **下一待办**：暂无 —— 需回复 `[1]` 规划新需求
<!-- TIER1-ACTIVE:END -->
- **当前工位**: [SOP-01] (模式: STANDARD)
- **责任操作人**: admin
- **单一事实源 (SSOT)**: MySQL `t_req_requirement` / `t_req_workflow_record`
- **最后同步时间**: 2026-11-18T19:08:58.439103

## 🎯 核心质量与安全护栏
1. 单次改动 ≤ 3 文件 & 零野公网依赖
2. 保持真实数据源，必须通过 gate.py 机器门禁
3. 唯一归档出口: board.py verify ➔ complete --accept
