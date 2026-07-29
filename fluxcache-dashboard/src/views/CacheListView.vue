<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Input, Modal, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  LineChartOutlined,
  KeyOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import {
  clearCache,
  fetchAllCaches,
  fetchStaticsSummary,
} from '@/api/cache'
import CacheLayerCell from '@/components/CacheLayerCell.vue'
import CacheLevelTag from '@/components/CacheLevelTag.vue'
import ElKeyCell from '@/components/ElKeyCell.vue'
import KeyOpsDrawer from '@/components/KeyOpsDrawer.vue'
import MethodCell from '@/components/MethodCell.vue'
import OverviewKpiCards from '@/components/OverviewKpiCards.vue'
import { useServiceStore } from '@/stores/connection'
import type { CacheOverviewRow } from '@/types/cache'
import { formatHitRate } from '@/utils/format'

const router = useRouter()
const store = useServiceStore()

const loading = ref(false)
const rows = ref<CacheOverviewRow[]>([])
const filterServiceId = ref<string>('')

const opsOpen = ref(false)
const opsCacheName = ref('')
const opsServiceId = ref('')

const serviceOptions = computed(() => [
  { label: '全部服务', value: '' },
  ...store.services.map((s) => ({
    label: s.enabled ? s.name : `${s.name}（已禁用）`,
    value: s.id,
  })),
])

const filteredRows = computed(() => {
  const list = filterServiceId.value
    ? rows.value.filter((r) => r.serviceId === filterServiceId.value)
    : rows.value
  // Keep offline placeholder rows only when that service has no online caches
  return list
})

const tableRows = computed(() =>
  filteredRows.value.filter((r) => r.online || r.cacheName === '-'),
)

const onlineServices = computed(() =>
  store.enabledServices.filter((s) => store.getRuntime(s.id).status === 'online'),
)
const offlineServices = computed(() =>
  store.enabledServices.filter((s) => store.getRuntime(s.id).status === 'offline'),
)

const columns = computed<TableColumnsType>(() => [
  { title: '服务', key: 'serviceName', fixed: 'left', width: 130 },
  { title: '缓存名', dataIndex: 'cacheName', key: 'cacheName', width: 160, ellipsis: true },
  { title: '层级', key: 'level', width: 110 },
  { title: 'Method', key: 'method', width: 200 },
  { title: 'Key', key: 'elKey', width: 140 },
  { title: 'L1', key: 'first', width: 170 },
  { title: 'L2', key: 'second', width: 170 },
  { title: '命中率', key: 'hitRate', width: 96, align: 'right' },
  { title: '请求', key: 'req', width: 80, align: 'right' },
  { title: '状态', key: 'status', width: 88 },
  { title: '操作', key: 'action', fixed: 'right', width: 220 },
])

async function loadOne(serviceId: string): Promise<CacheOverviewRow[]> {
  const service = store.serviceById(serviceId)
  if (!service || !service.enabled) return []

  try {
    const data = await fetchAllCaches(service)
    const summary = await fetchStaticsSummary(service)
    const summaryMap = new Map((summary?.items || []).map((i) => [i.cacheName, i] as const))
    const namespace = data.namespace || summary?.namespace || ''
    const ops = data.cacheOperations || []
    store.setRuntime(service.id, {
      status: 'online',
      namespace,
      lastError: '',
      cacheCount: ops.length,
    })
    return ops.map((op) => {
      const s = summaryMap.get(op.cacheName)
      return {
        ...op,
        rowKey: `${service.id}::${op.cacheName}`,
        serviceId: service.id,
        serviceName: service.name,
        namespace,
        hitRate: s?.overallHitRate,
        totalRequest: s?.totalRequest,
        online: true,
      } satisfies CacheOverviewRow
    })
  } catch (e) {
    const err = e as Error
    store.setRuntime(service.id, {
      status: 'offline',
      namespace: '',
      lastError: err.message || '连接失败',
      cacheCount: 0,
    })
    return [
      {
        cacheName: '-',
        rowKey: `${service.id}::__offline__`,
        serviceId: service.id,
        serviceName: service.name,
        namespace: '-',
        online: false,
        error: err.message || '连接失败',
      },
    ]
  }
}

