package xyz.kuailemao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.DashboardOverviewVO;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;
import xyz.kuailemao.service.DashboardService;

import java.util.List;

@Tag(name = "仪表盘")
@RestController
@RequestMapping("dashboard")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @Operation(summary = "获取概览指标")
    @GetMapping("/overview")
    public ResponseResult<DashboardOverviewVO> getOverview() {
        return ResponseResult.success(dashboardService.getOverview());
    }

    @Operation(summary = "获取访问量趋势")
    @GetMapping("/visitTrend")
    public ResponseResult<List<TrendVO>> getVisitTrend(@RequestParam(value = "days", defaultValue = "7") Integer days) {
        return ResponseResult.success(dashboardService.getVisitTrend(days));
    }

    @Operation(summary = "获取文章发布趋势")
    @GetMapping("/articleTrend")
    public ResponseResult<List<TrendVO>> getArticleTrend(@RequestParam(value = "days", defaultValue = "7") Integer days) {
        return ResponseResult.success(dashboardService.getArticleTrend(days));
    }

    @Operation(summary = "获取访客地域分布")
    @GetMapping("/visitor/region")
    public ResponseResult<List<RegionStatVO>> getRegionStatistics(@RequestParam(value = "days", defaultValue = "30") Integer days) {
        return ResponseResult.success(dashboardService.getRegionStatistics(days));
    }
}
