import http from './http'

export const ptzApi = {
  continuous(cameraId: number, panSpeed: number, tiltSpeed: number, zoomSpeed: number, timeoutMs = 0) {
    return http.post(`/api/cameras/${cameraId}/ptz/continuous`, {
      panSpeed, tiltSpeed, zoomSpeed, timeoutMs
    })
  },
  stop(cameraId: number, panTilt = true, zoom = true) {
    return http.post(`/api/cameras/${cameraId}/ptz/stop`, { panTilt, zoom })
  },
  relative(cameraId: number, pan: number, tilt: number, zoom: number) {
    return http.post(`/api/cameras/${cameraId}/ptz/relative`, { pan, tilt, zoom })
  },
  absolute(cameraId: number, pan: number, tilt: number, zoom: number) {
    return http.post(`/api/cameras/${cameraId}/ptz/absolute`, { pan, tilt, zoom })
  },
  getPresets(cameraId: number) {
    return http.get(`/api/cameras/${cameraId}/ptz/presets`).then(r => r.data.data)
  },
  gotoPreset(cameraId: number, token: string) {
    return http.post(`/api/cameras/${cameraId}/ptz/presets/${token}/goto`)
  },
  setPreset(cameraId: number, name: string) {
    return http.post(`/api/cameras/${cameraId}/ptz/presets`, { name })
  },
}
