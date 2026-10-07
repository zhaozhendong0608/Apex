# 🎨 项目主题与设计系统 Token (Design System Tokens)

> 本文件用于供 AI 与前端开发者读取，硬性约束 UI 界面风格与 Token 变量，支持多领域（医疗、工业、SaaS、暗黑模式等）一键主题切换。

---

## 1. 多领域主题调色板 (Multi-Domain Theme Palettes)

### 🍎 苹果极简科技蓝 (Apple Tech Blue - 默认)
- **主色 (Primary)**: `#0066CC`
- **悬浮色 (Primary Hover)**: `#0052A3`
- **浅底色 (Primary Light)**: `#E6F0FA`
- **页面背景 (Body BG)**: `#F5F5F7`
- **文本颜色 (Text Main)**: `#1D1D1F`

### 🌲 医疗/生态/环保：翡翠绿 (Emerald Eco)
- **主色 (Primary)**: `#059669`
- **悬浮色 (Primary Hover)**: `#047857`
- **浅底色 (Primary Light)**: `#ECFDF5`
- **页面背景 (Body BG)**: `#F0FDF4`
- **文本颜色 (Text Main)**: `#064E3B`

### 🔴 工业/政企/高端能量：朱砂红 (Crimson Industrial)
- **主色 (Primary)**: `#DC2626`
- **悬浮色 (Primary Hover)**: `#B91C1C`
- **浅底色 (Primary Light)**: `#FEF2F2`
- **页面背景 (Body BG)**: `#FAFAFA`
- **文本颜色 (Text Main)**: `#18181B`

### 💜 SaaS / AI 大模型：皇家星空紫 (Royal Violet)
- **主色 (Primary)**: `#7C3AED`
- **悬浮色 (Primary Hover)**: `#6D28D9`
- **浅底色 (Primary Light)**: `#F5F3FF`
- **页面背景 (Body BG)**: `#FAF5FF`
- **文本颜色 (Text Main)**: `#2E1065`

### 🌌 极光暗黑模式 (Aurora Dark Mode)
- **主色 (Primary)**: `#38BDF8`
- **页面背景 (Body BG)**: `#0B0F17`
- **卡片背景 (Card BG)**: `#151C2C`
- **文本颜色 (Text Main)**: `#F3F4F6`

---

## 2. 排版与字体 (Typography Guidelines - Apple Standard)

- **字体族 (Font Family)**: `-apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "Inter", "Segoe UI", Roboto, sans-serif`
- **字号与行高**:
  - 页面主标题: `22px` / Bold / Letter-spacing `-0.5px`
  - 卡片大标题: `18px` / Bold
  - 正文/表格: `14px` / Regular / Line-height `1.5`
  - 侧边栏/小按钮: `13px`~`12px` / Semi-Bold

---

## 3. 圆角与动效 (Radius & Micro-Interactions)

- **卡片/弹窗圆角**: `12px` (平滑苹果风格圆角)
- **按钮/小元素圆角**: `8px`
- **标签 Badge 圆角**: `20px` (胶囊微小样式)
- **平滑过渡**: `transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1)`
