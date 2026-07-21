package xyz.crucistau.config.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author kuailemao
 * <p>
 * 创建时间：2026/07/20
 * ES 同步 RabbitMq 配置类
 */
@Configuration
public class EsSyncRabbitConfig {
    /**
     * 创建 ES 同步队列
     */
    @Value("${spring.rabbitmq.queue.es-sync}")
    public String ES_SYNC_QUEUE;
    /**
     * 创建 ES 同步交换机
     */
    @Value("${spring.rabbitmq.exchange.es}")
    public String ES_EXCHANGE;
    /**
     * 创建 ES 同步路由键
     */
    @Value("${spring.rabbitmq.routingKey.es-sync}")
    public String ES_SYNC_ROUTING_KEY;

    /**
     * 定义交换机
     */
    @Bean
    public DirectExchange esExchange() {
        return ExchangeBuilder.directExchange(ES_EXCHANGE).durable(true).build();
    }

    /**
     * 声明队列
     */
    @Bean
    public Queue esSyncQueue() {
        return QueueBuilder.durable(ES_SYNC_QUEUE).build();
    }

    /**
     * 绑定队列跟交换机
     */
    @Bean
    public Binding esSyncBinding(DirectExchange esExchange, Queue esSyncQueue) {
        return BindingBuilder.bind(esSyncQueue).to(esExchange).with(ES_SYNC_ROUTING_KEY);
    }

}
