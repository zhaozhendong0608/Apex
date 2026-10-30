# 🏛️ 老项目切片与关系图谱元数据池 (tier2_legacy_arch.md)

> 💡 **本文件是 `[B1]` 老项目破局的产出物**，由 `arch.py` 自动生成/增量更新。
> ⚠️ **仅在「接手存量项目」时才启用**；新项目 / 业务迭代项目下，它保持未启用状态。

---

## 🚦 启用状态

**当前状态：⏸️ 未启用（本仓库尚未执行过 `[B1]` 老项目破局）**

启用方式（接手老项目时执行）：

```bash
python3 .ai/scripts/arch.py tree 3          # 前 3 层骨架
python3 .ai/scripts/arch.py find "order"    # 关键词切片
python3 .ai/scripts/arch.py analyze "/order/create" "订单创建模块" \
    "OrderService.java" "t_order,t_user" "POST /api/v1/order"
```

> 🚨 **禁止手写本文件**：所有条目必须由 `arch.py analyze` 生成，
> 以保证「路由 / 关键文件 / API / 关联表」四维元数据可追溯、可重算。
> 一旦手工补录，图谱与真实代码就会脱钩——这比没有地图更危险。

---

## 🕸️ 全局拓扑关系图谱 (Global Topology Graph)

```mermaid
graph TD
    START["🚀 尚未执行 [B1] 破局：本图谱为空"]
```

---

## 🧩 模块元数据注册池 (Metadata Pool)

> 执行 `[B1]` 后，`arch.py analyze` 会在此追加形如以下的卡片：

```markdown
### 🧩 模块: <模块名>
- **路由入口**: `<前端路由>`
- **关键文件**: `<File1.java, File2.vue>`
- **绑定 API**: `<POST /api/xxx>`
- **关联数据表**: `<t_xxx>`
```

（当前为空）
