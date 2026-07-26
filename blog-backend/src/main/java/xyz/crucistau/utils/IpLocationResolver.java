package xyz.crucistau.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于本地 ip2region XDB 数据库解析 IP 归属地。
 *
 * <p>访客日志的地址字段保持“省份 城市”格式，兼容现有地域和城市聚合 SQL。</p>
 */
@Slf4j
@Component
public class IpLocationResolver {

    private static final String DATABASE_PATH = "ip2region/ip2region_v4.xdb";
    private static final String UNKNOWN = "未知";
    private static final String INTERNAL_IP = "内网IP";
    private static final String CHINA = "中国";
    private static final String PLACEHOLDER = "0";

    private Ip2Region ip2Region;

    /**
     * 在应用启动时将 XDB 加载到内存；资源不可用时直接阻止启动，避免运行期静默写入大量未知地址。
     */
    @PostConstruct
    public void initialize() {
        try (InputStream inputStream = new ClassPathResource(DATABASE_PATH).getInputStream()) {
            Config v4Config = Config.custom()
                    .setCachePolicy(Config.BufferCache)
                    .setXdbInputStream(inputStream)
                    .asV4();
            ip2Region = Ip2Region.create(v4Config, null);
        } catch (Exception e) {
            log.error("加载 ip2region IPv4 数据库失败，资源路径={}", DATABASE_PATH, e);
            throw new IllegalStateException("ip2region IPv4 数据库不可用，应用无法启动", e);
        }
    }

    /**
     * 将公网 IPv4 解析为可供现有统计 SQL 使用的地址格式。
     *
     * @param ip 请求中提取出的客户端 IP
     * @return 内网IP、未知，或“省份 城市”/“国家 城市”
     */
    public String resolve(String ip) {
        String normalizedIp = ip == null ? "" : ip.trim();
        if (!isIpv4(normalizedIp)) {
            return UNKNOWN;
        }
        if (IpUtils.internalIp(normalizedIp)) {
            return INTERNAL_IP;
        }

        try {
            String region = ip2Region.search(normalizedIp);
            return formatRegion(region);
        } catch (Exception e) {
            log.error("本地 IP 归属地查询失败，ip={}", normalizedIp, e);
            return UNKNOWN;
        }
    }

    private String formatRegion(String region) {
        if (region == null || region.isBlank()) {
            return UNKNOWN;
        }
        String[] parts = region.split("\\|", -1);
        String country = valueAt(parts, 0);
        String province = valueAt(parts, 1);
        String city = valueAt(parts, 2);

        if (CHINA.equals(country)) {
            return joinLocation(province, city);
        }
        return joinLocation(country, city);
    }

    private String valueAt(String[] parts, int index) {
        if (index >= parts.length) {
            return "";
        }
        String value = parts[index].trim();
        return PLACEHOLDER.equals(value) ? "" : value;
    }

    private String joinLocation(String... values) {
        List<String> locationParts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                locationParts.add(value);
            }
        }
        return locationParts.isEmpty() ? UNKNOWN : String.join(" ", locationParts);
    }

    private boolean isIpv4(String ip) {
        String[] segments = ip.split("\\.", -1);
        if (segments.length != 4) {
            return false;
        }
        for (String segment : segments) {
            try {
                if (segment.isBlank() || segment.length() > 3 || Integer.parseInt(segment) > 255 || Integer.parseInt(segment) < 0) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    @PreDestroy
    public void destroy() {
        if (ip2Region == null) {
            return;
        }
        try {
            ip2Region.close();
        } catch (Exception e) {
            log.error("关闭 ip2region 查询服务失败", e);
        }
    }
}
