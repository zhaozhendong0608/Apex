import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '系统登录' }
  },
  {
    path: '/',
    component: () => import('@/views/layout/Layout.vue'),
    redirect: '/system/user',
    children: [
      {
        path: '/system/user',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagementView.vue'),
        meta: { title: '用户开户管理' }
      },
      {
        path: '/system/role',
        name: 'RoleManagement',
        component: () => import('@/views/system/RoleManagementView.vue'),
        meta: { title: '角色权限管理' }
      },
      {
        path: '/network-sim',
        name: 'TopologyList',
        component: () => import('@/modules/network-sim/views/TopologyListView.vue'),
        meta: { title: '网络拓扑方案管理' }
      },
      {
        path: '/network-sim/designer',
        name: 'NetworkDesigner',
        component: () => import('@/modules/network-sim/views/NetworkSimView.vue'),
        meta: { title: '网络拓扑仿真工作台' }
      },
      {
        path: '/req/dashboard',
        name: 'ReqDashboard',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '项目工作台' }
      },
      {
        path: '/req/detail',
        name: 'ReqDetail',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '需求详情 & 清单' }
      },
      {
        path: '/req/prototypes',
        name: 'ReqPrototypes',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '原型版本与托管' }
      },
      {
        path: '/req/baselines',
        name: 'ReqBaselines',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '需求基线与签字' }
      },
      {
        path: '/req/baseline-diff',
        name: 'ReqBaselineDiff',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '基线差异对比' }
      },
      {
        path: '/req/releases',
        name: 'ReqReleases',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '发布版本追踪' }
      },
      {
        path: '/req/timeline',
        name: 'ReqTimeline',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '需求演进时间线' }
      },
      {
        path: '/req/enums',
        name: 'ReqEnums',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '码表配置中心' }
      },
      {
        path: '/req/theme',
        name: 'ReqTheme',
        component: () => import('@/modules/req-management/views/ReqManagementView.vue'),
        meta: { title: '外观与工业主题' }
      },
      {
        path: '/req/management',
        redirect: '/req/dashboard'
      },
      {
        path: '/req',
        redirect: '/req/dashboard'
      }
    ]
  },
  {
    path: '/sign/:token',
    name: 'PublicSign',
    component: () => import('@/views/public/SignConfirmView.vue'),
    meta: { title: '客户法定免登签署' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录阻断 (对 /login 和 /sign/:token 免登放行)
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('access_token')
  const isPublic = to.path === '/login' || to.path.startsWith('/sign')
  if (!isPublic && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
