<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCaptcha } from '@/api/user'
import { cleanCaptchaImage } from '@/utils'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()
const userStore = useUserStore()

const form = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  captcha: '',
  captchaKey: '',
})
const captchaImage = ref('')
const loading = ref(false)
const captchaLoading = ref(false)
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

async function handleRegister() {
  if (!form.value.username || !form.value.password || !form.value.captcha) {
    errorMsg.value = '请填写完整信息'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    errorMsg.value = '两次密码输入不一致'
    return
  }
  if (form.value.password.length < 6) {
    errorMsg.value = '密码至少6位'
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    await userStore.registerAction({
      username: form.value.username,
      password: form.value.password,
      email: form.value.email || undefined,
      captcha: form.value.captcha,
      captchaKey: form.value.captchaKey,
    })
    alert('注册成功，请登录')
    router.push('/login')
  } catch (err: any) {
    const msg = getErrorMsg(err, '注册失败')
    errorMsg.value = msg
    fetchCaptcha()
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push('/login')
}

onMounted(() => {
  fetchCaptcha()
})
</script>

<template>
  <AppLayout>
    <div class="register-page">
      <div class="register-card">
        <h2 class="register-title">用户注册</h2>

        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <form class="register-form" @submit.prevent="handleRegister">
          <div class="form-group">
            <label>用户名 <span class="required">*</span></label>
            <input v-model="form.username" type="text" placeholder="请输入用户名" autocomplete="username" />
          </div>

          <div class="form-group">
            <label>密码 <span class="required">*</span></label>
            <input
              v-model="form.password"
              type="password"
              placeholder="请输入密码（至少6位）"
              autocomplete="new-password"
            />
          </div>

          <div class="form-group">
            <label>确认密码 <span class="required">*</span></label>
            <input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              autocomplete="new-password"
            />
          </div>

          <div class="form-group">
            <label>邮箱</label>
            <input v-model="form.email" type="email" placeholder="请输入邮箱（选填）" />
          </div>

          <div class="form-group">
            <label>验证码 <span class="required">*</span></label>
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
            {{ loading ? '注册中...' : '注册' }}
          </button>
        </form>

        <p class="switch-link">
          已有账号？
          <a href="javascript:void(0)" @click="goToLogin">立即登录</a>
        </p>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.register-page {
  min-height: calc(100vh - 60px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: linear-gradient(135deg, #fff5f0 0%, #fff 100%);
}

.register-card {
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.register-title {
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

.required {
  color: #e53e3e;
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
</style>