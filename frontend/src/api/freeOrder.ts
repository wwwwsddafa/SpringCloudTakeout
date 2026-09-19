import request from './index'
import type { ApiResponse, CreateFreeOrderEventParams, FreeOrderCoupon, FreeOrderEvent, FreeOrderEventCurrent, FreeOrderEventDetail, FreeOrderGrabResult } from '@/types'

export function createFreeOrderEvent(params: CreateFreeOrderEventParams) {
  return request.post<ApiResponse<FreeOrderEvent>>('/admin/free-order/event', params)
}

export function getCurrentFreeOrderEvent() {
  return request.get<ApiResponse<FreeOrderEventCurrent[]>>('/free-order/event/current')
}

export function grabFreeOrder() {
  return request.post<ApiResponse<FreeOrderGrabResult>>('/free-order/grab')
}

export function getMyFreeOrderCoupons() {
  return request.get<ApiResponse<FreeOrderCoupon[]>>('/free-order/my-coupons')
}

export function getUsableFreeOrderCoupons() {
  return request.get<ApiResponse<FreeOrderCoupon[]>>('/free-order/usable-coupons')
}

export function getFreeOrderEventList(page: number, size: number) {
  return request.get<ApiResponse<{ total: number; records: FreeOrderEvent[] }>>(`/admin/free-order/events?page=${page}&size=${size}`)
}

export function endFreeOrderEvent(eventId: string) {
  return request.post<ApiResponse<string>>(`/admin/free-order/event/${eventId}/end`)
}

export function updateFreeOrderEvent(eventId: string, data: Partial<FreeOrderEvent>) {
  return request.put<ApiResponse<FreeOrderEvent>>(`/admin/free-order/event/${eventId}`, data)
}

export function deleteFreeOrderEvent(eventId: string) {
  return request.delete<ApiResponse<string>>(`/admin/free-order/event/${eventId}`)
}

export function getFreeOrderEventDetail(eventId: string) {
  return request.get<ApiResponse<FreeOrderEventDetail>>(`/admin/free-order/event/${eventId}`)
}