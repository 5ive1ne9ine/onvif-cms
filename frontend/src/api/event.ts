import http from './http'

export const eventApi = {
  listRules(cameraId: number) {
    return http.get('/api/event-rules', { params: { cameraId } }).then(r => r.data.data)
  },
  createRule(data: any) {
    return http.post('/api/event-rules', data).then(r => r.data.data)
  },
  updateRule(id: number, data: any) {
    return http.put(`/api/event-rules/${id}`, data).then(r => r.data.data)
  },
  deleteRule(id: number) {
    return http.delete(`/api/event-rules/${id}`)
  },
  logs(page = 1, size = 20, cameraId?: number, topic?: string, from?: string, to?: string) {
    return http.get('/api/events', { params: { page, size, cameraId, topic, from, to } })
      .then(r => r.data.data)
  },
}
