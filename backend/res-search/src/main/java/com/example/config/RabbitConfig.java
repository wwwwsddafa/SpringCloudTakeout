package com.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitConfig {

    public static final String FOOD_EXCHANGE = "food.exchange";
    public static final String FOOD_SYNC_QUEUE = "food.sync.queue";
    public static final String FOOD_SYNC_ROUTING_KEY = "food.sync";

    /* ==================== 死信（DLX）配置 ====================
     * 背景：food.sync.queue 曾因「空向量写 ES → document_parsing_exception → Spring AMQP
     * 默认 requeue → 立即重投」形成无限重投风暴，累计 redeliver 337 万次，队列永久堵塞。
     * 根治手段：给主队列绑定死信交换机。消费者抛出的任何异常（包括我们主动抛的
     * AmqpRejectAndDontRequeueException）都会把消息投递到 food.sync.dlx.queue，
     * 而不是无限重投。死信队列无消费者、无 TTL，先把「坏消息」安全收容，人工排查后重放。
     */
    public static final String FOOD_DLX_EXCHANGE = "food.dlx.exchange";
    public static final String FOOD_DLQ_QUEUE = "food.sync.dlx.queue";
    public static final String FOOD_DLQ_ROUTING_KEY = "food.sync.dlx";

    @Bean
    public TopicExchange foodExchange() {
        return new TopicExchange(FOOD_EXCHANGE, true, false);
    }

    /**
     * 主队列：开启死信路由。x-dead-letter-routing-key 保证消息在死信交换机上仍走固定路由键。
     */
    @Bean
    public Queue foodSyncQueue() {
        return QueueBuilder.durable(FOOD_SYNC_QUEUE)
                .deadLetterExchange(FOOD_DLX_EXCHANGE)
                .deadLetterRoutingKey(FOOD_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding foodSyncBinding() {
        return BindingBuilder.bind(foodSyncQueue())
                .to(foodExchange())
                .with(FOOD_SYNC_ROUTING_KEY);
    }

    /* ==================== 死信队列组件 ==================== */

    @Bean
    public DirectExchange foodDlxExchange() {
        return new DirectExchange(FOOD_DLX_EXCHANGE, true, false);
    }

    /**
     * 死信队列：不设消费者（故意让消息滞留以便人工排查），
     * 不设 TTL（避免坏消息被静默丢弃），纯收容。
     */
    @Bean
    public Queue foodDlqQueue() {
        return QueueBuilder.durable(FOOD_DLQ_QUEUE).build();
    }

    @Bean
    public Binding foodDlqBinding() {
        return BindingBuilder.bind(foodDlqQueue())
                .to(foodDlxExchange())
                .with(FOOD_DLQ_ROUTING_KEY);
    }

    /**
     * 消费者并发度：3~5 个并发消费者。
     * 注意 prefetch 默认值 250 过大——单个消费者会一口气拉 250 条消息进内存缓冲，
     * 一旦这批消息处理失败，它们会卡在 unacked 状态反复重投，此时 purge 主队列
     * 也清不掉（消息不在 ready 状态），只能重启消费者。压到 1 可保证「处理一条、
     * 确认一条」，失败消息能立即进入死信队列。
     */
    public static final int PREFETCH_COUNT = 1;

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);

        // 每次只预取 1 条，避免大批消息挤在消费者缓冲区里无法 purge
        factory.setPrefetchCount(PREFETCH_COUNT);

        // 处理失败不重回队列，交由死信交换机收容。
        // 这里返回 true 表示「该异常致命 → 拒绝消息且不重新入队」，
        // 于是消息被 RabbitMQ 路由到 x-dead-letter-exchange（food.dlx.exchange）。
        factory.setErrorHandler(new ConditionalRejectingErrorHandler(t -> {
            Throwable cause = t.getCause();
            if (cause != null
                    && cause.getMessage() != null
                    && cause.getMessage().contains("x-java-serialized-object")) {
                log.warn("丢弃队列中的旧格式消息（Java序列化），该消息将被跳过");
                return true;
            }
            // 关键变更：不再回落到 DefaultExceptionStrategy。
            // 后者只把「不可恢复的框架异常」视为致命，业务异常会被 requeue，
            // 正是它导致了 337 万次重投。现在一律拒绝进死信队列，由人工排查重放。
            log.error("消息处理失败，转入死信队列 {}: {}", FOOD_DLQ_QUEUE, cause == null ? t.toString() : cause.toString());
            return true;
        }));

        return factory;
    }
}