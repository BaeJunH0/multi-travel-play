// accessToken은 메모리에만 저장 (XSS 방지)
let _accessToken: string | null = null

export const auth = {
  getToken: () => _accessToken,
  setToken: (token: string) => { _accessToken = token },
  clearToken: () => { _accessToken = null },
}
