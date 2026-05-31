<template>
  <el-row :gutter="16">
    <el-col :span="16">
      <el-card>
        <template #header>
          <span>{{ camera?.name }} - 实时预览</span>
          <el-tag style="margin-left:8px" :type="camera?.status === 'ONLINE' ? 'success' : 'danger'">
            {{ camera?.status }}
          </el-tag>
        </template>
        <WebRtcPlayer v-if="cameraId" :camera-id="cameraId" :key="cameraId" />
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card v-if="camera?.ptzSupported">
        <template #header>PTZ 控制</template>
        <PtzPanel :camera-id="cameraId" />
      </el-card>
      <el-card v-else>
        <el-empty description="此摄像头不支持 PTZ" />
      </el-card>
      <el-card style="margin-top:12px">
        <template #header>手动录制</template>
        <el-button type="primary" v-if="!recording" @click="startManual">开始录制</el-button>
        <el-button type="danger" v-else @click="stopManual">停止录制</el-button>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import WebRtcPlayer from '../../components/WebRtcPlayer.vue'
import PtzPanel from '../../components/PtzPanel.vue'
import { cameraApi } from '../../api/camera'
import { streamApi } from '../../api/stream'
import { recordingApi } from '../../api/recording'
import { ElMessage } from 'element-plus'

const route = useRoute()
const cameraId = ref(Number(route.params.id))
const camera = ref<any>(null)
const recording = ref(false)

onMounted(async () => {
  camera.value = await cameraApi.get(cameraId.value)
})

onUnmounted(() => {
  // 离开页面停止流
  streamApi.stop(cameraId.value).catch(() => {})
})

async function startManual() {
  await recordingApi.manualStart(cameraId.value)
  recording.value = true
  ElMessage.success('已开始录制')
}

async function stopManual() {
  await recordingApi.manualStop(cameraId.value)
  recording.value = false
  ElMessage.success('已停止录制')
}
</script>
