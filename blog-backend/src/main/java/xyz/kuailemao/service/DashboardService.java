package xyz.kuailemao.service;

import xyz.kuailemao.domain.vo.DashboardOverviewVO;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;

import java.util.List;

public interface DashboardService {
    DashboardOverviewVO getOverview();
    List<TrendVO> getVisitTrend(Integer days);
    List<TrendVO> getArticleTrend(Integer days);
    List<RegionStatVO> getRegionStatistics(Integer days);
}
