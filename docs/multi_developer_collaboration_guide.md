# 👥 团队多人协作与 Git 分支流转最佳实践指南

> **适用范围**：多个研发成员基于同一个大需求（REQ-XXX）进行不同切片/模块并行开发。
> **核心原则**：**宏观切片冻结契约，微观便利贴自主隔离；两段式门禁，主干自动化汇聚。**

---

## 🚀 研发三步流转标准动作

### 第一步：大需求拆解与切片领取
1. 架构师或 Lead 统筹宏观大需求（如 `REQ-001`），完成系统分层与 API 接口契约（DLD）锁死。
2. 为团队各成员分配各自的业务切片模块名（如 `ORDER`、`PAY`、`REQ_MGMT`）。
3. 研发检出各自的独立分支：
   ```bash
   git checkout -b feature/req-001-order
   ```

---

### 第二步：分支内微观任务自主拆解与开发闭环
每个研发人员在自己的分支上，使用智能发号命令创建原子小任务，**系统会自动提取你的 Git 用户名进行命名空间隔离**：

```bash
# 智能生成任务 ID: Task-<MODULE>-<GIT_USER>-<序号> (如: Task-ORDER-updong-01)
python3 .ai/scripts/board.py new ORDER "开发订单支付回调接口" "白话验收目标描述" src/OrderController.java
```

开发完成并编写单元测试后，在自己的分支内完成原子归档：
```bash
# 1. 锁定焦点并开工
python3 .ai/scripts/board.py start Task-ORDER-updong-01

# 2. 跑通个人切片级验证
python3 .ai/scripts/board.py verify Task-ORDER-updong-01

# 3. 终验放行与黑匣子归档
python3 .ai/scripts/board.py complete Task-ORDER-updong-01 --accept
```

---

### 第三步：合并回主干与流水线工序推进 (Advance)
当团队成员分别完成各自的切片功能并提 PR 合并回主干后：

1. **主干一键自愈与大图刷新**：
   ```bash
   # 刷新大模块矩阵索引表
   python3 .ai/scripts/sync_tree.py

   # 刷新第一层极简工作区名片
   python3 .ai/scripts/board.py rebuild-tier1
   ```

2. **全局门禁集成测试**：
   运行全局全量回归门禁：
   ```bash
   python3 .ai/scripts/gate.py --staged
   ```

3. **推进 MySQL 数据库大需求工序状态**：
   当全局门禁通过后，一键推进大需求（如进入 `[SOP-07] 双向验收` 或 `[SOP-09] 发布`）：
   ```bash
   python3 .ai/scripts/board.py sync-node SOP-07 COMPLETED "全切片合并完成"
   ```
