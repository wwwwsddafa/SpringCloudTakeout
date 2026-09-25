package com.example.mq;

import com.example.config.RabbitConfig;
import com.example.document.FoodDocument;
import com.example.sevice.EmbeddingService;
import com.example.sevice.SearchService;
import com.example.web.vo.FoodSyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Slf4j
public class FoodSyncListener {

    /**
     * 向量维度，必须与 ES 索引 mapping 中 fnameVector 的 dims 一致（bge-m3 = 1024）。
     * 维度不符的向量写入 ES 会直接抛 document_parsing_exception。
     */
    @Value("${ollama.embedding-dim:1024}")
    private int embeddingDim;

    @Autowired
    private SearchService searchService;

    @Autowired
    private EmbeddingService embeddingService;

    @RabbitListener(queues = RabbitConfig.FOOD_SYNC_QUEUE, concurrency = "3-5")
    public void handleFoodSync(FoodSyncMessage message) {
        log.info("收到商品同步消息: action={}, fid={}, fname={}", message.getAction(), message.getFid(), message.getFname());

        if (FoodSyncMessage.ACTION_DELETE.equals(message.getAction())) {
            searchService.deleteByFid(message.getFid());
            return;
        }

        String fname = message.getFname();
        String detail = message.getDetail();
        List<Float> fnameVector = null;

        if (StringUtils.hasText(fname)) {
            String combinedText = fname;
            if (StringUtils.hasText(detail)) {
                combinedText = fname + "。" + detail;
            }
            fnameVector = embeddingService.embed(combinedText);

            // 关键修复：向量生成失败（Ollama 未启动 / 网络故障）或维度不符时，
            // 必须让消息进入死信队列，绝不能带着空向量继续写 ES。
            // 否则 ES 会抛 document_parsing_exception，Spring AMQP 默认 requeue，
            // 消息被无限重投（实测曾累计 337 万次重投，队列永久堵塞）。
            if (fnameVector == null || fnameVector.isEmpty()) {
                throw new AmqpRejectAndDontRequeueException(
                        "向量生成失败（Ollama 不可用？），消息转入死信队列: fid=" + message.getFid());
            }
            if (fnameVector.size() != embeddingDim) {
                throw new AmqpRejectAndDontRequeueException(
                        "向量维度不匹配，期望 " + embeddingDim + " 实际 " + fnameVector.size()
                                + "，消息转入死信队列: fid=" + message.getFid());
            }

            log.info("向量生成完成: fid={}, fname={}, detailLen={}, dim={}",
                    message.getFid(), fname,
                    StringUtils.hasText(detail) ? detail.length() : 0,
                    fnameVector.size());
        } else {
            // 无名称无法生成向量：跳过向量字段，仅建关键词索引（保证商品仍可被关键词搜到）
            log.warn("商品无名称，跳过向量生成只建关键词索引: fid={}", message.getFid());
        }

        FoodDocument doc = FoodDocument.builder()
                .fid(message.getFid())
                .fname(fname)
                .normprice(message.getNormprice())
                .realprice(message.getRealprice())
                .detail(message.getDetail())
                .fphoto(message.getFphoto())
                .category(message.getCategory())
                .status(message.getStatus())
                .likeCount(message.getLikeCount() != null ? message.getLikeCount() : 0)
                .dislikeCount(message.getDislikeCount() != null ? message.getDislikeCount() : 0)
                .fnameVector(fnameVector)
                .build();

        try {
            searchService.saveOrUpdate(doc);
        } catch (Exception e) {
            // 写 ES 失败同样不应无限重投：转入死信队列，由人工/定时任务排查重放
            throw new AmqpRejectAndDontRequeueException(
                    "ES 索引写入失败，消息转入死信队列: fid=" + message.getFid(), e);
        }
    }
}