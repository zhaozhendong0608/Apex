# 📦 项目交接与架构变更卡片 (handover-20261030-req_management.md)

## 📌 1. 本次会话核心成果与关键决策
- **需求治理平台全链路动态闭环与 Mock 数据物理清空**：
  - 彻底清除了 `ReqManagementView.vue` 前端在工作台、原型管理（P08）、基线管理（P10）、发版流水线（P16）及客户签署（E02/E03）上的所有静态假数据及兜底回退。
  - 前端各子页面全面直连 MySQL 真实数据（`t_req_project`, `t_req_requirement`, `t_req_confirm_item`, `t_req_prototype_version`, `t_req_baseline`, `t_req_approval`），当数据为空时统一呈现标准化防呆空状态引导。
- **项目级联物理粉碎删除闭环**：
  - 后端新增 `DELETE /api/v1/req/projects/{id}` 接口，实现自底向上级联删除物理粉碎清除（覆盖审批人、基线、原型版本、四层清单条目、需求、项目）。
  - 前端工作台新增删除项目操作，并做安全校验拦截（若存在冻结基线，提示须先特权解冻或清退）。
- **管理员特权解冻功能**：
  - 后端新增 `POST /api/v1/req/requirements/{id}/unfreeze` 接口，解决基线已冻结（400301）无法修改四层清单问题。
  - 前端详情页增加「🔓 管理员特权解冻」按钮，解冻后状态恢复为 `CLARIFYING` 并重新开放清单编辑与代价计算。
- **MySQL 字符集与高保真种子数据规范化**：
  - 修复了命令行乱码导致数据库中文字符损坏的问题，使用 `--default-character-set=utf8mb4` 重建规范种子数据。
  - 验证了所有接口（项目、需求、四层清单、代价引擎、门禁核验、原型版本、基线、差分、客户签署与存证）真实连通。
- **前端生产编译验证**：
  - 修复未引用响应式变量 TS 警告，执行 `npm run build` 验证通过（`vue-tsc && vite build`，0 errors）。

---

## 🛠️ 2. 本次改动核心文件清单
1. **前端交互与视图**：
   - `base_template/frontend/src/api/req.ts` (增补 unfreezeRequirementApi, getPrototypeListApi, getBaselineListApi)
   - `base_template/frontend/src/modules/req-management/views/ReqManagementView.vue` (数据直连、Mock 清除、响应式闭环、空状态防呆)
2. **后端需求治理模块**：
   - `base_template/backend/platform-server/src/main/java/com/apex/req/controller/ProjectController.java` (级联物理粉碎删除)
   - `base_template/backend/platform-server/src/main/java/com/apex/req/service/ProjectService.java`
   - `base_template/backend/platform-server/src/main/java/com/apex/req/controller/RequirementController.java` (特权解冻)
   - `base_template/backend/platform-server/src/main/java/com/apex/req/service/RequirementService.java`
3. **数据库脚本**：
   - `docs/db/service.sql` (标准 utf8mb4 表结构与种子数据)

---

## 💡 3. 关键架构决策与踩坑预防 (KI)
1. **绝不使用假数据兜底**：前端 `ref` 初始数据禁止放置假数组/占位对象，必须初始化为空数组/空状态；所有字段由接口驱动，接口为空时渲染友好空状态，防止前端产生未删除干净的幻觉。
2. **字符集规范**：与 Docker MySQL 通信执行数据导入或更新时，必须显式指定 `--default-character-set=utf8mb4`，避免 CLI 管道使用 latin1 编码导致汉字乱码。
3. **基线不可篡改与解冻特权分离**：严格遵循蓝皮书规范，一旦基线冻结（`BASELINED`），常规 API 强行阻断清单物理修改；唯有具备管理员权限显式调用 `/unfreeze` 解冻后方可回退编辑。

---

## 🚀 4. 下一步工作建议
- 需求全生命周期治理平台的 17 个子页面、双重视角（内部协同 vs 客户免登签署）、四层清单、原型托管与 Git 物理锚点存证链路已完整打通真实数据库。
- 下一会话可直接发送 `6` 或 `继续` 启动后续流程。
