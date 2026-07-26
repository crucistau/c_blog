<script setup lang="ts">
import { onMounted, ref } from 'vue'
import StatCard from './components/StatCard.vue'
import VisitTrend from './components/VisitTrend.vue'
import ArticleTrend from './components/ArticleTrend.vue'
import VisitorMap from './components/VisitorMap.vue'
import SystemMonitor from './components/SystemMonitor.vue'
import {
  getDashboardOverview,
  getVisitTrend,
  getArticleTrend,
  getVisitorRegion,
  getVisitorCity,
} from '~/api/blog/dashboard'
import type { DashboardOverview, TrendItem, RegionStat, CityStat } from '~/api/blog/dashboard'

const overview = ref<DashboardOverview>()
const visitTrend = ref<TrendItem[]>([])
const articleTrend = ref<TrendItem[]>([])
const regionData = ref<RegionStat[]>([])
const cityData = ref<CityStat[]>([])
const loading = ref(true)

const visitDays = ref(7)
const articleDays = ref(7)
const regionDays = ref(30)

async function loadOverview() {
  const { data } = await getDashboardOverview()
  overview.value = data
}

async function loadVisitTrend(days: number) {
  visitDays.value = days
  const { data } = await getVisitTrend(days)
  visitTrend.value = data ?? []
}

async function loadArticleTrend(days: number) {
  articleDays.value = days
  const { data } = await getArticleTrend(days)
  articleTrend.value = data ?? []
}

async function loadRegion(days: number) {
  regionDays.value = days
  const result = await getVisitorRegion(days)
  regionData.value = result.data ?? []
  // 同步刷新城市数据
  loadCity(days)
}

async function loadCity(days: number) {
  const result = await getVisitorCity(days)
  cityData.value = result?.data ?? []
}

onMounted(async () => {
  await Promise.all([
    loadOverview(),
    loadVisitTrend(7),
    loadArticleTrend(7),
    loadRegion(30),
    loadCity(30),
  ])
  loading.value = false
})
</script>

<template>
  <div class="dashboard">
    <!-- 第一行：核心指标卡片 -->
    <div class="row stat-row">
      <StatCard
        title="文章总数"
        :value="overview?.articleCount ?? 0"
        gradient="#667eea, #764ba2"
        icon="FileTextOutlined"
      />
      <StatCard
        title="总访问量"
        :value="overview?.visitCount ?? 0"
        gradient="#f093fb, #f5576c"
        icon="EyeOutlined"
      />
      <StatCard
        title="评论数"
        :value="overview?.commentCount ?? 0"
        gradient="#4facfe, #00f2fe"
        icon="MessageOutlined"
      />
      <StatCard
        title="分类数"
        :value="overview?.categoryCount ?? 0"
        gradient="#43e97b, #38f9d7"
        icon="AppstoreOutlined"
      />
    </div>

    <!-- 第二行：趋势图 + 系统监控 (2:1) -->
    <div class="row chart-row">
      <div class="chart-main">
        <VisitTrend
          :data="visitTrend"
          :days="visitDays"
          @update:days="loadVisitTrend"
        />
      </div>
      <div class="chart-side">
        <SystemMonitor />
      </div>
    </div>

    <!-- 第三行：地域分布 + 文章趋势 (1:1) -->
    <div class="row chart-row">
      <div class="chart-half">
        <VisitorMap
          :data="regionData"
          :cityData="cityData"
          :days="regionDays"
          @update:days="loadRegion"
        />
      </div>
      <div class="chart-half">
        <ArticleTrend
          :data="articleTrend"
          :days="articleDays"
          @update:days="loadArticleTrend"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="less">
.dashboard {
  padding: 0;
}

.row {
  margin-bottom: 20px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.chart-row {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.chart-main {
  flex: 2;
}

.chart-side {
  flex: 1;
  min-width: 280px;
}

.chart-half {
  flex: 1;
}
</style>
