package xyz.crucistau.constants;

/**
 * @author kuailemao
 * <p>
 * 创建时间：2023/10/16 20:56
 * RabbitMq常量类
 */
public class RabbitConst {

    /**
     * 邮件队列
     */
    public static final String MAIL_QUEUE = "email_queue";

    /**
     * 登录日志队列
     */
    public static final String LOG_LOGIN_QUEUE = "log_login_queue";

    /**
     * 系统操作日志队列
     */
    public static final String LOG_SYSTEM_QUEUE = "log_system_queue";

    /**
     * ES 同步队列
     */
    public static final String ES_SYNC_QUEUE = "es_sync_queue";

    /**
     * ES 同步交换机
     */
    public static final String ES_EXCHANGE = "es_exchange";

    /**
     * ES 同步路由键
     */
    public static final String ES_SYNC_ROUTING_KEY = "es_sync_routing_key";
}
