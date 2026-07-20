package xyz.kuailemao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import xyz.kuailemao.domain.entity.VisitorLog;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;

import java.util.List;

public interface VisitorLogMapper extends BaseMapper<VisitorLog> {
    List<RegionStatVO> selectRegionStatistics(@Param("days") Integer days);
    List<TrendVO> selectVisitTrend(@Param("days") Integer days);
}
