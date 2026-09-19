import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CartItem } from '@/types'
import { getCartList, addToCart, updateCartItem, removeCartItem, clearCart } from '@/api/cart'

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])

  const totalCount = computed(() => items.value.reduce((sum, item) => sum + item.num, 0))
  const totalPrice = computed(() =>
    items.value.reduce((sum, item) => sum + item.realprice * item.num, 0),
  )

  async function fetchCart() {
    try {
      const res = await getCartList()
      items.value = res.data.data
    } catch {
      items.value = []
    }
  }

  async function addItem(params: { fid: string; fname: string; realprice: number; fphoto: string; num: number }) {
    await addToCart(params)
    await fetchCart()
  }

  async function updateItem(fid: string, num: number) {
    await updateCartItem(fid, num)
    await fetchCart()
  }

  async function removeItem(fid: string) {
    await removeCartItem(fid)
    await fetchCart()
  }

  async function clearAll() {
    await clearCart()
    items.value = []
  }

  return {
    items,
    totalCount,
    totalPrice,
    fetchCart,
    addItem,
    updateItem,
    removeItem,
    clearAll,
  }
})