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

// 浏览器与 ZLM 通常在同一台机器(localhost)访问, 不需要公网 STUN;
// 留空 iceServers 让浏览器只用本机 host candidate 直连 ZLM (ice-lite)。
async function play() {
  try {
    await streamApi.start(props.cameraId)
    pc = new RTCPeerConnection({ iceServers: [] })
    pc.ontrack = (e) => {
      if (videoEl.value && e.streams[0]) {
        videoEl.value.srcObject = e.streams[0]
        status.value = ''
      }
    }
    pc.onicecandidate = (e) => {
      if (e.candidate) console.log('[webrtc] local candidate:', e.candidate.candidate)
    }
    pc.oniceconnectionstatechange = () => {
      const st = pc?.iceConnectionState
      console.log('[webrtc] ice state:', st)
      if (st === 'checking') status.value = '正在建立连接...'
      else if (st === 'connected' || st === 'completed') status.value = ''
      else if (st === 'failed') status.value = 'WebRTC 连接失败 (ICE failed) - 检查 ZLM 媒体端口 8000 是否可达'
      else if (st === 'disconnected') status.value = 'WebRTC 连接断开'
    }
    pc.addTransceiver('video', { direction: 'recvonly' })
    pc.addTransceiver('audio', { direction: 'recvonly' })

    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    // 等待 ICE gathering 完成, 把完整 candidate 一次性发给 ZLM (非 trickle)
    await new Promise<void>((resolve) => {
      if (pc!.iceGatheringState === 'complete') return resolve()
      const check = () => { if (pc!.iceGatheringState === 'complete') resolve() }
      pc!.addEventListener('icegatheringstatechange', check)
      // 兜底: 最多等 2s
      setTimeout(resolve, 2000)
    })
    const answer = await streamApi.webrtcOffer(props.cameraId, pc.localDescription!.sdp!)

    // 验证 SDP answer 有效性
    if (!answer || !answer.sdp || !answer.sdp.startsWith('v=')) {
      const detail = answer?.msg || answer?.message || '无效的 SDP 响应'
      throw new Error('SDP 应答无效: ' + detail)
    }
    console.log('[webrtc] remote candidates:', answer.sdp.match(/a=candidate[^\r\n]*/g)?.join(' | '))
    // Docker Desktop 的 vpnkit 对 ZLM->浏览器方向的持续 UDP 媒体流(SRTP)做非对称 NAT 会丢包,
    // 导致只有 gop_cache 里第一个 I 帧穿过去后画面冻结。TCP-ICE 候选走 vpnkit 的 TCP 代理,双向可靠。
    // TCP-ICE 候选与真 UDP 候选的传输协议字段同为 udp,但 TCP-ICE 带 tcptype 参数。
    // 因此只删掉不含 tcptype 的 udp 候选(真 UDP),保留含 tcptype 的(TCP-ICE),强制浏览器走 TCP。
    const forcedTcpSdp = answer.sdp
      .split(/\r?\n/)
      .filter((line: string) => !(/^a=candidate:.*\sudp\s/.test(line) && !/tcptype=/.test(line)))
      .join('\r\n')
    console.log('[webrtc] forced-tcp candidates:', forcedTcpSdp.match(/a=candidate[^\r\n]*/g)?.join(' | '))
    await pc.setRemoteDescription({ type: 'answer', sdp: forcedTcpSdp })
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
