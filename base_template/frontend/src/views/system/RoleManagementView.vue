<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getRoleListApi } from '@/api/role'
import type { RoleVO } from '@/types'

const loading = ref(false)
const roleList = ref<RoleVO[]>([])

const fetchRoles = async () => {
  loading.value = true
  try {
    const res = await getRoleListApi()
    if (res.code === 200 && res.data) {
      roleList.value = res.data
    }
  } catch (err) {
    // 自动捕获
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchRoles()
})
</script>

<template>
  <div class="page-container">
    <div class="table-card">
      <div class="table-header">
        <div class="title-group">
          <h3>🛡️ 角色组与 RBAC 权限授权</h3>
          <p class="subtitle">管理系统权限角色组及动态菜单与 Action 权限控制映射</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="roleList" stripe style="width: 100%;">
        <el-table-column prop="roleId" label="角色 ID" width="100" />
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column prop="roleCode" label="角色标识 (Role Code)" min-width="180">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.roleCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
}

.table-card {
  background: var(--bg-card);
  padding: 24px;
  border-radius: var(--radius-base);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-subtle);
}

.table-header {
  margin-bottom: 20px;
}

.title-group h3 {
  font-size: 18px;
  color: var(--text-main);
  margin-bottom: 4px;
}

.subtitle {
  font-size: 13px;
  color: var(--text-muted);
}
</style>
