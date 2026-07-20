<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Line } from '@antv/g2plot'
import type { TrendItem } from '~/api/blog/dashboard'

const props = defineProps<{
  data: TrendItem[]
  days: number
}>()

const emit = defineEmits(['update:days'])
const containerRef = ref<HTMLDivElement>()

let plot: Line | null = null

function renderChart() {
  if (!containerRef.value || !props.data.length) return

  plot?.destroy()

  plot = new Line(containerRef.value, {
    data: props.data,
    xField: 'date',
    yField: 'count',
    smooth: true,
    color: '#1677ff',
    lineStyle: { lineWidth: 2 },
    point: { size: 3, shape: 'circle' },
    xAxis: {
      label: {
        formatter: (text: string) => text.slice(5),
      },
    },
    yAxis: {
      min: 0,
    },
    tooltip: {
      formatter: (datum: any) => ({
        name: '访问量',
        value: datum.count,
      }),
    },
    animation: { appear: { animation: 'wave-in' } },
  })

  plot.render()
}

onMounted(renderChart)
watch(() => props.data, renderChart, { deep: true })
</script>

<template>
  <div class="trend-card">
    <div class="trend-header">
      <div class="trend-title">
        <LineChartOutlined /> 访问量趋势
      </div>
      <div class="trend-tabs">
        <a-button
          :type="days === 7 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 7)"
        >7天</a-button>
        <a-button
          :type="days === 30 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 30)"
        >30天</a-button>
      </div>
    </div>
    <div ref="containerRef" class="chart-container" />
  </div>
</template>

<style scoped lang="less">
.trend-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
}

.trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.trend-title {
  font-size: 16px;
  font-weight: 600;
}

.trend-tabs {
  display: flex;
  gap: 8px;
}

.chart-container {
  height: 280px;
}
</style>
