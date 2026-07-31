<script setup lang="ts">
import { computed } from 'vue'
import { shortMethodName } from '@/utils/format'

const props = defineProps<{
  methodName?: string | null
}>()

const display = computed(() => shortMethodName(props.methodName))
</script>

<template>
  <a-tooltip :title="display.full" placement="topLeft">
    <code class="method-chip mono">{{ display.short }}</code>
  </a-tooltip>
</template>

<style scoped>
.method-chip {
  display: inline-block;
  max-width: 220px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #f5f7fa;
  border: 1px solid #eef0f3;
  color: #262626;
  font-size: 12px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
</style>
