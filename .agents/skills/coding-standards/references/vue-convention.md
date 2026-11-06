# 💚 Vue 3 + TypeScript 企业级架构与响应性规范 (Vue Convention)

> **设计哲学 (Evan You 视角)**：响应性显式化、类型推导优先、组件轻量解耦、零内存泄漏。

---

## 🏛️ 1. 标准工程分层

```plaintext
src/
├── api/          # 🔌 API 契约层：1:1 对齐 DLD RESTful 接口 (Axios 请求与 TS 接口)
├── views/        # 🖥️ 路由视图组件 (仅负责页面编排与视图调度)
│   └── order/
│       ├── index.vue
│       └── components/ # 视图特有子组件 (Modal/Form/Table)
├── components/   # 🧩 全局通用 UI 组件 (纯展示、低耦合)
├── stores/       # 📦 Pinia 状态层 (仅跨页面共享状态)
├── types/        # 📐 全局 TypeScript 类型定义 (.d.ts)
└── utils/        # 🛠️ 纯函数工具库 (request/date 等)
```

---

## ✅ 2. 硬性规约 (Do - 必须遵循)

| 维度 | 硬性要求 (Strict Rules) |
| :--- | :--- |
| **SFC 架构** | 强制使用 `<script setup lang="ts">`；组件代码块排序固定为 `<script>` ➔ `<template>` ➔ `<style scoped>`。 |
| **类型声明** | Props / Emits 强类型化 (`defineProps<{ id: string }>()`)；API 层必须显式定义 Request DTO & Response VO 接口 (100% 对齐 DLD)。 |
| **计算属性** | 模板中严禁写入复杂内联表达式，复杂逻辑与过滤计算统一使用 `computed()`。 |
| **Pinia 状态** | 仅跨页面全局状态入 Store；解构 Pinia store 状态必须使用 `storeToRefs(store)` 防丢失响应性。 |
| **生命周期清理**| `onMounted` 中创建的 `setInterval`/`window.addEventListener`，必须在 `onUnmounted` 中显式销毁清理。 |
| **代码整洁** | 组件体行数 **≤ 200 行** (超出必须拆分为 `components/` 子组件)；方法 **≤ 25 行**；优先使用 Guard Clauses 提前返回。 |

---

## 🚫 3. 红线禁令 (Don't - 一票否决)

1. ❌ **严禁组件直接发起 HTTP 请求**：所有 API 请求必须收楼至 `src/api/*.ts` 契约层，禁止在 `.vue` 中出现 `axios.get()`。
2. ❌ **严禁破坏响应性解构**：禁止直接解构 `props` 或 `reactive` 对象 (如 `const { foo } = props` 会丢失响应性，必须用 `toRefs()`/`storeToRefs()`)。
3. ❌ **严禁子组件直接修改 Props**：禁止 `props.visible = false`；必须通过 `emit('update:visible', false)` 单向数据流传递。
4. ❌ **严禁滥用 `: any` 类型与内联样式**：TS 必须显式定义类型；UI 样式统一使用 Scoped CSS / TailwindCSS。
5. ❌ **严禁硬编码 Mock 假数据**：禁止在 API 层直接 `return Promise.resolve({...})` 假数据，必须对接真实接口或抛出网络异常。

---

## 💡 4. 精炼 SFC 范例

