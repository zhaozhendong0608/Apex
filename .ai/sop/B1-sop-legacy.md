# [B1] 老项目破局与切片索引 (B1-sop-legacy)
> **触发**：`[B1]`/`[B1]`/`老项目`/`接手` ｜ **前置**：无 ｜ **产出**：`.ai/tier2_legacy_arch.md` 切片地图

## 📌 核心目标
对老代码库做**前 3 层骨架鸟瞰 + 黑盒线索解密**，防上下文溢出，静默生成模块切片图谱。

## 🛡️ 3 大防溢出护栏
1. **禁止全量读文件**：严禁一次性读取/扫描深层业务源码。
2. **前 3 层骨架**：初次只看 3 层目录与依赖文件（`package.json`/`pom.xml`）。
3. **切片留痕**：分析出的链路必须静默写入 `.ai/tier2_legacy_arch.md`。

## ✅ 三种模式
**A. 顶层骨架**（`0` 或 `0 骨架`）
```bash
python3 .ai/scripts/arch.py tree 3
python3 .ai/scripts/arch.py deps
```

**B. 黑盒解密与图谱**（`0 路由`/`0 关键词`/`0 表名`）
1. 激活 `legacy-archaeologist` Skill。
2. 深度搜寻与切片：
```bash
python3 .ai/scripts/arch.py find "order"
python3 .agents/skills/legacy-archaeologist/scripts/slice_code.py <file_path>
```
3. 导出契约与行为锁死探针：`docs/legacy/legacy_hld_<module>.md` + `scripts/tests/test_legacy_<module>.py`。
4. 注册四维元数据并生成 Mermaid：
```bash
python3 .ai/scripts/arch.py analyze "/order/create" "订单创建模块" "OrderCalc.vue,OrderService.java" "t_order,t_user" "POST /api/v1/order"
```

**C. 菜单探查**（`0 菜单`）：扫前 2 层目录，列 3~5 个主业务模块供选择。

## 🧷 能力点
`C-B1-01` 前 3 层骨架防溢出；`C-B1-02` 黑盒线索解密；`C-B1-03` 四维元数据注册；`C-B1-04` 拓扑图谱与共享表推断；`C-B1-05` 行为锁死探针。

## 📤 交付
卡片 ≤4 行：技术栈 / 定位模块 / 已落盘图谱路径 / 下一步（`[1]` 规划 或 `[3]` 直接改）。
