package com.example.sevice.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.example.document.FoodDocument;
import com.example.repository.FoodRepository;
import com.example.sevice.EmbeddingService;
import com.example.sevice.SearchService;
import com.example.web.vo.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private EmbeddingService embeddingService;

    @Value("${ollama.embedding-dim}")
    private int embeddingDim;

    private static final int MAX_PAGE_SIZE = 50;
    private static final int RRF_RANK_CONSTANT = 60;

    @Override
    public PageResult<FoodDocument> search(String keyword, String category, int page, int size, String sort, String searchMode) {
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        if (page < 1) {
            page = 1;
        }

        String mode = StringUtils.hasText(searchMode) ? searchMode : "hybrid";

        String actualMode;
        PageResult<FoodDocument> result;

        if ("semantic".equals(mode) && StringUtils.hasText(keyword)) {
            actualMode = "semantic";
            result = semanticSearch(keyword, category, page, size, sort);
        } else if ("hybrid".equals(mode) && StringUtils.hasText(keyword)) {
            actualMode = "hybrid";
            result = hybridSearch(keyword, category, page, size, sort);
        } else {
            actualMode = "keyword";
            result = keywordSearch(keyword, category, page, size, sort);
        }

        List<String> foodNames = result.getRecords().stream()
                .map(FoodDocument::getFname)
                .collect(Collectors.toList());
        log.info("搜索完成 [mode={}] keyword={}, total={}, top10={}", actualMode, keyword, result.getTotal(), foodNames);

        return result;
    }

    private PageResult<FoodDocument> keywordSearch(String keyword, String category, int page, int size, String sort) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"bool\":{\"must\":[{\"term\":{\"status\":1}}");

        if (StringUtils.hasText(category)) {
            jsonBuilder.append(",{\"term\":{\"category\":\"")
                    .append(category).append("\"}}");
        }

        if (StringUtils.hasText(keyword)) {
            jsonBuilder.append(",{\"bool\":{\"should\":[")
                    .append("{\"match\":{\"fname\":\"")
                    .append(keyword).append("\"}},")
                    .append("{\"match\":{\"detail\":\"")
                    .append(keyword).append("\"}}")
                    .append("],\"minimum_should_match\":1}}");
        }

        jsonBuilder.append("]}}");

        StringQuery query = new StringQuery(jsonBuilder.toString());
        query.setPageable(PageRequest.of(page - 1, size, buildSort(sort)));

        SearchHits<FoodDocument> searchHits = elasticsearchOperations.search(query, FoodDocument.class);
        return toPageResult(searchHits);
    }

    /**
     * 查询侧专用的向量生成包装：Ollama 不可用时返回空列表，让检索链路优雅降级到关键词搜索，
     * 而不是把 500 抛给前端。写入侧（MQ 消费者）不使用此包装，失败必须显式暴露。
     */
    private List<Float> safeEmbedForSearch(String keyword) {
        try {
            return embeddingService.embedForSearch(keyword);
        } catch (Exception e) {
            log.warn("查询向量生成失败，降级为关键词搜索: keyword={}, reason={}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private PageResult<FoodDocument> semanticSearch(String keyword, String category, int page, int size, String sort) {
        List<Float> queryVector = safeEmbedForSearch(keyword);
        if (queryVector.isEmpty()) {
            log.warn("语义搜索向量生成失败，降级为关键词搜索");
            return keywordSearch(keyword, category, page, size, sort);
        }

        try {
            SearchResponse<FoodDocument> response = elasticsearchClient.search(
                    SearchRequest.of(s -> s
                            .index("resfood")
                            .query(q -> q
                                    .scriptScore(ss -> ss
                                            .query(buildFilterQuery(category))
                                            .script(script -> script
                                                    .inline(inline -> inline
                                                            .source("cosineSimilarity(params.query_vector, 'fnameVector') + 1.0")
                                                            .params("query_vector", JsonData.of(queryVector))
                                                    )
                                            )
                                    )
                            )
                            .from((page - 1) * size)
                            .size(size)),
                    FoodDocument.class);

            return toPageResult(response);
        } catch (Exception e) {
            log.error("语义搜索失败", e);
            return keywordSearch(keyword, category, page, size, sort);
        }
    }

    private PageResult<FoodDocument> hybridSearch(String keyword, String category, int page, int size, String sort) {
        List<Float> queryVector = safeEmbedForSearch(keyword);
        if (queryVector.isEmpty()) {
            log.warn("混合搜索向量生成失败，降级为关键词搜索");
            return keywordSearch(keyword, category, page, size, sort);
        }

        int fetchSize = size * 3;

        SearchHits<FoodDocument> keywordHits = keywordSearchRaw(keyword, category, fetchSize, sort);
        List<FoodDocument> semanticDocs = semanticSearchRaw(keyword, queryVector, category, fetchSize);

        List<String> keywordNames = keywordHits.getSearchHits().stream()
                .map(h -> h.getContent().getFname())
                .collect(Collectors.toList());
        List<String> semanticNames = semanticDocs.stream()
                .map(FoodDocument::getFname)
                .collect(Collectors.toList());
        log.info("混合搜索召回: keyword=[{}] ({}条), semantic=[{}] ({}条)",
                String.join(", ", keywordNames), keywordNames.size(),
                String.join(", ", semanticNames), semanticNames.size());

        Map<String, FoodDocument> docMap = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        accumulateRrf(keywordHits, rrfScores, docMap, RRF_RANK_CONSTANT);
        accumulateRrf(semanticDocs, rrfScores, docMap, RRF_RANK_CONSTANT);

        List<FoodDocument> merged = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> docMap.get(e.getKey()))
                .collect(Collectors.toList());

        int start = (page - 1) * size;
        int end = Math.min(start + size, merged.size());
        if (start >= merged.size()) {
            return new PageResult<>(merged.size(), Collections.emptyList());
        }

        return new PageResult<>(merged.size(), merged.subList(start, end));
    }

    private SearchHits<FoodDocument> keywordSearchRaw(String keyword, String category, int size, String sort) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"bool\":{\"must\":[{\"term\":{\"status\":1}}");

        if (StringUtils.hasText(category)) {
            jsonBuilder.append(",{\"term\":{\"category\":\"")
                    .append(category).append("\"}}");
        }

        if (StringUtils.hasText(keyword)) {
            jsonBuilder.append(",{\"bool\":{\"should\":[")
                    .append("{\"match\":{\"fname\":\"")
                    .append(keyword).append("\"}},")
                    .append("{\"match\":{\"detail\":\"")
                    .append(keyword).append("\"}}")
                    .append("],\"minimum_should_match\":1}}");
        }

        jsonBuilder.append("]}}");

        StringQuery query = new StringQuery(jsonBuilder.toString());
        query.setPageable(PageRequest.of(0, size, buildSort(sort)));
        return elasticsearchOperations.search(query, FoodDocument.class);
    }

    private List<FoodDocument> semanticSearchRaw(String keyword, List<Float> queryVector, String category, int size) {
        try {
            SearchResponse<FoodDocument> response = elasticsearchClient.search(
                    SearchRequest.of(s -> s
                            .index("resfood")
                            .query(q -> q
                                    .scriptScore(ss -> ss
                                            .query(buildFilterQuery(category))
                                            .script(script -> script
                                                    .inline(inline -> inline
                                                            .source("cosineSimilarity(params.query_vector, 'fnameVector') + 1.0")
                                                            .params("query_vector", JsonData.of(queryVector))
                                                    )
                                            )
                                    )
                            )
                            .size(size)),
                    FoodDocument.class);

            List<FoodDocument> docs = new ArrayList<>();
            for (Hit<FoodDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    docs.add(hit.source());
                }
            }
            return docs;
        } catch (Exception e) {
            log.error("语义搜索原始查询失败", e);
            return Collections.emptyList();
        }
    }

    private Query buildFilterQuery(String category) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                .must(TermQuery.of(t -> t.field("status").value(FieldValue.of(1)))._toQuery());

        if (StringUtils.hasText(category)) {
            boolBuilder.must(TermQuery.of(t -> t.field("category").value(FieldValue.of(category)))._toQuery());
        }

        return boolBuilder.build()._toQuery();
    }

    private PageResult<FoodDocument> toPageResult(SearchResponse<FoodDocument> response) {
        List<FoodDocument> records = new ArrayList<>();
        for (Hit<FoodDocument> hit : response.hits().hits()) {
            if (hit.source() != null) {
                records.add(hit.source());
            }
        }
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        return new PageResult<>(total, records);
    }

    private void accumulateRrf(SearchHits<FoodDocument> hits, Map<String, Double> rrfScores,
                               Map<String, FoodDocument> docMap, int k) {
        int rank = 1;
        for (SearchHit<FoodDocument> hit : hits) {
            FoodDocument doc = hit.getContent();
            String fid = doc.getFid();
            docMap.putIfAbsent(fid, doc);
            rrfScores.merge(fid, 1.0 / (k + rank), Double::sum);
            rank++;
        }
    }

    private void accumulateRrf(List<FoodDocument> docs, Map<String, Double> rrfScores,
                               Map<String, FoodDocument> docMap, int k) {
        int rank = 1;
        for (FoodDocument doc : docs) {
            String fid = doc.getFid();
            docMap.putIfAbsent(fid, doc);
            rrfScores.merge(fid, 1.0 / (k + rank), Double::sum);
            rank++;
        }
    }

    private org.springframework.data.domain.Sort buildSort(String sort) {
        if ("realprice_asc".equals(sort)) {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "realprice");
        } else if ("realprice_desc".equals(sort)) {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "realprice");
        } else if ("likeCount_desc".equals(sort)) {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "likeCount");
        }
        return org.springframework.data.domain.Sort.unsorted();
    }

    private PageResult<FoodDocument> toPageResult(SearchHits<FoodDocument> searchHits) {
        List<FoodDocument> records = new ArrayList<>();
        for (SearchHit<FoodDocument> hit : searchHits) {
            records.add(hit.getContent());
        }
        return new PageResult<>(searchHits.getTotalHits(), records);
    }

    @Override
    public List<String> suggest(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String jsonQuery = "{\"bool\":{\"must\":["
                + "{\"term\":{\"status\":1}},"
                + "{\"match\":{\"fname\":\"" + keyword + "\"}}"
                + "]}}";
        StringQuery query = new StringQuery(jsonQuery);
        query.setPageable(PageRequest.of(0, 5));

        SearchHits<FoodDocument> searchHits = elasticsearchOperations.search(query, FoodDocument.class);
        return searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent().getFname())
                .collect(Collectors.toList());
    }

    @Override
    public void saveOrUpdate(FoodDocument food) {
        foodRepository.save(food);
        log.info("ES索引更新: fid={}, fname={}", food.getFid(), food.getFname());
    }

    @Override
    public void batchSaveOrUpdate(List<FoodDocument> foods) {
        if (foods == null || foods.isEmpty()) {
            return;
        }
        foodRepository.saveAll(foods);
        log.info("ES批量索引更新: count={}", foods.size());
    }

    @Override
    public void deleteByFid(String fid) {
        foodRepository.deleteById(fid);
        log.info("ES索引删除: fid={}", fid);
    }

    @Override
    public void initIndex() {
        long count = foodRepository.count();
        log.info("ES索引初始化完成，当前文档数: {}", count);
    }
}