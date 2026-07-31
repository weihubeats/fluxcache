<script setup lang="ts">
import { computed } from 'vue'
import { formatCacheLevel } from '@/utils/format'
import type { FluxCacheLevel } from '@/types/cache'

const props = defineProps<{
  level?: FluxCacheLevel | null
}>()

const display = computed(() => formatCacheLevel(props.level))

const tierCls = computed(() => {
  if (display.value.raw === 'FirstCacheable') return 'l1'
  if (display.value.raw === 'SecondaryCacheable') return 'l12'
  return ''
})
</script>

<template>
  <a-tooltip :title="display.raw">
    <span class="pill-tier" :class="tierCls">
      <span class="tdot"></span>{{ display.label }}
    </span>
  </a-tooltip>
</template>

<style scoped>
.pill-tier {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--mono);
  font-size: 10.5px;
  font-weight: 600;
  padding: 4px 9px;
  border-radius: 30px;
  white-space: nowrap;
  color: var(--text-faint);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--line);
}

.pill-tier .tdot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-faint);
}

.pill-tier.l1 {
  color: var(--teal);
  background: rgba(51, 216, 194, 0.1);
  border: 1px solid rgba(51, 216, 194, 0.3);
}

.pill-tier.l1 .tdot {
  background: var(--teal);
}

.pill-tier.l12 {
  color: #fff;
  background: linear-gradient(90deg, rgba(51, 216, 194, 0.16), rgba(244, 177, 62, 0.16));
  border: 1px solid transparent;
  position: relative;
}

.pill-tier.l12::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 30px;
  padding: 1px;
  background: linear-gradient(90deg, var(--teal), var(--amber));
  -webkit-mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}

.pill-tier.l12 .tdot {
  background: linear-gradient(90deg, var(--teal), var(--amber));
}
</style>
