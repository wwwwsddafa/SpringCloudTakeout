<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const menuItems = [
  { path: '/admin/products', label: '商品管理', icon: '📦' },
  { path: '/admin/orders', label: '订单管理', icon: '📋' },
  { path: '/admin/free-order', label: '免单活动', icon: '🎁' },
  { path: '/admin/chat', label: '客服聊天', icon: '💬' },
  { path: '/admin/ops-stats', label: '运营数据', icon: '📊' },
  { path: '/admin/report-send', label: '运营报告', icon: '📈' },
]

function isActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}

function logout() {
  userStore.clearAuth()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-icon">🍔</span>
        <span class="brand-text">外卖管理后台</span>
      </div>
      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <div class="user-info">
          <span class="user-avatar">👤</span>
          <span class="user-name">{{ userStore.userInfo?.nickname || '管理员' }}</span>
        </div>
        <button class="btn-logout" @click="logout">退出</button>
      </div>
    </aside>
    <main class="main-content">
      <header class="topbar">
        <h2 class="page-title">
          {{ menuItems.find((m) => isActive(m.path))?.label || '管理后台' }}
        </h2>
      </header>
      <div class="content-area">
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 220px;
  min-width: 220px;
  background: #1a1a2e;
  color: #ccc;
  display: flex;
  flex-direction: column;
  user-select: none;
}

.sidebar-brand {
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-icon {
  font-size: 1.3rem;
}

.brand-text {
  font-size: 1rem;
  font-weight: 700;
  color: #e0e0e0;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  color: #aaa;
  text-decoration: none;
  font-size: 0.9rem;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #ddd;
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  border-left-color: #4caf50;
}

.nav-icon {
  font-size: 1rem;
  width: 22px;
  text-align: center;
}

.nav-label {
  white-space: nowrap;
}

.sidebar-footer {
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: #aaa;
}

.user-avatar {
  font-size: 1.1rem;
}

.user-name {
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-logout {
  padding: 5px 12px;
  background: rgba(255, 255, 255, 0.1);
  color: #ccc;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-logout:hover {
  background: rgba(229, 62, 62, 0.3);
  border-color: rgba(229, 62, 62, 0.5);
  color: #e53e3e;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f5f6fa;
}

.topbar {
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  color: #333;
}

.content-area {
  flex: 1;
  overflow: auto;
  padding: 20px 24px;
}
</style>