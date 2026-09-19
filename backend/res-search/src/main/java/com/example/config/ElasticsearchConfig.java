package com.example.config;

import com.example.api.ProductApi;
import com.example.document.FoodDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.example.repository")
@Slf4j
public class ElasticsearchConfig {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductApi productApi;

    public ElasticsearchConfig(ElasticsearchOperations elasticsearchOperations,
                               ProductApi productApi) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.productApi = productApi;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initIndex() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(FoodDocument.class);
            if (!indexOps.exists()) {
                indexOps.create(createSettings(), createMapping());
                log.info("ES索引 resfood 创建成功，映射已建立");
            } else {
                log.info("ES索引 resfood 已存在，跳过创建");
            }
        } catch (Exception e) {
            log.error("ES索引初始化失败: {}", e.getMessage(), e);
        }

        try {
            log.info("启动时自动触发全量数据同步，补齐宕机期间遗漏的数据...");
            productApi.rebuildSearchIndex();
            log.info("ES全量数据同步已触发，请等待同步完成");
        } catch (Exception e) {
            log.warn("自动触发ES数据同步失败（product服务可能尚未就绪），保留现有ES数据: {}", e.getMessage());
        }
    }

    private Document createSettings() {
        Document settings = Document.create();
        settings.put("index.number_of_shards", 1);
        settings.put("index.number_of_replicas", 0);
        return settings;
    }

    private Document createMapping() {
        Document mapping = Document.create();
        Document properties = Document.create();

        properties.put("fid", createField("keyword"));
        properties.put("fname", createTextField("ik_smart", "ik_smart"));
        properties.put("detail", createTextField("ik_smart", "ik_smart"));
        properties.put("normprice", createField("double"));
        properties.put("realprice", createField("double"));
        properties.put("fphoto", createField("keyword"));
        properties.put("category", createField("keyword"));
        properties.put("status", createField("integer"));
        properties.put("likeCount", createField("integer"));
        properties.put("dislikeCount", createField("integer"));

        Document fnameVector = Document.create();
        fnameVector.put("type", "dense_vector");
        fnameVector.put("dims", 1024);
        properties.put("fnameVector", fnameVector);

        mapping.put("properties", properties);
        return mapping;
    }

    private Document createField(String type) {
        Document field = Document.create();
        field.put("type", type);
        return field;
    }

    private Document createTextField(String analyzer, String searchAnalyzer) {
        Document field = Document.create();
        field.put("type", "text");
        field.put("analyzer", analyzer);
        field.put("search_analyzer", searchAnalyzer);
        return field;
    }
}