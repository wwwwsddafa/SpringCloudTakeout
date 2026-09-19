<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCaptcha } from '@/api/user'
import { cleanCaptchaImage } from '@/utils'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = ref({
  username: '',
  password: '',
  captcha: '',
  captchaKey: '',
})
const captchaImage = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function fetchCaptcha() {
  try {
    const res = await getCaptcha()
    const { captchaImage: img, captchaKey } = res.data.data
    captchaImage.value = cleanCaptchaImage(img)
    form.value.captchaKey = captchaKey
    form.value.captcha = ''
  } catch (err: any) {
    console.error('获取验证码失败:', err)
    captchaImage.value = ''
  }
}

async function handleLogin() {
  if (!form.value.username || !form.value.password || !form.value.captcha) {
    errorMsg.value = '请填写完整信息'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await userStore.loginAction(form.value)
    if (!userStore.isAdmin) {
      errorMsg.value = '非管理员账号，请使用管理员账号登录'
      userStore.clearAuth()
      fetchCaptcha()
      return
    }
    const redirect = (route.query.redirect as string) || '/admin/products'
    router.push(redirect)
  } catch (err: any) {
    errorMsg.value = getErrorMsg(err, '登录失败')
    fetchCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchCaptcha()
})
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <span class="login-icon">🍔</span>
        <h1>外卖管理后台</h1>
        <p>请使用管理员账号登录</p>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="form.username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>
        <div class="form-group captcha-group">
          <label>验证码</label>
          <div class="captcha-row">
            <input
              v-model="form.captcha"
              type="text"
              placeholder="验证码"
              maxlength="4"
            />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-img"
              @click="fetchCaptcha"
              title="点击刷新"
            />
          </div>
        </div>

        <button
          class="btn-login"
          type="submit"
          :disabled="loading || !form.username || !form.password || !form.captcha"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1a1a2e;
}

.login-card {
  width: 380px;
  padding: 40px 36px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 24px;
}

.login-icon {
  font-size: 2.5rem;
  display: block;
  margin-bottom: 8px;
}

.login-header h1 {
  margin: 0 0 4px;
  font-size: 1.3rem;
  color: #1a1a2e;
}

.login-header p {
  margin: 0;
  font-size: 0.85rem;
  color: #888;
}

.error-msg {
  background: #fff1f0;
  color: #cf1322;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 0.85rem;
  margin-bottom: 16px;
  text-align: center;
  border: 1px solid #ffa39e;
}

.form-group {
  margin-bottom: 14px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 0.85rem;
  color: #555;
  font-weight: 500;
}

.form-group input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.9rem;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.form-group input:focus {
  border-color: #4a90d9;
}

.captcha-row {
  display: flex;
  gap: 10px;
}

.captcha-row input {
  flex: 1;
}

.captcha-img {
  height: 42px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid #e0e0e0;
}

.btn-login {
  width: 100%;
  padding: 12px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  margin-top: 4px;
}

.btn-login:hover:not(:disabled) {
  background: #2a2a4e;
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>