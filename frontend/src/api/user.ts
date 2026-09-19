import request from './index'
import type { ApiResponse, CaptchaData, LoginData, LoginParams, RegisterParams, UserInfo } from '@/types'

export function getCaptcha() {
  return request.get<ApiResponse<CaptchaData>>('/user/captcha')
}

export function register(params: RegisterParams) {
  return request.post<ApiResponse<UserInfo>>('/user/register', params)
}

export function login(params: LoginParams) {
  return request.post<ApiResponse<LoginData>>('/user/login', params)
}

export function qqLogin(code: string) {
  return request.post<ApiResponse<LoginData>>(`/user/login/qq?code=${code}`)
}

export function logout() {
  return request.post<ApiResponse<string>>('/user/logout')
}

export function checkLoginStatus() {
  return request.get<ApiResponse<UserInfo>>('/user/check')
}

export function getUserInfo() {
  return request.get<ApiResponse<UserInfo>>('/user/info')
}