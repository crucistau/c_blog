<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Column } from '@antv/g2plot'
import type { TrendItem } from '~/api/blog/dashboard'
import { fillTrendDates } from '../utils'

const props = defineProps<{
  data: TrendItem[]
  days: number
}>()

const emit = defineEmits(['update:days'])
const containerRef = ref<HTMLDivElement>()

let plot: Column | null = null

function renderChart() {
  if (!containerRef.value) return

  plot?.destroy()

  // 补全最近 days 天日期序列，无发布的日期填 0，保证图表始终渲染完整坐标轴
  const chartData = fillTrendDates(props.data, props.days)

  plot = new Column(containerRef.value, {
    data: chartData,
    xField: 'date',
    yField: 'count',
    color: '#52c41a',
    columnStyle: { radius: [4, 4, 0, 0] },
    xAxis: {
      label: {
        formatter: (text: string) => text.slice(5),
      },
    },
    yAxis: { min: 0 },
    tooltip: {
      formatter: (datum: any) => ({
        name: '发布数',
        value: datum.count,
      }),
    },
    animation: { appear: { animation: 'scale-in-y' } },
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
        <BarChartOutlined /> 文章发布趋势
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
  display: flex;
  flex-direction: column;
  height: 100%;
}

.trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;
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
  flex: 1;
  min-height: 0;
}
</style>
