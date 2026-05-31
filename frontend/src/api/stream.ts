import http from './http'

export const streamApi = {
  start(cameraId: number) {
    return http.post(`/api/stream/${cameraId}/start`).then(r => r.data.data)
  },
  stop(cameraId: number) {
    return http.post(`/api/stream/${cameraId}/stop`)
  },
  webrtcOffer(cameraId: number, sdp: string) {
    return http.post(`/api/stream/${cameraId}/webrtc/offer`, { sdp, type: 'offer' })
      .then(r => r.data.data)
  },
}
