<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Input, Modal, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  clearCache,
  fetchAllCaches,
  fetchStaticsSummary,
} from '@/api/cache'
import KeyOpsDrawer from '@/components/KeyOpsDrawer.vue'
import { useServiceStore } from '@/stores/connection'
import type { CacheOverviewRow } from '@/types/cache'
import { formatCacheConfig, formatHitRate } from '@/utils/format'

const router = useRouter()
const store = useServiceStore()

const loading = ref(false)
const rows = ref<CacheOverviewRow[]>([])
/** empty = all services */
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
  if (!filterServiceId.value) return rows.value
  return rows.value.filter((r) => r.serviceId === filterServiceId.value)
})

const onlineServices = computed(() =>
  store.enabledServices.filter((s) => store.getRuntime(s.id).status === 'online'),
)
const offlineServices = computed(() =>
  store.enabledServices.filter((s) => store.getRuntime(s.id).status === 'offline'),
)

const columns = computed<TableColumnsType>(() => [
  { title: '服务', dataIndex: 'serviceName', key: 'serviceName', fixed: 'left', width: 140 },
  { title: 'Namespace', dataIndex: 'namespace', key: 'namespace', width: 140, ellipsis: true },
  { title: 'Cache Name', dataIndex: 'cacheName', key: 'cacheName', width: 180, ellipsis: true },
  { title: 'Level', dataIndex: 'fluxCacheLevel', key: 'fluxCacheLevel', width: 130 },
  { title: 'Method', dataIndex: 'methodName', key: 'methodName', width: 140, ellipsis: true },
  { title: 'Key (EL)', dataIndex: 'key', key: 'key', width: 140, ellipsis: true },
  {
    title: '一级缓存',
    key: 'first',
    width: 200,
    ellipsis: true,
    customRender: ({ record }) => formatCacheConfig((record as CacheOverviewRow).firstCacheConfig),
  },
  {
    title: '二级缓存',
    key: 'second',
    width: 200,
    ellipsis: true,
    customRender: ({ record }) =>
      formatCacheConfig((record as CacheOverviewRow).secondaryCacheConfig),
  },
  {
    title: '命中率',
    key: 'hitRate',
    width: 100,
    customRender: ({ record }) => {
      const row = record as CacheOverviewRow
      return row.online ? formatHitRate(row.hitRate) : '-'
    },
  },
  {
    title: '请求数',
    key: 'req',
    width: 90,
    customRender: ({ record }) => {
      const row = record as CacheOverviewRow
      return row.online ? (row.totalRequest ?? '-') : '-'
    },
  },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', fixed: 'right', width: 240 },
])

async function loadOne(serviceId: string): Promise<CacheOverviewRow[]> {
  const service = store.serviceById(serviceId)
  if (!service || !service.enabled) return []

  try {
    const data = await fetchAllCaches(service)
    const summary = await fetchStaticsSummary(service)
    const summaryMap = new Map(
      (summary?.items || []).map((i) => [i.cacheName, i] as const),
    )
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

onMounted(load)
</script>

<template>
  <div>
    <a-row :gutter="[16, 16]" style="margin-bottom: 16px">
      <a-col :xs="12" :md="6">
        <div class="page-card kpi">
          <div class="kpi-label">已配置服务</div>
          <div class="kpi-value">{{ store.services.length }}</div>
        </div>
      </a-col>
      <a-col :xs="12" :md="6">
        <div class="page-card kpi">
          <div class="kpi-label">启用中</div>
          <div class="kpi-value">{{ store.enabledServices.length }}</div>
        </div>
      </a-col>
      <a-col :xs="12" :md="6">
        <div class="page-card kpi">
          <div class="kpi-label">在线</div>
          <div class="kpi-value ok">{{ onlineServices.length }}</div>
        </div>
      </a-col>
      <a-col :xs="12" :md="6">
        <div class="page-card kpi">
          <div class="kpi-label">离线</div>
          <div class="kpi-value" :class="{ bad: offlineServices.length }">
            {{ offlineServices.length }}
          </div>
        </div>
      </a-col>
    </a-row>

    <div class="page-card">
      <div class="toolbar">
        <a-space wrap>
          <a-typography-title :level="4" style="margin: 0">多服务缓存总览</a-typography-title>
          <a-select
            v-model:value="filterServiceId"
            style="width: 220px"
            :options="serviceOptions"
            placeholder="按服务筛选"
          />
        </a-space>
        <a-space>
          <a-button @click="router.push({ name: 'settings' })">服务管理</a-button>
          <a-button type="primary" :loading="loading" @click="load">刷新全部</a-button>
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
        type="warning"
        show-icon
        style="margin-bottom: 16px"
        :message="`${offlineServices.length} 个服务离线`"
        :description="offlineServices.map((s) => `${s.name}: ${store.getRuntime(s.id).lastError || '未知错误'}`).join('；')"
      />

      <a-table
        row-key="rowKey"
        size="middle"
        :loading="loading"
        :columns="columns"
        :data-source="filteredRows"
        :scroll="{ x: 1500 }"
        :pagination="{ pageSize: 20, showSizeChanger: true }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="(record as CacheOverviewRow).online ? 'success' : 'error'">
              {{ (record as CacheOverviewRow).online ? '在线' : '离线' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="(record as CacheOverviewRow).online">
              <a-button type="link" size="small" @click="openDetail(record as CacheOverviewRow)">
                监控
              </a-button>
              <a-button type="link" size="small" @click="openOps(record as CacheOverviewRow)">
                Key 运维
              </a-button>
              <a-button
                type="link"
                size="small"
                danger
                @click="confirmClear(record as CacheOverviewRow)"
              >
                清空
              </a-button>
            </a-space>
            <a-typography-text v-else type="secondary">
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
.kpi-label {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}
.kpi-value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}
.kpi-value.ok {
  color: #52c41a;
}
.kpi-value.bad {
  color: #ff4d4f;
}
</style>
