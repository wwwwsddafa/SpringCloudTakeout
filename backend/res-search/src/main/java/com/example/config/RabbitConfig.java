package com.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
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

    @Bean
    public TopicExchange foodExchange() {
        return new TopicExchange(FOOD_EXCHANGE, true, false);
    }

    @Bean
    public Queue foodSyncQueue() {
        return new Queue(FOOD_SYNC_QUEUE, true);
    }

    @Bean
    public Binding foodSyncBinding() {
        return BindingBuilder.bind(foodSyncQueue())
                .to(foodExchange())
                .with(FOOD_SYNC_ROUTING_KEY);
    }

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

        factory.setErrorHandler(new ConditionalRejectingErrorHandler(
                t -> {
                    Throwable cause = t.getCause();
                    if (cause != null
                            && cause.getMessage() != null
                            && cause.getMessage().contains("x-java-serialized-object")) {
                        log.warn("丢弃队列中的旧格式消息（Java序列化），该消息将被跳过");
                        return true;
                    }
                    return new ConditionalRejectingErrorHandler.DefaultExceptionStrategy()
                            .isFatal(t);
                }));

        return factory;
    }
}