import request from './index'
import type { ApiResponse, SearchResult } from '@/types'

export interface SearchParams {
  keyword?: string
  category?: string
  page?: number
  size?: number
  sort?: string
}

export function searchFood(params: SearchParams) {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.category) query.set('category', params.category)
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  if (params.sort) query.set('sort', params.sort)
  return request.get<ApiResponse<SearchResult>>(`/search/food?${query.toString()}`)
}