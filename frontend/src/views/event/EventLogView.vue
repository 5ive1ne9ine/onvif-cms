<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:12px; align-items:center">
        <span>事件日志</span>
        <el-select v-model="cameraId" placeholder="全部摄像头" style="width:180px" clearable @change="load(1)">
          <el-option v-for="c in cameras" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-input v-model="topic" placeholder="Topic 过滤" style="width:200px" clearable @change="load(1)" />
      </div>
    </template>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="occurredAt" label="时间" width="180" />
      <el-table-column prop="cameraId" label="摄像头" width="100" />
      <el-table-column prop="topic" label="Topic" />
      <el-table-column prop="payloadJson" label="数据" show-overflow-tooltip />
      <el-table-column label="截图" width="80">
        <template #default="{row}">
          <el-image v-if="row.snapshotPath" style="width:60px;height:40px" fit="cover"
                    :src="`/api/recordings/snapshot?path=${encodeURIComponent(row.snapshotPath)}`"
                    :preview-src-list="[`/api/recordings/snapshot?path=${encodeURIComponent(row.snapshotPath)}`]" />
        </template>
      </el-table-column>
      <el-table-column label="录像" width="100">
        <template #default="{row}">
          <el-button v-if="row.recordingId" link type="primary"
                     @click="playRecord(row.recordingId)">回放</el-button>
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
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { eventApi } from '../../api/event'
import { cameraApi } from '../../api/camera'

const router = useRouter()
const cameras = ref<any[]>([])
const cameraId = ref<number | undefined>()
const topic = ref('')
const rows = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)

async function load(p = 1) {
  loading.value = true
  try {
    page.value = p
    const d = await eventApi.logs(p, size.value, cameraId.value, topic.value || undefined)
    rows.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

function playRecord(id: number) {
  router.push({ path: '/recordings', query: { play: id } })
}

onMounted(async () => {
  cameras.value = (await cameraApi.list(1, 200)).records
  load()
})
</script>
