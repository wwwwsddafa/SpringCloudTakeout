<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import request from '@/api/index'
import { getErrorMsg } from '@/utils/error'

const sendDate = ref(new Date(Date.now() - 86400000).toISOString().split('T')[0])
const sending = ref(false)
const saving = ref(false)
const loadingConfig = ref(false)

const sendTime = ref('12:00')
const adminEmail = ref('')

const currentTime = ref('')
const currentEmail = ref('')

function timeToCron(time: string): string {
  const [h, m] = time.split(':')
  return `0 ${parseInt(m)} ${parseInt(h)} * * ?`
}

function cronToTime(cron: string): string {
  const parts = cron.split(' ')
  if (parts.length >= 3) {
    const h = parts[2].padStart(2, '0')
    const m = parts[1].padStart(2, '0')
    return `${h}:${m}`
  }
  return ''
}

const handleSend = async () => {
  if (!sendDate.value) {
    alert('请选择日期')
    return
  }
  sending.value = true
  try {
    const res = await request.post('/ops/report/send', null, {
      params: { date: sendDate.value },
    })
    alert(res.data.data || '报告已发送')
  } catch (err: any) {
    alert(getErrorMsg(err, '发送失败'))
  } finally {
    sending.value = false
  }
}

const handleRefreshConfig = async () => {
  loadingConfig.value = true
  try {
    const res = await request.get('/ops/report/config')
    const data = res.data.data
    if (data) {
      currentTime.value = cronToTime(data.cron || '')
      currentEmail.value = data.adminEmail || ''
      sendTime.value = cronToTime(data.cron || '') || '12:00'
      adminEmail.value = data.adminEmail || ''
    }
  } catch (err: any) {
    console.error('读取配置失败:', err)
  } finally {
    loadingConfig.value = false
  }
}

const handleSaveConfig = async () => {
  if (!sendTime.value) {
    alert('请设置发送时间')
    return
  }
  saving.value = true
  try {
    const cron = timeToCron(sendTime.value)
    const res = await request.post('/ops/report/config', null, {
      params: {
        cron,
        adminEmail: adminEmail.value,
      },
    })
    alert(res.data.data || '配置已保存，定时任务已生效')
    handleRefreshConfig()
  } catch (err: any) {
    alert(getErrorMsg(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  handleRefreshConfig()
})
</script>

<template>
  <AdminLayout>
    <div class="ops-report-container">
      <!-- 手动发送报告 -->
      <div class="section">
        <div class="section-header">
          <h3 class="section-title">手动发送运营报告</h3>
        </div>
        <div class="send-form">
          <div class="form-row">
            <label class="form-label">统计日期</label>
            <input
              type="date"
              v-model="sendDate"
              :max="new Date().toISOString().split('T')[0]"
              class="date-input"
            />
            <button class="btn btn-primary" :disabled="sending" @click="handleSend">
              {{ sending ? '生成中...' : '立即发送报告' }}
            </button>
            <span class="tip">报告将发送至配置的管理员邮箱</span>
          </div>
        </div>
      </div>

      <!-- 定时发送配置 -->
      <div class="section" style="margin-top: 20px">
        <div class="section-header">
          <h3 class="section-title">定时发送配置</h3>
        </div>

        <div class="config-form">
          <div class="form-group">
            <label class="form-label">每天发送时间</label>
            <input type="time" v-model="sendTime" class="time-input" />
            <span class="tip tip-time">精确到分钟，如 08:30 表示每天早上 8:30 发送</span>
          </div>

          <div class="form-group">
            <label class="form-label">管理员邮箱</label>
            <input
              v-model="adminEmail"
              type="email"
              placeholder="your@email.com"
              class="text-input email-input"
            />
            <span class="tip">报告将发送到此邮箱</span>
          </div>

          <div class="form-actions">
            <button class="btn btn-success" :disabled="saving" @click="handleSaveConfig">
              {{ saving ? '保存中...' : '保存并生效' }}
            </button>
            <button class="btn btn-outline" :disabled="loadingConfig" @click="handleRefreshConfig">
              {{ loadingConfig ? '加载中...' : '刷新当前配置' }}
            </button>
          </div>
        </div>

        <hr class="divider" />

        <!-- 当前配置 -->
        <div class="current-config">
          <h4 class="config-heading">当前生效配置</h4>
          <div class="config-info">
            <div class="config-item">
              <span class="config-key">发送时间</span>
              <span class="config-value tag-success">
                {{ currentTime ? '每天 ' + currentTime + ' 自动发送' : '未设置' }}
              </span>
            </div>
            <div class="config-item">
              <span class="config-key">管理员邮箱</span>
              <span class="config-value tag-primary">{{ currentEmail || '未设置' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.ops-report-container {
  max-width: 700px;
}

.section {
  background: #fff;
  border-radius: 8px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.section-header {
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.send-form,
.config-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.form-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.form-label {
  font-size: 14px;
  color: #555;
  min-width: 100px;
  font-weight: 500;
}

.form-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
}

.date-input {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
}

.time-input {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  width: 140px;
}

.text-input {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
}

.email-input {
  width: 260px;
}

.tip {
  color: #999;
  font-size: 12px;
}

.tip-time {
  color: #aaa;
}

.btn {
  padding: 8px 20px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  background: #fff;
  color: #333;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.btn-primary:hover:not(:disabled) {
  background: #40a9ff;
  border-color: #40a9ff;
}

.btn-success {
  background: #52c41a;
  color: #fff;
  border-color: #52c41a;
}

.btn-success:hover:not(:disabled) {
  background: #73d13d;
  border-color: #73d13d;
}

.btn-outline {
  background: #fff;
  color: #555;
  border-color: #d9d9d9;
}

.btn-outline:hover:not(:disabled) {
  color: #1890ff;
  border-color: #1890ff;
}

.divider {
  margin: 20px 0;
  border: none;
  border-top: 1px solid #f0f0f0;
}

.config-heading {
  margin: 0 0 12px;
  font-size: 14px;
  color: #666;
}

.config-info {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.config-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.config-key {
  font-size: 13px;
  color: #999;
}

.config-value {
  font-size: 13px;
  padding: 3px 10px;
  border-radius: 4px;
  font-weight: 500;
}

.tag-success {
  background: #f6ffed;
  color: #389e0d;
  border: 1px solid #b7eb8f;
}

.tag-primary {
  background: #e6f7ff;
  color: #1890ff;
  border: 1px solid #91d5ff;
}
</style>