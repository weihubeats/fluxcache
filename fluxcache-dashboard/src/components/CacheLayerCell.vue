<script setup lang="ts">
import { computed } from 'vue'
import { parseCacheLayer } from '@/utils/format'
import type { FluxCacheConfig } from '@/types/cache'

const props = defineProps<{
  config?: FluxCacheConfig | null
}>()

const layer = computed(() => parseCacheLayer(props.config))

const typeColor = computed(() => {
  if (layer.value.kind === 'caffeine') return 'purple'
  if (layer.value.kind === 'redis') return 'volcano'
  if (layer.value.kind === 'none') return undefined
  return 'default'
})
</script>

<template>
  <div v-if="layer.kind === 'none'" class="none-text">未配置</div>
  <div v-else class="layer-cell">
    <a-tag :color="typeColor" class="type-tag">{{ layer.typeLabel }}</a-tag>
    <span v-if="layer.ttlLabel" class="meta">TTL {{ layer.ttlLabel }}</span>
    <span v-if="layer.maxSizeLabel" class="meta dim">{{ layer.maxSizeLabel }}</span>
  </div>
</template>

<style scoped>
.layer-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.type-tag {
  margin: 0;
  border-radius: 6px;
  font-weight: 500;
}
.meta {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  white-space: nowrap;
}
.meta.dim {
  color: rgba(0, 0, 0, 0.35);
}
.none-text {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.35);
}
</style>
