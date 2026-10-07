<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserListApi, createUserApi, updateUserStatusApi } from '@/api/user'
import type { UserVO, CreateUserDTO } from '@/types'

const loading = ref(false)
const userList = ref<UserVO[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)

const createForm = reactive<CreateUserDTO>({
  username: '',
  nickname: '',
  phone: '',
  password: '',
  roleIds: [2]
})

const fetchUserList = async () => {
  loading.value = true
  try {
    const res = await getUserListApi()
    if (res.code === 200 && res.data) {
      userList.value = res.data
    }
  } catch (err) {
    // request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

const handleOpenCreate = () => {
  createForm.username = ''
  createForm.nickname = ''
  createForm.phone = ''
  createForm.password = ''
  createForm.roleIds = [2]
  dialogVisible.value = true
}

const handleCreateUser = async () => {
  if (!createForm.username || !createForm.nickname || !createForm.password) {
    ElMessage.warning('请填写必填表单项')
    return
  }
  submitLoading.value = true
  try {
    const res = await createUserApi(createForm)
    if (res.code === 200) {
      ElMessage.success('管理员统一开户成功')
      dialogVisible.value = false
      fetchUserList()
    }
  } finally {
    submitLoading.value = false
  }
}

const handleStatusChange = async (row: UserVO) => {
  try {
    const newStatus = row.status === 1 ? 0 : 1
    const res = await updateUserStatusApi(row.userId, newStatus)
    if (res.code === 200) {
      ElMessage.success(newStatus === 1 ? '账号已启用' : '账号已禁用')
      row.status = newStatus
    }
  } catch (err) {
    // 恢复状态
    fetchUserList()
  }
}

onMounted(() => {
  fetchUserList()
})
</script>

<template>
  <div class="page-container">
    <div class="table-card">
      <div class="table-header">
        <div class="title-group">
          <h3>👤 统一开户与用户管理</h3>
          <p class="subtitle">禁止前台自由注册，所有账号均由管理员在后台统一创建配发</p>
        </div>
        <el-button type="primary" icon="Plus" class="create-btn" @click="handleOpenCreate">
          管理员开户
        </el-button>
      </div>

      <el-table v-loading="loading" :data="userList" stripe style="width: 100%;">
        <el-table-column prop="userId" label="用户 ID" width="100" />
        <el-table-column prop="username" label="登录账号" min-width="140" />
        <el-table-column prop="nickname" label="真实姓名" min-width="140" />
        <el-table-column prop="phone" label="手机号码" min-width="140" />
        <el-table-column label="角色分配" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles" :key="r" size="small" style="margin-right: 4px;">{{ r }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="正常"
              inactive-text="禁用"
              inline-prompt
              @change="() => handleStatusChange(row)"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 开户对话框 -->
    <el-dialog v-model="dialogVisible" title="🔑 管理员统一开户" width="480px" destroy-on-close>
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="登录账号" required>
          <el-input v-model="createForm.username" placeholder="请输入登录账号 (如 zhangsan)" />
        </el-form-item>
        <el-form-item label="真实姓名" required>
          <el-input v-model="createForm.nickname" placeholder="请输入用户真实姓名" />
        </el-form-item>
        <el-form-item label="手机号码">
          <el-input v-model="createForm.phone" placeholder="请输入手机号码" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="createForm.password" type="password" show-password placeholder="设置初始开户密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreateUser">确认开户</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.table-card {
  background: var(--bg-card);
  padding: 24px;
  border-radius: var(--radius-base);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-subtle);
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.create-btn {
  background-color: var(--primary-color);
  border-color: var(--primary-color);
}
</style>
