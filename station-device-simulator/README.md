# 独立电站设备数据模拟服务 (Station Device Simulator) 架构方案与开发指引

> **归档时间**: 2026-10-22  
> **归档位置**: 项目根目录 `独立电站设备模拟服务方案.md`  
> **文档定位**: 供后续在**新工作区/新工程**中唤醒 AI 助手时，作为 **Single Source of Truth (SSOT)**，指导 AI 快速理解 `etl_station` 业务脉络、定位核心文件与表结构，并一键完成独立模拟器工程的构建。

---

## 🧭 一、宿主工程全景与绝对代码路径 (AI 关键索引)

后续 AI 无需全盘扫描，直接依据本表定位 `etl_station` 核心文件：

| 关注维度 | 本地绝对路径 / 文件相对路径 | 核心职责与关键类 / 属性 |
| :--- | :--- | :--- |
| **工作区根目录** | `/Users/dongzi/Documents/工作/workplace/etl_station` | 规约配置、Docker 脚本与内核架构基座 |
| **系统宪法总纲** | `Sentinel-K_内核/00_系统总纲 (Kernel).md` | 协作法则、红线约束与证据链交付标准 |
| **技术栈与风险** | `Sentinel-K_内核/01_项目全景 (Landscape).md` | Spring Boot 3.5.x、Vue 3、TDengine/JSDB、LiteFlow 算子引擎 |
| **踩坑账本** | `PITFALLS.md` | **P-014 (极生SDK不可达死等50s)**, **P-001 (增量打包毒化)** |
| **决策日志** | `DECISION_LOG.md` | **2026-09-14 (极生多kid指针污染Bug，改单点并发)** |
| **MySQL 点位模型 (Entity)** | `RuoYi-Vue-Plus/ruoyi-modules/badger-fusion/src/main/java/com/jtech/badger/fusion/domain/entity/FusionAsset.java` | 资产树实体：`id`, `parentId`, `name`, `code`, `type` (`unit`/`device`/`component`/`point`) |
| **测点元数据 (Entity)** | `.../badger-fusion/.../domain/entity/FusionAssetPoint.java` | 测点属性：`assetId`, `metricType` (`analog`/`discrete`), `semanticTag`, `datasourceId` |
| **极生时序取数策略** | `.../badger-fusion/.../strategy/datasource/impl/JsTdbDataSourceStrategy.java` | 极生时序读取、双层网络探针（TCP 1.5s + TDBAPI.tables 2.5s 超时） |
| **现有模拟写入参考** | `.../badger-fusion/.../service/PumpedStorageSuperInsertRuntimeService.java` | 现有的 JSDB 写入逻辑参考（`DBMS.load()`, `TDBAPI.insert/commit/merge`） |
| **MySQL 默认配置** | `RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-dev.yml` | 库名: `etl_badger` (默认端口 3306) |

---

## 🎯 二、需求背景与目标定位

在工业数据智能分析与开发平台（`etl_station`）中，后端算法引擎（LiteFlow 算子网流）和告警中心依赖持续、稳定、具备**真实物理电厂特征**的时序数据流。

* **核心痛点**:
  1. **现场物理隔离**: 工控内网环境无法外联，开发与测试缺少持续推流源；
  2. **传统 Mock 失真**: 简单随机数无法模拟大惯性温度爬升、高频机械振动杂波及遥信跳变，无法验证边缘计算算子与告警灵敏度；
  3. **进程资源争抢**: 若在 `etl_station` 主服务内运行高频生成，会抢占算子重计算和 JVM 堆内存。
* **建设目标**:
  1. **物理进程隔离**: 独立部署的设备模拟服务（如 `station-device-simulator`），独立启停，单向写入 JSDB；
  2. **元数据同源共享 (SSOT)**: 纯只读连接 MySQL 的 `fusion_asset` 与 `fusion_asset_point` 表，与主平台点位 100% 保持一致；
  3. **特征化物理建模**: 依据点位所属的设备名称、部件分类及测点语义（`metricType` / `semanticTag`），自动生成匹配的物理波形。

---

## 🏗️ 三、端到端解耦架构设计

```
 ┌─────────────────────────────────────────────────────────────────┐
 │               MySQL 元数据底座 (SSOT 唯一信任源: etl_badger)     │
 │   - fusion_asset (机组/设备/部件/点位 4层树结构与名称)            │
 │   - fusion_asset_point (metricType: 模拟量/开关量, semanticTag) │
 └───────────────┬─────────────────────────────────┬───────────────┘
                 │ 只读拉取测点元数据              │ 资产树维护
                 ▼                                 ▼
 ┌───────────────────────────────┐   时序写入   ┌───────────────────────────────┐
 │   独立设备模拟服务             │ ──────────> │     极生时序数据库 (JSDB)      │
 │   (Station Device Simulator)  │   TDBAPI    │   (表/超表: position / tdb)   │
 └───────────────────────────────┘             └───────────────┬───────────────┘
                                                               │ 订阅/拉取计算
                                                               ▼
                                                ┌───────────────────────────────┐
                                                │   主平台 (etl_station)        │
                                                │   (LiteFlow 算子、告警、大盘) │
                                                └───────────────────────────────┘
```

---

## 🔬 四、结合 REA (Reverse Engineer Anything) 的实战价值

在独立服务搭建中，REA 可在以下两个关键技术节点提供底层证据与逆向支撑：

