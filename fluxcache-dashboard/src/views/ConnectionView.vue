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
  { title: '服务名', dataIndex: 'name', key: 'name', width: 160 },
  { title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl', ellipsis: true },
  { title: 'Prefix', dataIndex: 'prefix', key: 'prefix', width: 180 },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '状态', key: 'status', width: 160 },
  { title: '操作', key: 'action', width: 260 },
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
  <div class="page-card">
    <div class="toolbar">
      <div>
        <a-typography-title :level="4" style="margin-top: 0; margin-bottom: 4px">
          服务管理
        </a-typography-title>
        <a-typography-paragraph type="secondary" style="margin-bottom: 0">
          为每个接入 FluxCache 的业务实例配置独立入口，例如
          <code>pay-service</code>、<code>order-service</code>。总览页会聚合所有启用服务的缓存。
        </a-typography-paragraph>
      </div>
      <a-space>
        <a-button :loading="testingAll" @click="testAll">全部探测</a-button>
        <a-button type="primary" @click="openCreate">添加服务</a-button>
      </a-space>
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
            <a-tag
              :color="
                store.getRuntime((record as ServiceEndpoint).id).status === 'online'
                  ? 'success'
                  : store.getRuntime((record as ServiceEndpoint).id).status === 'offline'
                    ? 'error'
                    : 'default'
              "
            >
              {{
                store.getRuntime((record as ServiceEndpoint).id).status === 'online'
                  ? '在线'
                  : store.getRuntime((record as ServiceEndpoint).id).status === 'offline'
                    ? '离线'
                    : '未探测'
              }}
            </a-tag>
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
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
