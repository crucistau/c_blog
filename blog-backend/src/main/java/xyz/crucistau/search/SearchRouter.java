package xyz.crucistau.search;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.crucistau.search.impl.ArticleDbSearch;
import xyz.crucistau.search.impl.ArticleEsSearch;

/**
 * 搜索策略路由器：按配置开关在 ES 策略与 DB 策略之间路由。
 *
 * <p>{@code search.enabled=false} 或 {@code search.entities.article=false} 时走 DB 策略；
 * ES bean 缺失（总开关关闭导致不装配）时自动回退 DB。</p>
 *
 * @author kuailemao
 * @date 2026-07-20
 * @description ES/DB 搜索策略路由器
 */
@Component
public class SearchRouter {

    @Value("${search.enabled:true}")
    private boolean enabled;

    @Value("${search.entities.article:true}")
    private boolean articleEnabled;

    @Resource(name = "articleEsSearch")
    private ObjectProvider<ArticleSearch> articleEs;

    @Resource
    private ArticleDbSearch articleDb;

    /**
     * 返回文章搜索策略：ES 开关全开 → ES 策略，否则 DB 策略。
     */
    public ArticleSearch article() {
        return (enabled && articleEnabled) ? articleEs.getIfAvailable(() -> articleDb) : articleDb;
    }
}
