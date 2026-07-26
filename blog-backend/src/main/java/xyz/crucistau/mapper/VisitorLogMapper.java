package xyz.crucistau.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import xyz.crucistau.domain.entity.VisitorLog;
import xyz.crucistau.domain.vo.CityStatVO;
import xyz.crucistau.domain.vo.RegionStatVO;
import xyz.crucistau.domain.vo.TrendVO;

import java.util.List;

public interface VisitorLogMapper extends BaseMapper<VisitorLog> {
    List<RegionStatVO> selectRegionStatistics(@Param("days") Integer days);
    List<CityStatVO> selectCityStatistics(@Param("days") Integer days);
    List<TrendVO> selectVisitTrend(@Param("days") Integer days);
}
