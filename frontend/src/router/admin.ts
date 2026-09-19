import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: '/admin/products',
    },
    {
      path: '/admin/login',
      name: 'AdminLogin',
      component: () => import('@/views/admin/AdminLogin.vue'),
      meta: { guest: true },
    },
    {
      path: '/admin/products',
      name: 'AdminProducts',
      component: () => import('@/views/admin/ProductManage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/products/add',
      name: 'AdminAddProduct',
      component: () => import('@/views/admin/AddProduct.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/products/edit/:fid',
      name: 'AdminEditProduct',
      component: () => import('@/views/admin/EditProduct.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/orders',
      name: 'AdminOrders',
      component: () => import('@/views/admin/AdminOrders.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/free-order',
      name: 'AdminFreeOrder',
      component: () => import('@/views/admin/FreeOrderManage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/chat',
      name: 'AdminChat',
      component: () => import('@/views/admin/AdminChat.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/ops-stats',
      name: 'AdminOpsStats',
      component: () => import('@/views/admin/OpsStats.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/report-send',
      name: 'AdminReportSend',
      component: () => import('@/views/admin/OpsReportSend.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/admin/products',
    },
  ],
})

router.beforeEach(async (to, _from) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth) {
    if (!userStore.isLoggedIn) {
      return { name: 'AdminLogin', query: { redirect: to.fullPath } }
    }
    const isAuthed = await userStore.checkAuth()
    if (!isAuthed) {
      return { name: 'AdminLogin', query: { redirect: to.fullPath } }
    }
    if (!userStore.isAdmin) {
      return { name: 'AdminLogin' }
    }
  }

  if (to.meta.guest && userStore.isLoggedIn) {
    return { name: 'AdminProducts' }
  }
})

export default router