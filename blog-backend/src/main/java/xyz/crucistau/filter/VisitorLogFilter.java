package xyz.crucistau.filter;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.crucistau.domain.entity.VisitorLog;
import xyz.crucistau.service.VisitorLogService;
import xyz.crucistau.utils.AddressUtils;
import xyz.crucistau.utils.BrowserUtil;
import xyz.crucistau.utils.IpUtils;

import java.io.IOException;

@Slf4j
@Component
public class VisitorLogFilter extends OncePerRequestFilter {

    @Resource
    private VisitorLogService visitorLogService;

    private static final String[] FRONTEND_PATHS = {
            "/article/",
            "/comment/",
            "/category/",
            "/tag/",
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        if (shouldRecord(requestURI)) {
            try {
                String ip = IpUtils.getIpAddr(request);
                VisitorLog log = VisitorLog.builder()
                        .ip(ip)
                        .address(AddressUtils.getRealAddressByIP(ip))
                        .browser(BrowserUtil.browserName(request))
                        .os(BrowserUtil.osName(request))
                        .pageUrl(requestURI)
                        .userAgent(request.getHeader("User-Agent"))
                        .build();
                visitorLogService.recordVisit(log);
            } catch (Exception e) {
                log.warn("记录访客信息异常: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldRecord(String uri) {
        if (uri.contains("/auth/") || uri.contains("/back/") || uri.startsWith("/menu")
                || uri.startsWith("/role") || uri.startsWith("/permission")
                || uri.startsWith("/monitor") || uri.startsWith("/dashboard")
                || uri.contains("swagger") || uri.contains("doc.html")) {
            return false;
        }
        for (String prefix : FRONTEND_PATHS) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
