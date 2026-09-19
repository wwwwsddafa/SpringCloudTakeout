<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { ref } from 'vue'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const mobileMenuOpen = ref(false)

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

async function handleLogout() {
  await userStore.logoutAction()
  router.push('/')
}

function goTo(path: string) {
  closeMobileMenu()
  router.push(path)
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-container">
      <router-link to="/" class="navbar-brand" @click="closeMobileMenu">
        <span class="brand-icon">🍔</span>
        <span class="brand-text">外卖点餐</span>
      </router-link>

      <button class="mobile-toggle" @click="toggleMobileMenu">
        <span class="toggle-bar" :class="{ open: mobileMenuOpen }"></span>
        <span class="toggle-bar" :class="{ open: mobileMenuOpen }"></span>
        <span class="toggle-bar" :class="{ open: mobileMenuOpen }"></span>
      </button>

      <nav class="navbar-nav" :class="{ active: mobileMenuOpen }">
        <router-link to="/" class="nav-link" @click="closeMobileMenu">首页</router-link>

        <template v-if="userStore.isLoggedIn">
          <router-link to="/cart" class="nav-link cart-link" @click="closeMobileMenu">
            购物车
            <span v-if="cartStore.totalCount > 0" class="cart-badge">{{ cartStore.totalCount }}</span>
          </router-link>
          <router-link to="/free-order" class="nav-link" @click="closeMobileMenu">免单活动</router-link>
          <router-link to="/order/list" class="nav-link" @click="closeMobileMenu">我的订单</router-link>
          <router-link to="/my-reviews" class="nav-link" @click="closeMobileMenu">我的评价</router-link>
          <router-link to="/chat" class="nav-link" @click="closeMobileMenu">客服</router-link>
          <div class="nav-user">
            <span class="user-name">{{ userStore.username }}</span>
            <button class="btn-logout" @click="handleLogout">退出</button>
          </div>
        </template>

        <template v-else>
          <router-link to="/login" class="nav-link" @click="closeMobileMenu">登录</router-link>
          <router-link to="/register" class="nav-link" @click="closeMobileMenu">注册</router-link>
        </template>
      </nav>
    </div>
  </header>
  <div class="page-content">
    <slot />
  </div>
</template>

<style scoped>
.navbar {
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: 0 2px 12px rgba(255, 107, 53, 0.3);
}

.navbar-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: #fff;
  font-weight: 700;
  font-size: 1.3rem;
}

.brand-icon {
  font-size: 1.5rem;
}

.navbar-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-link {
  color: rgba(255, 255, 255, 0.9);
  text-decoration: none;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: all 0.2s;
  position: relative;
}

.nav-link:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.nav-link.router-link-exact-active {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.cart-link {
  display: flex;
  align-items: center;
  gap: 4px;
}

.cart-badge {
  background: #fff;
  color: #ff6b35;
  font-size: 0.75rem;
  font-weight: 700;
  min-width: 20px;
  height: 20px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}

.nav-user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 8px;
  padding-left: 16px;
  border-left: 1px solid rgba(255, 255, 255, 0.3);
}

.user-name {
  font-size: 0.9rem;
  opacity: 0.9;
}

.btn-logout {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.2s;
}

.btn-logout:hover {
  background: rgba(255, 255, 255, 0.35);
}

.mobile-toggle {
  display: none;
  flex-direction: column;
  gap: 5px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
}

.toggle-bar {
  display: block;
  width: 24px;
  height: 2px;
  background: #fff;
  border-radius: 2px;
  transition: all 0.3s;
}

.toggle-bar.open:nth-child(1) {
  transform: rotate(45deg) translate(5px, 5px);
}

.toggle-bar.open:nth-child(2) {
  opacity: 0;
}

.toggle-bar.open:nth-child(3) {
  transform: rotate(-45deg) translate(5px, -5px);
}

.page-content {
  min-height: calc(100vh - 60px);
  background: #f5f5f5;
}

@media (max-width: 768px) {
  .mobile-toggle {
    display: flex;
  }

  .navbar-nav {
    position: fixed;
    top: 60px;
    left: 0;
    right: 0;
    background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
    flex-direction: column;
    padding: 16px;
    gap: 4px;
    transform: translateY(-110%);
    transition: transform 0.3s ease;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }

  .navbar-nav.active {
    transform: translateY(0);
  }

  .nav-user {
    flex-direction: column;
    margin-left: 0;
    padding-left: 0;
    padding-top: 12px;
    border-left: none;
    border-top: 1px solid rgba(255, 255, 255, 0.3);
    width: 100%;
    align-items: center;
  }
}
</style>