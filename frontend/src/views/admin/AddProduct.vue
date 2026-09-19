<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { addProduct } from '@/api/admin'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()

const form = ref({
  fname: '',
  normprice: 0,
  realprice: 0,
  detail: '',
  category: 'hot',
})
const photoFiles = ref<File[]>([])
const photoPreview = ref<string[]>([])
const submitting = ref(false)
const errorMsg = ref('')

function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  if (!target.files) return
  const files = Array.from(target.files)
  photoFiles.value = files
  photoPreview.value = files.map((f) => URL.createObjectURL(f))
}

function removePhoto(index: number) {
  photoFiles.value.splice(index, 1)
  photoPreview.value.splice(index, 1)
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
  if (photoFiles.value.length === 0) {
    errorMsg.value = '请上传商品图片'
    return
  }

  submitting.value = true
  errorMsg.value = ''
  try {
    const fd = new FormData()
    fd.append('fname', form.value.fname)
    fd.append('normprice', String(form.value.normprice))
    fd.append('realprice', String(form.value.realprice))
    fd.append('detail', form.value.detail)
    fd.append('category', form.value.category)
    photoFiles.value.forEach((f) => fd.append('photo', f))

    await addProduct(fd)
    alert('添加成功')
    router.push('/admin/products')
  } catch (err: any) {
    errorMsg.value = getErrorMsg(err, '添加失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/admin/products')
}
</script>

<template>
  <AdminLayout>
    <div class="form-page">
      <div class="form-header">
        <h3>添加商品</h3>
        <button class="btn btn-outline" @click="goBack">← 返回列表</button>
      </div>

      <div class="card form-card">
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <div class="form-group">
          <label>商品名称 <span class="required">*</span></label>
          <input v-model="form.fname" type="text" placeholder="请输入商品名称" class="input" />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>原价</label>
            <input v-model.number="form.normprice" type="number" step="0.01" min="0" placeholder="0.00" class="input" />
          </div>
          <div class="form-group">
            <label>现价 <span class="required">*</span></label>
            <input v-model.number="form.realprice" type="number" step="0.01" min="0" placeholder="0.00" class="input" />
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
          <label>商品详情</label>
          <textarea v-model="form.detail" placeholder="请输入商品详情描述" rows="4" class="input"></textarea>
        </div>

        <div class="form-group">
          <label>商品图片 <span class="required">*</span></label>
          <div class="upload-area">
            <input
              type="file"
              accept="image/*"
              multiple
              @change="handleFileChange"
              class="file-input"
              id="photo-upload"
            />
            <label for="photo-upload" class="upload-label">
              <span class="upload-icon">+</span>
              <span>点击上传图片</span>
            </label>
          </div>
          <div v-if="photoPreview.length > 0" class="preview-list">
            <div v-for="(url, idx) in photoPreview" :key="idx" class="preview-item">
              <img :src="url" alt="预览" />
              <button class="btn-remove-pic" @click="removePhoto(idx)">×</button>
            </div>
          </div>
        </div>

        <button class="btn btn-primary btn-block" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? '提交中...' : '提交' }}
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

.upload-area {
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  padding: 20px;
  text-align: center;
  transition: border-color 0.15s;
  cursor: pointer;
}

.upload-area:hover {
  border-color: #4a90d9;
}

.file-input {
  display: none;
}

.upload-label {
  cursor: pointer;
  color: #888;
  font-size: 0.9rem;
}

.upload-icon {
  display: block;
  font-size: 1.5rem;
  margin-bottom: 4px;
  color: #4a90d9;
}

.preview-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.preview-item {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e8e8e8;
}

.preview-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.btn-remove-pic {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  border: none;
  border-radius: 50%;
  font-size: 0.75rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
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