async function load() {
  loading.value = true
  try {
    const targets = store.enabledServices
    if (!targets.length) {
      rows.value = []
      message.warning('请先在「服务管理」中添加至少一个启用的服务')
      return
    }
    const batches = await Promise.all(targets.map((s) => loadOne(s.id)))
    rows.value = batches.flat()
  } finally {
    loading.value = false
  }
}

function openDetail(row: CacheOverviewRow) {
  if (!row.online) return
  router.push({
    name: 'cache-detail',
    params: { serviceId: row.serviceId, cacheName: row.cacheName },
  })
}

function openOps(row: CacheOverviewRow) {
  if (!row.online) return
  opsServiceId.value = row.serviceId
  opsCacheName.value = row.cacheName
  opsOpen.value = true
}

function confirmClear(row: CacheOverviewRow) {
  if (!row.online) return
  const service = store.serviceById(row.serviceId)
  if (!service) return
  let input = ''
  Modal.confirm({
    title: '清空缓存确认',
    content: h('div', [
      h(
        'p',
        `将清空服务「${row.serviceName}」下缓存「${row.cacheName}」。请输入完整 cacheName 确认：`,
      ),
      h(Input, {
        placeholder: row.cacheName,
        onChange: (e: Event) => {
          input = (e.target as HTMLInputElement).value
        },
      }),
    ]),
    okType: 'danger',
    async onOk() {
      if (input !== row.cacheName) {
        message.warning('请输入完整的 cacheName')
        return Promise.reject()
      }
      const ok = await clearCache(service, row.cacheName)
      if (ok) {
        message.success('已清空')
        await load()
      } else {
        message.warning('清空返回 false')
      }
    },
  })
}

function hitRateTone(rate?: number): string {
  if (rate == null) return ''
  if (rate >= 0.9) return 'hit-good'
  if (rate >= 0.7) return 'hit-mid'
  return 'hit-low'
}

onMounted(load)
</script>

