import request from './index'
import type { AddCartParams, ApiResponse, CartItem } from '@/types'

export function addToCart(params: AddCartParams) {
  return request.post<ApiResponse<string>>('/cart/add', params)
}

export function getCartList() {
  return request.get<ApiResponse<CartItem[]>>('/cart/list')
}

export function updateCartItem(fid: string, num: number) {
  return request.post<ApiResponse<string>>(`/cart/update?fid=${fid}&num=${num}`)
}

export function removeCartItem(fid: string) {
  return request.delete<ApiResponse<string>>(`/cart/remove/${fid}`)
}

export function clearCart() {
  return request.delete<ApiResponse<string>>('/cart/clear')
}