package com.example.sevice.impl;

import com.example.sevice.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
        return doEmbed(text);
    }

    @SuppressWarnings("unchecked")
    private List<Float> doEmbed(String text) {
        try {
            Map<String, Object> request = Map.of("model", model, "prompt", text);
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/api/embeddings", request, Map.class);

            if (response == null || !response.containsKey("embedding")) {
                log.warn("Ollama 返回空结果: text={}", text);
                return Collections.emptyList();
            }

            List<Double> raw = (List<Double>) response.get("embedding");
            return raw.stream().map(Double::floatValue).toList();
        } catch (Exception e) {
            log.error("Ollama Embedding 调用失败: text={}", text, e);
            return Collections.emptyList();
        }
    }
}