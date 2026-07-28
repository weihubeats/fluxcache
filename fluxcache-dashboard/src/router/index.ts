import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        {
          path: '',
          name: 'caches',
          component: () => import('@/views/CacheListView.vue'),
          meta: { title: '缓存总览' },
        },
        {
          path: 'services/:serviceId/caches/:cacheName',
          name: 'cache-detail',
          component: () => import('@/views/CacheDetailView.vue'),
          meta: { title: '缓存详情' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/ConnectionView.vue'),
          meta: { title: '服务管理' },
        },
        {
          // legacy single-service detail → bounce to list
          path: 'caches/:cacheName',
          redirect: { name: 'caches' },
        },      ],
    },
  ],
})

export default router
