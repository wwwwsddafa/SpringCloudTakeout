<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminProductPage, changeProductStatus, deleteProduct } from '@/api/admin'
import type { Product } from '@/types'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()

const products = ref<Product[]>([])
const loading = ref(true)
const loadError = ref('')
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const actionFid = ref<string | null>(null)

async function fetchProducts() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getAdminProductPage({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
    })
    products.value = res.data.data.records
    total.value = res.data.data.total
  } catch (err: any) {
    products.value = []
    loadError.value = getErrorMsg(err, '加载商品失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  fetchProducts()
}

function onPageChange(newPage: number) {
  page.value = newPage
  fetchProducts()
}

async function handleStatusChange(product: Product) {
  const newStatus = product.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '上架' : '下架'
  if (!confirm(`确定要${action}该商品吗？`)) return

  actionFid.value = product.fid
  try {
    await changeProductStatus(product.fid, newStatus)
    await fetchProducts()
  } catch (err: any) {
    alert(getErrorMsg(err, `${action}失败`))
  } finally {
    actionFid.value = null
  }
}

async function handleDelete(product: Product) {
  if (product.status !== 0) {
    alert('只能删除已下架的商品')
    return
  }
  if (!confirm('确定要删除该商品吗？此操作不可恢复。')) return

  actionFid.value = product.fid
  try {
    await deleteProduct(product.fid)
    await fetchProducts()
  } catch (err: any) {
    alert(getErrorMsg(err, '删除失败'))
  } finally {
    actionFid.value = null
  }
}

function goToAdd() {
  router.push('/admin/products/add')
}

function goToEdit(fid: string) {
  router.push(`/admin/products/edit/${fid}`)
}

onMounted(() => {
  fetchProducts()
})
</script>

<template>
  <AdminLayout>
    <div class="toolbar">
      <div class="toolbar-left">
        <input
          v-model="keyword"
          type="text"
          class="search-input"
          placeholder="搜索商品名称..."
          @keyup.enter="onSearch"
        />
        <button class="btn btn-primary" @click="onSearch">搜索</button>
      </div>
      <button class="btn btn-primary" @click="goToAdd">+ 添加商品</button>
    </div>

    <div v-if="loading" class="state-box">
      <p>加载中...</p>
    </div>

    <div v-else-if="loadError" class="state-box error-box">
      <p>⚠️ {{ loadError }}</p>
      <button class="btn-retry" @click="fetchProducts">重试</button>
    </div>

    <div v-else-if="products.length === 0" class="state-box">
      <p>暂无商品数据</p>
    </div>

    <div v-else class="card">
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>原价</th>
            <th>现价</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in products" :key="p.fid">
            <td class="td-id">{{ p.fid }}</td>
            <td>{{ p.fname }}</td>
            <td>¥{{ p.normprice }}</td>
            <td>¥{{ p.realprice }}</td>
            <td>
              <span
                class="tag"
                :class="{
                  'tag-on': p.status === 1,
                  'tag-off': p.status === 0,
                  'tag-del': p.status === -1,
                }"
              >
                {{ p.status === 1 ? '上架' : p.status === 0 ? '下架' : '已删除' }}
              </span>
            </td>
            <td class="td-time">{{ p.createTime ? new Date(p.createTime).toLocaleDateString('zh-CN') : '-' }}</td>
            <td class="td-actions">
              <button
                v-if="p.status !== -1"
                class="btn btn-sm btn-outline"
                @click="goToEdit(p.fid)"
              >
                编辑
              </button>
              <button
                v-if="p.status === 1"
                class="btn btn-sm btn-warn"
                :disabled="actionFid === p.fid"
                @click="handleStatusChange(p)"
              >
                下架
              </button>
              <button
                v-if="p.status === 0"
                class="btn btn-sm btn-success"
                :disabled="actionFid === p.fid"
                @click="handleStatusChange(p)"
              >
                上架
              </button>
              <button
                v-if="p.status === 0"
                class="btn btn-sm btn-danger"
                :disabled="actionFid === p.fid"
                @click="handleDelete(p)"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="total > pageSize">
        <button
          class="btn btn-sm btn-outline"
          :disabled="page <= 1"
          @click="onPageChange(page - 1)"
        >
          上一页
        </button>
        <span class="page-num">{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
        <button
          class="btn btn-sm btn-outline"
          :disabled="page >= Math.ceil(total / pageSize)"
          @click="onPageChange(page + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-input {
  padding: 8px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 0.9rem;
  width: 220px;
  outline: none;
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: #4a90d9;
}

.card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.table thead {
  background: #fafafa;
}

.table th {
  padding: 12px 14px;
  text-align: left;
  font-weight: 600;
  color: #555;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}

.table td {
  padding: 12px 14px;
  border-bottom: 1px solid #f5f5f5;
  color: #333;
}

.table tbody tr:hover {
  background: #fafbff;
}

.td-id {
  font-family: monospace;
  font-size: 0.8rem;
  color: #888;
}

.td-time {
  color: #999;
  font-size: 0.85rem;
}

.td-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 500;
}

.tag-on {
  background: #e6f7e9;
  color: #389e0d;
}

.tag-off {
  background: #fff7e6;
  color: #d48806;
}

.tag-del {
  background: #fff1f0;
  color: #cf1322;
}

.state-box {
  text-align: center;
  padding: 60px 20px;
  color: #999;
  background: #fff;
  border-radius: 8px;
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

.btn-sm {
  padding: 4px 12px;
  font-size: 0.8rem;
}

.btn-outline {
  background: #fff;
  color: #4a90d9;
  border: 1px solid #4a90d9;
}

.btn-outline:hover:not(:disabled) {
  background: #e8f0fe;
}

.btn-warn {
  background: #faad14;
  color: #fff;
}

.btn-warn:hover:not(:disabled) {
  background: #e8a400;
}

.btn-success {
  background: #52c41a;
  color: #fff;
}

.btn-success:hover:not(:disabled) {
  background: #49b016;
}

.btn-danger {
  background: #ff4d4f;
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #e53e3e;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid #f0f0f0;
}

.page-num {
  font-size: 0.9rem;
  color: #666;
}

.error-state {
  text-align: center;
  padding: 60px 20px;
  color: #c53030;
}

.error-icon {
  font-size: 3rem;
  margin: 0 0 12px;
}

.error-state p {
  margin: 0 0 16px;
  color: #c53030;
}

.error-box {
  color: #c53030;
}

.btn-retry {
  padding: 8px 24px;
  background: #fff;
  color: #c53030;
  border: 1px solid #c53030;
  border-radius: 8px;
  font-size: 0.9rem;
  cursor: pointer;
}

.btn-retry:hover {
  background: #c53030;
  color: #fff;
}

</style>