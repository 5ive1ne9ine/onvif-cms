<template>
  <el-card>
    <template #header>
      <div style="display:flex; justify-content:space-between; align-items:center">
        <span>局域网 ONVIF 设备发现</span>
        <el-button type="primary" :loading="scanning" @click="scan">开始扫描</el-button>
      </div>
    </template>

    <el-alert v-if="!scanning && devices.length === 0" type="info" :closable="false"
              title="点击 '开始扫描' 通过 WS-Discovery 在局域网内查找 ONVIF 摄像头" />

    <el-table v-if="devices.length" :data="devices" style="margin-top:12px">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="ip" label="IP" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="hardware" label="硬件" />
      <el-table-column prop="xaddr" label="ONVIF 地址" />
      <el-table-column label="操作" width="120">
        <template #default="{row}">
          <el-button link type="primary" @click="addCamera(row)">添加</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { cameraApi } from '../../api/camera'
import { ElMessage } from 'element-plus'

const router = useRouter()
const scanning = ref(false)
const devices = ref<any[]>([])

async function scan() {
  scanning.value = true
  try {
    devices.value = await cameraApi.scan(4000)
    ElMessage.success(`发现 ${devices.value.length} 个设备`)
  } finally {
    scanning.value = false
  }
}

function addCamera(d: any) {
  router.push({
    path: '/cameras/add',
    query: { ip: d.ip, port: d.port }
  })
}
</script>
