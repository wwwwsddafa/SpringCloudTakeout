import request from './index'
import type { ApiResponse, CreateReviewParams, Review, ReviewListResult, ReviewRating } from '@/types'

export function createReview(formData: FormData) {
  return request.post<ApiResponse<string>>('/product/review', formData)
}

export function deleteReview(reviewId: string) {
  return request.delete<ApiResponse<string>>(`/product/review/${reviewId}`)
}

export function getReviewList(fid: string, page = 1, size = 10) {
  return request.get<ApiResponse<ReviewListResult>>(`/product/review/${fid}?page=${page}&size=${size}`)
}

export function getReviewRating(fid: string) {
  return request.get<ApiResponse<ReviewRating>>(`/product/review/${fid}/rating`)
}

export function getReviewCheck(fid: string) {
  return request.get<ApiResponse<Review | null>>(`/product/review/${fid}/check`)
}

export function getMyReviews(page = 1, size = 10) {
  return request.get<ApiResponse<ReviewListResult>>(`/product/review/my?page=${page}&size=${size}`)
}