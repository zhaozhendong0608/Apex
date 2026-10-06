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
