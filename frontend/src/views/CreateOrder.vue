<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { createOrder } from '@/api/order'
import { getUsableFreeOrderCoupons } from '@/api/freeOrder'
import type { FreeOrderCoupon } from '@/types'
import AppLayout from '@/components/AppLayout.vue'
import { getErrorMsg } from '@/utils/error'

const router = useRouter()
const cartStore = useCartStore()

const form = ref({
  address: '',
  tel: '',
  deliveryType: 'now',
  payment: 'alipay',
  ps: '',
})
const submitting = ref(false)
const errorMsg = ref('')

const coupons = ref<FreeOrderCoupon[]>([])
const selectedCouponNo = ref('')
const couponLoading = ref(false)
const couponError = ref('')

const selectedCoupon = computed(() =>
  coupons.value.find((c) => c.couponNo === selectedCouponNo.value)
)

const discountAmount = computed(() => {
  if (!selectedCoupon.value) return 0
  return Math.min(selectedCoupon.value.couponAmount, cartStore.totalPrice)
})

const finalPrice = computed(() =>
  Math.max(0, cartStore.totalPrice - discountAmount.value)
)

async function fetchCoupons() {
  couponLoading.value = true
  couponError.value = ''
  try {
    const res = await getUsableFreeOrderCoupons()
    coupons.value = res.data.data
  } catch (err: any) {
    coupons.value = []
    couponError.value = getErrorMsg(err, '加载免单券失败')
  } finally {
    couponLoading.value = false
  }
}

function selectCoupon(couponNo: string) {
  if (selectedCouponNo.value === couponNo) {
    selectedCouponNo.value = ''
  } else {
    selectedCouponNo.value = couponNo
  }
}

onMounted(() => {
  cartStore.fetchCart()
  fetchCoupons()
})

