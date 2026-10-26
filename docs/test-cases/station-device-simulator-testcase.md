# 自动化测试用例与验证规约: station-device-simulator

> **测试目标**: 验证独立设备模拟服务的多模块构建、SPI扩展模型、极生API安全规约与波形计算正确性  
> **设计契约**: [dld-station-device-simulator-v1.0.md](docs/design/dld-station-device-simulator-v1.0.md)  
> **创建时间**: 2026-10-22  

---

## 1. 测试用例清单 (Test Cases)

| 用例编号 | 测试模块 | 测试项描述 | 输入条件 / 场景 | 预期断言结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-001** | `station-device-simulator` | Maven 聚合工程 POM 依赖与模块装配 | `mvn clean compile` | 编译成功，所有子模块正确识别版本与内部依赖 | 待验证 |
| **TC-002** | `simulator-metadata-spi` | 测点元数据抽象模型定义 | 实例化 `PointMetadata` (模拟量/开关量) | 字段完整（id, code, name, metricType, strategyType） | 待验证 |
| **TC-003** | `simulator-core` | 一阶惯性滞后温升波形算法 | `val_t = val_{t-1} + 0.03*(target - val_{t-1})` | 数值平滑单调逼近目标值，无跳变断崖，处于物理包络内 | 待验证 |
| **TC-004** | `simulator-core` | 开关量遥信与负荷因果连锁断言 | 触发断路器跳闸 (`breaker=0`) | 负荷瞬时断崖跌落为 0MW，瓦温进入自然降温 | 待验证 |
| **TC-005** | `simulator-sink-jsdb` | 极生驱动 `TDBAPI.insert` 入参契约反编译对齐 | 构造 `JSONArray kids` 与 `JSONArray docs` | 保证单点独立批次，数组维度规避 P-014 缺陷 | 待验证 |
