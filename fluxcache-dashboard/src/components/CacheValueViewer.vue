<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { prettyJson } from '@/utils/format'

const props = defineProps<{
  value: unknown
  found: boolean
}>()

const text = computed(() => prettyJson(props.value))

async function copy() {
  try {
    await navigator.clipboard.writeText(text.value)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}
</script>

<template>
  <div>
    <a-space style="margin-bottom: 8px">
      <a-tag :color="found ? 'success' : 'default'">{{ found ? '命中' : '未命中' }}</a-tag>
      <a-button size="small" :disabled="!found" @click="copy">复制 JSON</a-button>
    </a-space>
    <pre v-if="found" class="json-viewer mono">{{ text }}</pre>
    <a-empty v-else description="缓存中不存在该 key" />
  </div>
</template>
