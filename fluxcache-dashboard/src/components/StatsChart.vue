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

const AXIS = '#5e6c87'
const SPLIT = 'rgba(255,255,255,0.06)'
const TEAL = '#33d8c2'
const AMBER = '#f4b13e'
const ROSE = '#ff6b6b'
const TEXT = '#9aa7c0'

const categories = computed(() => props.windows.map((w) => formatTime(w.startTime)))

const base: EChartsOption = {
  backgroundColor: 'transparent',
  grid: { left: 48, right: 24, top: 40, bottom: 48 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(13,18,28,0.95)',
    borderColor: 'rgba(255,255,255,0.1)',
    textStyle: { color: '#eaf0fb' },
  },
  legend: {
    top: 0,
    textStyle: { color: TEXT, fontFamily: 'JetBrains Mono', fontSize: 11 },
  },
  dataZoom: [
    {
      type: 'inside',
      borderColor: 'transparent',
      backgroundColor: 'rgba(255,255,255,0.02)',
      fillerColor: 'rgba(51,216,194,0.08)',
      handleStyle: { color: '#5e6c87' },
      textStyle: { color: AXIS },
    },
    { type: 'slider', height: 18, borderColor: 'transparent' },
  ],
  xAxis: {
    type: 'category',
    data: categories.value,
    boundaryGap: props.type !== 'hitRate',
    axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
    axisLabel: { color: AXIS, fontFamily: 'JetBrains Mono', fontSize: 10.5 },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    scale: true,
    axisLabel: { color: AXIS, fontFamily: 'JetBrains Mono', fontSize: 10.5 },
    splitLine: { lineStyle: { color: SPLIT } },
  },
}

const option = computed<EChartsOption>(() => {
  if (props.type === 'hitRate') {
    return {
      ...base,
      yAxis: {
        type: 'value',
        min: 0,
        max: 1,
        axisLabel: {
          color: AXIS,
          formatter: (v: number) => formatHitRate(v),
          fontFamily: 'JetBrains Mono',
          fontSize: 10.5,
        },
        splitLine: { lineStyle: { color: SPLIT } },
      },
      series: [
        {
          name: '命中率',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: props.windows.map((w) => w.hitRate),
          lineStyle: { color: TEAL, width: 2.5 },
          itemStyle: { color: TEAL },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(51,216,194,0.22)' },
                { offset: 1, color: 'rgba(51,216,194,0)' },
              ],
            },
          },
        },
      ],
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(13,18,28,0.95)',
        borderColor: 'rgba(255,255,255,0.1)',
        textStyle: { color: '#eaf0fb' },
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
          itemStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(90,169,240,0.85)' },
                { offset: 1, color: 'rgba(90,169,240,0.25)' },
              ],
            },
            borderRadius: [3, 3, 0, 0],
          },
        },
        {
          name: '命中',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: props.windows.map((w) => w.hit),
          lineStyle: { color: TEAL, width: 2.5 },
          itemStyle: { color: TEAL },
        },
        {
          name: '未命中',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: props.windows.map((w) => w.miss),
          lineStyle: { color: ROSE, width: 2.5 },
          itemStyle: { color: ROSE },
        },
      ],
    }
  }

  return {
    ...base,
    yAxis: {
      type: 'value',
      axisLabel: {
        color: AXIS,
        formatter: (v: number) => `${v} ms`,
        fontFamily: 'JetBrains Mono',
        fontSize: 10.5,
      },
      splitLine: { lineStyle: { color: SPLIT } },
    },
    series: [
      {
        name: '最大加载耗时',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: props.windows.map((w) => w.maxLoadTime),
        lineStyle: { color: AMBER, width: 2.5 },
        itemStyle: { color: AMBER },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(244,177,62,0.18)' },
              { offset: 1, color: 'rgba(244,177,62,0)' },
            ],
          },
        },
      },
    ],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(13,18,28,0.95)',
      borderColor: 'rgba(255,255,255,0.1)',
      textStyle: { color: '#eaf0fb' },
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
  height: 300px;
}
.chart {
  width: 100%;
  height: 100%;
}
</style>
