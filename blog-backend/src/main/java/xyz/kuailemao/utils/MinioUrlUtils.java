package xyz.kuailemao.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MinIO URL 工具类
 * <p>
 * 由于数据库存储的是上传时拼好的完整 MinIO URL（如 http://old-ip:9000/blog/path/to.jpg），
 * 当更换 MinIO endpoint 后，旧记录的 URL 不会自动更新。
 * 该工具类提供统一的 URL 替换逻辑，查询时动态替换为当前配置的 endpoint。
 * </p>
 *
 * @author kuailemao
 */
@Slf4j
@Component
public class MinioUrlUtils {

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucketName}")
    private String bucketName;

    /**
     * 将数据库中的 MinIO URL 替换为当前配置的 endpoint
     * <p>
     * 例如: http://141.98.198.67:9000/blog/banners/xxx.jpg
     *   → https://img.crucistau.xyz/blog/banners/xxx.jpg
     * </p>
     *
     * @param url 数据库中存储的原始 URL
     * @return 替换后的 URL，若无法识别则返回原值
     */
    public String convertUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        int idx = url.indexOf("/" + bucketName + "/");
        if (idx != -1) {
            return minioEndpoint + url.substring(idx);
        }
        return url;
    }

    /**
     * 批量转换 URL
     *
     * @param urls URL 列表
     * @return 转换后的 URL 列表
     */
    public java.util.List<String> convertUrls(java.util.List<String> urls) {
        if (urls == null) return java.util.List.of();
        return urls.stream().map(this::convertUrl).toList();
    }
}