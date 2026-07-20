package xyz.kuailemao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.kuailemao.domain.entity.VisitorLog;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;

import java.util.List;

public interface VisitorLogService extends IService<VisitorLog> {
    List<RegionStatVO> getRegionStatistics(Integer days);
    List<TrendVO> getVisitTrend(Integer days);
    void recordVisit(VisitorLog log);
}
