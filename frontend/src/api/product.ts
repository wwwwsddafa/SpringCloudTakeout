import request from './index'
import type { ApiResponse, Product } from '@/types'

export function getProductList() {
  return request.get<ApiResponse<Product[]>>('/product/list')
}

export function getProductDetail(fid: string) {
  return request.get<ApiResponse<Product>>(`/product/detail/${fid}`)
}