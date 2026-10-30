# 📁 文档与原型外壳模板库 (Templates Directory)

本目录用于存放生成 **交互原型外壳 HTML** 以及 **软著/产品使用手册** 的通用模板。

---

## 📂 目录结构规范

```plaintext
docs/templates/
├── README.md                  # 📖 目录说明文件
├── base-shell-template.html   # 🖥️ 通用交互原型全局外壳 (Header + Sidebar 统一布局)
└── user-manual-template.md    # 📖 产品使用手册与软著导出通用模板
```

---

## 🛠️ 模板用途

1. **`base-shell-template.html`**：
   * 在输入 `[1+] 模块名` 生成模块线框原型时，作为 HTML 外壳被 AI 动态继承。
2. **`user-manual-template.md`**：
   * 项目交付或申请软著（软件著作权）时，作为产品使用说明书的标准导出格式。