async function handleSubmit() {
  if (!form.value.address || !form.value.tel) {
    errorMsg.value = '请填写收货地址和联系电话'
    return
  }
  if (cartStore.items.length === 0) {
    errorMsg.value = '购物车为空，请先添加商品'
    return
  }

  submitting.value = true
  errorMsg.value = ''
  try {
    const orderItems = cartStore.items.map((item) => ({
      fid: item.fid,
      quantity: item.num,
    }))
    const res = await createOrder({
      orderItems,
      address: form.value.address,
      tel: form.value.tel,
      deliveryType: form.value.deliveryType,
      payment: form.value.payment,
      remark: form.value.ps || undefined,
      freeOrderNo: selectedCouponNo.value || undefined,
      originalAmount: cartStore.totalPrice,
      expectAmount: finalPrice.value,
    })
    await cartStore.clearAll()
    const order = res.data.data
    router.push(`/order/${order.roid}`)
  } catch (err: any) {
    errorMsg.value = getErrorMsg(err, '下单失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}
</script>

<template>
  <AppLayout>
    <div class="order-page">
      <div class="content-wrapper">
        <div class="page-header">
          <button class="btn-back" @click="goBack">← 返回</button>
          <h2>确认订单</h2>
        </div>

        <div class="order-layout">
          <div class="order-form-section">
            <div class="form-card">
              <h3>收货信息</h3>
              <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
              <div class="form-group">
                <label>收货地址 <span class="required">*</span></label>
                <input
                  v-model="form.address"
                  type="text"
                  placeholder="请输入收货地址"
                />
              </div>
              <div class="form-group">
                <label>联系电话 <span class="required">*</span></label>
                <input
                  v-model="form.tel"
                  type="tel"
                  placeholder="请输入联系电话"
                />
              </div>
              <div class="form-group">
                <label>配送方式</label>
                <select v-model="form.deliveryType">
                  <option value="now">立即配送</option>
                </select>
              </div>
              <div class="form-group">
                <label>支付方式</label>
                <select v-model="form.payment">
                  <option value="alipay">支付宝</option>
                  <option value="wechat">微信支付</option>
                </select>
              </div>
              <div class="form-group">
                <label>备注</label>
                <textarea
                  v-model="form.ps"
                  placeholder="如有特殊要求请备注（选填）"
                  rows="3"
                ></textarea>
              </div>
            </div>
          </div>

          <div class="order-summary-section">
            <div class="summary-card">
              <h3>订单商品</h3>
              <div v-if="cartStore.items.length === 0" class="empty-cart">
                购物车为空，请先去添加商品
              </div>
              <div v-else class="summary-items">
                <div
                  v-for="item in cartStore.items"
                  :key="item.fid"
                  class="summary-item"
                >
                  <span class="si-name">{{ item.fname }}</span>
                  <span class="si-num">x{{ item.num }}</span>
                  <span class="si-price">¥{{ (item.realprice * item.num).toFixed(2) }}</span>
                </div>
              </div>
              <div class="summary-total">
                <span>商品合计</span>
                <span class="total-amount">¥{{ cartStore.totalPrice.toFixed(2) }}</span>
              </div>
              <div v-if="discountAmount > 0" class="summary-discount">
                <span>免单券抵扣</span>
                <span class="discount-amount">-¥{{ discountAmount.toFixed(2) }}</span>
              </div>
              <div class="summary-total final">
                <span>应付金额</span>
                <span class="final-amount">¥{{ finalPrice.toFixed(2) }}</span>
              </div>
              <div v-if="couponError" class="coupon-error">
                ⚠️ {{ couponError }}
              </div>
              <div v-if="coupons.length > 0" class="coupon-section">
                <h4>免单券</h4>
                <div class="coupon-list">
                  <div
                    v-for="coupon in coupons"
                    :key="coupon.couponNo"
                    class="coupon-item"
                    :class="{ selected: selectedCouponNo === coupon.couponNo }"
                    @click="selectCoupon(coupon.couponNo)"
                  >
                    <div class="coupon-info">
                      <span class="coupon-name">{{ coupon.eventName }}</span>
                      <span class="coupon-amount">¥{{ coupon.couponAmount }}（范围 ¥{{ coupon.minAmount }}~¥{{ coupon.maxAmount }}）</span>
                    </div>
                  </div>
                </div>
              </div>
              <button
                class="btn-submit"
                :disabled="submitting || cartStore.items.length === 0"
                @click="handleSubmit"
              >
                {{ submitting ? '提交中...' : '提交订单' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.order-page {
  min-height: calc(100vh - 60px);
}

.content-wrapper {
  max-width: 1000px;
  margin: 0 auto;
  padding: 32px 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 1.5rem;
  color: #333;
}

.btn-back {
  background: none;
  border: none;
  color: #ff6b35;
  font-size: 1rem;
  cursor: pointer;
  padding: 4px 8px;
}

.order-layout {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
  align-items: start;
}

.form-card,
.summary-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.form-card h3,
.summary-card h3 {
  margin: 0 0 20px;
  font-size: 1.1rem;
  color: #333;
}

.error-msg {
  background: #fff0f0;
  color: #e53e3e;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.9rem;
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 18px;
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

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: border-color 0.2s;
  box-sizing: border-box;
  outline: none;
  font-family: inherit;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  border-color: #ff6b35;
}

.summary-items {
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 12px;
  margin-bottom: 12px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 0.9rem;
}

.si-name {
  flex: 1;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.si-num {
  color: #999;
  min-width: 30px;
}

.si-price {
  color: #333;
  font-weight: 600;
  min-width: 60px;
  text-align: right;
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  font-size: 1rem;
  color: #333;
}

.total-amount {
  font-size: 1.5rem;
  font-weight: 700;
  color: #ff6b35;
}

.summary-discount {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 0.95rem;
  color: #555;
}

.discount-amount {
  font-size: 1.1rem;
  font-weight: 600;
  color: #38a169;
}

.summary-total.final {
  border-top: 1px dashed #e0e0e0;
  margin-top: 4px;
  padding-top: 12px;
}

.final-amount {
  font-size: 1.5rem;
  font-weight: 700;
  color: #e53e3e;
}

.coupon-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
  margin-bottom: 8px;
}

.coupon-section h4 {
  margin: 0 0 10px;
  font-size: 0.9rem;
  color: #555;
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.coupon-item {
  padding: 10px 14px;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.coupon-item:hover {
  border-color: #ff6b35;
}

.coupon-item.selected {
  border-color: #ff6b35;
  background: #fff8f4;
}

.coupon-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.coupon-name {
  font-size: 0.85rem;
  color: #333;
  font-weight: 600;
}

.coupon-amount {
  font-size: 0.85rem;
  color: #ff6b35;
  font-weight: 700;
}

.btn-submit {
  width: 100%;
  padding: 14px;
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
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-cart {
  text-align: center;
  padding: 32px 0;
  color: #999;
  font-size: 0.9rem;
}

@media (max-width: 768px) {
  .order-layout {
    grid-template-columns: 1fr;
  }
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

.coupon-error {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fff5f5;
  border: 1px solid #feb2b2;
  border-radius: 6px;
  color: #c53030;
  font-size: 0.85rem;
}

</style>