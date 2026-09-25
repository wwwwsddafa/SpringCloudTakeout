package com.example.configs;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Druid 数据源配置（历史遗留占位类）。
 *
 * 背景：
 * 本类原先手写 new DruidDataSource()，并只设置 url / username / password /
 * driverClassName 四个属性就调用 init()，完全绕过了 druid-spring-boot-3-starter
 * 的自动装配。后果是 Nacos 中 dataSource.yml 的 spring.datasource.druid.* 全部失效，
 * 连接池参数落回 Druid 默认值：
 *   1. max-active 实际为 8（配置值 50）—— 并发 8 以上即排队等连接
 *   2. min-idle / initial-size 实际为 0 —— 每次都临时建连
 *   3. remove-abandoned 实际为 false —— 连接泄漏无兜底
 *   4. keep-alive / pool-prepared-statements / 慢 SQL 统计全部失效
 * Druid 监控页显示 MaxActive=8 即为该缺陷的直接证据。
 *
 * 修复结论：
 * 本类不再声明任何 DataSource Bean。由 druid-spring-boot-3-starter 的
 * DruidDataSourceAutoConfigure 全权接管数据源创建与初始化，它会读取
 * spring.datasource.*（url / username / password / driver-class-name / type）
 * 以及 spring.datasource.druid.*（连接池参数），从而使 Nacos 配置真正生效。
 *
 * 踩坑记录（重要）：
 * 曾尝试在本类保留一个“兜底”数据源，注解为
 *   @Bean + @ConditionalOnMissingBean(DataSource.class)
 *        + @ConfigurationProperties("spring.datasource.druid")
 * 结果服务启动后 /actuator/health 返回 503，报错如下：
 *
 *   NullPointerException: Cannot invoke
 *   "com.alibaba.druid.stat.JdbcDataSourceStat.getRuningSqlList()"
 *   because the return value of "DruidDataSource.getDataSourceStat()" is null
 *
 * 原因是双重的：
 *   1. @ConditionalOnMissingBean 在用户配置类中评估早于 auto-configuration，
 *      于是兜底 Bean 抢先在 starter 的 Bean 之前被创建，starter 随即退让；
 *   2. 该兜底 Bean 的 @ConfigurationProperties 前缀是 spring.datasource.druid，
 *      而 url / username / password 实际位于 spring.datasource 前缀下，
 *      属性绑不上；同时 Bean 方法体又没有显式调用 init()，
 *      最终得到一个 dataSourceStat == null 的“半成品”数据源。
 *
 * 教训：
 * 绝不要在应用侧声明 DataSource 类型、且带 @ConditionalOnMissingBean 的兜底 Bean，
 * 那会与 starter 的 BackOff 语义打架。要么完全不写，要么写完整的 @Primary 实现。
 */
@Configuration
@Slf4j
public class DruidDataSourceProperties {

    /*
     * ==================== 已删除的实现（留档对照） ====================
     *
     * 第一版（缺陷根源）：
     *
     *   @Bean
     *   @Primary
     *   @RefreshScope
     *   public DataSource druid() {
     *       log.info("使用的编程式的数据源创建.");
     *       DruidDataSource ds = new DruidDataSource();
     *       ds.setUsername(username);
     *       ds.setPassword(password);
     *       ds.setDriverClassName(this.driverClassName);
     *       ds.setUrl(url);
     *       ds.init();          // 只设了 4 个属性就初始化，druid.* 配置全部丢失
     *       return ds;
     *   }
     *
     * 第二版（错误修复，导致 503，不要再这样做）：
     *
     *   @Bean
     *   @ConditionalOnMissingBean(DataSource.class)
     *   @ConfigurationProperties(prefix = "spring.datasource.druid")
     *   public DataSource druidFallback() {
     *       return new DruidDataSource();   // 未 init()；且前缀绑不到 url/username/password
     *   }
     *
     * =================================================================
     * 正确做法：什么都不声明，交给 starter。
     * =================================================================
     */

    public DruidDataSourceProperties() {
        log.debug("DruidDataSourceProperties: 数据源由 druid-spring-boot-3-starter 自动装配，本类仅作占位。");
    }

}
