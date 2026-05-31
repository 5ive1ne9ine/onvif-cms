<template>
  <el-card>
    <template #header>
      <div style="display:flex; justify-content:space-between; align-items:center">
        <span>摄像头列表</span>
        <div>
          <el-input v-model="keyword" placeholder="搜索名称/IP" style="width:200px" clearable @change="load(1)" />
          <el-button type="primary" @click="$router.push('/cameras/add')" style="margin-left:8px">新增</el-button>
          <el-button @click="$router.push('/discover')">设备发现</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="name" label="名称" />
      <el-table-column label="地址">
        <template #default="{row}">{{ row.ip }}:{{ row.onvifPort }}</template>
      </el-table-column>
      <el-table-column prop="manufacturer" label="厂商" width="120" />
      <el-table-column prop="model" label="型号" width="140" />
      <el-table-column label="能力" width="160">
        <template #default="{row}">
          <el-tag v-if="row.ptzSupported" size="small" type="success">PTZ</el-tag>
          <el-tag v-if="row.eventsSupported" size="small" type="warning" style="margin-left:4px">事件</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{row}">
          <el-button link type="primary" @click="$router.push(`/preview/${row.id}`)">预览</el-button>
          <el-button link type="primary" @click="test(row.id)">连接测试</el-button>
          <el-button link type="primary" @click="$router.push(`/cameras/${row.id}/edit`)">编辑</el-button>
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
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { cameraApi } from '../../api/camera'
import { ElMessage } from 'element-plus'

const rows = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const loading = ref(false)

async function load(p = 1) {
  loading.value = true
  try {
    page.value = p
    const data = await cameraApi.list(p, size.value, keyword.value)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function del(id: number) {
  await cameraApi.delete(id)
  ElMessage.success('已删除')
  load(page.value)
}

async function test(id: number) {
  ElMessage.info('正在测试连接...')
  await cameraApi.test(id)
  ElMessage.success('测试完成')
  load(page.value)
}

onMounted(() => load())
</script>
