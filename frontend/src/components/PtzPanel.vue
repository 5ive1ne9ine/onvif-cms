<template>
  <div class="ptz-panel">
    <div class="grid">
      <div></div>
      <el-button :icon="Top" @mousedown="start(0,1)" @mouseup="stop" @mouseleave="stop"></el-button>
      <div></div>
      <el-button :icon="Back" @mousedown="start(-1,0)" @mouseup="stop" @mouseleave="stop"></el-button>
      <el-button :icon="Aim" @click="stop"></el-button>
      <el-button :icon="Right" @mousedown="start(1,0)" @mouseup="stop" @mouseleave="stop"></el-button>
      <div></div>
      <el-button :icon="Bottom" @mousedown="start(0,-1)" @mouseup="stop" @mouseleave="stop"></el-button>
      <div></div>
    </div>
    <div class="zoom">
      <span>变焦</span>
      <el-button :icon="Plus" @mousedown="zoomStart(1)" @mouseup="stop" @mouseleave="stop"></el-button>
      <el-button :icon="Minus" @mousedown="zoomStart(-1)" @mouseup="stop" @mouseleave="stop"></el-button>
    </div>
    <div class="speed">
      <span>速度</span>
      <el-slider v-model="speed" :min="0.1" :max="1" :step="0.1" style="width:140px" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Top, Bottom, Back, Right, Aim, Plus, Minus } from '@element-plus/icons-vue'
import { ptzApi } from '../api/ptz'

const props = defineProps<{ cameraId: number }>()
const speed = ref(0.5)

async function start(pan: number, tilt: number) {
  await ptzApi.continuous(props.cameraId, pan * speed.value, tilt * speed.value, 0, 0)
}
async function zoomStart(zoom: number) {
  await ptzApi.continuous(props.cameraId, 0, 0, zoom * speed.value, 0)
}
async function stop() {
  await ptzApi.stop(props.cameraId, true, true)
}
</script>

<style scoped>
.ptz-panel { padding: 12px; background: #f5f7fa; border-radius: 4px; }
.grid { display: grid; grid-template-columns: 48px 48px 48px; gap: 6px; justify-content: center; }
.grid .el-button { width: 48px; height: 48px; }
.zoom, .speed { margin-top: 14px; display: flex; align-items: center; gap: 10px; }
</style>
