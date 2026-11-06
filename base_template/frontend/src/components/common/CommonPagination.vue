<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  total: number
  page?: number
  limit?: number
  pageSizes?: number[]
  layout?: string
  background?: boolean
  autoScroll?: boolean
  hidden?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  page: 1,
  limit: 10,
  pageSizes: () => [6, 12, 24, 48],
  layout: 'total, sizes, prev, pager, next, jumper',
  background: true,
  autoScroll: false,
  hidden: false
})

const emit = defineEmits<{
  'update:page': [value: number]
  'update:limit': [value: number]
  pagination: [payload: { page: number; limit: number }]
}>()

const currentPage = computed({
  get() {
    return props.page
  },
  set(val: number) {
    emit('update:page', val)
  }
})

const pageSize = computed({
  get() {
    return props.limit
  },
  set(val: number) {
    emit('update:limit', val)
  }
})

const handleSizeChange = (val: number) => {
  emit('update:limit', val)
  emit('pagination', { page: currentPage.value, limit: val })
  if (props.autoScroll) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

const handleCurrentChange = (val: number) => {
  emit('update:page', val)
  emit('pagination', { page: val, limit: pageSize.value })
  if (props.autoScroll) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}
</script>

<template>
  <div :class="{ hidden: hidden }" class="common-pagination-container">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :background="background"
      :layout="layout"
      :page-sizes="pageSizes"
      :total="total"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<style scoped>
.common-pagination-container {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 16px 0;
  margin-top: 12px;
}

.common-pagination-container.hidden {
  display: none;
}

/* 强绑定当前主题色彩 Design Tokens */
:deep(.el-pagination.is-background .el-pager li:not(.is-disabled).is-active) {
  background-color: var(--primary-color) !important;
  color: #ffffff !important;
  font-weight: 600;
  border-radius: var(--radius-sm, 6px);
}

:deep(.el-pagination.is-background .el-pager li:not(.is-disabled):hover) {
  color: var(--primary-color) !important;
}

:deep(.el-pagination.is-background .btn-prev:hover:not(:disabled)),
:deep(.el-pagination.is-background .btn-next:hover:not(:disabled)) {
  color: var(--primary-color) !important;
}

:deep(.el-pagination .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--primary-color) inset !important;
}
</style>

