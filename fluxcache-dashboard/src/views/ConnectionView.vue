<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { fetchAllCaches } from '@/api/cache'
import { resetHttpClient } from '@/api/http'
import {
  buildApiRoot,
  useServiceStore,
  type ServiceEndpoint,
} from '@/stores/connection'

const store = useServiceStore()
const modalOpen = ref(false)
const testingId = ref<string | null>(null)
const testingAll = ref(false)
const editingId = ref<string | null>(null)

const form = reactive({
  name: '',
  baseUrl: '',
  prefix: '/cache/manager/v1',
  enabled: true,
})

const columns: TableColumnsType = [
  { title: '服务名', dataIndex: 'name', key: 'name', width: 150 },
  { title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl', ellipsis: true },
  { title: 'Prefix', dataIndex: 'prefix', key: 'prefix', width: 170 },
  { title: '启用', key: 'enabled', width: 70 },
  { title: '状态', key: 'status', width: 190 },
  { title: '操作', key: 'action', width: 240 },
]

const rows = computed(() => store.services)

function resetForm() {
  form.name = ''
  form.baseUrl = ''
  form.prefix = '/cache/manager/v1'
  form.enabled = true
  editingId.value = null
}

function openCreate() {
  resetForm()
  modalOpen.value = true
}

function openEdit(svc: ServiceEndpoint) {
  editingId.value = svc.id
  form.name = svc.name
  form.baseUrl = svc.baseUrl
  form.prefix = svc.prefix
  form.enabled = svc.enabled
  modalOpen.value = true
}

function save() {
  if (!form.name.trim()) {
    message.warning('请填写服务名，例如 pay-service')
    return
  }
  if (!form.prefix.trim()) {
    message.warning('请填写 API Prefix')
    return
  }
  if (editingId.value) {
    store.updateService(editingId.value, {
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      prefix: form.prefix.trim(),
      enabled: form.enabled,
    })
    resetHttpClient(editingId.value)
    message.success('已更新')
  } else {
    store.addService({
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      prefix: form.prefix.trim(),
      enabled: form.enabled,
    })
    message.success('已添加')
  }
  modalOpen.value = false
  resetForm()
}

function remove(svc: ServiceEndpoint) {
  Modal.confirm({
    title: '删除服务？',
    content: `确认删除「${svc.name}」？`,
    okType: 'danger',
    onOk() {
      store.removeService(svc.id)
      resetHttpClient(svc.id)
      message.success('已删除')
    },
  })
}

async function testOne(svc: ServiceEndpoint) {
  testingId.value = svc.id
  resetHttpClient(svc.id)
  try {
    const data = await fetchAllCaches(svc)
    store.setRuntime(svc.id, {
      status: 'online',
      namespace: data.namespace || '',
      lastError: '',
      cacheCount: data.cacheOperations?.length || 0,
    })
    message.success(`${svc.name} 连接成功，namespace=${data.namespace || '-'}`)
  } catch (e) {
    const err = e as Error
    store.setRuntime(svc.id, {
      status: 'offline',
      namespace: '',
      lastError: err.message || '连接失败',
      cacheCount: 0,
    })
  } finally {
    testingId.value = null
  }
}

async function testAll() {
  testingAll.value = true
  try {
    for (const svc of store.enabledServices) {
      await testOne(svc)
    }
  } finally {
    testingAll.value = false
  }
}

function toggleEnabled(svc: ServiceEndpoint, checked: boolean) {
  store.updateService(svc.id, { enabled: checked })
}
</script>

<template>
  <div class="page-card content-view">
    <div class="pagehead">
      <div>
        <h1 class="ttl"><span class="accent"></span>服务管理</h1>
        <div class="sub">
          为每个接入 FluxCache 的业务实例配置独立入口，例如
          <span>pay-service</span>、<span>order-service</span>。总览页会聚合所有启用服务的缓存。
        </div>
      </div>
      <div class="head-actions">
        <button class="btn" :class="{ spin: testingAll }" :disabled="testingAll" @click="testAll">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/></svg>
          全部探测
        </button>
        <button class="btn primary" @click="openCreate">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M12 5v14M5 12h14"/></svg>
          添加服务
        </button>
      </div>
    </div>

    <a-table
      row-key="id"
      size="middle"
      :columns="columns"
      :data-source="rows"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'baseUrl'">
          <span class="mono">{{ record.baseUrl || '(relative / proxy)' }}</span>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <a-switch
            :checked="(record as ServiceEndpoint).enabled"
            @change="(v) => toggleEnabled(record as ServiceEndpoint, !!v)"
          />
        </template>
        <template v-else-if="column.key === 'status'">
          <a-space direction="vertical" :size="0">
            <span
              class="status"
              :class="store.getRuntime((record as ServiceEndpoint).id).status"
            >
              <span class="d"></span>
              {{
                store.getRuntime((record as ServiceEndpoint).id).status === 'online'
                  ? '在线'
                  : store.getRuntime((record as ServiceEndpoint).id).status === 'offline'
                    ? '离线'
                    : '未探测'
              }}
            </span>
            <a-typography-text
              v-if="store.getRuntime((record as ServiceEndpoint).id).namespace"
              type="secondary"
              class="mono"
            >
              ns: {{ store.getRuntime((record as ServiceEndpoint).id).namespace }}
            </a-typography-text>
            <a-typography-text
              v-if="store.getRuntime((record as ServiceEndpoint).id).lastError"
              type="danger"
              style="font-size: 12px"
            >
              {{ store.getRuntime((record as ServiceEndpoint).id).lastError }}
            </a-typography-text>
          </a-space>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button
              type="link"
              size="small"
              :loading="testingId === (record as ServiceEndpoint).id"
              @click="testOne(record as ServiceEndpoint)"
            >
              探测
            </a-button>
            <a-button type="link" size="small" @click="openEdit(record as ServiceEndpoint)">
              编辑
            </a-button>
            <a-button type="link" size="small" danger @click="remove(record as ServiceEndpoint)">
              删除
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑服务' : '添加服务'"
      ok-text="保存"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item label="服务名" required>
          <a-input v-model:value="form.name" placeholder="例如 pay-service" />
        </a-form-item>
        <a-form-item label="API Base URL">
          <a-input
            v-model:value="form.baseUrl"
            placeholder="例如 http://pay-service:8080（本地可留空走 Vite 代理）"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="API Prefix（flux.cache.prefix）" required>
          <a-input v-model:value="form.prefix" placeholder="/cache/manager/v1" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
        <a-alert
          type="info"
          show-icon
          :message="`预览路径：${buildApiRoot(form) || '(empty)'}`"
        />
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
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
  font-size: clamp(26px, 3vw, 34px);
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
  height: 30px;
  border-radius: 6px;
  background: linear-gradient(180deg, var(--teal), var(--sky));
}

.sub {
  color: var(--text-faint);
  font-size: 12.5px;
  margin-top: 9px;
  font-family: var(--mono);
  letter-spacing: 0.2px;
  max-width: 680px;
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

.btn.spin svg {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-faint);
}

.status .d {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-faint);
}

.status.online {
  color: var(--teal);
}

.status.online .d {
  background: var(--teal);
  animation: pulse 2.2s infinite;
}

.status.offline {
  color: var(--rose);
}

.status.offline .d {
  background: var(--rose);
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
