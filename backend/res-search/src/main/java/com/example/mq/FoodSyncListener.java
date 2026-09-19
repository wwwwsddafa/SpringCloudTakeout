package com.example.mq;

import com.example.config.RabbitConfig;
import com.example.document.FoodDocument;
import com.example.sevice.EmbeddingService;
import com.example.sevice.SearchService;
import com.example.web.vo.FoodSyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Slf4j
public class FoodSyncListener {

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
            log.info("向量生成完成: fid={}, fname={}, detailLen={}, dim={}",
                    message.getFid(), fname,
                    StringUtils.hasText(detail) ? detail.length() : 0,
                    fnameVector.size());
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

        searchService.saveOrUpdate(doc);
    }
}