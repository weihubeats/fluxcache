<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components'
import type { FluxCacheStaticsVO } from '@/types/cache'
import { formatHitRate, formatTime } from '@/utils/format'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
])

const props = defineProps<{
  windows: FluxCacheStaticsVO[]
  type: 'hitRate' | 'request' | 'maxLoad'
}>()

const categories = computed(() => props.windows.map((w) => formatTime(w.startTime)))

const option = computed<EChartsOption>(() => {
  const base: EChartsOption = {
    grid: { left: 48, right: 24, top: 40, bottom: 48 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18 }],
    xAxis: { type: 'category', data: categories.value, boundaryGap: props.type !== 'hitRate' },
    yAxis: { type: 'value', scale: true },
  }

  if (props.type === 'hitRate') {
    return {
      ...base,
      yAxis: {
        type: 'value',
        min: 0,
        max: 1,
        axisLabel: { formatter: (v: number) => formatHitRate(v) },
      },
      series: [
        {
          name: '命中率',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: props.windows.map((w) => w.hitRate),
          areaStyle: { opacity: 0.08 },
        },
      ],
      tooltip: {
        trigger: 'axis',
        valueFormatter: (v) => formatHitRate(Number(v)),
      },
    }
  }

  if (props.type === 'request') {
    return {
      ...base,
      series: [
        {
          name: '请求',
          type: 'bar',
          stack: 'req',
          data: props.windows.map((w) => w.requestCount),
          itemStyle: { color: '#1677ff' },
        },
        {
          name: '命中',
          type: 'line',
          data: props.windows.map((w) => w.hit),
          itemStyle: { color: '#52c41a' },
        },
        {
          name: '未命中',
          type: 'line',
          data: props.windows.map((w) => w.miss),
          itemStyle: { color: '#ff4d4f' },
        },
      ],
    }
  }

  return {
    ...base,
    yAxis: {
      type: 'value',
      axisLabel: { formatter: (v: number) => `${v} ms` },
    },
    series: [
      {
        name: '最大加载耗时',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: props.windows.map((w) => w.maxLoadTime),
        areaStyle: { opacity: 0.08 },
        itemStyle: { color: '#fa8c16' },
      },
    ],
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => `${v} ms`,
    },
  }
})

const chartRef = ref<InstanceType<typeof VChart> | null>(null)

function resize() {
  chartRef.value?.resize()
}

onMounted(() => window.addEventListener('resize', resize))
onUnmounted(() => window.removeEventListener('resize', resize))
watch(
  () => props.windows,
  () => resize(),
)
</script>

<template>
  <div class="chart-wrap">
    <VChart ref="chartRef" class="chart" :option="option" autoresize />
  </div>
</template>

<style scoped>
.chart-wrap {
  width: 100%;
  height: 320px;
}
.chart {
  width: 100%;
  height: 100%;
}
</style>
