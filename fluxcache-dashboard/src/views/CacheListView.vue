<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Input, Modal, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { clearCache, fetchAllCaches, fetchStaticsSummary } from '@/api/cache'
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
const searchText = ref('')
const lastSync = ref('—')

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
  const kw = searchText.value.trim().toLowerCase()
  let list = filterServiceId.value
    ? rows.value.filter((r) => r.serviceId === filterServiceId.value)
    : rows.value
  if (kw) {
    list = list.filter(
      (r) =>
        r.cacheName?.toLowerCase().includes(kw) ||
        r.serviceName?.toLowerCase().includes(kw) ||
        r.methodName?.toLowerCase().includes(kw) ||
        r.key?.toLowerCase().includes(kw),
    )
  }
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

const cacheStrategyCount = computed(() => rows.value.filter((r) => r.online).length)

const columns = computed<TableColumnsType>(() => [
  { title: '服务', key: 'serviceName', fixed: 'left', width: 150 },
  { title: '缓存名', dataIndex: 'cacheName', key: 'cacheName', width: 180, ellipsis: true },
  { title: '层级', key: 'level', width: 104 },
  { title: 'Method', key: 'method', width: 200 },
  { title: 'Key', key: 'elKey', width: 130 },
  { title: 'L1', key: 'first', width: 172 },
  { title: 'L2', key: 'second', width: 172 },
  { title: '命中率', key: 'hitRate', width: 108, align: 'right' },
  { title: '请求', key: 'req', width: 76, align: 'right' },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', fixed: 'right', width: 230 },
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
    lastSync.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
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
  if (rate >= 0.9) return 'good'
  if (rate >= 0.7) return 'mid'
  return 'low'
}

onMounted(load)
</script>

<template>
  <div class="overview content-view">
    <!-- page head -->
    <div class="pagehead">
      <div>
        <h1 class="ttl"><span class="accent"></span>缓存总览</h1>
        <div class="sub">
          实时聚合 <span>{{ store.enabledServices.length }}</span> 个服务 ·
          <span>{{ cacheStrategyCount }}</span> 条缓存策略 · 上次同步
          <span>{{ lastSync }}</span>
        </div>
      </div>
      <div class="head-actions">
        <button class="btn" @click="router.push({ name: 'settings' })">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 3H2l8 9.46V19l4 2v-8.54z"/></svg>
          服务管理
        </button>
        <button class="btn primary" :class="{ spin: loading }" @click="load">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/></svg>
          刷新全部
        </button>
      </div>
    </div>

    <OverviewKpiCards
      :configured="store.services.length"
      :enabled="store.enabledServices.length"
      :online="onlineServices.length"
      :offline="offlineServices.length"
    />

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

    <!-- table panel -->
    <section class="panel">
      <div class="toolbar">
        <h3>
          <svg class="panel-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/><path d="M3 12c0 1.66 4 3 9 3s9-1.34 9-3"/></svg>
          缓存明细
        </h3>
        <div class="search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          <input v-model="searchText" type="text" placeholder="搜索缓存名 / Method / Key…" />
        </div>
        <a-select
          v-model:value="filterServiceId"
          :options="serviceOptions"
          placeholder="按服务筛选"
          allow-clear
          class="service-filter"
        />
      </div>

      <div class="twrap">
        <a-table
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
              <div v-if="(record as CacheOverviewRow).online" class="hit">
                <span class="v" :class="hitRateTone((record as CacheOverviewRow).hitRate)">
                  {{ formatHitRate((record as CacheOverviewRow).hitRate) }}
                </span>
                <div class="hitbar">
                  <i
                    :class="hitRateTone((record as CacheOverviewRow).hitRate)"
                    :style="{ width: `${Math.max(((record as CacheOverviewRow).hitRate ?? 0) * 100, 2)}%` }"
                  ></i>
                </div>
              </div>
              <span v-else class="none-text">—</span>
            </template>

            <template v-else-if="column.key === 'req'">
              <span v-if="(record as CacheOverviewRow).online" class="req">
                {{ (record as CacheOverviewRow).totalRequest ?? '—' }}
              </span>
              <span v-else class="none-text">—</span>
            </template>

            <template v-else-if="column.key === 'status'">
              <span
                v-if="(record as CacheOverviewRow).online"
                class="status"
              >
                <span class="d"></span>在线
              </span>
              <span v-else class="status off"><span class="d"></span>离线</span>
            </template>

            <template v-else-if="column.key === 'action'">
              <div v-if="(record as CacheOverviewRow).online" class="ops">
                <button class="op" @click="openDetail(record as CacheOverviewRow)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3v18h18"/><path d="M7 13l3-3 3 3 4-5"/></svg>
                  监控
                </button>
                <button class="op" @click="openOps(record as CacheOverviewRow)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
                  Key
                </button>
                <button class="op danger" @click="confirmClear(record as CacheOverviewRow)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
                  清空
                </button>
              </div>
              <a-typography-text v-else type="danger" class="offline-err">
                {{ (record as CacheOverviewRow).error || '不可用' }}
              </a-typography-text>
            </template>
          </template>
        </a-table>
      </div>
    </section>

    <KeyOpsDrawer
      v-model:open="opsOpen"
      :service-id="opsServiceId"
      :cache-name="opsCacheName"
      @cleared="load"
    />
  </div>
</template>

<style scoped>
/* ===== page head ===== */
.pagehead {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.ttl {
  font-family: var(--display);
  font-weight: 800;
  font-size: clamp(28px, 3.4vw, 40px);
  letter-spacing: -0.5px;
  line-height: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0;
  color: var(--text);
}

.ttl .accent {
  width: 8px;
  height: 34px;
  border-radius: 6px;
  background: linear-gradient(180deg, var(--teal), var(--sky));
}

.sub {
  color: var(--text-faint);
  font-size: 13px;
  margin-top: 9px;
  font-family: var(--mono);
  letter-spacing: 0.3px;
}

.sub span {
  color: var(--teal);
}

.head-actions {
  display: flex;
  gap: 10px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--body);
  font-weight: 700;
  font-size: 13px;
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 1px solid var(--line-2);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-dim);
  transition: 0.2s;
  white-space: nowrap;
}

