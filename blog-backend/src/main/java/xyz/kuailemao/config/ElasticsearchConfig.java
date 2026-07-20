package xyz.kuailemao.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import lombok.Data;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * Elasticsearch 官方客户端配置（HTTPS + 账号密码 + 自签 CA 信任）。
 *
 * <p>仅在 {@code search.enabled=true}（缺省视为 true）时装配；总开关关闭时
 * 整个配置类不生效，应用可在无 ES 环境正常启动。</p>
 *
 * <p>不使用 {@code spring.elasticsearch.*} 命名空间，避免触发 Spring Data ES
 * 自动配置；客户端版本严格对齐服务器 8.18.0。</p>
 *
 * @author kuailemao
 */
@Configuration
@ConditionalOnProperty(name = "search.enabled", havingValue = "true", matchIfMissing = true)
@ConfigurationProperties(prefix = "elasticsearch")
@Data
public class ElasticsearchConfig {

    /**
     * ES 连接地址（含协议，例如 {@code https://host:9200}）。
     */
    private String uris;

    /**
     * ES 账号（默认 elastic）。
     */
    private String username;

    /**
     * ES 密码，通过 {@code ES_PASSWORD} 环境变量注入，禁止明文入库。
     */
    private String password;

    /**
     * 服务器自签 CA 证书路径，默认 {@code classpath:es/http_ca.crt}。
     * 部署时由运维放置真实证书；开发期可临时指向本地文件。
     */
    private String caCertPath;

    /**
     * 连接超时（毫秒）。
     */
    private int connectTimeout = 5000;

    /**
     * Socket 超时（毫秒）。
     */
    private int socketTimeout = 30000;

    /**
     * 底层 RestClient：信任服务器自签 CA 证书 + basic auth。
     */
    @Bean
    public RestClient restClient() throws Exception {
        // 1. 信任服务器自签 CA 证书（纯 JDK API，不依赖具体 httpclient 版本）
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Resource caRes = new DefaultResourceLoader().getResource(caCertPath);
        try (InputStream is = caRes.getInputStream()) {
            trustStore.setCertificateEntry("es-ca", cf.generateCertificate(is));
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        // 2. basic auth（elasticsearch-rest-client 8.x 基于 Apache HttpClient 5）
        BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

        // 3. 构建 RestClient
        java.net.URI uri = java.net.URI.create(uris);
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        return RestClient.builder(httpHost)
                .setHttpClientConfigCallback(hc -> hc
                        .setSSLContext(sslContext)
                        .setDefaultCredentialsProvider(creds))
                .setRequestConfigCallback(rc -> rc
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(socketTimeout))
                .build();
    }

    /**
     * 官方高层客户端：{@link RestClient} → {@link RestClientTransport} → {@link ElasticsearchClient}。
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
