import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Dashboard', component: () => import('../views/DashboardView.vue') },
      { path: 'cameras', name: 'CameraList', component: () => import('../views/camera/CameraListView.vue') },
      { path: 'cameras/add', name: 'CameraAdd', component: () => import('../views/camera/CameraFormView.vue') },
      { path: 'cameras/:id/edit', name: 'CameraEdit', component: () => import('../views/camera/CameraFormView.vue') },
      { path: 'discover', name: 'Discover', component: () => import('../views/camera/CameraDiscoverView.vue') },
      { path: 'preview/:id', name: 'LivePreview', component: () => import('../views/preview/LivePreviewView.vue') },
      { path: 'events', name: 'EventLog', component: () => import('../views/event/EventLogView.vue') },
      { path: 'event-rules', name: 'EventRules', component: () => import('../views/event/EventRuleView.vue') },
      { path: 'recordings', name: 'Recordings', component: () => import('../views/recording/RecordingView.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _, next) => {
  if (to.meta.requiresAuth) {
    const auth = useAuthStore()
    if (!auth.token) {
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }
  }
  next()
})

export default router
