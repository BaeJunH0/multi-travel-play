import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff } from 'lucide-react'
import toast from 'react-hot-toast'
import { authApi } from '../services/authApi'
import { auth } from '../store/auth'

type Tab = 'social' | 'email' | 'signup' | 'reset'

interface Props {
  onLogin: () => void
}

export default function LoginPage({ onLogin }: Props) {
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('social')

  // 이메일 로그인 상태
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [loading, setLoading] = useState(false)

  // 회원가입 상태
  const [signupEmail, setSignupEmail] = useState('')
  const [signupPassword, setSignupPassword] = useState('')
  const [signupPasswordConfirm, setSignupPasswordConfirm] = useState('')
  const [signupNickname, setSignupNickname] = useState('')
  const [showSignupPw, setShowSignupPw] = useState(false)
  const [signupLoading, setSignupLoading] = useState(false)

  // 비밀번호 찾기 상태
  const [resetEmail, setResetEmail] = useState('')
  const [resetStep, setResetStep] = useState<1 | 2>(1)
  const [resetToken, setResetToken] = useState('')
  const [resetNewPassword, setResetNewPassword] = useState('')
  const [resetNewPasswordConfirm, setResetNewPasswordConfirm] = useState('')
  const [showResetPw, setShowResetPw] = useState(false)
  const [resetRequestLoading, setResetRequestLoading] = useState(false)
  const [resetConfirmLoading, setResetConfirmLoading] = useState(false)

  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const { data } = await authApi.login(email, password)
      auth.setToken(data.accessToken)
      onLogin()
      navigate('/')
    } catch (err: any) {
      toast.error(err.response?.status === 401 ? '이메일 또는 비밀번호가 올바르지 않습니다.' : '로그인에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault()
    if (signupPassword !== signupPasswordConfirm) return
    setSignupLoading(true)
    try {
      const { data } = await authApi.signup(signupEmail, signupPassword, signupNickname)
      auth.setToken(data.accessToken)
      onLogin()
      navigate('/')
    } catch (err: any) {
      toast.error(err.response?.status === 409 ? '이미 사용 중인 이메일입니다.' : '회원가입에 실패했습니다.')
    } finally {
      setSignupLoading(false)
    }
  }

  const handleResetRequest = async (e: React.FormEvent) => {
    e.preventDefault()
    setResetRequestLoading(true)
    try {
      await authApi.passwordResetRequest(resetEmail)
      setResetStep(2)
    } catch {
      // 서버는 항상 200 반환이지만 네트워크 오류 등 대비
      setResetStep(2)
    } finally {
      setResetRequestLoading(false)
    }
  }

  const handleResetConfirm = async (e: React.FormEvent) => {
    e.preventDefault()
    if (resetNewPassword !== resetNewPasswordConfirm) return
    setResetConfirmLoading(true)
    try {
      const { data } = await authApi.passwordResetConfirm(resetEmail, resetToken, resetNewPassword)
      auth.setToken(data.accessToken)
      onLogin()
      navigate('/')
    } catch (err: any) {
      toast.error(err.response?.status === 400 ? '인증 코드가 올바르지 않거나 만료됐습니다.' : '비밀번호 변경에 실패했습니다.')
    } finally {
      setResetConfirmLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-violet-50 to-indigo-100 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-10">
          <div className="text-5xl mb-4">✈️</div>
          <h1 className="text-2xl font-bold text-gray-800">TripPlanner</h1>
          <p className="text-sm text-gray-500 mt-2">함께 만드는 여행 일정</p>
        </div>

        <div className="bg-white rounded-3xl shadow-lg px-8 py-8">
          {tab === 'social' && (
            <div className="space-y-3">
              <p className="text-center text-sm text-gray-400 mb-5">소셜 계정으로 간편하게 시작하세요</p>
              <button
                onClick={authApi.kakaoLogin}
                className="w-full flex items-center gap-3 px-5 py-3.5 rounded-2xl bg-yellow-400 hover:bg-yellow-500 transition-colors"
              >
                <svg className="w-5 h-5 flex-shrink-0" viewBox="0 0 24 24" fill="#3C1E1E">
                  <path d="M12 3C6.48 3 2 6.58 2 11c0 2.76 1.7 5.2 4.3 6.7l-1.1 4.1 4.8-3.1c.65.09 1.32.14 2 .14 5.52 0 10-3.58 10-8S17.52 3 12 3z"/>
                </svg>
                <span className="flex-1 text-center text-sm font-bold text-yellow-900">카카오로 계속하기</span>
              </button>

              <button
                onClick={authApi.googleLogin}
                className="w-full flex items-center gap-3 px-5 py-3.5 rounded-2xl border border-gray-200 bg-white hover:bg-gray-50 transition-colors"
              >
                <span className="w-5 h-5 flex-shrink-0 flex items-center justify-center text-base font-bold text-blue-500">G</span>
                <span className="flex-1 text-center text-sm font-bold text-gray-700">구글로 계속하기</span>
              </button>

              <div className="flex items-center gap-3 py-2">
                <div className="flex-1 h-px bg-gray-100" />
                <span className="text-xs text-gray-300">또는</span>
                <div className="flex-1 h-px bg-gray-100" />
              </div>

              <button
                onClick={() => setTab('email')}
                className="w-full py-3 rounded-2xl border border-gray-200 text-sm text-gray-500 hover:bg-gray-50 transition-colors"
              >
                이메일로 로그인
              </button>
            </div>
          )}

          {tab === 'email' && (
            <form onSubmit={handleEmailLogin} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">이메일</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="example@email.com"
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">비밀번호</label>
                <div className="relative">
                  <input
                    type={showPw ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="비밀번호를 입력하세요"
                    required
                    className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400 pr-10"
                  />
                  <button type="button" onClick={() => setShowPw((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-300 hover:text-gray-500">
                    {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <button type="submit" disabled={loading}
                className="w-full py-3 rounded-2xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-60 transition-colors mt-2">
                {loading ? '로그인 중...' : '로그인'}
              </button>

              <div className="flex items-center justify-center gap-4 pt-1">
                <button type="button" onClick={() => setTab('reset')} className="text-xs text-gray-400 hover:text-gray-600">비밀번호 찾기</button>
                <span className="text-gray-200">|</span>
                <button type="button" onClick={() => setTab('signup')} className="text-xs text-gray-400 hover:text-gray-600">회원가입</button>
                <span className="text-gray-200">|</span>
                <button type="button" onClick={() => setTab('social')} className="text-xs text-gray-400 hover:text-gray-600">소셜 로그인</button>
              </div>
            </form>
          )}

          {tab === 'signup' && (
            <form onSubmit={handleSignup} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">이메일</label>
                <input
                  type="email"
                  value={signupEmail}
                  onChange={(e) => setSignupEmail(e.target.value)}
                  placeholder="example@email.com"
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">비밀번호</label>
                <div className="relative">
                  <input
                    type={showSignupPw ? 'text' : 'password'}
                    value={signupPassword}
                    onChange={(e) => setSignupPassword(e.target.value)}
                    placeholder="8자 이상 입력하세요"
                    required
                    minLength={8}
                    className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400 pr-10"
                  />
                  <button type="button" onClick={() => setShowSignupPw((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-300 hover:text-gray-500">
                    {showSignupPw ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                {signupPassword && signupPassword.length < 8 && (
                  <p className="text-xs text-red-400 mt-1">비밀번호는 8자 이상이어야 합니다.</p>
                )}
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">비밀번호 확인</label>
                <input
                  type="password"
                  value={signupPasswordConfirm}
                  onChange={(e) => setSignupPasswordConfirm(e.target.value)}
                  placeholder="비밀번호를 다시 입력하세요"
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
                {signupPasswordConfirm && signupPassword !== signupPasswordConfirm && (
                  <p className="text-xs text-red-400 mt-1">비밀번호가 일치하지 않습니다.</p>
                )}
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">닉네임</label>
                <input
                  type="text"
                  value={signupNickname}
                  onChange={(e) => setSignupNickname(e.target.value)}
                  placeholder="닉네임을 입력하세요"
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
              </div>

              <button
                type="submit"
                disabled={signupLoading || signupPassword.length < 8 || (!!signupPasswordConfirm && signupPassword !== signupPasswordConfirm)}
                className="w-full py-3 rounded-2xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-60 transition-colors mt-2"
              >
                {signupLoading ? '가입 중...' : '가입하기'}
              </button>

              <p className="text-center text-xs text-gray-400 pt-1">
                이미 계정이 있으신가요?{' '}
                <button type="button" onClick={() => setTab('email')} className="text-violet-500 hover:text-violet-700 font-medium">
                  로그인
                </button>
              </p>
            </form>
          )}

          {tab === 'reset' && (
            <div className="space-y-4">
              {resetStep === 1 ? (
                <form onSubmit={handleResetRequest} className="space-y-4">
                  <p className="text-sm text-gray-500">가입하신 이메일로 인증 코드를 발송해 드립니다.</p>
                  <div>
                    <label className="block text-xs font-medium text-gray-500 mb-1.5">이메일</label>
                    <input
                      type="email"
                      value={resetEmail}
                      onChange={(e) => setResetEmail(e.target.value)}
                      placeholder="example@email.com"
                      required
                      className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={resetRequestLoading}
                    className="w-full py-3 rounded-2xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-60 transition-colors"
                  >
                    {resetRequestLoading ? '전송 중...' : '인증코드 전송'}
                  </button>

                  <p className="text-center text-xs text-gray-400 pt-1">
                    <button type="button" onClick={() => setTab('social')} className="text-violet-500 hover:text-violet-700 font-medium">
                      로그인으로 돌아가기
                    </button>
                  </p>
                </form>
              ) : (
                <form onSubmit={handleResetConfirm} className="space-y-4">
                  <p className="text-sm text-gray-500">입력하신 이메일로 인증 코드를 발송했습니다.</p>
                  <div>
                    <label className="block text-xs font-medium text-gray-500 mb-1.5">인증코드</label>
                    <input
                      type="text"
                      inputMode="numeric"
                      maxLength={6}
                      value={resetToken}
                      onChange={(e) => setResetToken(e.target.value.replace(/\D/g, ''))}
                      placeholder="6자리 인증 코드"
                      required
                      className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400 tracking-widest"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-500 mb-1.5">새 비밀번호</label>
                    <div className="relative">
                      <input
                        type={showResetPw ? 'text' : 'password'}
                        value={resetNewPassword}
                        onChange={(e) => setResetNewPassword(e.target.value)}
                        placeholder="새 비밀번호를 입력하세요"
                        required
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400 pr-10"
                      />
                      <button type="button" onClick={() => setShowResetPw((v) => !v)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-300 hover:text-gray-500">
                        {showResetPw ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-500 mb-1.5">비밀번호 확인</label>
                    <input
                      type="password"
                      value={resetNewPasswordConfirm}
                      onChange={(e) => setResetNewPasswordConfirm(e.target.value)}
                      placeholder="새 비밀번호를 다시 입력하세요"
                      required
                      className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                    />
                    {resetNewPasswordConfirm && resetNewPassword !== resetNewPasswordConfirm && (
                      <p className="text-xs text-red-400 mt-1">비밀번호가 일치하지 않습니다.</p>
                    )}
                  </div>

                  <button
                    type="submit"
                    disabled={resetConfirmLoading || (!!resetNewPasswordConfirm && resetNewPassword !== resetNewPasswordConfirm)}
                    className="w-full py-3 rounded-2xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-60 transition-colors"
                  >
                    {resetConfirmLoading ? '변경 중...' : '비밀번호 변경'}
                  </button>

                  <p className="text-center text-xs text-gray-400 pt-1">
                    <button type="button" onClick={() => setResetStep(1)} className="text-gray-400 hover:text-gray-600">
                      ← 이메일 재입력
                    </button>
                  </p>

                  <p className="text-center text-xs text-gray-400">
                    <button type="button" onClick={() => setTab('social')} className="text-violet-500 hover:text-violet-700 font-medium">
                      로그인으로 돌아가기
                    </button>
                  </p>
                </form>
              )}
            </div>
          )}
        </div>

        <p className="text-center text-xs text-gray-400 mt-6">
          로그인 시 <span className="underline cursor-pointer">이용약관</span> 및 <span className="underline cursor-pointer">개인정보처리방침</span>에 동의합니다
        </p>
      </div>
    </div>
  )
}
