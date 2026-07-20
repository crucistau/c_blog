package xyz.kuailemao.domain.vo;

import lombok.Data;

@Data
public class DashboardOverviewVO {
    private Long articleCount;
    private Long visitCount;
    private Long commentCount;
    private Long categoryCount;
    private Long tagCount;
}
