<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6"><el-card><el-statistic title="摄像头总数" :value="stat.total" /></el-card></el-col>
      <el-col :span="6"><el-card><el-statistic title="在线摄像头" :value="stat.online" /></el-card></el-col>
      <el-col :span="6"><el-card><el-statistic title="离线摄像头" :value="stat.offline" /></el-card></el-col>
      <el-col :span="6"><el-card><el-statistic title="今日事件" :value="stat.todayEvents" /></el-card></el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <template #header><span>摄像头总览</span></template>
      <el-row :gutter="16">
        <el-col v-for="c in cameras" :key="c.id" :span="6" style="margin-bottom:16px">
          <el-card shadow="hover" class="cam-card" @click="goPreview(c.id)">
            <div class="cam-name">
              <el-tag :type="c.status === 'ONLINE' ? 'success' : 'danger'" size="small">{{ c.status }}</el-tag>
              <span style="margin-left:8px">{{ c.name }}</span>
            </div>
            <div class="cam-meta">{{ c.ip }}:{{ c.onvifPort }}</div>
            <div class="cam-meta">{{ c.manufacturer }} {{ c.model }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { cameraApi } from '../api/camera'
import { eventApi } from '../api/event'

const router = useRouter()
const cameras = ref<any[]>([])
const stat = reactive({ total: 0, online: 0, offline: 0, todayEvents: 0 })

async function load() {
  const data = await cameraApi.list(1, 100)
  cameras.value = data.records
  stat.total = data.total
  stat.online = cameras.value.filter(c => c.status === 'ONLINE').length
  stat.offline = stat.total - stat.online

  const today = new Date().toISOString().slice(0, 10) + 'T00:00:00'
  const logs = await eventApi.logs(1, 1, undefined, undefined, today, undefined)
  stat.todayEvents = logs.total
}

function goPreview(id: number) {
  router.push(`/preview/${id}`)
}

onMounted(load)
</script>

<style scoped>
.cam-card { cursor: pointer; }
.cam-name { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.cam-meta { color: #909399; font-size: 13px; margin-top: 4px; }
</style>
