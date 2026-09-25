package com.example.sevice.impl;

import com.example.sevice.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;

    public EmbeddingServiceImpl(RestTemplate restTemplate,
                                @Value("${ollama.base-url}") String baseUrl,
                                @Value("${ollama.embedding-model}") String model) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public List<Float> embed(String text) {
        return doEmbed(text);
    }

    @Override
    public List<Float> embedForSearch(String text) {
        // 查询侧允许降级：调用失败时抛出的异常由 SearchService 捕获并退回关键词检索，
        // 所以这里保持「异常向上抛、由调用方决定降级策略」的统一语义。
        return doEmbed(text);
    }

    /**
     * 调用 Ollama 生成向量。
     *
     * <p><b>重要语义约定</b>：本方法只对「调用失败」抛异常，不再静默返回空列表。
     * 静默失败曾导致写入侧带着空向量去写 ES，触发 dimensions [0] != [1024] 的
     * document_parsing_exception，进而被 Spring AMQP 无限重投，队列累积数百万次 redeliver。
     *
     * <ul>
     *   <li>输入为空 → 返回空列表（调用方明确知道没有向量可生成，属正常情况）</li>
     *   <li>Ollama 不可用 / 网络超时 / 返回体异常 → 抛 IllegalStateException（调用方必须显式处理）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<Float> doEmbed(String text) {
        if (!StringUtils.hasText(text)) {
            log.debug("Embedding 输入为空，跳过向量生成");
            return Collections.emptyList();
        }

        Map<String, Object> response;
        try {
            Map<String, Object> request = Map.of("model", model, "prompt", text);
            response = restTemplate.postForObject(baseUrl + "/api/embeddings", request, Map.class);
        } catch (Exception e) {
            // 网络故障 / 连接拒绝 / 超时：必须让调用方感知，绝不能伪装成「没有向量」
            throw new IllegalStateException(
                    "Ollama Embedding 服务不可用（base-url=" + baseUrl + ", model=" + model
                            + "），请确认 Ollama 已启动: " + e.getMessage(), e);
        }

        if (response == null || !response.containsKey("embedding") || response.get("embedding") == null) {
            throw new IllegalStateException(
                    "Ollama Embedding 返回体异常（缺少 embedding 字段），model=" + model
                            + "，原始响应=" + response);
        }

        Object raw = response.get("embedding");
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalStateException(
                    "Ollama Embedding 返回空向量，model=" + model + "，text长度=" + text.length());
        }

        return ((List<Double>) rawList).stream().map(Double::floatValue).toList();
    }
}