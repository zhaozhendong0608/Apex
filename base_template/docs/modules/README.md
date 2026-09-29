# 📦 业务大模块 PRD 与交互原型库 (Modules Directory)

本目录用于存放按业务大模块拆分的局部 PRD 文档及 **🖥️ 可交互 HTML 线框原型 (`mockup.html`)**。

---

## 📂 目录结构规范

每当在 AI 对话框中输入 `[1+] 模块名`（如：`[1+] 认证模块`）时，AI 会在此目录下自动新建子文件夹：

```plaintext
docs/modules/
├── README.md                  # 📖 目录说明文件
├── auth/                      # 🔐 认证/权限模块
│   ├── prd.md                 # 📄 模块功能性 PRD 说明
│   └── mockup.html            # 🖥️ 可交互 HTML 原型 (强行继承 base-shell 外壳)
│
└── order/                     # 📦 订单/支付模块
    ├── prd.md                 # 📄 模块功能性 PRD 说明
    └── mockup.html            # 🖥️ 可交互 HTML 原型
```

---

## 🛠️ 原型生成死命令

1. **外壳继承规范**：
   * 所有生成的 `mockup.html` **必须硬性继承 `docs/templates/base-shell-template.html` 的结构外壳**（顶部 Header + 左侧 Sidebar 导航）。
   * AI 仅填充中间的 `Main Content` 区域，保证整个项目所有模块的交互原型在风格和大布局上 100% 保持一致。
