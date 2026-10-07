# ⚡ 技术原型 (PoC) 沙盒分析框架 (poc-framework.md)

## 📌 1. 技术 PoC 决策树 (Decision Matrix)

```mermaid
graph TD
    Start["收到业务场景需求"] --> Check{"是否包含技术迷雾？<br/>(第三方SDK/复杂算法/高并发)"}
    Check -- "否 (简单CRUD)" --> Skip["🟢 自动 Skip 跳过 PoC<br/>直接进入 DLD 详细设计"]
    Check -- "是 (有技术未知)" --> Active["🔴 激活 poc-tech-prototype"]
    Active --> Grill["💬 抛出 A/B 方案供用户选择确认"]
    Grill --> Spike["🧪 在 scratch/poc_sandbox/ 编写测试 Spike"]
    Spike --> Report["📄 导出 poc-report.md 契约依据"]
    Report --> DLD["📐 进入 design-spec-architect 生成 DLD"]
```

## 📌 2. PoC 报告模板结构 (`poc-report.md`)
1. **测试目标与背景**
2. **核心技术选型结论 (方案 A/B 决策)**
3. **真实调用入参/出参 JSON Payload**
4. **性能/并发边界数据**
5. **合入 DLD 的核心建议**
