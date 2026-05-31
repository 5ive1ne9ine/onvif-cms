import http from './http'

export const recordingApi = {
  list(page = 1, size = 20, cameraId?: number, type?: string, from?: string, to?: string) {
    return http.get('/api/recordings', { params: { page, size, cameraId, type, from, to } })
      .then(r => r.data.data)
  },
  delete(id: number) {
    return http.delete(`/api/recordings/${id}`)
  },
  playUrl(id: number) {
    return `/api/recordings/${id}/play`
  },
  downloadUrl(id: number) {
    return `/api/recordings/${id}/download`
  },
  manualStart(cameraId: number, maxSeconds = 300) {
    return http.post('/api/recordings/manual/start', null, { params: { cameraId, maxSeconds } })
      .then(r => r.data.data)
  },
  manualStop(cameraId: number) {
    return http.post(`/api/recordings/manual/${cameraId}/stop`).then(r => r.data.data)
  },
}
