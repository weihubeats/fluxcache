<script setup lang="ts">
import { computed } from 'vue'
import { parseCacheLayer } from '@/utils/format'
import type { FluxCacheConfig } from '@/types/cache'

const props = defineProps<{
  config?: FluxCacheConfig | null
}>()

const layer = computed(() => parseCacheLayer(props.config))
</script>

<template>
  <div v-if="layer.kind === 'none'" class="empty"><span class="dash"></span>未配置</div>
  <div v-else class="layer-cell">
    <span class="type-tag" :class="layer.kind">
      <span class="sq"></span>{{ layer.typeLabel }}
    </span>
    <span class="meta">TTL <b>{{ layer.ttlLabel }}</b></span>
    <span v-if="layer.maxSizeLabel" class="meta dim">{{ layer.maxSizeLabel }}</span>
  </div>
</template>

<style scoped>
.layer-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--mono);
  font-size: 10.5px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 7px;
  white-space: nowrap;
}

.type-tag .sq {
  width: 7px;
  height: 7px;
  border-radius: 2px;
}

.type-tag.caffeine {
  color: #c3b6ff;
  background: var(--violet-d);
}
.type-tag.caffeine .sq {
  background: var(--violet);
}

.type-tag.redis {
  color: #ff9a8b;
  background: rgba(255, 107, 107, 0.12);
}
.type-tag.redis .sq {
  background: var(--red);
}

.type-tag.other {
  color: var(--amber);
  background: var(--amber-d);
}
.type-tag.other .sq {
  background: var(--amber);
}

.meta {
  font-family: var(--mono);
  font-size: 10.5px;
  color: var(--text-dim);
  white-space: nowrap;
}

.meta b {
  color: var(--text);
  font-weight: 500;
}

.meta.dim {
  color: var(--text-faint);
}

.empty {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--text-faint);
  display: flex;
  align-items: center;
  gap: 6px;
}

.empty .dash {
  width: 14px;
  height: 1px;
  background: var(--line-3);
  display: inline-block;
}
</style>
