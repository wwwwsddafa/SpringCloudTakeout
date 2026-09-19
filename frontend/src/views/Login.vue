<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCaptcha } from '@/api/user'
import { cleanCaptchaImage } from '@/utils'
import AppLayout from '@/components/AppLayout.vue'

// ==================== QQ 登录配置 ====================
// 请替换为你在 QQ 互联 (https://connect.qq.com) 申请的实际参数
// const QQ_APP_ID = '1905492838'
// const QQ_REDIRECT_URI = encodeURIComponent(window.location.origin + '/login')
// const QQ_OAUTH_URL = `https://graph.qq.com/oauth2.0/authorize?response_type=code&client_id=${QQ_APP_ID}&redirect_uri=${QQ_REDIRECT_URI}&state=qq_login`
import { getErrorMsg } from '@/utils/error'

const QQ_OAUTH_URL = 'https://qq.wch666.com/api/qq.php?token=fec338fd29ed5c087da6b001323ad0cd&msg=666&display=pc'



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
const captchaLoading = ref(false)
const qqLoading = ref(false)
const errorMsg = ref('')

async function fetchCaptcha() {
  captchaLoading.value = true
  errorMsg.value = ''
  try {
    const res = await getCaptcha()
    const data = res.data.data
    captchaImage.value = cleanCaptchaImage(data.captchaImage)
    form.value.captchaKey = data.captchaKey
    form.value.captcha = ''
  } catch (err: any) {
    errorMsg.value = getErrorMsg(err, '获取验证码失败')
  } finally {
    captchaLoading.value = false
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
    await userStore.loginAction({
      username: form.value.username,
      password: form.value.password,
      captcha: form.value.captcha,
      captchaKey: form.value.captchaKey,
    })
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (err: any) {
    const msg = getErrorMsg(err, '登录失败')
    errorMsg.value = msg
    fetchCaptcha()
  } finally {
    loading.value = false
  }
}

function handleQQLogin() {
  window.location.href = QQ_OAUTH_URL
}

async function handleQQCallback(code: string) {
  qqLoading.value = true
  errorMsg.value = ''
  try {
    await userStore.qqLoginAction(code)
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (err: any) {
    const msg = getErrorMsg(err, 'QQ 登录失败')
    errorMsg.value = msg
    router.replace('/login')
  } finally {
    qqLoading.value = false
  }
}

function goToRegister() {
  router.push('/register')
}

onMounted(() => {
  const code = route.query.code as string
  if (code) {
    handleQQCallback(code)
  } else {
    fetchCaptcha()
  }
})
</script>

<template>
  <AppLayout>
    <div class="login-page">
      <div class="login-card">
        <h2 class="login-title">用户登录</h2>

        <!-- QQ 登录回调加载中 -->
        <div v-if="qqLoading" class="qq-loading">
          <div class="spinner"></div>
          <p>QQ 登录中，请稍候...</p>
        </div>

        <template v-else>
          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

          <form class="login-form" @submit.prevent="handleLogin">
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

            <div class="form-group">
              <label>验证码</label>
              <div class="captcha-row">
                <input
                  v-model="form.captcha"
                  type="text"
                  placeholder="请输入验证码"
                  maxlength="4"
                  class="captcha-input"
                />
                <div class="captcha-img-wrap" @click="fetchCaptcha" :class="{ loading: captchaLoading }">
                  <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                  <span v-else class="captcha-placeholder">点击获取</span>
                </div>
              </div>
            </div>

            <button type="submit" class="btn-submit" :disabled="loading">
              {{ loading ? '登录中...' : '登录' }}
            </button>
          </form>

          <div class="divider">
            <span class="divider-line"></span>
            <span class="divider-text">其他登录方式</span>
            <span class="divider-line"></span>
          </div>

          <div class="social-login">
            <button class="btn-qq-login" @click="handleQQLogin" title="QQ 登录">
              <svg class="qq-icon" viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                <path d="M12.003 2c-2.265 0-6.29 1.364-6.29 7.325v1.195S3.55 14.96 3.55 17.474c0 1.515 1.08 2.526 2.426 2.526.515 0 1.038-.185 1.426-.487.647-.502 1.055-1.258 1.055-2.039 0-.78-.408-1.536-1.055-2.039.185-.485.647-1.55 1.036-2.625.205.18.463.334.74.437.036.464.125.936.267 1.416-.388.502-.647 1.258-.647 2.039 0 .78.408 1.536 1.055 2.039.388.302.91.487 1.426.487 1.346 0 2.426-1.01 2.426-2.526 0-2.514-2.163-6.954-2.163-6.954V9.325C18.293 3.364 14.268 2 12.003 2z"/>
              </svg>
              QQ 登录
            </button>
          </div>

          <p class="switch-link">
            还没有账号？
            <a href="javascript:void(0)" @click="goToRegister">立即注册</a>
          </p>
        </template>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.login-page {
  min-height: calc(100vh - 60px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: linear-gradient(135deg, #fff5f0 0%, #fff 100%);
}

.login-card {
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.login-title {
  text-align: center;
  margin: 0 0 28px;
  font-size: 1.5rem;
  color: #333;
}

.error-msg {
  background: #fff0f0;
  color: #e53e3e;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.9rem;
  margin-bottom: 16px;
  text-align: center;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 0.9rem;
  font-weight: 600;
  color: #555;
  margin-bottom: 6px;
}

.form-group input {
  width: 100%;
  padding: 12px 14px;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s;
  box-sizing: border-box;
  outline: none;
}

.form-group input:focus {
  border-color: #ff6b35;
}

.captcha-row {
  display: flex;
  gap: 12px;
}

.captcha-input {
  flex: 1;
  width: auto !important;
}

.captcha-img-wrap {
  width: 110px;
  height: 44px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 1.5px solid #e0e0e0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9f9f9;
}

.captcha-img-wrap img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.captcha-img-wrap.loading {
  opacity: 0.6;
  pointer-events: none;
}

.captcha-placeholder {
  font-size: 0.8rem;
  color: #999;
}

.btn-submit {
  width: 100%;
  padding: 13px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
  margin-top: 8px;
}

.btn-submit:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.switch-link {
  text-align: center;
  margin-top: 20px;
  font-size: 0.9rem;
  color: #888;
}

.switch-link a {
  color: #ff6b35;
  text-decoration: none;
  font-weight: 600;
}

.switch-link a:hover {
  text-decoration: underline;
}

.qq-loading {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #eee;
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 12px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.divider {
  display: flex;
  align-items: center;
  margin: 24px 0 20px;
  gap: 12px;
}

.divider-line {
  flex: 1;
  height: 1px;
  background: #e0e0e0;
}

.divider-text {
  font-size: 0.8rem;
  color: #bbb;
  white-space: nowrap;
}

.social-login {
  display: flex;
  justify-content: center;
}

.btn-qq-login {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px;
  background: #12b7f5;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-qq-login:hover {
  background: #0ea0dd;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(18, 183, 245, 0.3);
}

.btn-qq-login:active {
  transform: translateY(0);
}

.qq-icon {
  flex-shrink: 0;
}
</style>