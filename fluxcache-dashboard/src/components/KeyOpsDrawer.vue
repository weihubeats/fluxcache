<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { clearCache, evictCache, fetchCacheValue } from '@/api/cache'
import { useServiceStore } from '@/stores/connection'
import CacheValueViewer from './CacheValueViewer.vue'

const open = defineModel<boolean>('open', { default: false })

const props = defineProps<{
  serviceId: string
  cacheName: string
}>()

const emit = defineEmits<{
  cleared: []
}>()

const store = useServiceStore()

const activeTab = ref('query')
const queryKey = ref('')
const querying = ref(false)
const queryResult = ref<{ flag: boolean; value: unknown } | null>(null)

const evictKeysText = ref('')
const evicting = ref(false)

const clearConfirmName = ref('')
const clearing = ref(false)

const service = computed(() => store.serviceById(props.serviceId))
const title = computed(
  () => `Key 运维 · ${service.value?.name || props.serviceId} / ${props.cacheName}`,
)

watch(open, (v) => {
  if (v) {
    queryResult.value = null
    queryKey.value = ''
    evictKeysText.value = ''
    clearConfirmName.value = ''
    activeTab.value = 'query'
  }
})

function requireService() {
  const svc = service.value
  if (!svc) {
    message.error('服务不存在，请先在服务管理中配置')
    return null
  }
  return svc
}

async function onQuery() {
  const svc = requireService()
  if (!svc) return
  if (!queryKey.value.trim()) {
    message.warning('请输入 key')
    return
  }
  querying.value = true
  try {
    queryResult.value = await fetchCacheValue(svc, props.cacheName, queryKey.value.trim())
  } finally {
    querying.value = false
  }
}

function parseKeys(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function onEvict() {
  const svc = requireService()
  if (!svc) return
  const keys = parseKeys(evictKeysText.value)
  if (!keys.length) {
    message.warning('请输入至少一个 key')
    return
  }
  Modal.confirm({
    title: '确认按 key 清理？',
    content: `将从「${svc.name} / ${props.cacheName}」删除 ${keys.length} 个 key`,
    okType: 'danger',
    async onOk() {
      evicting.value = true
      try {
        const ok = await evictCache(svc, props.cacheName, keys)
        if (ok) message.success('清理成功')
        else message.warning('清理返回 false')
      } finally {
        evicting.value = false
      }
    },
  })
}

async function onClear() {
  const svc = requireService()
  if (!svc) return
  if (clearConfirmName.value !== props.cacheName) {
    message.warning('请输入完整的 cacheName 以确认')
    return
  }
  Modal.confirm({
    title: '确认清空整个缓存？',
    content: `此操作将清空「${svc.name} / ${props.cacheName}」下全部数据，不可恢复。`,
    okType: 'danger',
    async onOk() {
      clearing.value = true
      try {
        const ok = await clearCache(svc, props.cacheName)
        if (ok) {
          message.success('已清空')
          emit('cleared')
          open.value = false
        } else {
          message.warning('清空返回 false')
        }
      } finally {
        clearing.value = false
      }
    },
  })
}
</script>

<template>
  <a-drawer v-model:open="open" :title="title" width="560" destroy-on-close>
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="query" tab="查询 Key">
        <a-space direction="vertical" style="width: 100%">
          <a-input-search
            v-model:value="queryKey"
            placeholder="输入缓存 key"
            enter-button="查询"
            :loading="querying"
            @search="onQuery"
          />
          <CacheValueViewer
            v-if="queryResult"
            :found="queryResult.flag"
            :value="queryResult.value"
          />
        </a-space>
      </a-tab-pane>

      <a-tab-pane key="evict" tab="按 Key 清理">
        <a-space direction="vertical" style="width: 100%">
          <a-textarea
            v-model:value="evictKeysText"
            :rows="6"
            placeholder="多个 key 用换行或逗号分隔"
          />
          <a-button type="primary" danger :loading="evicting" @click="onEvict">清理选中 Key</a-button>
        </a-space>
      </a-tab-pane>

      <a-tab-pane key="clear" tab="清空缓存">
        <a-alert
          type="warning"
          show-icon
          message="危险操作"
          description="清空会删除该 cacheName 下全部缓存条目。请输入完整 cacheName 确认。"
          style="margin-bottom: 16px"
        />
        <a-space direction="vertical" style="width: 100%">
          <a-input
            v-model:value="clearConfirmName"
            :placeholder="`输入 ${cacheName} 确认`"
          />
          <a-button type="primary" danger :loading="clearing" @click="onClear">清空整个缓存</a-button>
        </a-space>
      </a-tab-pane>
    </a-tabs>
  </a-drawer>
</template>
