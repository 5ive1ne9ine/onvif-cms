import http from './http'

export const authApi = {
  login(username: string, password: string) {
    return http.post('/api/auth/login', { username, password })
      .then(r => r.data.data)
  },
  logout() {
    return http.post('/api/auth/logout')
  },
  me() {
    return http.get('/api/auth/me').then(r => r.data.data)
  },
}
