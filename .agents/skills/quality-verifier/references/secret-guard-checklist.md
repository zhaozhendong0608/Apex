# 🔒 预提交秘钥脱敏与安全扫描规范 (Secret Guard Checklist)

## 📌 目标与背景

在敏捷编码与 AI 辅助开发中，极其容易发生的严重安全隐患是：**AI 或开发者在配置文件、测试代码或源码中直接硬编码真实数据库密码、第三方 API Key、JWT 私钥或云厂商 AccessKey，并随代码提交进入 Git 仓库**。

本规范定义了**硬编码秘钥扫描正则矩阵**与**敏感配置安全注入协议**，严防凭据泄露。

---

## 🛡️ 一、 敏感秘钥特征匹配矩阵 (Secret Pattern Matrix)

在代码落盘与质量校验阶段，AI 必须对变更代码段进行静态文本扫描。凡匹配以下正则表达式或特征的明文文本，**一律判定为安全拦截项**：

| 秘钥类型 | 特征模式 / 正则表达式 (Pattern) | 示例 | 风险等级 |
| :--- | :--- | :--- | :--- |
| **数据库明文密码** | `password\s*[:=]\s*["'][^"']{3,}["']` | `password: "admin123"` | 🔴 高危 |
| **OpenAI / LLM API Key** | `sk-[a-zA-Z0-9]{32,}` / `api[-_]?key` | `sk-proj-9821xxxx...` | 🔴 高危 |
| **AWS / 阿里云 AK/SK** | `(AKIA[0-9A-Z]{16})` / `AccessKeySecret` | `AKIAIOSFODNN7EXAMPLE` | 🔴 高危 |
| **RSA / JWT 私钥文本** | `-----BEGIN (RSA \|EC\|PRIVATE) KEY-----` | `-----BEGIN PRIVATE KEY-----` | 🔴 高危 |
| **支付/第三方 Token** | `(mch_secret\|app_secret\|client_secret)\s*[:=]` | `app_secret = "a1b2c3d4"` | 🔴 高危 |
| **硬编码 Token 标头** | `Bearer\s+[a-zA-Z0-9\._\-]{20,}` | `Bearer eyJhbGciOi...` | 🟡 中危 |

---

## 🛠️ 二、 安全脱敏与规范注入协议 (Security Injection Protocol)

### 1. 配置文件零明文原则 (Zero Hardcoded Secret)
在 `application.yml` / `application.properties` / `.env` 文件中，所有敏感秘钥必须通过环境变量或配置占位符读取：

```yaml
# ❌ 错误示范：明文硬编码
spring:
  datasource:
    password: "Root_Password_123!"

# ✅ 标准示范：环境变量占位符 + 默认空值
spring:
  datasource:
    password: ${DB_PASSWORD:root}
```

### 2. 测试代码与联调日志脱敏 (Log Masking Standard)
在关键接口打印入参/返参联调日志或导出自动化测试脚本时，必须对敏感字段进行打码掩码：

- **手机号**：`138****1234`
- **身份证**：`110101********1234`
- **密码 / Token**：`******` 或截取前 4 后 4 (`sk-p***9821`)

---

## ⚡ 三、 预提交安全扫描流 (Pre-commit Secret Scan)

```mermaid
flowchart TD
    A["代码增量落盘"] --> B["搜寻文本中敏感秘钥特征"]
    B --> C{"是否包含明文 Key/密码?"}
    C -- 否 --> D["通过安全防护，继续执行 TDD 测试"]
    C -- 是 --> E["🔴 安全拦截阻断"]
    E --> F["自动将明文秘钥重构成 ENV_VAR 占位符"]
    F --> G["在输出卡片中展示脱敏警告"]
```

### 拦截告警示范：
```markdown
🔴 **安全防护阻断告警 (Secret Guard Alert)**：
- **目标文件**：`src/main/resources/application.yml` (Line 15)
- **拦截特征**：检测到明文硬编码密码 `password: "MyPass123!"`
- **已自动修补**：已自动将其重构成环境变量注入 `password: ${DB_PASSWORD:}`，请将真实秘钥配置在本地环境变量中！
```
