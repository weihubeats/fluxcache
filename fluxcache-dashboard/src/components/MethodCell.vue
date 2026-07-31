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
  max-width: 230px;
  padding: 2px 8px;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--line);
  color: var(--text-dim);
  font-size: 11.5px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
</style>
