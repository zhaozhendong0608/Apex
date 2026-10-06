# 📦 SOP: 上下文清理与记忆交接 (05-sop-archive)

## 📌 核心目标
当阶段性开发结束、上下文长度过长（Token 接近上限），或用户准备清空会话时，**压缩当前上下文**，提取核心项目进展，生成标准化“交接记忆卡”，**追加保存至 `.ai/tier3_handover.md`**，确保新会话可 100% 无缝复活。

---

## 🤖 AI 执行流程

### 第一步：读取全盘状态
1. 静默读取 `.ai/tier3_status.md` 看板，获取已完成任务（`DONE`）与剩余任务（`TODO`）。
2. 检索本次会话中**新增的核心关键决策**（如：修改了什么数据库结构、新增了什么环境变量、调整了什么关键架构）。

### 第二步：清理临时垃圾
1. 忽略所有排错过程中的临时报错日志、长堆栈信息和无效对话。
2. 提炼出真正修改过的**核心文件列表**。

### 第二.五步：踩坑经验扫描与 KI 自动演进 (Knowledge Card Extraction)
1. 参照 `references/self-evolution-and-ki-guide.md`，扫描本会话中发生的复杂 Bug/框架坑/隐蔽报错。
2. 若存在高价值踩坑，自动在 `.agents/skills/quality-verifier/references/knowledge-cards/` 导出 `postmortem-*.md` 踩坑卡片，并自动将预防看门狗规则反哺更新到校验规范中。

### 第三步：生成交接卡与落盘 Handover Artifact
1. 参照 `references/agent-handover-protocol.md`，在 `docs/handovers/` 目录下生成标准化的交接文档 `docs/handovers/handover-{YYYYMMDD}-{MODULE}.md`。
2. 静默调用 Python 脚本追加保存至 `.ai/tier3_handover.md` 并刷新目录树：

```bash
python .ai/scripts/board.py archive "* 🎯 完成任务: [Task-XXX]
* 🛠️ 本次改动核心文件: [修改文件列表]
* 💡 关键决策: [关键决策点]"
python3 .ai/scripts/sync_tree.py
pkill -f "sync_tree.py --watch" || true
```

---

## 💬 最终输出格式 (给用户的交接卡片)

AI 压缩完成后，向用户输出以下极简交接卡片：

---
🧹 **上下文清理与归档已完成！**

### 📦 项目交接卡片 (Handover Card)

* **📅 归档时间**：[当前日期与时间]
* **🎯 当前进度**：已完成 [X] 个任务 / 剩余 [Y] 个任务
* **📄 标准交接文档**：`docs/handovers/handover-[日期]-[模块].md`
* **🛠️ 本次改动核心文件**：
  - `src/app.js`
  - `.env`
* **💡 关键决策/注意事项**：
  > [例如：数据库新增了 `user_role` 字段；接口 Token 有效期设为了 2 小时]

---

### 🛟 新会话一键复活指南：
交接卡已自动存入 `.ai/tier3_handover.md`！当下次开启新对话或换号时：

1. 直接把新会话打开。
2. 发送指令：**`6`** 或 **`继续`**。

---
👉 **现在您可以随时放心关闭或清空此聊天窗口了！**
