import axios from 'axios'
import type { ApiResponse } from '@/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

request.interceptors.request.use(
  (config) => {
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    const userId = localStorage.getItem('userId')
    if (userId) {
      config.headers['X-User-Id'] = userId
    }
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

request.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse

    // 验证码接口返回的 code 统一为 200，直接放行不做额外校验
    if (response.config.url?.includes('/captcha')) {
      return response
    }

    if (data.code === -1006 || data.code === -1007 || data.code === -1008) {
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return Promise.reject(new Error(data.msg || '登录已过期'))
    }

    if (data.code === -1009) {
      return Promise.reject(new Error('权限不足'))
    }

    // 关键：所有非 200 的业务码统一 reject，否则后端错误信息前端 catch 不到
    if (data.code !== 200) {
      return Promise.reject(new Error(data.msg || '操作失败'))
    }

    return response
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userId')
        localStorage.removeItem('userInfo')
        window.location.href = '/login'
      } else if (status === 403) {
        console.error('权限不足')
      }
    }
    return Promise.reject(error)
  },
)

export default request