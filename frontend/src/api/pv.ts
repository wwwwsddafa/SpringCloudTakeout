import request from './index'
import type { ApiResponse } from '@/types'

export type PageType =
  | 'HOME'
  | 'PRODUCT_LIST'
  | 'PRODUCT_DETAIL'
  | 'CART'
  | 'ORDER'
  | 'ORDER_DETAIL'
  | 'USER_CENTER'
  | 'SEARCH'
  | 'OTHER'

export interface PvTrackParams {
  pageUrl: string
  pageType: PageType
  fid?: string
  referer?: string
  staySeconds?: number
}

export function trackPv(params: PvTrackParams) {
  return request.post<ApiResponse<null>>('/ops/pv/track', params)
}