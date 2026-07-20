<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Bar } from '@antv/g2plot'
import type { RegionStat } from '~/api/blog/dashboard'

const props = defineProps<{
  data: RegionStat[]
  days: number
}>()

const emit = defineEmits(['update:days'])
const containerRef = ref<HTMLDivElement>()

let plot: Bar | null = null

function renderChart() {
  if (!containerRef.value || !props.data.length) return

  plot?.destroy()

  const chartData = props.data.slice(0, 15).reverse()

  plot = new Bar(containerRef.value, {
    data: chartData,
    xField: 'count',
    yField: 'province',
    seriesField: 'province',
    color: '#1677ff',
    barStyle: { radius: [0, 4, 4, 0] },
    xAxis: { min: 0 },
    label: {
      position: 'right',
      formatter: (datum: any) => `${datum.count}`,
    },
    tooltip: {
      formatter: (datum: any) => ({
        name: '访客数',
        value: datum.count,
      }),
    },
    animation: { appear: { animation: 'fade-in' } },
  })

  plot.render()
}

onMounted(renderChart)
watch(() => props.data, renderChart, { deep: true })
</script>

<template>
  <div class="map-card">
    <div class="map-header">
      <div class="map-title">
        <EnvironmentOutlined /> 访客地域分布
      </div>
      <div class="map-tabs">
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
.map-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
}

.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.map-title {
  font-size: 16px;
  font-weight: 600;
}

.map-tabs {
  display: flex;
  gap: 8px;
}

.chart-container {
  height: 280px;
}
</style>
