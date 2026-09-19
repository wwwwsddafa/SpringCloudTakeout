import request from './index'
import type { ApiResponse } from '@/types'

export interface HourlyData {
  hour: number
  value: number
}

export interface TrendPoint {
  date: string
  value: number
}

export interface DashboardStats {
  statDate: string
  totalPv: number
  totalUv: number
  totalOrders: number
  totalRevenue: number
  conversionRate: number
  avgOrderAmount: number
  activeUsers: number
  newUsers: number
  totalLikes: number
  totalReviews: number
  pvByPageType: Record<string, number>
  hourlyPv: HourlyData[]
  hourlyOrders: HourlyData[]
  deviceDistribution: Record<string, number>
  browserDistribution: Record<string, number>
}

export interface TopProduct {
  fid: string
  fname: string
  pvCount: number
  uvCount: number
  orderCount: number
  salesAmount: number
  likeCount: number
  reviewCount: number
  avgStar: number
  conversionRate: number
}

export interface DailyStats {
  statDate: string
  totalPv: number
  totalUv: number
  totalOrders: number
  totalRevenue: number
  conversionRate: number
  avgOrderAmount: number
  activeUsers: number
  newUsers: number
  deviceDistribution: Record<string, number>
  browserDistribution: Record<string, number>
  topProducts: TopProduct[]
}

export interface DeviceTrendPoint {
  date: string
  mobile: number
  desktop: number
  tablet: number
}

export interface TrendStats {
  pvTrend: TrendPoint[]
  uvTrend: TrendPoint[]
  orderTrend: TrendPoint[]
  revenueTrend: TrendPoint[]
  deviceTrend: DeviceTrendPoint[]
}

export function getDashboardStats() {
  return request.get<ApiResponse<DashboardStats>>('/ops/stats/dashboard')
}

export function getDailyStats(date: string) {
  return request.get<ApiResponse<DailyStats>>('/ops/stats/daily', {
    params: { date }
  })
}

export function getTrendStats(days?: number) {
  return request.get<ApiResponse<TrendStats>>('/ops/stats/trend', {
    params: days ? { days } : undefined
  })
}

export interface RankItem {
  fid: string
  fname: string
  category: string
  value: number
  pvCount: number
  orderCount: number
  paidOrderCount: number
  cartCount: number
  reviewCount: number
  avgStar: number
  salesAmount: number
}

export interface ProductRanking {
  statDate: string
  topByOrderCount: RankItem[]
  topByReviewCount: RankItem[]
  topByAvgStar: RankItem[]
  topByPvCount: RankItem[]
  topByCartCount: RankItem[]
  topByPaidOrderCount: RankItem[]
}

export function getProductRanking(date: string, topN?: number) {
  return request.get<ApiResponse<ProductRanking>>('/ops/stats/product-ranking', {
    params: { date, topN: topN ?? 10 }
  })
}