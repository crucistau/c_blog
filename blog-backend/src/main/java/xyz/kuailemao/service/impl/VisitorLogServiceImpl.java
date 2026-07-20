package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.entity.VisitorLog;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;
import xyz.kuailemao.mapper.VisitorLogMapper;
import xyz.kuailemao.service.VisitorLogService;

import java.util.List;

@Slf4j
@Service
public class VisitorLogServiceImpl extends ServiceImpl<VisitorLogMapper, VisitorLog> implements VisitorLogService {

    @Resource
    private VisitorLogMapper visitorLogMapper;

    @Override
    public List<RegionStatVO> getRegionStatistics(Integer days) {
        return visitorLogMapper.selectRegionStatistics(days);
    }

    @Override
    public List<TrendVO> getVisitTrend(Integer days) {
        return visitorLogMapper.selectVisitTrend(days);
    }

    @Async
    @Override
    public void recordVisit(VisitorLog visitorLog) {
        try {
            this.save(visitorLog);
        } catch (Exception e) {
            log.error("保存访客记录失败: {}", e.getMessage());
        }
    }
}
