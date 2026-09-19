import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'
import { login as loginApi, qqLogin as qqLoginApi, register as registerApi, logout as logoutApi, getUserInfo, checkLoginStatus } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(
    (() => {
      const stored = localStorage.getItem('userInfo')
      return stored ? JSON.parse(stored) : null
    })()
  )

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const username = computed(() => userInfo.value?.username || '')

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
    localStorage.setItem('userId', info.userId)
  }

  function clearAuth() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('userInfo')
  }

  async function loginAction(params: { username: string; password: string; captcha: string; captchaKey: string }) {
    const res = await loginApi(params)
    const data = res.data.data
    setToken(data.token)
    setUserInfo({
      userId: data.userInfo.userId,
      username: data.userInfo.username,
      role: data.userInfo.role,
    })
    return data
  }

  async function qqLoginAction(code: string) {
    const res = await qqLoginApi(code)
    const data = res.data.data
    setToken(data.token)
    setUserInfo({
      userId: data.userInfo.userId,
      username: data.userInfo.username,
      role: data.userInfo.role,
    })
    return data
  }

  async function registerAction(params: {
    username: string
    password: string
    email?: string
    captcha: string
    captchaKey: string
  }) {
    const res = await registerApi(params)
    return res.data.data
  }

  async function logoutAction() {
    try {
      await logoutApi()
    } finally {
      clearAuth()
    }
  }

  async function fetchUserInfo() {
    const res = await getUserInfo()
    setUserInfo(res.data.data)
    return res.data.data
  }

  async function checkAuth() {
    if (!token.value) return false
    try {
      const res = await checkLoginStatus()
      setUserInfo({
        userId: res.data.data.userId,
        username: res.data.data.username,
        role: res.data.data.role,
      })
      return true
    } catch {
      clearAuth()
      return false
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    username,
    setToken,
    setUserInfo,
    clearAuth,
    loginAction,
    qqLoginAction,
    registerAction,
    logoutAction,
    fetchUserInfo,
    checkAuth,
  }
})