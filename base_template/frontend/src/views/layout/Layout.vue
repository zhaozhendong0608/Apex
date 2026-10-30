<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, type ThemeType } from '@/stores/theme'
import { Fold, Expand } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const { userInfo } = storeToRefs(authStore)

const activeMenu = computed(() => route.path)
const isCollapse = ref(false)

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

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
        <el-button
          class="collapse-toggle-btn"
          link
          :title="isCollapse ? '展开菜单' : '收起菜单'"
          @click="toggleCollapse"
        >
          <el-icon :size="18">
            <component :is="isCollapse ? Expand : Fold" />
          </el-icon>
        </el-button>
        <div class="brand-icon">A</div>
        <span v-show="!isCollapse">SysPlatform 通用平台</span>
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
          <el-button link class="logout-btn" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </div>
    </header>

    <!-- 主体区域：侧边栏 + 内容区 -->
    <div class="main-body">
      <aside class="app-sidebar" :class="{ 'is-collapsed': isCollapse }">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="true"
          router
          class="sidebar-menu"
        >
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
          <el-sub-menu index="/modules">
            <template #title>
              <el-icon><Connection /></el-icon>
              <span>仿真实验平台</span>
            </template>
            <el-menu-item index="/network-sim">
              <el-icon><Share /></el-icon>
              <span>网络拓扑仿真</span>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="/req">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>需求治理平台</span>
            </template>
            <el-menu-item index="/req/dashboard">
              <el-icon><Odometer /></el-icon>
              <span>项目工作台</span>
            </el-menu-item>
            <el-menu-item index="/req/prototypes">
              <el-icon><Picture /></el-icon>
              <span>原型版本与托管</span>
            </el-menu-item>
            <!-- 方案A：需求基线与签字、基线差异对比 已收拢至需求详情页内下钻，不在全局主菜单平铺 -->
            <el-menu-item index="/req/releases">
              <el-icon><Promotion /></el-icon>
              <span>发布版本追踪</span>
            </el-menu-item>
            <el-menu-item index="/req/timeline">
              <el-icon><TrendCharts /></el-icon>
              <span>需求演进时间线</span>
            </el-menu-item>
            <el-menu-item index="/req/enums">
              <el-icon><Setting /></el-icon>
              <span>码表配置中心</span>
            </el-menu-item>
            <el-menu-item index="/req/theme">
              <el-icon><Brush /></el-icon>
              <span>外观与工业主题</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="app-content" :class="{ 'is-fullscreen': route.path.includes('/network-sim/designer') || route.path.startsWith('/req') }">
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

.collapse-toggle-btn {
  color: var(--text-main);
  padding: 4px;
  margin-right: 2px;
  border-radius: 4px;
}

.collapse-toggle-btn:hover {
  background: var(--border-color);
  color: var(--primary-color);
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
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow-x: hidden;
}

.app-sidebar.is-collapsed {
  width: 64px;
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
  position: relative;
}

.app-content.is-fullscreen {
  padding: 0 !important;
  overflow: hidden !important;
}
</style>
