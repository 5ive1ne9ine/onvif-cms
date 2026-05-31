<template>
  <div class="webrtc-player">
    <video ref="videoEl" autoplay playsinline controls muted></video>
    <div v-if="status" class="status">{{ status }}</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { streamApi } from '../api/stream'

const props = defineProps<{ cameraId: number }>()

const videoEl = ref<HTMLVideoElement>()
const status = ref('正在连接...')
let pc: RTCPeerConnection | null = null

async function play() {
  try {
    await streamApi.start(props.cameraId)
    pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] })
    pc.ontrack = (e) => {
      if (videoEl.value && e.streams[0]) {
        videoEl.value.srcObject = e.streams[0]
        status.value = ''
      }
    }
    pc.oniceconnectionstatechange = () => {
      if (pc?.iceConnectionState === 'failed' || pc?.iceConnectionState === 'disconnected') {
        status.value = 'WebRTC 连接断开'
      }
    }
    pc.addTransceiver('video', { direction: 'recvonly' })
    pc.addTransceiver('audio', { direction: 'recvonly' })

    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    const answer = await streamApi.webrtcOffer(props.cameraId, offer.sdp!)
    await pc.setRemoteDescription({ type: 'answer', sdp: answer.sdp })
  } catch (e: any) {
    status.value = '播放失败: ' + (e.message || e)
  }
}

function stop() {
  if (pc) {
    pc.close()
    pc = null
  }
  if (videoEl.value) videoEl.value.srcObject = null
}

onMounted(play)
onUnmounted(stop)

defineExpose({ play, stop })
</script>

<style scoped>
.webrtc-player { position: relative; background: #000; width: 100%; aspect-ratio: 16/9; }
.webrtc-player video { width: 100%; height: 100%; object-fit: contain; }
.status {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  color: #fff; font-size: 14px; background: rgba(0,0,0,0.6); padding: 8px 12px; border-radius: 4px;
}
</style>
