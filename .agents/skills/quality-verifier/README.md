# 🧪 Quality-Verifier (质量验证与小黄鸭排错专家)

> **技能定位**：负责在编码前根据 DLD 契约自动生成测试用例文档与自动化脚本 (TDD)，在编码后执行契约碰撞测试、基础设施环境预检与秘钥脱敏扫描，并在运行/编译报错时激活小黄鸭探针（日志抓取 ➔ 根因分析 ➔ 最小化验证）进行精准排错。

---

## 📂 目录结构与资源索引

- **[SKILL.md](./SKILL.md)**：技能核心定义文件（包含 TDD 规则、自动化碰撞流程、小黄鸭探针4步排错协议）。
- **`references/`**：
  - **[preflight-and-migration-guide.md](./references/preflight-and-migration-guide.md)**：环境基础设施探针预检 (DB/Port/Docker) 与可逆 DB Migration (Flyway/Liquibase) 规范。
  - **[secret-guard-checklist.md](./references/secret-guard-checklist.md)**：预提交秘钥脱敏与安全扫描规范（6 类敏感秘钥正则与环境变量占位符）。
  - **[test-case-template.md](./references/test-case-template.md)**：测试用例文档 (`docs/test-cases/`) 标准写作模板。
  - **[rubber-duck-protocol.md](./references/rubber-duck-protocol.md)**：小黄鸭探针日志排错与根因分析避坑指南。
- **`scripts/`**：
  - **`generate_test.py`**：基于 DLD 契约自动生成工程 `scripts/tests/test_<module_name>.py` Python 测试脚本。
  - **`run_test.py`**：自动化契约碰撞测试运行脚本（失败时抓取 Traceback 喂给小黄鸭探针）。

---

## 🚀 典型使用场景

1. **TDD 测试先行**：在编码前根据 DLD 设计说明书生成 `docs/test-cases/` 用例文档与自动化测试套件。
2. **环境预检与契约碰撞**：在编码完成后，校验数据库/服务连通性，自动执行 `run_test.py` 进行真实 HTTP / DB 契约碰撞。
3. **预提交秘钥脱敏**：扫描代码与配置文件，防止明文数据库密码、API Key、AK/SK 被硬编码提交。
4. **小黄鸭探针精准排错**：报错时抓取完整 Log 堆栈，进行橡皮鸭根因推导与外科手术式修复，杜绝未经分析凭感觉瞎改代码。
