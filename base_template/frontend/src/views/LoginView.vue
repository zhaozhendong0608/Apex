<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, type ThemeType } from '@/stores/theme'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: 'RawPassword123!'
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const res = await authStore.login({ username: form.username, password: form.password })
    if (res.code === 200) {
      ElMessage.success('登录成功')
      router.push('/')
    }
  } catch (error) {
    // 错误在 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

const themes: { name: string; key: ThemeType }[] = [
  { name: '🍎 Apple 蓝', key: 'apple-blue' },
  { name: '🌲 翡翠绿', key: 'emerald-green' },
  { name: '🔴 朱砂红', key: 'crimson-red' },
  { name: '💜 星空紫', key: 'royal-violet' },
  { name: '🌌 极光黑', key: 'aurora-dark' }
]
</script>

<template>
  <div class="login-wrapper">
    <div class="login-card">
      <div class="login-header">
        <div class="logo">A</div>
        <h2>SysPlatform 管理系统</h2>
        <p class="subtitle">统一身份认证与安全开户平台</p>
      </div>

      <el-form :model="form" class="login-form" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="请输入管理员/用户账号" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password size="large" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          安全登录
        </el-button>
      </el-form>

      <div class="theme-picker">
        <span class="theme-label">切换主题:</span>
        <div class="theme-dots">
          <span
            v-for="item in themes"
            :key="item.key"
            class="dot"
            :class="{ active: themeStore.currentTheme === item.key }"
            :title="item.name"
            @click="themeStore.setTheme(item.key)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-body);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: var(--bg-card);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-modal);
  border: 1px solid var(--border-color);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.logo {
  width: 48px;
  height: 48px;
  background: var(--primary-color);
  color: #fff;
  font-size: 24px;
  font-weight: bold;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.login-header h2 {
  font-size: 22px;
  color: var(--text-main);
  margin-bottom: 6px;
}

.subtitle {
  font-size: 13px;
  color: var(--text-muted);
}

.login-btn {
  width: 100%;
  margin-top: 10px;
  background-color: var(--primary-color);
  border-color: var(--primary-color);
  border-radius: var(--radius-sm);
}

.login-btn:hover {
  background-color: var(--primary-hover);
  border-color: var(--primary-hover);
}

.theme-picker {
  margin-top: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.theme-label {
  font-size: 12px;
  color: var(--text-muted);
}

.theme-dots {
  display: flex;
  gap: 8px;
}

.dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid transparent;
}

.dot:nth-child(1) { background: #0066cc; }
.dot:nth-child(2) { background: #059669; }
.dot:nth-child(3) { background: #dc2626; }
.dot:nth-child(4) { background: #7c3aed; }
.dot:nth-child(5) { background: #38bdf8; }

.dot.active {
  transform: scale(1.2);
  border-color: var(--text-main);
}
</style>