<template>
  <div class="overview">
    <OverviewKpiCards
      :configured="store.services.length"
      :enabled="store.enabledServices.length"
      :online="onlineServices.length"
      :offline="offlineServices.length"
    />

    <div class="page-card table-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title">缓存总览</h2>
          <a-select
            v-model:value="filterServiceId"
            class="service-filter"
            :options="serviceOptions"
            placeholder="按服务筛选"
            allow-clear
          />
        </div>
        <a-space>
          <a-button @click="router.push({ name: 'settings' })">
            <template #icon><SettingOutlined /></template>
            服务管理
          </a-button>
          <a-button type="primary" :loading="loading" @click="load">
            <template #icon><ReloadOutlined /></template>
            刷新全部
          </a-button>
        </a-space>
      </div>

      <a-alert
        v-if="!store.enabledServices.length"
        type="warning"
        show-icon
        message="尚未配置启用的服务"
        description="请在「服务管理」中添加 pay-service、order-service 等业务实例的 API 地址。"
        style="margin-bottom: 16px"
      />
      <a-alert
        v-else-if="offlineServices.length"
        type="error"
        show-icon
        style="margin-bottom: 16px"
        :message="`${offlineServices.length} 个服务离线`"
        :description="
          offlineServices
            .map((s) => `${s.name}: ${store.getRuntime(s.id).lastError || '未知错误'}`)
            .join('；')
        "
      />

      <a-table
        class="overview-table"
        row-key="rowKey"
        size="middle"
        :loading="loading"
        :columns="columns"
        :data-source="tableRows"
        :scroll="{ x: 1480 }"
        :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'serviceName'">
            <div class="svc-cell">
              <span class="svc-name">{{ (record as CacheOverviewRow).serviceName }}</span>
              <span v-if="(record as CacheOverviewRow).online" class="ns mono">
                {{ (record as CacheOverviewRow).namespace }}
              </span>
            </div>
          </template>

          <template v-else-if="column.key === 'cacheName'">
            <template v-if="(record as CacheOverviewRow).online">
              <a-typography-text strong class="cache-name">
                {{ (record as CacheOverviewRow).cacheName }}
              </a-typography-text>
            </template>
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'level'">
            <CacheLevelTag
              v-if="(record as CacheOverviewRow).online"
              :level="(record as CacheOverviewRow).fluxCacheLevel"
            />
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'method'">
            <MethodCell
              v-if="(record as CacheOverviewRow).online"
              :method-name="(record as CacheOverviewRow).methodName"
            />
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'elKey'">
            <ElKeyCell
              v-if="(record as CacheOverviewRow).online"
              :el-key="(record as CacheOverviewRow).key"
            />
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'first'">
            <CacheLayerCell
              v-if="(record as CacheOverviewRow).online"
              :config="(record as CacheOverviewRow).firstCacheConfig"
            />
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'second'">
            <CacheLayerCell
              v-if="(record as CacheOverviewRow).online"
              :config="(record as CacheOverviewRow).secondaryCacheConfig"
            />
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'hitRate'">
            <span
              v-if="(record as CacheOverviewRow).online"
              class="metric"
              :class="hitRateTone((record as CacheOverviewRow).hitRate)"
            >
              {{ formatHitRate((record as CacheOverviewRow).hitRate) }}
            </span>
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'req'">
            <span v-if="(record as CacheOverviewRow).online" class="metric">
              {{ (record as CacheOverviewRow).totalRequest ?? '—' }}
            </span>
            <span v-else class="none-text">—</span>
          </template>

          <template v-else-if="column.key === 'status'">
            <a-badge
              :status="(record as CacheOverviewRow).online ? 'success' : 'error'"
              :text="(record as CacheOverviewRow).online ? '在线' : '离线'"
            />
          </template>

          <template v-else-if="column.key === 'action'">
            <div v-if="(record as CacheOverviewRow).online" class="action-group">
              <a-button size="small" @click="openDetail(record as CacheOverviewRow)">
                <template #icon><LineChartOutlined /></template>
                监控
              </a-button>
              <a-button size="small" @click="openOps(record as CacheOverviewRow)">
                <template #icon><KeyOutlined /></template>
                Key
              </a-button>
              <a-popconfirm
                title="确认清空该缓存？"
                :description="`服务 ${(record as CacheOverviewRow).serviceName} / ${(record as CacheOverviewRow).cacheName}`"
                ok-text="继续"
                cancel-text="取消"
                ok-type="danger"
                @confirm="confirmClear(record as CacheOverviewRow)"
              >
                <a-button size="small" danger>
                  <template #icon><DeleteOutlined /></template>
                  清空
                </a-button>
              </a-popconfirm>
            </div>
            <a-typography-text v-else type="danger" class="offline-err">
              {{ (record as CacheOverviewRow).error || '不可用' }}
            </a-typography-text>
          </template>
        </template>
      </a-table>

      <KeyOpsDrawer
        v-model:open="opsOpen"
        :service-id="opsServiceId"
        :cache-name="opsCacheName"
        @cleared="load"
      />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.service-filter {
  width: 220px;
}

.table-card {
  padding-top: 18px;
}

.overview-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.65);
  font-size: 12px;
}

.svc-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.svc-name {
  font-weight: 600;
  color: #262626;
}

.ns {
  font-size: 11px;
  color: rgba(0, 0, 0, 0.4);
}

.cache-name {
  font-size: 13px;
}

.none-text {
  color: rgba(0, 0, 0, 0.25);
}

.metric {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  font-size: 13px;
}

.hit-good {
  color: #389e0d;
}
.hit-mid {
  color: #d48806;
}
.hit-low {
  color: #cf1322;
}

.action-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px;
  flex-wrap: wrap;
}

.offline-err {
  font-size: 12px;
  display: inline-block;
  max-width: 200px;
}
</style>
