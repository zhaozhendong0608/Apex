<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  modelValue: boolean
  title: string
  mode?: 'dialog' | 'drawer'
  width?: string | number
  direction?: 'rtl' | 'ltr' | 'ttb' | 'btt'
  confirmText?: string
  cancelText?: string
  confirmLoading?: boolean
  showFooter?: boolean
  destroyOnClose?: boolean
  closeOnClickModal?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'drawer',
  width: '520px',
  direction: 'rtl',
  confirmText: '确定提交',
  cancelText: '取消',
  confirmLoading: false,
  showFooter: true,
  destroyOnClose: false,
  closeOnClickModal: false
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

const visible = computed({
  get() {
    return props.modelValue
  },
  set(val: boolean) {
    emit('update:modelValue', val)
  }
})

const handleClose = () => {
  visible.value = false
  emit('cancel')
}

const handleConfirm = () => {
  emit('confirm')
}
</script>

<template>
  <!-- 模式一：侧边滑出抽屉 (Drawer) -->
  <el-drawer
    v-if="mode === 'drawer'"
    v-model="visible"
    :title="title"
    :size="width"
    :direction="direction"
    :destroy-on-close="destroyOnClose"
    :close-on-click-modal="closeOnClickModal"
    class="common-form-drawer"
    @close="emit('cancel')"
  >
    <template #header>
      <div class="common-modal-header">
        <slot name="title">
          <span class="common-modal-title">{{ title }}</span>
        </slot>
      </div>
    </template>

    <div class="common-form-body">
      <slot />
    </div>

    <template v-if="showFooter" #footer>
      <div class="common-modal-footer">
        <slot name="footer">
          <el-button @click="handleClose">{{ cancelText }}</el-button>
          <el-button
            type="primary"
            :loading="confirmLoading"
            @click="handleConfirm"
          >
            {{ confirmText }}
          </el-button>
        </slot>
      </div>
    </template>
  </el-drawer>

  <!-- 模式二：居中弹出窗口 (Dialog) -->
  <el-dialog
    v-else
    v-model="visible"
    :title="title"
    :width="width"
    :destroy-on-close="destroyOnClose"
    :close-on-click-modal="closeOnClickModal"
    class="common-form-dialog"
    align-center
    @close="emit('cancel')"
  >
    <template #header>
      <div class="common-modal-header">
        <slot name="title">
          <span class="common-modal-title">{{ title }}</span>
        </slot>
      </div>
    </template>

    <div class="common-form-body">
      <slot />
    </div>

    <template v-if="showFooter" #footer>
      <div class="common-modal-footer">
        <slot name="footer">
          <el-button @click="handleClose">{{ cancelText }}</el-button>
          <el-button
            type="primary"
            :loading="confirmLoading"
            @click="handleConfirm"
          >
            {{ confirmText }}
          </el-button>
        </slot>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.common-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}

.common-modal-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main, #1e293b);
}

.common-form-body {
  padding: 4px 0;
  max-height: 75vh;
  overflow-y: auto;
}

.common-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
}

/* 强绑定当前主题色彩与圆角规范 */
:deep(.el-drawer__header),
:deep(.el-dialog__header) {
  margin-bottom: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
}

:deep(.el-drawer__body),
:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-drawer__footer),
:deep(.el-dialog__footer) {
  padding: 16px 20px;
  border-top: 1px solid var(--border-color, #e2e8f0);
}

:deep(.el-button--primary) {
  background-color: var(--primary-color) !important;
  border-color: var(--primary-color) !important;
}

:deep(.el-button--primary:hover) {
  background-color: var(--primary-hover) !important;
  border-color: var(--primary-hover) !important;
}
</style>

