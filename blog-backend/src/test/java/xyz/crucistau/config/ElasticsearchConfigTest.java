package xyz.crucistau.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link ElasticsearchConfig} 的条件装配：
 *
 * <ul>
 *   <li>{@code search.enabled=false} 时，{@link ElasticsearchClient} 与
 *       {@link RestClient} bean 都不创建 —— 应用可在无 ES 环境启动。</li>
 * </ul>
 *
 * <p>启用路径（{@code search.enabled=true} 或缺省）会触发 SSL truststore 加载，
 * 需要真实 {@code http_ca.crt} 才能成功构造 bean，难以纯单测，按 task brief
 * 指引跳过，留给集成测试 / 真实部署环境验证。</p>
 *
 * @author kuailemao
 */
class ElasticsearchConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ElasticsearchConfig.class);

    @Test
    void whenSearchDisabled_thenEsBeansNotCreated() {
        runner
                .withPropertyValues(
                        "search.enabled=false",
                        "elasticsearch.uris=https://localhost:9200",
                        "elasticsearch.username=elastic",
                        "elasticsearch.password=changeme",
                        "elasticsearch.ca-cert-path=classpath:es/http_ca.crt"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ElasticsearchClient.class);
                    assertThat(context).doesNotHaveBean(RestClient.class);
                });
    }
}