```vue
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import { getOrderListApi, type OrderVO } from '@/api/order'

// 1. Props & Emits 强类型
const props = defineProps<{ orderId: string }>()
const emit = defineEmits<{ (e: 'success'): void }>()

// 2. Pinia 响应性解构
const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)

// 3. 本地状态与计算属性
const loading = ref(false)
const orderData = ref<OrderVO | null>(null)
const isPaid = computed(() => orderData.value?.status === 1)

// 4. 定时器显式销毁防泄漏
let timer: number | null = null
onMounted(() => { timer = window.setInterval(() => {}, 5000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="order-container">
    <el-skeleton :loading="loading">
      <div v-if="orderData">
        <span>订单号：{{ props.orderId }}</span>
        <el-tag :type="isPaid ? 'success' : 'warning'">{{ isPaid ? '已支付' : '待支付' }}</el-tag>
      </div>
    </el-skeleton>
  </div>
</template>

<style scoped>
.order-container { padding: 16px; }
</style>

---

## 📦 5. 全局通用公共组件规约 (Common Components)

全项目所有包含表格、卡片网格等分页场景的页面，**强制统一使用通用分页组件**，禁止在业务页面内直接散落零碎的 `<el-pagination>`。

### 5.1 组件路径与参数契约
* **组件路径**：`@/components/common/CommonPagination.vue`
* **参数契约**：

| 属性 / 事件 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `v-model:page` | `number` | `1` | 当前页码 (双向绑定) |
| `v-model:limit` | `number` | `10` | 每页条数 (双向绑定) |
| `total` | `number` | `0` | 总记录数 (必传) |
| `pageSizes` | `number[]` | `[6, 12, 24, 48]` | 每页大小候选下拉列表 |
| `background` | `boolean` | `true` | 是否带背景卡片样式 |
| `@pagination` | `(payload: { page, limit }) => void` | - | 页码或条数变化统一回调 |

### 5.2 标准使用范例
```vue
<script setup lang="ts">
import { ref } from 'vue'
import CommonPagination from '@/components/common/CommonPagination.vue'

const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(100)

const handlePagination = ({ page, limit }: { page: number; limit: number }) => {
  fetchTableData(page, limit)
}
</script>

<template>
  <div class="table-container">
    <!-- 数据展示区域 -->
    <el-table ... />

    <!-- 统一底部分页组件 -->
    <CommonPagination
      v-model:page="pageNo"
      v-model:limit="pageSize"
      :total="total"
      @pagination="handlePagination"
    />
  </div>
</template>
```

---

## 🪟 6. 表单新增/编辑通用模态组件规约 (CommonFormModal)

为保证项目交互规范的一致性与高奢 Apple 质感，全系统所有“新增”、“编辑”、“参数配置”录入场景，**统一强制使用 `@/components/common/CommonFormModal.vue`**。

### 6.1 抽屉 (Drawer) vs 弹窗 (Dialog) 的选型基准
在业务场景中必须遵循以下选型规约：
1. **优先使用侧边抽屉 (`mode="drawer"`)**：
   * **业务场景**：复杂业务录入、长表单、具有上下文对照参考诉求（如：**快速录入需求**、编辑确认清单、创建基线）。
   * **交互优势**：右侧滑出不遮挡左侧主体工作台信息，支持自然向下长滚动，提供充足的思考和校验空间。
2. **轻量场景使用居中弹窗 (`mode="dialog"`)**：
   * **业务场景**：字段极少（≤4项）、短平快操作（如：**新建项目空间**、上传文件附件、输入驳回原因等）。

### 6.2 组件契约参数

| 参数 / 事件 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `v-model` | `boolean` | `false` | 显示/隐藏状态 (双向绑定) |
| `mode` | `'drawer' \| 'dialog'` | `'drawer'` | 展示模式 (抽屉或弹窗) |
| `title` | `string` | `''` | 头部标题 |
| `width` | `string \| number` | `'520px'` | 宽度 (抽屉默认 520px，弹窗建议 480~580px) |
| `confirmText` | `string` | `'确定提交'` | 提交确认按钮文案 |
| `cancelText` | `string` | `'取消'` | 取消按钮文案 |
| `confirmLoading` | `boolean` | `false` | 提交按钮加载中状态 |
| `@confirm` | `() => void` | - | 点击提交按钮回调 |
| `@cancel` | `() => void` | - | 点击取消或右上角关闭回调 |

### 6.3 标准使用范例
```vue
<script setup lang="ts">
import { ref } from 'vue'
import CommonFormModal from '@/components/common/CommonFormModal.vue'

const drawerVisible = ref(false)
const submitting = ref(false)

const handleSave = async () => {
  submitting.value = true
  try {
    await saveFormApi()
    drawerVisible.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <!-- 抽屉模式 -->
  <CommonFormModal
    v-model="drawerVisible"
    mode="drawer"
    width="500px"
    title="➕ 快速录入新需求"
    confirm-text="保存并细化"
    :confirm-loading="submitting"
    @confirm="handleSave"
  >
    <el-form label-position="top">
      <el-form-item label="需求标题" required>
        <el-input placeholder="请输入需求标题" />
      </el-form-item>
    </el-form>
  </CommonFormModal>
</template>
```
