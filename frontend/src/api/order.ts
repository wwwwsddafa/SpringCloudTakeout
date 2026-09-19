import request from './index'
import type { ApiResponse, CreateOrderParams, Order } from '@/types'

export function createOrder(params: CreateOrderParams) {
  return request.post<ApiResponse<Order>>('/order/create', params)
}

export function getOrderList() {
  return request.get<ApiResponse<Order[]>>('/order/list')
}

export function getOrderDetail(roid: string) {
  return request.get<ApiResponse<Order>>(`/order/detail/${roid}`)
}

export function confirmOrder(roid: string) {
  return request.post<ApiResponse<string>>(`/order/confirm/${roid}`)
}

export function cancelOrder(roid: string) {
  return request.post<ApiResponse<string>>(`/order/cancel/${roid}`)
}

export function checkPurchase(fid: string) {
  return request.get<ApiResponse<boolean>>(`/order/checkPurchase?fid=${fid}`)
}

export function alipayPay(roid: string) {
  return request.post<ApiResponse<string>>(`/order/alipay/pay/${roid}`)
}