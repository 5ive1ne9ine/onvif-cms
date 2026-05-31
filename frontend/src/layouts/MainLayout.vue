<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">📹 ONVIF CMS</div>
      <el-menu :default-active="$route.path" router class="menu" background-color="#001529" text-color="#fff"
               active-text-color="#409EFF">
        <el-menu-item index="/"><el-icon><Monitor /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="/cameras"><el-icon><VideoCamera /></el-icon><span>摄像头管理</span></el-menu-item>
        <el-menu-item index="/discover"><el-icon><Search /></el-icon><span>设备发现</span></el-menu-item>
        <el-menu-item index="/event-rules"><el-icon><Setting /></el-icon><span>事件规则</span></el-menu-item>
        <el-menu-item index="/events"><el-icon><Bell /></el-icon><span>事件日志</span></el-menu-item>
        <el-menu-item index="/recordings"><el-icon><Film /></el-icon><span>录制回放</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="title">摄像头管理系统</span>
        <div class="user">
          <span>{{ auth.nickname }}</span>
          <el-button link @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #001529; color: #fff; }
.logo { color: #fff; font-size: 18px; font-weight: 600; padding: 18px 20px; border-bottom: 1px solid #1f2d3d; }
.menu { border: none; }
.header {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; box-shadow: 0 1px 4px rgba(0,21,41,0.08); padding: 0 24px;
}
.title { font-size: 18px; font-weight: 600; }
.user { display: flex; align-items: center; gap: 12px; }
.main { background: #f0f2f5; padding: 16px; overflow: auto; }
</style>
