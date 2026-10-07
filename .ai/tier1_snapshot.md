# ⚡ [第一层：极简快照名片层] 项目动态快照 (tier1_snapshot.md)

> 🏛️ **【三层记忆金字塔 - 第一层：极简快照名片】**
> 💡 物理死锁在 30 行以内（<500 Token）。输入 [6] 断点复活时优先且只读本文件。

---

## 📌 1. 项目身份与基本信息
- **项目名称**：通用后台管理平台 (SysPlatform)
- **核心技术栈**：Java 17 / Spring Boot 3 / Vue 3 / Vite / MySQL 8.0

---

## 🏆 2. 已稳固里程碑 (MAX 3 行)
- **[Milestone-1.2]** SysPlatform Vue3+Vite+Element Plus 前端工程与登录/开户/角色 UI 页面 (Task-103) 构建验收全量归档 🟢
- **[Milestone-1.3]** SysPlatform 后端 Java 全量真实归档 (Task-104) 补齐启动类、全量消灭 Mock 假数据、连接 MySQL 8.0 及 Lombok 1.18.38 规范化 🟢

---

## 🎯 3. 当前活跃状态 (ACTIVE Focus)
- **[Task-NONE]** SysPlatform 基础平台前后端（Java 3.2.4 + Vue 3）全量真实生产级代码已成功归档，等待规划新业务子模块

---

## ⚠️ 4. 核心架构约束
1. **统一开户模式**：禁止前台自由注册，所有账号均由 `admin` 在后台开户。
2. **测试先行 & 看门狗**：改动绑定 DLD/Testcase 契约，单次改动控制范围，禁止假 Mock。
