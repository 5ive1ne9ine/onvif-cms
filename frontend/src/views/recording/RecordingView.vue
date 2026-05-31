<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:12px; align-items:center">
        <span>录制回放</span>
        <el-select v-model="cameraId" placeholder="全部摄像头" style="width:180px" clearable @change="load(1)">
          <el-option v-for="c in cameras" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="type" placeholder="全部类型" style="width:140px" clearable @change="load(1)">
          <el-option label="事件触发" value="EVENT" />
          <el-option label="手动录制" value="MANUAL" />
          <el-option label="定时录制" value="SCHEDULED" />
        </el-select>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column prop="cameraId" label="摄像头" width="80" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="durationMs" label="时长" width="100">
        <template #default="{row}">{{ row.durationMs ? (row.durationMs/1000).toFixed(1) + 's' : '-' }}</template>
      </el-table-column>
      <el-table-column prop="fileSize" label="大小" width="100">
        <template #default="{row}">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="row.status === 'COMPLETED' ? 'success' : (row.status === 'FAILED' ? 'danger' : 'warning')">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{row}">
          <el-button link type="primary" @click="play(row)" :disabled="row.status !== 'COMPLETED'">播放</el-button>
          <el-button link type="primary" @click="download(row.id)" :disabled="row.status !== 'COMPLETED'">下载</el-button>
          <el-popconfirm title="确认删除?" @confirm="del(row.id)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top:16px"
      :current-page="page"
      :page-size="size"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="load"
    />

    <el-dialog v-model="playerOpen" :title="playerTitle" width="800px" @close="playerSrc=''">
      <video v-if="playerSrc" :src="playerSrc" controls autoplay style="width:100%"></video>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { recordingApi } from '../../api/recording'
import { cameraApi } from '../../api/camera'
import { ElMessage } from 'element-plus'

const route = useRoute()
const cameras = ref<any[]>([])
const cameraId = ref<number | undefined>()
const type = ref<string | undefined>()
const rows = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const playerOpen = ref(false)
const playerSrc = ref('')
const playerTitle = ref('')

async function load(p = 1) {
  loading.value = true
  try {
    page.value = p
    const d = await recordingApi.list(p, size.value, cameraId.value, type.value)
    rows.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

function play(row: any) {
  playerSrc.value = recordingApi.playUrl(row.id) + '?t=' + Date.now()
    + '&token=' + localStorage.getItem('token')
  playerTitle.value = `回放 - ${row.startTime}`
  playerOpen.value = true
}
function download(id: number) {
  window.open(recordingApi.downloadUrl(id), '_blank')
}
async function del(id: number) {
  await recordingApi.delete(id)
  ElMessage.success('已删除')
  load(page.value)
}

function formatSize(b?: number) {
  if (!b) return '-'
  const u = ['B', 'KB', 'MB', 'GB']
  let i = 0; let v = b
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++ }
  return v.toFixed(1) + ' ' + u[i]
}

onMounted(async () => {
  cameras.value = (await cameraApi.list(1, 200)).records
  if (route.query.play) {
    // 弹出指定录像
    const d = await recordingApi.list(1, 1)
    rows.value = d.records
  }
  load()
})
</script>
