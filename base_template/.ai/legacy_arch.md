# 🏛️ 老项目模块与核心链路索引表 (Legacy Project Architecture Index)

> 本文件由 AI 在执行老项目破局归档时自动生成与维护。记录老项目的核心模块职责、老代码调用链与改动风险区。

---

## 📌 1. 老项目概览与技术栈信息
- **项目名称**：[自动填入老项目名称]
- **技术栈/框架**：[如：Java Spring Boot / Node.js Express / Vue2 等]
- **核心入口文件**：`src/index.js` / `src/main/java/App.java`
- **构建与依赖包配置文件**：`package.json` / `pom.xml` / `go.mod`

---

## 🧩 2. 现有模块归档矩阵 (Module Directory Archive)

| 模块名称 | 目录路径 | 核心功能职责 | 变更危险等级 (高/中/低) | 关联依赖文件 |
| :--- | :--- | :--- | :--- | :--- |
| **认证模块** | `src/auth/` | 用户登录、JWT 签发与权限拦截 | 🔴 高危 | `src/config/jwt.js` |
| **业务逻辑** | `src/services/` | 核心数据计算与外部接口调用 | 🟡 中危 | `src/models/` |

---

## 🔍 3. 核心业务切片链路 (Single Chain Traces)

### 链路 1: [例如：用户下单链路]
- **路由入口**：`POST /api/v1/orders` (`src/routes/order.js`)
- **控制器**：`OrderController.create` (`src/controllers/orderController.js`)
- **服务层**：`OrderService.processOrder` (`src/services/orderService.js`)
- **数据库 DAO**：`OrderRepository.save` (`src/models/Order.js`)
- **注意坑点与老代码约定**：[例如：订单编号生成依赖了 Redis 自增锁，修改时须保持锁后缀一致]
