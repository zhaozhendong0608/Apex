# 🏛️ SOP: 老项目破局与切片索引 (00-sop-legacy)

## 📌 核心目标
针对老代码库进行前 3 层骨架鸟瞰与黑盒线索解密（路由/表名/菜单），防范上下文超载，静默运行 `arch.py` 生成/更新 `.ai/tier2_legacy_arch.md` 切片地图。

---

## 🛡️ 3 大防溢出护栏
1. **禁止全量读文件**：严禁一次性全量读取/扫描深层业务源代码。
2. **前 3 层骨架**：初次鸟瞰仅读取前 3 层目录结构与包依赖文件 (`package.json`/`pom.xml`)。
3. **切片留痕**：分析得出的老代码模块链路必须静默更新写入 `.ai/tier2_legacy_arch.md`。

---

## 🤖 3 大触发模式与脚本调用

### 模式 A：顶层骨架鸟瞰 (输入 `0` 或 `0 骨架`)
静默运行确切脚本获取前 3 层目录树与依赖：
```bash
python3 .ai/scripts/arch.py tree 3
python3 .ai/scripts/arch.py deps
```

### 模式 B：黑盒线索解密 (输入 `0 接口URL` / `0 关键词` / `0 表名`)
根据线索（如 `/api/refund` 或表名 `t_order`）全局搜索定位 3~4 个核心文件，静默追加：
```bash
python3 .ai/scripts/arch.py append "退款模块" "src/controllers/refund.js" "处理订单退款"
```

### 模式 C：探查菜单生成 (输入 `0 菜单`)
扫描前 2 层文件夹，列出 3~5 个主要业务模块 A/B/C 菜单供用户选择。

---

## 💬 最终回复卡片
🏛️ **老项目架构索引已落盘 (`.ai/tier2_legacy_arch.md`)！**
- **技术栈**：[如：Java Spring Boot + MyBatis]
- **定位模块**：[如：`src/controllers/refund/` 退款服务]
👉 **请回复 [1] 发起需求规划，或回复 [2] 开启特定 Task 的编写！**
