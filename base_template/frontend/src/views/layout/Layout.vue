<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, type ThemeType } from '@/stores/theme'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const { userInfo } = storeToRefs(authStore)

const activeMenu = computed(() => route.path)

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}

const themeOptions: { label: string; value: ThemeType }[] = [
  { label: '🍎 Apple 科技蓝', value: 'apple-blue' },
  { label: '🌲 翡翠绿色', value: 'emerald-green' },
  { label: '🔴 朱砂红色', value: 'crimson-red' },
  { label: '💜 皇家星空紫', value: 'royal-violet' },
  { label: '🌌 极光暗黑模式', value: 'aurora-dark' }
]
</script>

<template>
  <div class="layout-container">
    <!-- Header 顶部导航栏 -->
    <header class="app-header">
      <div class="brand">
        <div class="brand-icon">A</div>
        <span>SysPlatform 通用平台</span>
      </div>

      <div class="header-right">
        <!-- 主题切换器 -->
        <el-select
          :model-value="themeStore.currentTheme"
          size="small"
          style="width: 150px;"
          @change="(val: ThemeType) => themeStore.setTheme(val)"
        >
          <template #prefix>
            <el-icon><Brush /></el-icon>
          </template>
          <el-option
            v-for="item in themeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <!-- 用户头像与登出 -->
        <div class="user-info">
          <el-avatar :size="30">{{ userInfo?.nickname?.charAt(0) || 'A' }}</el-avatar>
          <span class="nickname">{{ userInfo?.nickname || '管理员' }}</span>
          <el-button type="text" class="logout-btn" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </div>
    </header>

    <!-- 主体区域：侧边栏 + 内容区 -->
    <div class="main-body">
      <aside class="app-sidebar">
        <el-menu :default-active="activeMenu" router class="sidebar-menu">
          <el-sub-menu index="/system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/system/user">
              <el-icon><User /></el-icon>
              <span>用户开户管理</span>
            </el-menu-item>
            <el-menu-item index="/system/role">
              <el-icon><Lock /></el-icon>
              <span>角色权限管理</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="app-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  height: 60px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: bold;
  color: var(--text-main);
}

.brand-icon {
  width: 32px;
  height: 32px;
  background: var(--primary-color);
  color: #fff;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.nickname {
  color: var(--text-main);
  font-weight: 500;
}

.logout-btn {
  color: var(--text-muted);
  margin-left: 8px;
}

.logout-btn:hover {
  color: var(--primary-color);
}

.main-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.app-sidebar {
  width: 220px;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color);
}

.sidebar-menu {
  border-right: none;
  background: transparent;
}

.app-content {
  flex: 1;
  padding: 24px;
  background: var(--bg-body);
  overflow-y: auto;
}
</style>
