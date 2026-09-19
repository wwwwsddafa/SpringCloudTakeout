<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductDetail } from '@/api/product'
import { updateProduct } from '@/api/admin'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import { getErrorMsg } from '@/utils/error'

const route = useRoute()
const router = useRouter()

const form = ref({
  fname: '',
  normprice: 0,
  realprice: 0,
  detail: '',
  category: 'hot',
})
const loading = ref(true)
const submitting = ref(false)
const errorMsg = ref('')

const photoFiles = ref<File[]>([])
const previewUrls = ref<string[]>([])

function handleFileChange(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files) return
  photoFiles.value = Array.from(files)
  previewUrls.value = photoFiles.value.map((f) => URL.createObjectURL(f))
}

async function fetchProduct() {
  loading.value = true
  try {
    const fid = route.params.fid as string
    const res = await getProductDetail(fid)
    const p = res.data.data
    form.value = {
      fname: p.fname,
      normprice: p.normprice,
      realprice: p.realprice,
      detail: p.detail,
      category: p.category,
    }
  } catch (err: any) {
    alert(getErrorMsg(err, '商品不存在'))
    router.push('/admin/products')
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!form.value.fname) {
    errorMsg.value = '请输入商品名称'
    return
  }
  if (form.value.realprice <= 0) {
    errorMsg.value = '请输入有效现价'
    return
  }

  submitting.value = true
  errorMsg.value = ''
  try {
    const fid = route.params.fid as string
    const fd = new FormData()
    fd.append('fid', fid)
    fd.append('fname', form.value.fname)
    fd.append('normprice', String(form.value.normprice))
    fd.append('realprice', String(form.value.realprice))
    fd.append('detail', form.value.detail)
    fd.append('category', form.value.category)
    photoFiles.value.forEach((file) => {
      fd.append('photo', file)
    })

    await updateProduct(fd)
    alert('更新成功')
    router.push('/admin/products')
  } catch (err: any) {
    errorMsg.value = getErrorMsg(err, '更新失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/admin/products')
}

onMounted(() => {
  fetchProduct()
})
</script>

<template>
  <AdminLayout>
    <div class="form-page">
      <div class="form-header">
        <h3>编辑商品</h3>
        <button class="btn btn-outline" @click="goBack">← 返回列表</button>
      </div>

      <div v-if="loading" class="state-box">
        <p>加载中...</p>
      </div>

      <div v-else class="card form-card">
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <div class="form-group">
          <label>商品名称 <span class="required">*</span></label>
          <input v-model="form.fname" type="text" class="input" />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>原价</label>
            <input v-model.number="form.normprice" type="number" step="0.01" min="0" class="input" />
          </div>
          <div class="form-group">
            <label>现价 <span class="required">*</span></label>
            <input v-model.number="form.realprice" type="number" step="0.01" min="0" class="input" />
          </div>
        </div>

        <div class="form-group">
          <label>分类</label>
          <select v-model="form.category" class="input">
            <option value="hot">热销</option>
            <option value="new">新品</option>
            <option value="promo">促销</option>
          </select>
        </div>

        <div class="form-group">
          <label>商品图片（不选则保留原图）</label>
          <input type="file" multiple accept="image/*" @change="handleFileChange" class="input" />
          <div v-if="previewUrls.length" class="preview-list">
            <img v-for="(url, i) in previewUrls" :key="i" :src="url" class="preview-img" />
          </div>
        </div>

        <div class="form-group">
          <label>商品详情</label>
          <textarea v-model="form.detail" rows="4" class="input"></textarea>
        </div>

        <button class="btn btn-primary btn-block" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? '提交中...' : '保存修改' }}
        </button>
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.form-page {
  max-width: 640px;
}

.form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.form-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #333;
}

.card {
  background: #fff;
  border-radius: 8px;
}

.form-card {
  padding: 24px;
}

.state-box {
  text-align: center;
  padding: 60px 20px;
  color: #999;
  background: #fff;
  border-radius: 8px;
}

.error-msg {
  background: #fff1f0;
  color: #cf1322;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 0.9rem;
  margin-bottom: 20px;
  border: 1px solid #ffa39e;
}

.form-group {
  margin-bottom: 18px;
}

.form-group label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: #555;
  margin-bottom: 6px;
}

.required {
  color: #cf1322;
}

.input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 0.9rem;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
  transition: border-color 0.15s;
}

.input:focus {
  border-color: #4a90d9;
}

textarea.input {
  resize: vertical;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.preview-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.preview-img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.btn {
  padding: 8px 18px;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.15s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #4a90d9;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #3a7bc8;
}

.btn-outline {
  background: #fff;
  color: #4a90d9;
  border: 1px solid #4a90d9;
}

.btn-outline:hover:not(:disabled) {
  background: #e8f0fe;
}

.btn-block {
  width: 100%;
  padding: 12px;
  font-size: 0.95rem;
}
</style>