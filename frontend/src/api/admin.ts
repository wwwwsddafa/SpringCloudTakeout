import request from './index'
import type { AdminProductQuery, ApiResponse, Order, PageResult, Product } from '@/types'

export function getAdminProductPage(params: AdminProductQuery) {
  const { page = 1, size = 10, keyword } = params
  let url = `/admin/page?page=${page}&size=${size}`
  if (keyword) {
    url += `&keyword=${encodeURIComponent(keyword)}`
  }
  return request.get<ApiResponse<PageResult<Product>>>(url)
}

export function getAdminOrders() {
  return request.get<ApiResponse<Order[]>>('/order/admin/list')
}

export function addProduct(formData: FormData) {
  return request.post<ApiResponse<Product>>('/admin/addProduct', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function updateProduct(formData: FormData) {
  return request.post<ApiResponse<Product>>('/admin/updateProduct', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function changeProductStatus(fid: string, status: number) {
  return request.post<ApiResponse<string>>(`/admin/changeStatus/${fid}?status=${status}`)
}

export function deleteProduct(fid: string) {
  return request.delete<ApiResponse<string>>(`/admin/deleteProduct/${fid}`)
}

export function updateOrderStatus(roid: string, status: number) {
  return request.post<ApiResponse<string>>(`/order/admin/updateStatus/${roid}?status=${status}`)
}