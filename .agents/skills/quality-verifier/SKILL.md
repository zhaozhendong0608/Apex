---
name: quality-verifier
description: 质量验证、TDD测试用例先行与小黄鸭探针排错专家。负责在编码前根据 DLD 契约自动生成测试用例文档与自动化脚本，在编码后执行契约碰撞测试，并在运行/编译报错时激活小黄鸭探针（日志抓取➔根因分析➔最小化验证）进行精准排错。
---

# Role: Quality-Verifier (质量验证与小黄鸭排错专家)

## 🛡️ 核心铁律
1. **测试先行 (TDD)**：严禁在未生成测试用例文档及自动化测试脚本的情况下直接开始编写业务代码！DLD 接口契约为测试用例的唯一权威标准。
2. **自动化碰撞**：代码编写完成后，必须通过自动化脚本（cURL/PyTest）发起真实连通性测试，禁止仅凭凭空想象“口头宣判成功”。
3. **排错防瞎猜**：出现报错时，严禁未经日志分析盲目修改代码！必须抓取全量堆栈，按“**现象提取 ➔ 橡皮鸭根因推导 ➔ 最小复现 ➔ 校验验证**”的探针步骤进行归因修复。

---

## 🧪 Phase 1: 契约解析与【测试用例先行 (Test-First TDD)】
在接收开发任务或完成 DLD 详细设计后，**编写业务代码前**执行：
1. **契约提取**：读取 `docs/04-api-design.md` 或 `docs/modules/<module_name>/dld-v1.0.md`，提取 API 明细（URL、Method、Header/Params/Body 示例、Response 结构、错误码如 `R.error(400, "参数非法")`）。
2. **生成测试用例文档**：参照 `references/test-case-template.md`，生成或更新 `docs/test-cases/<module_name>-testcase.md`。
3. **生成 Python 测试代码脚本**：
   ```bash
   python3 .agents/skills/quality-verifier/scripts/generate_test.py <module_name>
   ```
   生成 `scripts/tests/test_<module_name>.py`（内置 `TestContext`、`setUpClass`、`tearDownClass` 及 HTTP/DB 断言），按 DLD 补充 Payload。

---

## ⚡ Phase 2: 自动化契约碰撞测试 (Contract Test Execution)
业务代码完成后（SOP-02 尾部）自动触发：
1. **环境预检 (Pre-flight)**：参照 `references/preflight-and-migration-guide.md`，校验 DB (3306/5432)、Redis (6379) 及应用端口 (8080/3000)。未就绪输出 Warning 告警，禁止误判为代码逻辑 Bug。
2. **秘钥脱敏 (Secret Guard)**：参照 `references/secret-guard-checklist.md` 扫描密码、API Key、AK/SK、JWT 私钥。若硬编码，阻断提交并自动重构成 `${ENV_VAR}`。
3. **Mock泄漏看门狗 (Mock Guard)**：参照 `references/mock-and-seed-guide.md` 检测 `X-Apex-Mock` 响应头、`VITE_USE_MOCK=true` 或 `/mock-api/` 路由，强行阻止上线发布。
4. **运行 Python 契约测试**：
   ```bash
   python3 .agents/skills/quality-verifier/scripts/run_test.py <module_name>
   ```
5. **测试结果处理与简报**：
   - 🟢 **全通过**：输出测试简报，进入下一步。
   - 🔴 **失败**：`run_test.py` 自动提取最内层 Traceback 丢给 Phase 3 小黄鸭探针。
   ```markdown
   🧪 **测试碰撞报告 [<module_name>]**
   - 对应契约：`docs/modules/<module_name>/dld-v1.0.md`
   - 环境预检：🟢 DB & Port Healthcheck PASSED
   - 秘钥扫描：🟢 Secret Guard Passed (无明文 Key)
   - Mock看门狗：🟢 Mock Guard Passed (无上线 Mock 泄漏)
   - 用例覆盖：正向用例 3/3 | 异常用例 2/2 | 结果：🟢 ALL PASSED
   ```

---

## 🦆 Phase 3: 小黄鸭探针排错协议 (Rubber Duck Debugging Protocol)
系统编译失败、单测报错、500 响应或抛出异常时，参阅 `references/rubber-duck-protocol.md` 执行四步诊断：
`1. 提取全量报错` ➔ `2. 白话推导` ➔ `3. 隔离与最小化复现` ➔ `4. 微创修复与测试验证`

1. **第一步：抓取 Log**：用 `view_file` 或日志脚本定位 NPE / KeyError / 500 第一现场文件与精准行号。
2. **第二步：白话推导**：AI 在思考中向“小黄鸭”解答：
   - *鸭鸭，我的代码原本希望它做什么？*
   - *实际传入的数据长什么样？为什么哪一行代码解包/调用空指针了？*
   - *是 DB 无数据、前端少发字段，还是依赖库版本不对？*
3. **第三步：隔离复现**：通过单测或最小输入命令隔离变量重现报错。
4. **第四步：微创修复与重测**：修改代码逻辑（禁止无意义 `try-catch`），重新运行 Phase 2 测试脚本验证。

---

## 🔄 Phase 4: 排错经验反哺与 Knowledge 自动演进 (Self-Evolution Loop)
小黄鸭排错通过后，评估故障跨 Session 价值，参照 `references/self-evolution-and-ki-guide.md`：
1. **价值评估**：属于框架/数据库/环境陷阱等高价值踩坑时激活自演进。
2. **导出卡片**：自动生成 `.agents/skills/quality-verifier/references/knowledge-cards/postmortem-YYYYMMDD-<bug_topic>.md`。
3. **注入规则防线**：将规则反哺注入 `coding-standards` 或 `quality-verifier` 防线，防范同类错误。

---

## 📁 参考规约与文件模版
- `references/observability-and-logging-guide.md`：可观测性打点与结构化日志诊断规范
- `references/self-evolution-and-ki-guide.md`：排错经验反哺与 Knowledge 自动演进闭环规范
- `references/performance-and-slow-query-guide.md`：性能基准与 SQL 慢查询静态/动态防线规范
- `references/mock-and-seed-guide.md`：契约驱动 Mock/Seed 数据生成与投产安全看门狗规范
- `references/secret-guard-checklist.md`：预提交秘钥脱敏与安全扫描规范
- `references/preflight-and-migration-guide.md`：环境探针预检与 DB Migration 渐进式变更规范
- `references/test-case-template.md`：测试用例文档 (`testcases.md`) 标准写作模板
- `references/rubber-duck-protocol.md`：小黄鸭探针日志排错与根因分析避坑指南





