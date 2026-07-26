<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import { MapChart, ScatterChart, EffectScatterChart } from 'echarts/charts'
import { GeoComponent, TitleComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import type { RegionStat, CityStat } from '~/api/blog/dashboard'
import chinaGeo from '~/assets/geo/china.json'
import geoCoordMap from '~/assets/geo/city2coord.json'

echarts.use([
  MapChart, ScatterChart, EffectScatterChart,
  GeoComponent, TitleComponent, TooltipComponent, VisualMapComponent,
  CanvasRenderer,
])

// eslint-disable-next-line @typescript-eslint/no-explicit-any
echarts.registerMap('china', chinaGeo as any)

// dataviz 已验证的 sequential blue ramp：访客数 magnitude，浅→深
const COLOR_RAMP = ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b']
const NO_DATA_COLOR = '#eef0f3'

const props = defineProps<{
  data: RegionStat[]
  cityData: CityStat[]
  days: number
}>()

const emit = defineEmits(['update:days'])
const containerRef = ref<HTMLDivElement>()
const chartRef = shallowRef<EChartsType | null>(null)

/**
 * 将后端城市名映射为地理坐标点。
 * city2coord.json 的 key 格式为 "成都市"/"北京"/"黔东南"（不带"市/省/州"后缀也可存在）。
 * 优先精确匹配 → 去"市/州/盟/地区"后缀匹配 → city 中包含 key（如 "成都" → "成都市"）。
 */
interface CoordPoint {
  name: string
  value: [number, number, number]
}

function convertData(cityStats: CityStat[]): CoordPoint[] {
  const keys = Object.keys(geoCoordMap) as string[]
  const result: CoordPoint[] = []

  for (const item of cityStats) {
    const raw = item.city?.trim()
    if (!raw) continue

    let coord: string[] | undefined

    // 1. 精确匹配
    if (raw in geoCoordMap) {
      coord = geoCoordMap[raw as keyof typeof geoCoordMap]
    }

    // 2. 去常见后缀匹配
    if (!coord) {
      const stripped = raw.replace(/(市|州|盟|地区|自治州|自治县|区)$/, '')
      const match = keys.find(k => k === stripped || k.startsWith(stripped))
      if (match) coord = (geoCoordMap as Record<string, string[]>)[match]
    }

    // 3. 兜底：city 包含 key
    if (!coord) {
      for (const k of keys) {
        if (raw.includes(k as string)) {
          coord = geoCoordMap[k as keyof typeof geoCoordMap]
          break
        }
      }
    }

    if (coord) {
      result.push({
        name: raw,
        value: [Number.parseFloat(coord[0]), Number.parseFloat(coord[1]), item.count],
      })
    }
  }
  return result
}

function renderChart() {
  if (!containerRef.value) return
  if (!chartRef.value) chartRef.value = echarts.init(containerRef.value)

  const coords = convertData(props.cityData)
  // 按访客数降序，取 Top5 做涟漪
  const sorted = [...coords].sort((a, b) => b.value[2] - a.value[2])
  const top5 = sorted.slice(0, 5)
  const rest = sorted.slice(5)

  // 省份着色数据（保留省份级热力色作为底图参考）
  const provinceMap = new Map<string, number>()
  props.data.forEach(d => provinceMap.set(d.province, d.count))

  chartRef.value.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesType === 'effectScatter' || params.seriesType === 'scatter') {
          return `${params.name}<br/>访客数：${params.value[2]}`
        }
        if (params.seriesType === 'map') {
          const count = provinceMap.get(params.name) ?? 0
          return `${params.name}<br/>访客数：${count}`
        }
        return ''
      },
    },
    visualMap: {
      show: false, // 散点模式下以点大小表量级，不显示色阶，减少视觉干扰
    },
    geo: {
      map: 'china',
      roam: true,
      scaleLimit: { min: 1, max: 8 },
      itemStyle: {
        areaColor: '#f5f7fa',
        borderColor: '#b0bec5',
        borderWidth: 0.5,
      },
      emphasis: {
        itemStyle: { areaColor: '#e3f2fd' },
        label: { show: true, color: '#0b0b0b', fontSize: 10 },
      },
    },
    series: [
      // 散点：所有非 Top5 城市
      {
        type: 'scatter',
        coordinateSystem: 'geo',
        data: rest.map(d => ({ name: d.name, value: d.value })),
        symbolSize: (val: number[]) => Math.max(4, Math.sqrt(val[2]) * 3),
        itemStyle: {
          color: '#1677ff',
          opacity: 0.7,
        },
        label: { show: false },
        emphasis: {
          itemStyle: { opacity: 1 },
          label: { show: true, formatter: '{b}', fontSize: 10 },
        },
      },
      // 涟漪散点：Top5 城市
      {
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: top5.map(d => ({ name: d.name, value: d.value })),
        symbolSize: (val: number[]) => Math.max(10, Math.sqrt(val[2]) * 5),
        showEffectOn: 'render',
        rippleEffect: {
          brushType: 'stroke',
          scale: 3,
          period: 4,
        },
        itemStyle: {
          color: '#ff4d4f',
          shadowBlur: 10,
          shadowColor: 'rgba(255, 77, 79, 0.5)',
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{b}',
          fontSize: 10,
          color: '#333',
        },
      },
    ],
  })
}

function handleResize() {
  chartRef.value?.resize()
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartRef.value?.dispose()
  chartRef.value = null
})

// 省份数据或城市数据变化时重新渲染
watch([() => props.data, () => props.cityData], renderChart, { deep: true })
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
        >
          7天
        </a-button>
        <a-button
          :type="days === 30 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 30)"
        >
          30天
        </a-button>
      </div>
    </div>
    <div ref="containerRef" class="chart-container" />
    <div v-if="!props.data.length && !props.cityData.length" class="empty-hint">
      暂无访客地域数据。地图轮廓仍可查看，产生外网访问后将按城市自动标记散点。
    </div>
  </div>
</template>

<style scoped lang="less">
.map-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;
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
  flex: 1;
  min-height: 0;
}

.empty-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #898781;
  text-align: center;
}
</style>
