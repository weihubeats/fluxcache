<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { fetchAllStatics } from '@/api/cache'
import KeyOpsDrawer from '@/components/KeyOpsDrawer.vue'
import StatsChart from '@/components/StatsChart.vue'
import StatsKpiCards from '@/components/StatsKpiCards.vue'
import { useServiceStore } from '@/stores/connection'
import type { FluxCacheAllStaticsVO } from '@/types/cache'
import { formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const store = useServiceStore()

const serviceId = computed(() => String(route.params.serviceId || ''))
const cacheName = computed(() => decodeURIComponent(String(route.params.cacheName || '')))
const service = computed(() => store.serviceById(serviceId.value))

const loading = ref(false)
const stats = ref<FluxCacheAllStaticsVO | null>(null)
const autoRefresh = ref(false)
const opsOpen = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  if (!cacheName.value || !service.value) {
    stats.value = null
    return
  }
  loading.value = true
  try {
    stats.value = await fetchAllStatics(service.value, cacheName.value)
  } catch {
    stats.value = null
  } finally {
    loading.value = false
  }
}

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, (on) => {
  clearTimer()
  if (on) {
    message.info('已开启 15s 自动刷新')
    timer = setInterval(load, 15000)
  }
})

watch([serviceId, cacheName], () => {
  load()
})

onMounted(load)
onUnmounted(clearTimer)
</script>

<template>
  <div>
    <div class="page-card" style="margin-bottom: 16px">
      <div class="toolbar">
        <a-space wrap>
          <a-button @click="router.push({ name: 'caches' })">返回列表</a-button>
          <a-tag color="blue">{{ service?.name || serviceId }}</a-tag>
          <a-typography-title :level="4" style="margin: 0">{{ cacheName }}</a-typography-title>
          <a-typography-text v-if="stats?.startTime" type="secondary">
            监控起始 {{ formatTime(stats.startTime) }}
          </a-typography-text>
        </a-space>
        <a-space>
          <a-switch
            v-model:checked="autoRefresh"
            checked-children="自动刷新"
            un-checked-children="手动"
          />
          <a-button :disabled="!service" @click="opsOpen = true">Key 运维</a-button>
          <a-button type="primary" :loading="loading" :disabled="!service" @click="load">
            刷新
          </a-button>
        </a-space>
      </div>
      <a-alert
        v-if="!service"
        type="error"
        show-icon
        style="margin-top: 12px"
        message="服务未配置"
        description="请先在「服务管理」中添加对应服务，或从总览页重新进入。"
      />
    </div>

    <a-spin :spinning="loading">
      <template v-if="stats">
        <div class="page-card" style="margin-bottom: 16px">
          <StatsKpiCards :stats="stats" />
        </div>

        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :lg="24">
            <div class="page-card">
              <a-typography-title :level="5">命中率</a-typography-title>
              <StatsChart type="hitRate" :windows="stats.windows || []" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="12">
            <div class="page-card">
              <a-typography-title :level="5">请求量</a-typography-title>
              <StatsChart type="request" :windows="stats.windows || []" />
            </div>
          </a-col>
          <a-col :xs="24" :lg="12">
            <div class="page-card">
              <a-typography-title :level="5">最大加载耗时</a-typography-title>
              <StatsChart type="maxLoad" :windows="stats.windows || []" />
            </div>
          </a-col>
        </a-row>
      </template>
      <a-empty v-else description="暂无统计数据" />
    </a-spin>

    <KeyOpsDrawer
      v-if="service"
      v-model:open="opsOpen"
      :service-id="serviceId"
      :cache-name="cacheName"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
