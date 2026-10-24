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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录阻断
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('access_token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
