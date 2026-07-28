<script setup lang="ts">
import type { FluxCacheAllStaticsVO } from '@/types/cache'
import { formatDuration, formatHitRate } from '@/utils/format'

defineProps<{
  stats: FluxCacheAllStaticsVO | null
}>()
</script>

<template>
  <a-row :gutter="[16, 16]">
    <a-col :xs="12" :sm="8" :md="6" :lg="4">
      <a-card size="small">
        <a-statistic title="命中率" :value="formatHitRate(stats?.overallHitRate)" />
      </a-card>
    </a-col>
    <a-col :xs="12" :sm="8" :md="6" :lg="4">
      <a-card size="small">
        <a-statistic title="总请求" :value="stats?.totalRequest ?? 0" />
      </a-card>
    </a-col>
    <a-col :xs="12" :sm="8" :md="6" :lg="4">
      <a-card size="small">
        <a-statistic title="命中" :value="stats?.totalHit ?? 0" />
      </a-card>
    </a-col>
    <a-col :xs="12" :sm="8" :md="6" :lg="4">
      <a-card size="small">
        <a-statistic title="未命中" :value="stats?.totalMiss ?? 0" />
      </a-card>
    </a-col>
    <a-col :xs="12" :sm="8" :md="6" :lg="4">
      <a-card size="small">
        <a-statistic title="写入 / 驱逐" :value="`${stats?.totalPut ?? 0} / ${stats?.totalEvict ?? 0}`" />
      </a-card>
    </a-col>
    <a-col :xs="12" :sm="8" :md="6" :lg="4">
      <a-card size="small">
        <a-statistic title="最大加载耗时" :value="formatDuration(stats?.maxLoadTimeOverall)" />
      </a-card>
    </a-col>
  </a-row>
</template>
