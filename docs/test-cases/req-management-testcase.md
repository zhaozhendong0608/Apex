# 🧪 TC-REQ-MANAGEMENT-V1.0: 需求与版本全生命周期治理模块测试说明书

> 基于 [prd-req-management-v1.0.md](../modules/req-management/prd-req-management-v1.0.md) 与 [detail.md](../design/req-management/detail.md) 契约在编码前 (Test-First TDD) 制作。

---

## 📌 1. 测试基础信息与契约基线

- **测试模块**：`req-management` (需求与版本管理平台核心域)
- **关联设计施工图**：[detail.md](../design/req-management/detail.md)
- **核心验证接口清单**：
  - `POST /api/v1/req/requirements` - [录入需求与客户原话]
  - `PUT /api/v1/req/requirements/{id}/confirm-items` - [保存四层清单与实时代价折算]
  - `POST /api/v1/req/prototypes/upload` - [上传原型ZIP并固化全目录SHA256快照]
  - `POST /api/v1/req/baselines` - [创建待签基线并派发一次性签名Token外链]
  - `GET /api/v1/public/sign/{token}` - [客户免登短链核验与只读沙盒预览]
  - `POST /api/v1/public/sign/{token}/confirm` - [客户一句话复述与电子签署终审]
  - `POST /api/v1/public/sign/{token}/reject` - [客户提出异议分歧并驳回至澄清态]
  - `GET /api/v1/req/baselines/{id}/diff` - [基线增量三合一智能差分比对]

---

## 📋 2. 测试用例详细矩阵 (Test Cases Matrix)

### 🟢 2.1 正向端到端链路用例 (Happy Path Cases)

| 用例 ID | 所属功能 | 接口及 Method | 测试场景与描述 | 输入参数 | 预期 HTTP 响应 | 数据库副作用断言 (DB Assert) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-REQ-HP-001` | 需求录入 | `POST /api/v1/req/requirements` | 录入需求标题与逐字客户原话 | `{"projectId": 1, "reqCode": "REQ-001", "title": "设备在线状态看板", "rawQuote": "我想在页面看到交换机是否在线", "ownerName": "李工"}` | `HTTP 200` <br/> `code: 200` <br/> 返回自增 `id` | `SELECT raw_quote FROM t_req_requirement WHERE req_code='REQ-001'` 与入参一致 |
| `TC-REQ-HP-002` | 清单决策 | `PUT /api/v1/req/requirements/1/confirm-items` | 批量录入四层清单条目并计算代价 | `{"items": [{"itemCode": "R-01", "category": "SCOPE", "decision": "CURRENT", "costDays": 2.0}, {"itemCode": "N-01", "category": "SKIP", "decision": "SKIP", "costDays": 0.0}]}` | `HTTP 200` <br/> `code: 200` <br/> 返回总代价 2.0 人天 | `SELECT SUM(cost_days) FROM t_req_confirm_item WHERE requirement_id=1 == 2.0` |
| `TC-REQ-HP-003` | 免登签署 | `POST /api/v1/public/sign/{valid_token}/confirm` | 客户凭有效 Token 输入 4 位码与一句话复述完成签字 | `{"accessCode": "7892", "signerName": "王工", "restatementText": "做在线与告警，不做历史曲线", "agreeDisclaimer": true}` | `HTTP 200` <br/> `code: 200` <br/> 返回防伪 `receipt_code` | `SELECT status, restatement_text FROM t_req_approval WHERE token='...'` 状态为 `SIGNED` |
| `TC-REQ-HP-004` | 基线冻结 | (三方全签自动触发) | 三方签字集齐后系统物理冻结并生成 Git Tag | (三方均已签署触发) | 系统生成 `manifest.json` 与 `REQ-001-v3.zip` | `t_req_baseline.status='BASELINED'`, `frozen_at` 非空, `git_tag` 成功打标 |

---

### 🟡 2.2 边界与异常防范测试用例 (Boundary & Exception Cases)

| 用例 ID | 所属功能 | 接口及 Method | 测试场景与描述 | 输入参数 | 预期响应 status & code | 预期错误提示 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-REQ-EX-001` | 签名防伪 | `GET /api/v1/public/sign/{expired_token}` | 访问已超过 7 天有效期的签名链接 | Token 对应的 `token_expires_at < NOW()` | `HTTP 400` <br/> `code: 400101` | "签名链接已失效或过期，请联系产品经理重新派发" |
| `TC-REQ-EX-002` | 访问码防窥 | `POST /api/v1/public/sign/{token}/confirm` | 提交错误的 4 位访问验证码 | `{"accessCode": "0000", ...}` | `HTTP 400` <br/> `code: 400102` | "访问验证码错误" |
| `TC-REQ-EX-003` | 原型安全校验 | `POST /api/v1/req/prototypes/upload` | 上传缺失 `mockup.html` 的非法原型 ZIP | ZIP 根目录下无 `mockup.html` | `HTTP 400` <br/> `code: 400202` | "原型压缩包必须包含根级 mockup.html 入口文件" |

---

### 🔴 2.3 防扯皮与业务红线安全测试用例 (Gatekeeper Security Cases)

| 用例 ID | 业务硬规则场景 | 测试触发条件 | 预期系统安全防范表现 |
| :--- | :--- | :--- | :--- |
| `TC-REQ-SEC-001` | 蓝皮书 §4.1: "本期不做"强制收敛门禁 (≥3条) | 试图在“本期不做”分类少于 3 条时申请冻结基线 (`confirm_items` 中 SKIP 条目只有 2 条) | 接口拒绝冻结操作，返回 `HTTP 400` / `code: 400201`，提示：“基线范围未收敛：'本期不做'事项必须≥3条，防止范围无限蔓延” |
| `TC-REQ-SEC-002` | 蓝皮书 §4.1: 四层清单严禁未定条目 | 清单中尚有条目 `decision='UNDECIDED'` 时尝试申请基线 | 拦截拒绝，提示：“存在未决定的清单条目，严禁提交基线” |
| `TC-REQ-SEC-003` | 基线物理不可变铁律 | 对状态已为 `BASELINED` 的基线或对应清单执行 `UPDATE` 或 `DELETE` | 数据库触发器或服务层严格拦截，抛出 `400301`：“已冻结基线禁止任何物理篡改，改动必须发起新基线差分” |
| `TC-REQ-SEC-004` | 客户逆向驳回闭环 | 客户点击驳回按钮提交异议分歧说明 | 需求状态自动回退为 `CLARIFYING`，基线退回 `DRAFT`，留存 `reject_reason` 异议记录并向产品经理派发协同待办 |
