import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { authApi } from './services/authApi'
import { auth } from './store/auth'
import LoginPage from './pages/LoginPage'
import TripListPage from './pages/TripListPage'
import TripEditorPage from './pages/TripEditorPage'
import InvitePage from './pages/InvitePage'

type InitState = 'loading' | 'authed' | 'unauthed'

// 현재 로그인 유저 정보 보관 (Presence 등에서 사용)
const loadMe = () => authApi.me().then(({ data }) => auth.setUser(data))

export default function App() {
  const [initState, setInitState] = useState<InitState>('loading')

  useEffect(() => {
    // refreshToken(HttpOnly 쿠키)으로 accessToken 복구 시도
    authApi.refresh()
      .then(({ data }) => {
        auth.setToken(data.accessToken)
        return loadMe()
      })
      .then(() => setInitState('authed'))
      .catch(() => setInitState('unauthed'))
  }, [])

  if (initState === 'loading') {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <span className="text-2xl animate-pulse">✈️</span>
      </div>
    )
  }

  return (
    <BrowserRouter>
      <Toaster position="bottom-center" toastOptions={{ duration: 3000 }} />
      <Routes>
        <Route path="/login" element={<LoginPage onLogin={() => { loadMe().catch(() => {}); setInitState('authed') }} />} />
        <Route path="/invite/:shareToken" element={<InvitePage />} />

        {initState === 'authed' ? (
          <>
            <Route path="/" element={<TripListPage />} />
            <Route path="/trips/:tripId" element={<TripEditorPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        ) : (
          <Route path="*" element={<Navigate to="/login" replace />} />
        )}
      </Routes>
    </BrowserRouter>
  )
}
