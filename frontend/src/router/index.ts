import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { trackPv, type PageType } from '@/api/pv'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { guest: true },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue'),
      meta: { guest: true },
    },
    {
      path: '/product/:fid',
      name: 'ProductDetail',
      component: () => import('@/views/ProductDetail.vue'),
    },
    {
      path: '/cart',
      name: 'Cart',
      component: () => import('@/views/Cart.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/order/create',
      name: 'CreateOrder',
      component: () => import('@/views/CreateOrder.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/order/list',
      name: 'OrderList',
      component: () => import('@/views/OrderList.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/order/:roid',
      name: 'OrderDetail',
      component: () => import('@/views/OrderDetail.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/free-order',
      name: 'FreeOrder',
      component: () => import('@/views/FreeOrder.vue'),
    },
    {
      path: '/free-order/my-coupons',
      name: 'MyFreeOrderCoupons',
      component: () => import('@/views/MyFreeOrderCoupons.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/Chat.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/my-reviews',
      name: 'MyReviews',
      component: () => import('@/views/MyReviews.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to, _from) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth) {
    if (!userStore.isLoggedIn) {
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
    const isAuthed = await userStore.checkAuth()
    if (!isAuthed) {
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
  }

  if (to.meta.guest && userStore.isLoggedIn) {
    return { name: 'Home' }
  }
})

const routeToPageType: Record<string, PageType> = {
  Home: 'HOME',
  ProductDetail: 'PRODUCT_DETAIL',
  Cart: 'CART',
  CreateOrder: 'ORDER',
  OrderList: 'ORDER_DETAIL',
  OrderDetail: 'ORDER_DETAIL',
  FreeOrder: 'OTHER',
  MyFreeOrderCoupons: 'USER_CENTER',
  Chat: 'OTHER',
  MyReviews: 'USER_CENTER',
  Login: 'OTHER',
  Register: 'OTHER',
}

let lastRouteTime = Date.now()
let lastRoutePath = ''

router.afterEach((to, from) => {
  const now = Date.now()
  const staySeconds = lastRoutePath ? Math.round((now - lastRouteTime) / 1000) : 0

  const pageType = routeToPageType[to.name as string] || 'OTHER'
  const pageUrl = to.fullPath
  const referer = from.fullPath || undefined
  const fid = to.params.fid as string | undefined

  trackPv({
    pageUrl,
    pageType,
    ...(fid && { fid }),
    ...(referer && { referer }),
    ...(staySeconds > 0 && { staySeconds }),
  }).catch(() => {
    // PV 上报失败静默处理，不影响用户体验
  })

  lastRouteTime = now
  lastRoutePath = to.fullPath
})

export default router