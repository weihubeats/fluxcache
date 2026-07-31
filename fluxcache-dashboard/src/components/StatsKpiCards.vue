<script setup lang="ts">
import type { FluxCacheAllStaticsVO } from '@/types/cache'
import { formatDuration, formatHitRate } from '@/utils/format'

defineProps<{
  stats: FluxCacheAllStaticsVO | null
}>()
</script>

<template>
  <div class="kpis">
    <div class="kpi">
      <div class="k">命中率</div>
      <div class="v teal">{{ formatHitRate(stats?.overallHitRate) }}</div>
    </div>
    <div class="kpi">
      <div class="k">总请求</div>
      <div class="v">{{ stats?.totalRequest ?? 0 }}</div>
    </div>
    <div class="kpi">
      <div class="k">命中</div>
      <div class="v">{{ stats?.totalHit ?? 0 }}</div>
    </div>
    <div class="kpi">
      <div class="k">未命中</div>
      <div class="v rose">{{ stats?.totalMiss ?? 0 }}</div>
    </div>
    <div class="kpi">
      <div class="k">写入 / 驱逐</div>
      <div class="v">{{ stats?.totalPut ?? 0 }}<em>/ {{ stats?.totalEvict ?? 0 }}</em></div>
    </div>
    <div class="kpi">
      <div class="k">最大加载耗时</div>
      <div class="v amber">{{ formatDuration(stats?.maxLoadTimeOverall) }}</div>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.kpi {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.02);
  min-width: 0;
}

.k {
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 1px;
  color: var(--text-faint);
  text-transform: uppercase;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.v {
  font-family: var(--display);
  font-weight: 700;
  font-size: 22px;
  margin-top: 3px;
  font-variant-numeric: tabular-nums;
  color: var(--text);
}

.v em {
  font-style: normal;
  font-size: 12px;
  color: var(--text-faint);
  font-family: var(--mono);
  margin-left: 3px;
}

.v.teal {
  color: var(--teal);
}
.v.rose {
  color: var(--rose);
}
.v.amber {
  color: var(--amber);
}

@media (max-width: 1200px) {
  .kpis {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 620px) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