1. **极生官方驱动 (`jsdb-1.0.0.jar` / `pydk-1.0.0.jar`) 协议与边界逆向**:
   * **背景**: 极生数据库缺乏标准 JDBC 驱动，而是通过 `pydk` / `TDBAPI` 进行 JNI 或原生 Socket 通信。
   * **REA 动作**:
     * 使用 REA 反编译和分析 `com.jsdb.XPhy.TDBAPI` 与 `com.pydk.util.DBMS`；
     * 明确批量写入、时间戳精度（秒级/毫秒级）、驱动指针复位机制，避免触发已有踩坑记录中的缺陷（如多 kid 指针未重置导致数据交叉污染，见 [DECISION_LOG.md](DECISION_LOG.md) 2026-09-14 记录）；
     * 提取 `commit` 与 `merge` 的最佳调用阈值，保证百万级写入时 0 内存泄漏。
2. **电站上位机与现场采集报文逆向**:
   * 若现场提供既有上位机通信抓包文件（PCAP），利用 REA 网络分析工具解析测点组帧协议，还原实际电站的采样周期与工况跃迁曲线。

---

## 📈 五、基于设备名称与点位类型的差异化生成策略 (Signal Strategy Router)

模拟服务启动时从 MySQL 载入 `fusion_asset` 树后，依据设备名称、部件名称关键字及 `metricType` 分流派发波形算法：

| 设备/部件/点位特征 | 典型物理类型 | 推荐数学模型 / 生成策略 | 业务数据特征 |
| :--- | :--- | :--- | :--- |
| **瓦温 / 油温 / 轴温 / 绕组温度** | 温度测点（大惯性热工量） | **一阶惯性滞后滤波 (Lag Filter)**：<br>`T(t) = T(t-1) + α·(T_target - T(t-1)) + Noise` | 45℃ ~ 75℃，平滑缓升缓降，受热惯性约束无突变 |
| **X向/Y向振动 / 摆度 / 偏心度** | 机械振动测点（高频连续量） | **基频正弦叠加 + 高斯白噪声**：<br>`y(t) = A1·sin(ωt) + A2·sin(2ωt) + N(0, σ²)` | 0.02mm ~ 0.15mm，具备旋转机械工频与倍频特征 |
| **机组有功功率 / 无功 / 电流** | 电气负荷量 | **阶梯工况跃迁模型**：<br>停机(0) -> 爬坡 -> 满负荷稳态运行 | 0MW ~ 额定MW，阶跃过程平滑受调速器速率限制 |
| **断路器 / 刀闸 / 开关量** | 遥信状态 (`discrete`) | **双稳态马尔可夫链**：<br>保持常态，定时或指令触发 0/1 跳变 | 纯离散整数：`0` (分闸) / `1` (合闸) |
| **水头 / 导叶开度 / 流量** | 水工水力测点 | **缓变相关性联动模型**：<br>开度与流量、有功功率正相关耦合 | 20% ~ 95% 范围，叠加微弱水锤阻尼抖动 |

---

## 🛠️ 六、未来 AI 快速开发实施手册 (AI Bootstrap Instructions)

当你在全新工作空间唤起 AI 时，直接向 AI 投喂本节指令：

### 1. 目标工程骨架定义
* **工程名**: `station-device-simulator`
* **技术选型**:
  * Java 17 + Spring Boot 3.x
  * MyBatis-Plus（只读操作 MySQL `etl_badger`）
  * 引入本地依赖 `jsdb.jar` 与 `pydk.jar`（直接拷贝自 `etl_station/RuoYi-Vue-Plus/ruoyi-modules/badger-fusion/pom.xml` 中的引用）

### 2. 核心模块与执行逻辑
1. **`PointRegistryService` (点位注册中心)**:
   * 启动时从 MySQL 查询 `fusion_asset` 中所有 `type = 'point'` 的记录；
   * 通过 `parentId` 向上回溯查出所属部件与设备名称；
   * 根据名称关键字打上策略标签：`TEMPERATURE`、`VIBRATION`、`POWER`、`SWITCH`、`HYDRAULIC`。
2. **`SignalGeneratorEngine` (波形计算引擎)**:
   * 采用无锁状态存储器（如 `ConcurrentHashMap<Long, Double>`）保存每个点位上一时刻的状态值；
   * 采用线程池定时触发（如每 1000ms 一次 tick）；
   * 支持通过 HTTP 接口动态注入**“故障突变”**（例如：让指定 pointId 瞬时飙升到 999，验证平台告警）。
3. **`JsdbBatchWriter` (极生写入器)**:
   * 组装极生数据对象：`time`（10位秒级戳）、`value`（Double）、`timestamp`（13位毫秒戳）；
   * 单批次满 100~500 条或到达时间窗口触发一次 `TDBAPI.insert(dbms, "tdb", tableName, kids, docs)`；
   * 每 50 次写入调用一次 `TDBAPI.commit(...)` 和 `TDBAPI.merge(...)`，释放极生底层文件句柄。

### 3. 不可触碰的红线提醒 (AI Anti-Hallucination)
* ❌ **严禁修改主库元数据**: 模拟器对 MySQL 数据库只拥有只读权限，绝不能更新 `fusion_asset` 结构。
* ❌ **防驱动缺陷**: 严禁在极生 `TDBAPI.insert` 中混入不规则的多 kid 嵌套数组，必须遵循已验证的单点/统一批次模式（避免 P-014 / 决策日志中的数据交叉污染 Bug）。
