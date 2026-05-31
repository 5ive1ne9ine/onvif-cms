<template>
  <el-card>
    <template #header>{{ isEdit ? '编辑摄像头' : '新增摄像头' }}</template>
    <el-form :model="form" ref="formRef" label-width="120px" style="max-width:600px">
      <el-form-item label="名称" prop="name" :rules="[{required: true, message:'请输入名称'}]">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="IP 地址" prop="ip" :rules="[{required: true, message:'请输入IP'}]">
        <el-input v-model="form.ip" placeholder="例: 192.168.1.100" />
      </el-form-item>
      <el-form-item label="ONVIF 端口">
        <el-input-number v-model="form.onvifPort" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="用户名">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password
                 :placeholder="isEdit ? '留空则不修改' : ''" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save" :loading="saving">保存</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cameraApi } from '../../api/camera'
import { ElMessage, type FormInstance } from 'element-plus'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const saving = ref(false)

const id = computed(() => Number(route.params.id))
const isEdit = computed(() => !!id.value)

const form = reactive({
  name: '',
  ip: route.query.ip as string || '',
  onvifPort: Number(route.query.port) || 80,
  username: '',
  password: '',
  enabled: true,
})

onMounted(async () => {
  if (isEdit.value) {
    const c = await cameraApi.get(id.value)
    Object.assign(form, c, { password: '' })
  }
})

async function save() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      await cameraApi.update(id.value, form)
    } else {
      await cameraApi.create(form)
    }
    ElMessage.success('保存成功')
    router.push('/cameras')
  } finally {
    saving.value = false
  }
}
</script>
