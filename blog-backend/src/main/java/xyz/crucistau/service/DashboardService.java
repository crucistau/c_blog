package xyz.crucistau.service;

import xyz.crucistau.domain.vo.CityStatVO;
import xyz.crucistau.domain.vo.DashboardOverviewVO;
import xyz.crucistau.domain.vo.RegionStatVO;
import xyz.crucistau.domain.vo.TrendVO;

import java.util.List;

public interface DashboardService {
    DashboardOverviewVO getOverview();
    List<TrendVO> getVisitTrend(Integer days);
    List<TrendVO> getArticleTrend(Integer days);
    List<RegionStatVO> getRegionStatistics(Integer days);
    List<CityStatVO> getCityStatistics(Integer days);
}
