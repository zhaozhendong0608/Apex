# 🧩 第二层：大模块矩阵索引表 (tier2_modules.md)

> 🏛️ **三层记忆金字塔 · 第二层：宏观业务大模块总览**
>
> 📌 **本文件是「派生视图」，不是手写资产。**
> 下方模块表由 `sync_tree.py` 扫描 `docs/modules/` 等目录**实时生成**（`python3 .ai/scripts/sync_tree.py`）。
> 手工新增模块时**不要改这张表**——把 PRD 放进 `docs/modules/<module>/` 即可自动出现。

---

## 📊 已注册模块（自动生成）

<!-- AUTO-MODULE-INDEX:START -->
| 模块 ID | 标题 | PRD | 齐备度 | 已具备资产 |
| :--- | :--- | :--- | :---: | :--- |
| `auth` | 用户登录与权限控制 | [prd-auth-v1.md](../docs/modules/auth/prd-auth-v1.md) | ⚪ 1/6 | PRD |
| `network_sim` | 网络拓扑仿真系统 (Module Network S… | [prd-network-sim-v1.0.md](../docs/modules/network_sim/prd-network-sim-v1.0.md) | 🟡 3/6 | PRD · 原型 · DLD |
| `order` | 订单创建与支付流程 | [prd-order-v1.md](../docs/modules/order/prd-order-v1.md) | ⚪ 1/6 | PRD |
| `req-management` | 业务模块 PRD 需求与版本全生命周期治理模块 | [prd-req-management-v1.0.md](../docs/modules/req-management/prd-req-management-v1.0.md) | 🟡 4/6 | PRD · 原型 · TC · Design |
| `sys_platform` | 通用后台管理平台核心模块 | [prd-sys-platform-v1.0.md](../docs/modules/sys_platform/prd-sys-platform-v1.0.md) | 🟡 4/6 | PRD · 原型 · DLD · TC |

> 🤖 共 5 个模块，由 `sync_tree.py` 扫描 `docs/modules` 自动汇总。
<!-- AUTO-MODULE-INDEX:END -->

---

## 🧭 一个模块要「齐全」需要哪四件套

新增模块时，在目标工程的 `docs/` 下应同时建立（缺项会在上表「齐备度」列显示为 🟡）：

| 资产 | 路径 | 由哪个阶段产出 |
| :--- | :--- | :--- |
| 模块 PRD | `docs/modules/<m>/prd-<m>-v1.md` | `[1]` PD |
| 交互原型 | `docs/modules/<m>/mockup.html` | `[1+]`（纯后端可豁免） |
| 研发规格 | `docs/srs/<m>/srs.md` | `[1]` SRS 轨 |
| 测试用例矩阵 | `docs/test-cases/<m>-testcase.md` | `[1]` TC 轨 |
| 详细技术设计 | `docs/design/<m>/detail.md` | `[2]` TD |

> 🔗 **链接基准**：本文件位于 `.ai/`，因此真实工程文档链接写作 `../base_template/docs/...`；
> 所有本地链接由 `gate.py --meta` 机器校验，悬空即阻断。
>
> 🚨 **注册纪律**：列表只呈现**真实存在 PRD 的模块**，不填 DEMO 占位链接。
