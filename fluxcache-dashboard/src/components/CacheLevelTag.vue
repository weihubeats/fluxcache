<script setup lang="ts">
import { computed } from 'vue'
import { formatCacheLevel } from '@/utils/format'
import type { FluxCacheLevel } from '@/types/cache'

const props = defineProps<{
  level?: FluxCacheLevel | null
}>()

const display = computed(() => formatCacheLevel(props.level))
</script>

<template>
  <a-tooltip :title="display.raw">
    <a-tag :color="display.color" class="level-tag">{{ display.label }}</a-tag>
  </a-tooltip>
</template>

<style scoped>
.level-tag {
  margin: 0;
  border-radius: 999px;
  padding-inline: 10px;
  font-weight: 500;
}
</style>
