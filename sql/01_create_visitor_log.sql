CREATE TABLE IF NOT EXISTS `t_visitor_log` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `ip`          VARCHAR(64)  NOT NULL COMMENT '访问者IP',
    `address`     VARCHAR(128) DEFAULT NULL COMMENT 'IP归属地',
    `browser`     VARCHAR(64)  DEFAULT NULL COMMENT '浏览器',
    `os`          VARCHAR(64)  DEFAULT NULL COMMENT '操作系统',
    `page_url`    VARCHAR(512) DEFAULT NULL COMMENT '访问页面URL',
    `user_agent`  TEXT         DEFAULT NULL COMMENT 'User-Agent',
    `create_time` DATETIME     NOT NULL COMMENT '访问时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`  TINYINT      DEFAULT 0 COMMENT '逻辑删除(0:未删除,1:已删除)',
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_ip` (`ip`),
    INDEX `idx_address` (`address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客访问日志';
