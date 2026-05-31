<template>
  <el-card>
    <template #header>
      <div style="display:flex; justify-content:space-between; align-items:center">
        <div>
          <span>事件规则</span>
          <el-select v-model="cameraId" placeholder="选择摄像头" style="width:200px; margin-left:12px"
                     @change="load">
            <el-option v-for="c in cameras" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </div>
        <el-button type="primary" @click="add" :disabled="!cameraId">新增规则</el-button>
      </div>
    </template>

    <el-table :data="rules" v-loading="loading">
      <el-table-column prop="ruleName" label="规则名称" />
      <el-table-column prop="topic" label="ONVIF Topic" />
      <el-table-column label="操作">
        <template #default="{row}">
          <el-tag v-if="row.recordVideo" type="warning" size="small">录像</el-tag>
          <el-tag v-if="row.snapshot" type="success" size="small" style="margin-left:4px">截图</el-tag>
          <span style="margin-left:8px">前{{row.preSeconds}}s / 后{{row.postSeconds}}s</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{row}"><el-switch v-model="row.enabled" @change="toggle(row)" /></template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{row}">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-popconfirm title="确认删除?" @confirm="del(row.id)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑规则' : '新增规则'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="规则名称"><el-input v-model="form.ruleName" /></el-form-item>
        <el-form-item label="ONVIF Topic">
          <el-select v-model="form.topic" filterable allow-create style="width:100%">
            <el-option v-for="t in topicOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="录制视频"><el-switch v-model="form.recordVideo" /></el-form-item>
        <el-form-item label="抓取截图"><el-switch v-model="form.snapshot" /></el-form-item>
        <el-form-item label="预录秒数"><el-input-number v-model="form.preSeconds" :min="0" :max="30" /></el-form-item>
        <el-form-item label="延录秒数"><el-input-number v-model="form.postSeconds" :min="0" :max="120" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { eventApi } from '../../api/event'
import { cameraApi } from '../../api/camera'
import { ElMessage } from 'element-plus'

const cameras = ref<any[]>([])
const cameraId = ref<number | undefined>()
const rules = ref<any[]>([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive<any>({})
const topicOptions = [
  'tns1:VideoSource/MotionAlarm',
  'tns1:RuleEngine/CellMotionDetector/Motion',
  'tns1:RuleEngine/TamperDetector/Tamper',
  'tns1:RuleEngine/LineDetector/Crossed',
  'tns1:VideoSource/ImageTooBlurry/AnalyticsService',
  'tns1:VideoSource/ImageTooDark',
  'tns1:Device/Trigger/DigitalInput',
  'tns1:VideoSource/SignalLoss',
]

onMounted(async () => {
  const d = await cameraApi.list(1, 200)
  cameras.value = d.records
  if (cameras.value.length) {
    cameraId.value = cameras.value[0].id
    await load()
  }
})

async function load() {
  if (!cameraId.value) return
  loading.value = true
  try {
    rules.value = await eventApi.listRules(cameraId.value)
  } finally {
    loading.value = false
  }
}

function add() {
  Object.assign(form, {
    id: null, cameraId: cameraId.value, ruleName: '', topic: 'tns1:VideoSource/MotionAlarm',
    recordVideo: true, snapshot: true, preSeconds: 5, postSeconds: 15, enabled: true,
  })
  dialog.value = true
}
function edit(row: any) { Object.assign(form, row); dialog.value = true }
async function save() {
  if (form.id) {
    await eventApi.updateRule(form.id, form)
  } else {
    await eventApi.createRule(form)
  }
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function del(id: number) {
  await eventApi.deleteRule(id)
  ElMessage.success('已删除')
  load()
}
async function toggle(row: any) {
  await eventApi.updateRule(row.id, row)
}
</script>
