import request from './index'
import type { ApiResponse } from '@/types'

export interface LikeResult {
  status: string
  likeCount: number
  dislikeCount: number
}

export function likeProduct(fid: string, likeType: number) {
  return request.post<ApiResponse<LikeResult>>('/product/like', null, {
    params: { fid, likeType }
  })
}

export function getLikeStatus(fid: string) {
  return request.get<ApiResponse<number | null>>(`/product/like/${fid}/status`)
}

export function getLikeCount(fid: string) {
  return request.get<ApiResponse<[number, number]>>(`/product/like/${fid}/count`)
}