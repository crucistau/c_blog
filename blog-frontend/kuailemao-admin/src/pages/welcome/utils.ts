import dayjs from 'dayjs'
import type { TrendItem } from '~/api/blog/dashboard'

/**
 * 将趋势数据补全为最近 days 天的完整日期序列，缺失日期填 fillValue（默认 0）。
 * 用于文章/访问趋势图：即使无数据也渲染完整坐标轴，而非整图空白。
 */
export function fillTrendDates(data: TrendItem[], days: number, fillValue = 0): TrendItem[] {
  const today = dayjs()
  const countMap = new Map(data.map(item => [item.date, item.count]))
  return Array.from({ length: days }, (_, i) => {
    const date = today.subtract(days - 1 - i, 'day').format('YYYY-MM-DD')
    return { date, count: countMap.get(date) ?? fillValue }
  })
}
