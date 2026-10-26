# 独立电站设备模拟服务 (Station Device Simulator) 详细设计说明书 (DLD)

---

## 📋 文件修订页 (Revision History)

| 编号 | 章节名称 | 修订内容描述 | 修订日期 | 修订版本 | 修订人 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | 所有章节 | 完成模块、算法、API 契约及规约的详细设计 | 2026-10-22 | V1.0 | AI Agent |

---

## 1 概述

### 1.1 编写目的
细化独立电站设备模拟服务（`station-device-simulator`）的代码目录架构、类层次职责、物理数学算法公式、数据模型定义以及 REST API 契约，指导后续编码无偏差落地。

### 1.2 术语与参考
- **DLD**: 详细设计说明书 (Detailed Design Specification)
- **TDBAPI / DBMS**: 极生数据库原生驱动包 `jsdb-1.0.0.jar` / `pydk-1.0.0.jar`
- **参考规范**: 《统一代码规范与 DLD 契约绑定》

---

## 2 总体设计约束与红线规约

### 2.1 技术与运行约束
1. **JDK 17 + Spring Boot 3.3.x**：纯粹现代化语法规范。
2. **只读保护 (Read-Only Safety)**：MySQL 数据源必须在配置层设置 `readOnly: true` 或数据源账号只授予 `SELECT` 权限。
3. **极生底层指针安全 (P-014 / 2026-09-14 决议)**：
   - 严禁同一次 `insert` 传递变长多维混合数组；
   - 必须以固定单点批次写入（以点位名或 kid 为单位独立打包批数据）；
   - 每 50 次批量写入或每 30 秒执行一次 `TDBAPI.commit` 与 `TDBAPI.merge`。

---

## 3 代码工程目录结构设计

```plaintext
station-device-simulator/
├── pom.xml                               # 根工程 POM (依赖版本统一管理)
├── simulator-metadata-spi/               # 元数据提取 SPI 接口定义
│   └── src/main/java/.../metadata/
│       ├── model/PointMetadata.java      # 统一测点抽象模型
│       └── spi/PointMetadataProvider.java# 测点元数据拉取 SPI
├── simulator-metadata-mysql/             # 基于 MySQL 的元数据提取实现
│   └── src/main/java/.../metadata/mysql/
│       ├── entity/FusionAssetEntity.java # 映射 MySQL fusion_asset 表
│       ├── mapper/FusionAssetMapper.java # 只读 Mapper
│       └── provider/MysqlPointMetadataProvider.java
├── simulator-core/                       # 核心波形计算与调度引擎
│   └── src/main/java/.../core/
│       ├── registry/PointRegistry.java   # 内存测点注册表与策略匹配
│       ├── strategy/SignalStrategy.java  # 波形算法策略接口
│       ├── strategy/impl/
│       │   ├── LagFilterTemperatureStrategy.java # 大惯性温升滤波
│       │   ├── SineGaussianVibrationStrategy.java # 正弦加白噪声机械振动
│       │   ├── StepPowerLoadStrategy.java        # 阶梯工况负荷
│       │   ├── MarkovSwitchStrategy.java         # 马尔可夫遥信跳变
│       │   └── HydraulicDampingStrategy.java     # 水工耦合模型
│       ├── fault/FaultInjectionManager.java      # 故障注入控制器
│       └── engine/SimulationScheduler.java       # 定时 Tick 调度中心
├── simulator-sink-spi/                   # 数据写入 SPI
│   └── src/main/java/.../sink/
│       ├── model/TimeSeriesRecord.java   # 时序数据帧
│       └── spi/TimeSeriesSink.java       # 下刷接收器 SPI
├── simulator-sink-jsdb/                  # 极生数据库安全写入实现
│   └── src/main/java/.../sink/jsdb/
│       ├── config/JsdbProperties.java    # IP、端口、用户、库配置
│       ├── client/JsdbClientWrapper.java # 封装 DBMS/TDBAPI
│       └── writer/JsdbBatchWriter.java   # 批量汇聚与阈值 Commit/Merge
└── simulator-starter/                    # 启动模块与 REST 控制端点
    ├── src/main/resources/
    │   └── application.yml               # 默认配置
    └── src/main/java/.../
        ├── SimulatorApplication.java     # 主启动类
        └── controller/
            ├── SimulatorStatusController.java  # 运行状态监控
            └── FaultInjectionController.java   # 动态故障与遥信注入 API
```

---

## 4 核心算法与数据模型设计

### 4.1 核心数据结构

