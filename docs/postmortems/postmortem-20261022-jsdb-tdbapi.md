# 踩坑总结 (Postmortem): 极生时序数据库 (JSDB/X-PHY) 写入规约与底座 Schema 踩坑

- **发生日期**: 2026-10-22
- **涉及模块**: `station-device-simulator/simulator-sink-jsdb`
- **问题分类**: JNI 驱动调用协议不匹配 / Schema 延迟注册导致空指针

---

## 1. 现象与报错

### 现象 A: `Cannot invoke "com.alibaba.fastjson.JSONObject.getIntValue(String)" because "<local9>" is null`
在调用 `TDBAPI.insert(dbms, "tdb", tableName, kids, docs)` 时发生异常。

### 现象 B: `Cannot read field "schema" because "<parameter3>" is null`
在 `TDBAPI.insert` 请求被底层 C/JNI 引擎处理时，报 Schm schema 属性为空。

---

## 2. 根因分析 (Root Cause)

1. **`docs` 数据行必须为有序数组 `JSONArray`**：
   - 极生底层 JNI 引擎解析每条时序数据行时，**严禁使用 `JSONObject` 键值对**。
   - 必须按字段顺序组织为纯 `JSONArray`：`[time (int秒), timestamp (long毫秒), value (double), flag (byte)]`。
2. **`kid` 必须为点位编码 MD5 哈希数值**：
   - 不能直接将点位 Code 字符串放入 `kids` 数组中。
   - 必须通过 `com.pydk.util.MD5.Md5Key(pointCode.getBytes(StandardCharsets.UTF_8))` 计算为 64 位 Long 键。
3. **`DBMS$Schm.schema` 预加载要求**：
   - 极生若在连接时未初始化或表为新建表，直接以 `flag=0` 打开会导致 schema 属性为 null。
   - 必须遵循 `etl_station` 规约，先以 `flag=1` 模式加载并注册超表 schema 字段，再以 `flag=0` 读写。

---

## 3. 防范措施与代码规约 (Guardrails)

```java
// 1. 初始化自动注册 Schema
DBMS createDbms = new DBMS();
createDbms.load(new String[]{address}, 1);
TDB.Schm schema = new TDB.Schm();
schema.dbname = tableName;
schema.alias = "";
TDBAPI.addColumn(schema, "time", "秒戳", TDB.VarType.TDB_INTEGER, 0, 0, 1);
TDBAPI.addColumn(schema, "timestamp", "毫秒戳", TDB.VarType.TDB_LONG, 0, 0, 1);
TDBAPI.addColumn(schema, "value", "测量值", TDB.VarType.TDB_DOUBLE, 7, 0, 1);
TDBAPI.addColumn(schema, "flag", "异常标记", TDB.VarType.TDB_CHAR, 0, 0, 1);
TDBAPI.create(createDbms, database, schema);
createDbms.close();

// 2. 写入时 docs 行包装规范
long kidLong = MD5.Md5Key(pointCode.getBytes(StandardCharsets.UTF_8));
kidsArray.add(kidLong);

JSONArray docRow = new JSONArray();
docRow.add((int) sec);
docRow.add((long) ms);
docRow.add((double) value);
docRow.add((byte) quality);
docsArray.add(docRow);
```
