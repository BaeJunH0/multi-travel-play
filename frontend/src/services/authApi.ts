import axios from 'axios'
import api from './api'

export const authApi = {
  // 이메일 로그인
  login: (email: string, password: string) =>
    api.post<{ accessToken: string }>('/auth/login', { email, password }),

  // 회원가입
  signup: (email: string, password: string, nickname: string) =>
    api.post<{ accessToken: string }>('/auth/signup', { email, password, nickname }),

  // 카카오 OAuth: 서버 URL로 직접 이동
  kakaoLogin: () => {
    window.location.href = '/api/auth/oauth2/kakao'
  },

  // refreshToken(HttpOnly 쿠키)으로 accessToken 재발급
  refresh: () =>
    axios.post<{ accessToken: string }>('/api/auth/refresh'),

  // 로그아웃
  logout: () => api.post('/auth/logout'),

  // 비밀번호 재설정 코드 요청
  passwordResetRequest: (email: string) =>
    api.post('/auth/password-reset/request', { email }),

  // 코드 확인 + 비밀번호 변경
  passwordResetConfirm: (email: string, token: string, newPassword: string) =>
    api.post<{ accessToken: string }>('/auth/password-reset/confirm', { email, token, newPassword }),
}