#### 4.1.1 测点元数据模型 (`PointMetadata`)
```java
public class PointMetadata {
    private Long id;               // 测点ID
    private String code;           // 点位编码 (对应极生中的 kid / 测点名)
    private String name;           // 点位中文名称 (如: "1号发电机推力瓦温1点")
    private String deviceName;     // 所属设备 (如: "1号发电机")
    private String componentName;  // 所属部件 (如: "推力轴承")
    private String metricType;     // 物理分类: analog (模拟量) / discrete (遥信)
    private String strategyType;   // 策略类型: TEMPERATURE, VIBRATION, POWER, SWITCH, HYDRAULIC
    private Double baseValue;      // 额定基准值
    private Double minValue;       // 物理下限
    private Double maxValue;       // 物理上限
}
```

#### 4.1.2 时序数据帧 (`TimeSeriesRecord`)
```java
public class TimeSeriesRecord {
    private String kid;            // 极生测点键 (对应 point code)
    private long timestampSec;     // 10 位秒级时间戳 (如 1716384000)
    private long timestampMs;      // 13 位毫秒级时间戳 (如 1716384000123)
    private Object value;          // Double 或 Integer
    private int quality;           // 数据质量码 (0=正常, 1=故障注入)
}
```

### 4.2 核心物理波形算法设计

1. **热工大惯性温升滤波 (`LagFilterTemperatureStrategy`)**:
   $$\text{val}_t = \text{val}_{t-1} + \alpha \cdot (\text{Target} - \text{val}_{t-1}) + \mathcal{N}(0, \sigma^2)$$
   - $\alpha$: 惯性衰减因子（取值 $0.01 \sim 0.05$）；
   - $\text{Target}$: 额定工况平衡温度（$55^\circ\text{C} \sim 75^\circ\text{C}$）；
   - $\mathcal{N}(0, \sigma^2)$: 传感器微弱热噪声扰动（$\sigma = 0.05$）。

2. **旋转机械高频振动 (`SineGaussianVibrationStrategy`)**:
   $$y(t) = A_1 \sin(\omega t) + A_2 \sin(2\omega t) + \mathcal{N}(0, \sigma^2)$$
   - $\omega$: 额定转速基频（如 50Hz 工频或电站机组旋转基频）；
   - $2\omega$: 机械不平衡与偏心二倍频特征；
   - 振动幅值约束在 $0.02\text{mm} \sim 0.12\text{mm}$。

3. **双稳态遥信马尔可夫模型 (`MarkovSwitchStrategy`)**:
   - 状态集 $\{0, 1\}$；
   - 默认稳态概率：保持 $P(1|1) = 0.9999$；
   - 触发跳闸/检修工况时，状态瞬时跃迁为 $0$。

---

## 5 REST 接口契约定义

### 5.1 查询当前仿真概览
- **URL**: `GET /api/simulator/status`
- **响应体**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "totalPoints": 1280,
    "strategyCounts": {
      "TEMPERATURE": 420,
      "VIBRATION": 260,
      "POWER": 80,
      "SWITCH": 320,
      "HYDRAULIC": 200
    },
    "currentTps": 1280,
    "totalPushedRecords": 512000,
    "jsdbStatus": "CONNECTED"
  }
}
```

### 5.2 动态故障与数值突变注入
- **URL**: `POST /api/simulator/fault/inject`
- **请求体**:
```json
{
  "pointCode": "U1_GEN_THRUST_TEMP_01",
  "targetValue": 98.5,
  "durationSeconds": 60,
  "reason": "模拟轴瓦断油导致温度飞升超限告警"
}
```
- **响应体**:
```json
{
  "code": 200,
  "msg": "故障注入成功，已锁定点位 U1_GEN_THRUST_TEMP_01 目标值 98.5，持续 60s"
}
```

### 5.3 遥信开关量状态干预
- **URL**: `POST /api/simulator/switch/toggle`
- **请求体**:
```json
{
  "pointCode": "U1_BREAKER_STATE_01",
  "state": 0
}
```
- **响应体**:
```json
{
  "code": 200,
  "msg": "开关状态变更成功，当前值: 0"
}
```

### 5.4 可视化控制台静态页面托管与数据帧拉取
- **静态资源**: `simulator-starter/src/main/resources/static/index.html` (直接映射 [mockup.html](docs/modules/simulator_console/mockup.html))
- **URL**: `GET /api/simulator/points/{pointCode}/recent-frames?limit=20`
- **响应体**:
```json
{
  "code": 200,
  "data": [
    {
      "kid": "U1_THRUST_PAD_TEMP_01",
      "timestampSec": 1716384000,
      "timestampMs": 1716384000123,
      "value": 58.42,
      "quality": 0,
      "stateTag": "NORMAL"
    }
  ]
}
```

