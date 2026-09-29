# 🎨 UI/UX 设计规范与主题资产库 (Design Assets Directory)

本目录用于存放当前工程的 **UI 设计语言、视觉规范 PDF、品牌 VI 指南及 AI 可读的主题 Design Tokens**。

---

## 📂 目录结构规范

```plaintext
docs/design/
├── README.md                  # 📖 目录说明文件
├── theme-spec.pdf             # 📄 原始主题/UI规范 PDF (设计师/客户提供，可选)
├── branding-guidelines.pdf    # 📄 品牌 VI 及 Logo 使用规范 (可选)
└── design-tokens.md           # ⚡ 【AI 专享】主题变量定义 (色值、字体、圆角)
```

---

## 🛠️ 使用指引

1. **原始文件归档**：
   * 将客户或 UI 设计师提供的视觉规范 PDF / 设计图直接命名为 `theme-spec.pdf` 放入本目录。
2. **提取 AI 设计 Token**：
   * 在 `design-tokens.md` 中记录核心颜色 Hex（如 Primary: `#3B82F6`）、字体、卡片圆角等参数。
   * AI 在运行 `[2]` 单焦点编码或生成 `mockup.html` 交互原型时，会优先读取 `design-tokens.md` 约束前端样式。
