// 仪表盘数据接口
import { message } from 'ant-design-vue'

export interface DashboardOverview {
  articleCount: number
  visitCount: number
  commentCount: number
  categoryCount: number
  tagCount: number
}

export interface TrendItem {
  date: string
  count: number
}

export interface RegionStat {
  province: string
  count: number
}

// 获取概览指标
export async function getDashboardOverview() {
  return useGet<DashboardOverview>('/dashboard/overview').catch(msg => message.warn(msg))
}

// 获取访问量趋势
export async function getVisitTrend(days: number = 7) {
  return useGet<TrendItem[]>('/dashboard/visitTrend', null, {
    params: { days },
  }).catch(msg => message.warn(msg))
}

// 获取文章发布趋势
export async function getArticleTrend(days: number = 7) {
  return useGet<TrendItem[]>('/dashboard/articleTrend', null, {
    params: { days },
  }).catch(msg => message.warn(msg))
}

// 获取访客地域分布
export async function getVisitorRegion(days: number = 30) {
  return useGet<RegionStat[]>('/dashboard/visitor/region', null, {
    params: { days },
  }).catch(msg => message.warn(msg))
}
