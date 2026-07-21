package xyz.crucistau.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.crucistau.domain.entity.VisitorLog;
import xyz.crucistau.domain.vo.RegionStatVO;
import xyz.crucistau.domain.vo.TrendVO;

import java.util.List;

public interface VisitorLogService extends IService<VisitorLog> {
    List<RegionStatVO> getRegionStatistics(Integer days);
    List<TrendVO> getVisitTrend(Integer days);
    void recordVisit(VisitorLog log);
}