.btn svg {
  width: 16px;
  height: 16px;
}

.btn:hover {
  color: #fff;
  border-color: var(--line-3);
  background: rgba(255, 255, 255, 0.07);
  transform: translateY(-1px);
}

.btn.primary {
  color: #04110f;
  background: linear-gradient(135deg, var(--teal), #6fe9d4);
  border-color: transparent;
  box-shadow: 0 10px 24px -12px rgba(51, 216, 194, 0.8);
}

.btn.primary:hover {
  filter: brightness(1.06);
  transform: translateY(-1px);
}

.btn.primary.spin svg {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ===== table panel ===== */
.panel {
  margin-top: 4px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
  background: linear-gradient(160deg, var(--surface), var(--bg-2));
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
}

.toolbar h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--display);
  font-weight: 700;
  font-size: 16px;
  margin: 0 4px 0 0;
  color: var(--text);
}

.toolbar h3 .panel-ico {
  width: 17px;
  height: 17px;
  color: var(--accent);
}

.search {
  position: relative;
  flex: 1;
  min-width: 180px;
  max-width: 320px;
}

.search svg {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 15px;
  height: 15px;
  color: var(--text-faint);
}

.search input {
  width: 100%;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--line-2);
  border-radius: var(--radius-sm);
  padding: 9px 12px 9px 34px;
  color: var(--text);
  font-family: var(--body);
  font-size: 13px;
  outline: none;
  transition: 0.2s;
}

.search input::placeholder {
  color: var(--text-faint);
}

.search input:focus {
  border-color: var(--teal);
  background: rgba(51, 216, 194, 0.05);
  box-shadow: 0 0 0 3px rgba(51, 216, 194, 0.1);
}

.service-filter {
  width: 200px;
}

/* ===== cells ===== */
.svc-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.svc-name {
  font-weight: 700;
  color: var(--text);
  font-size: 13px;
}

.ns {
  font-size: 10.5px;
  color: var(--text-faint);
}

.cache-name {
  font-size: 13px;
}

.none-text {
  color: rgba(234, 240, 251, 0.25);
}

.req {
  font-family: var(--mono);
  font-weight: 600;
  color: var(--text-dim);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--teal);
}

.status .d {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--teal);
  animation: pulse 2.2s infinite;
}

.status.off {
  color: var(--rose);
}

.status.off .d {
  background: var(--rose);
  animation: none;
}

.hit {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 96px;
}

.hit .v {
  font-family: var(--mono);
  font-weight: 700;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.hit .v.good {
  color: var(--lime);
}
.hit .v.mid {
  color: var(--amber);
}
.hit .v.low {
  color: var(--rose);
}
.hit .v:not(.good):not(.mid):not(.low) {
  color: var(--text-dim);
}

.hitbar {
  height: 5px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
  width: 96px;
}

.hitbar i {
  display: block;
  height: 100%;
  border-radius: 5px;
  transition: width 1.2s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.hitbar i.good {
  background: linear-gradient(90deg, var(--teal), var(--lime));
}
.hitbar i.mid {
  background: linear-gradient(90deg, var(--amber), #ffd27a);
}
.hitbar i.low {
  background: linear-gradient(90deg, var(--rose), #ff9aa9);
}
.hitbar i:not(.good):not(.mid):not(.low) {
  background: rgba(234, 240, 251, 0.25);
}

.ops {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.op {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--body);
  font-size: 11.5px;
  font-weight: 600;
  padding: 6px 9px;
  border-radius: 8px;
  border: 1px solid var(--line-2);
  background: rgba(255, 255, 255, 0.02);
  color: var(--text-dim);
  cursor: pointer;
  transition: 0.18s;
  white-space: nowrap;
}

.op svg {
  width: 13px;
  height: 13px;
}

.op:hover {
  color: #fff;
  border-color: var(--line-3);
  background: rgba(255, 255, 255, 0.07);
  transform: translateY(-1px);
}

.op.danger {
  color: var(--rose);
  border-color: rgba(240, 103, 126, 0.25);
}

.op.danger:hover {
  background: var(--rose-d);
  border-color: rgba(240, 103, 126, 0.5);
  color: #ffd0d8;
}

.offline-err {
  font-size: 12px;
  display: inline-block;
  max-width: 200px;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(51, 216, 194, 0.5);
  }
  70% {
    box-shadow: 0 0 0 7px rgba(51, 216, 194, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(51, 216, 194, 0);
  }
}
</style>
