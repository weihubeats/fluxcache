<script setup lang="ts">
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DatabaseOutlined,
  SettingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons-vue'
import { useServiceStore } from '@/stores/connection'

const route = useRoute()
const router = useRouter()
const store = useServiceStore()

const selectedKeys = computed(() => {
  if (route.name === 'settings') return ['settings']
  return ['caches']
})

const menuItems = [
  {
    key: 'caches',
    icon: () => h(DatabaseOutlined),
    label: '缓存总览',
    title: '缓存总览',
  },
  {
    key: 'settings',
    icon: () => h(SettingOutlined),
    label: '服务管理',
    title: '服务管理',
  },
]

const onlineCount = computed(
  () => store.enabledServices.filter((s) => store.getRuntime(s.id).status === 'online').length,
)

function onMenuClick(info: { key: string | number }) {
  if (String(info.key) === 'settings') router.push({ name: 'settings' })
  else router.push({ name: 'caches' })
}
</script>

<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider theme="light" width="200" breakpoint="lg" collapsed-width="64">
      <div class="brand">
        <span class="brand-mark">FC</span>
        <span class="brand-text">FluxCache</span>
      </div>
      <a-menu
        mode="inline"
        :selected-keys="selectedKeys"
        :items="menuItems"
        @click="onMenuClick"
      />
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <div class="header-title">{{ (route.meta.title as string) || 'Dashboard' }}</div>
        <div class="header-meta">
          <a-tag color="blue">服务 {{ store.enabledServices.length }}</a-tag>
          <a-space>
            <CheckCircleOutlined v-if="onlineCount > 0" style="color: #52c41a" />
            <CloseCircleOutlined v-else style="color: #ff4d4f" />
            <span>在线 {{ onlineCount }} / {{ store.enabledServices.length }}</span>
          </a-space>
        </div>
      </a-layout-header>
      <a-layout-content class="content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f0;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #1677ff;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.brand-text {
  font-weight: 600;
  letter-spacing: 0.02em;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 0 24px;
  border-bottom: 1px solid #f0f0f0;
  height: 64px;
  line-height: 64px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.content {
  margin: 16px;
}
</style>
