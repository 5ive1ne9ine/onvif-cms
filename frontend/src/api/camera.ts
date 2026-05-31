import http from './http'

export const cameraApi = {
  list(page = 1, size = 20, keyword = '') {
    return http.get('/api/cameras', { params: { page, size, keyword } })
      .then(r => r.data.data)
  },
  get(id: number) {
    return http.get(`/api/cameras/${id}`).then(r => r.data.data)
  },
  create(data: any) {
    return http.post('/api/cameras', data).then(r => r.data.data)
  },
  update(id: number, data: any) {
    return http.put(`/api/cameras/${id}`, data).then(r => r.data.data)
  },
  delete(id: number) {
    return http.delete(`/api/cameras/${id}`)
  },
  test(id: number) {
    return http.post(`/api/cameras/${id}/test`).then(r => r.data.data)
  },
  scan(timeoutMs = 4000) {
    return http.post('/api/discovery/scan', null, { params: { timeoutMs }, timeout: timeoutMs + 5000 })
      .then(r => r.data.data)
  },
}
