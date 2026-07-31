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
  <div class="detail content-view">
    <div class="page-card head-card">
      <div class="toolbar">
        <a-space wrap>
          <button class="btn ghost" @click="router.push({ name: 'caches' })">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5"/><path d="m12 19-7-7 7-7"/></svg>
            返回列表
          </button>
          <span class="tag svc">{{ service?.name || serviceId }}</span>
          <h1 class="ttl">{{ cacheName }}</h1>
          <span v-if="stats?.startTime" class="text-faint mono">
            监控起始 {{ formatTime(stats.startTime) }}
          </span>
        </a-space>
        <a-space>
          <a-switch
            v-model:checked="autoRefresh"
            checked-children="自动刷新"
            un-checked-children="手动"
          />
          <button class="btn ghost" :disabled="!service" @click="opsOpen = true">Key 运维</button>
          <button class="btn primary" :disabled="!service" :class="{ spin: loading }" @click="load">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/></svg>
            刷新
          </button>
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

        <div class="chart-grid">
          <div class="page-card chart-card">
            <div class="chart-title">
              <span class="dot teal"></span>命中率
            </div>
            <StatsChart type="hitRate" :windows="stats.windows || []" />
          </div>
          <div class="page-card chart-card">
            <div class="chart-title">
              <span class="dot amber"></span>请求量
            </div>
            <StatsChart type="request" :windows="stats.windows || []" />
          </div>
          <div class="page-card chart-card">
            <div class="chart-title">
              <span class="dot sky"></span>最大加载耗时
            </div>
            <StatsChart type="maxLoad" :windows="stats.windows || []" />
          </div>
        </div>
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

.head-card {
  margin-bottom: 16px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--body);
  font-weight: 700;
  font-size: 13px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 1px solid var(--line-2);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-dim);
  transition: 0.2s;
  white-space: nowrap;
}

.btn svg {
  width: 15px;
  height: 15px;
}

.btn:hover:not(:disabled) {
  color: #fff;
  border-color: var(--line-3);
  background: rgba(255, 255, 255, 0.07);
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn.primary {
  color: #04110f;
  background: linear-gradient(135deg, var(--teal), #6fe9d4);
  border-color: transparent;
  box-shadow: 0 10px 24px -12px rgba(51, 216, 194, 0.8);
}

.btn.primary:hover:not(:disabled) {
  filter: brightness(1.06);
}

.btn.primary.spin svg {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.tag.svc {
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 30px;
  border: 1px solid rgba(90, 169, 240, 0.3);
  color: var(--sky);
  background: rgba(90, 169, 240, 0.1);
  white-space: nowrap;
}

.ttl {
  font-family: var(--display);
  font-weight: 800;
  font-size: 22px;
  letter-spacing: -0.3px;
  margin: 0;
  color: var(--text);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.chart-card {
  min-width: 0;
}

.chart-card:first-child {
  grid-column: span 2;
}

.chart-title {
  font-family: var(--display);
  font-weight: 700;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 6px;
  color: var(--text);
}

.chart-title .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.chart-title .dot.teal {
  background: var(--teal);
}
.chart-title .dot.amber {
  background: var(--amber);
}
.chart-title .dot.sky {
  background: var(--sky);
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
  .chart-card:first-child {
    grid-column: span 1;
  }
}
</style>
