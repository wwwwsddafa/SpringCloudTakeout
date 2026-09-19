<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import AppLayout from '@/components/AppLayout.vue'

const router = useRouter()
const cartStore = useCartStore()

function goToProduct(fid: string) {
  router.push(`/product/${fid}`)
}

async function handleIncrease(fid: string, currentNum: number) {
  await cartStore.updateItem(fid, currentNum + 1)
}

async function handleDecrease(fid: string, currentNum: number) {
  if (currentNum <= 1) {
    if (confirm('确定要删除该商品吗？')) {
      await cartStore.removeItem(fid)
    }
    return
  }
  await cartStore.updateItem(fid, currentNum - 1)
}

async function handleRemove(fid: string) {
  if (confirm('确定要删除该商品吗？')) {
    await cartStore.removeItem(fid)
  }
}

async function handleClear() {
  if (confirm('确定要清空购物车吗？')) {
    await cartStore.clearAll()
  }
}

function goToOrder() {
  router.push('/order/create')
}

onMounted(() => {
  cartStore.fetchCart()
})
</script>

<template>
  <AppLayout>
    <div class="cart-page">
      <div class="content-wrapper">
        <div class="cart-header">
          <h2>购物车</h2>
          <button
            v-if="cartStore.items.length > 0"
            class="btn-clear"
            @click="handleClear"
          >
            清空购物车
          </button>
        </div>

        <div v-if="cartStore.items.length === 0" class="empty-state">
          <p class="empty-icon">🛒</p>
          <p>购物车是空的</p>
          <router-link to="/" class="btn-go-shop">去逛逛</router-link>
        </div>

        <template v-else>
          <div class="cart-list">
            <div
              v-for="item in cartStore.items"
              :key="item.fid"
              class="cart-item"
            >
              <div class="item-img" @click="goToProduct(item.fid)">
                <img
                  :src="item.fphoto"
                  :alt="item.fname"
                  @error="($event.target as HTMLImageElement).src = 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2280%22 height=%2280%22><rect fill=%22%23eee%22 width=%2280%22 height=%2280%22/></svg>'"
                />
              </div>
              <div class="item-info">
                <h4 class="item-name" @click="goToProduct(item.fid)">{{ item.fname }}</h4>
                <span class="item-price">¥{{ item.realprice }}</span>
              </div>
              <div class="item-actions">
                <div class="quantity-control">
                  <button @click="handleDecrease(item.fid, item.num)">-</button>
                  <span class="quantity-num">{{ item.num }}</span>
                  <button @click="handleIncrease(item.fid, item.num)">+</button>
                </div>
                <button class="btn-remove" @click="handleRemove(item.fid)">删除</button>
              </div>
              <div class="item-subtotal">
                ¥{{ (item.realprice * item.num).toFixed(2) }}
              </div>
            </div>
          </div>

          <div class="cart-footer">
            <div class="total-info">
              <span>共 {{ cartStore.totalCount }} 件商品</span>
              <span class="total-price">合计：¥{{ cartStore.totalPrice.toFixed(2) }}</span>
            </div>
            <button class="btn-checkout" @click="goToOrder">去结算</button>
          </div>
        </template>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.cart-page {
  min-height: calc(100vh - 60px);
}

.content-wrapper {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 20px;
}

.cart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.cart-header h2 {
  margin: 0;
  font-size: 1.5rem;
  color: #333;
}

.btn-clear {
  background: none;
  border: 1px solid #ddd;
  color: #999;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.btn-clear:hover {
  border-color: #e53e3e;
  color: #e53e3e;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #999;
}

.empty-icon {
  font-size: 3.5rem;
  margin-bottom: 12px;
}

.btn-go-shop {
  display: inline-block;
  margin-top: 16px;
  padding: 10px 32px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  text-decoration: none;
  border-radius: 8px;
  font-weight: 600;
}

.cart-list {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.cart-item {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  gap: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.cart-item:last-child {
  border-bottom: none;
}

.item-img {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
  background: #f0f0f0;
}

.item-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  margin: 0 0 6px;
  font-size: 1rem;
  color: #333;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-name:hover {
  color: #ff6b35;
}

.item-price {
  font-size: 0.95rem;
  color: #ff6b35;
  font-weight: 600;
}

.item-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quantity-control button {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1.5px solid #ddd;
  background: #fff;
  font-size: 0.9rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.quantity-control button:hover {
  border-color: #ff6b35;
  color: #ff6b35;
}

.quantity-num {
  font-size: 0.95rem;
  font-weight: 600;
  min-width: 24px;
  text-align: center;
}

.btn-remove {
  background: none;
  border: none;
  color: #bbb;
  font-size: 0.8rem;
  cursor: pointer;
  padding: 2px 6px;
}

.btn-remove:hover {
  color: #e53e3e;
}

.item-subtotal {
  font-size: 1rem;
  font-weight: 600;
  color: #333;
  min-width: 70px;
  text-align: right;
  flex-shrink: 0;
}

.cart-footer {
  background: #fff;
  border-radius: 12px;
  margin-top: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.total-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.9rem;
  color: #888;
}

.total-price {
  font-size: 1.3rem;
  font-weight: 700;
  color: #ff6b35;
}

.btn-checkout {
  padding: 14px 40px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-checkout:hover {
  opacity: 0.9;
}

@media (max-width: 768px) {
  .cart-item {
    flex-wrap: wrap;
    padding: 12px;
    gap: 10px;
  }

  .item-subtotal {
    width: 100%;
    text-align: right;
  }

  .cart-footer {
    flex-direction: column;
    gap: 16px;
  }

  .btn-checkout {
    width: 100%;
  }
}
</style>