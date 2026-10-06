# 🏛️ Legacy-Archaeologist (老项目逆向考古与代码解密专家)

> **技能定位**：负责在面对缺乏文档、代码庞杂的老项目时，遵循“鸟瞰全局 ➔ 建立索引 ➔ 切片追踪 ➔ 逆向考古”四步法，自顶向下扫描路由与 Controller/Service/DAO 链路切片，逆向提取 API 契约与表结构关系，自动生成 `docs/legacy/legacy_hld_<module>.md`，并编写反向行为锁死探针防止重构改崩旧业务。

---

## 📂 目录结构与资源索引

- **[SKILL.md](./SKILL.md)**：技能核心定义文件（包含四阶段逆向考古流程、切片规约、反向行为锁死探针标准）。
- **`references/`**：
  - **[archeology-checklist.md](./references/archeology-checklist.md)**：老代码逆向考古与契约提取核对清单。
  - **[reverse-probe-template.md](./references/reverse-probe-template.md)**：反向行为锁死探针 (`test_legacy_*.py`) 自动化写作模板。
- **`scripts/`**：
  - **`slice_code.py`**：业务链路切片提取脚本（自动拉取 Controller-Service-DAO 调用链并定位依赖资产）。

---

## 🚀 典型使用场景

1. **老项目破局初始化**：在 `.ai/sop/00-sop-legacy.md` 中被自动激活，扫描老代码前 3 层工程结构，生成架构索引地图 `.ai/tier2_legacy_arch.md`。
2. **遗留逻辑切片分析**：对特定黑盒 Controller/Service 执行 `slice_code.py` 追踪，提纯真实 API 契约与 DB 表关联。
3. **重构前行为锁死**：在修改或重构遗留代码前，编写 Python 反向行为锁死探针 (`test_legacy_<module>.py`)，运行打绿后再进行微创编码，杜绝改崩旧业务。
