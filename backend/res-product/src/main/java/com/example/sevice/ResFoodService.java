package com.example.sevice;


import com.example.bean.ResFood;
import com.example.web.vo.PageResult;
import com.example.web.vo.ResfoodVo;

import java.util.List;

public interface ResFoodService {

    /** User side: on-sale food list (Redis cached) */
    List<ResfoodVo> listOnSale();

    /** Food detail (on-sale) */
    ResFood getOnSaleDetail(String fid);

    /** Internal: get product name & category without status check */
    ResFood getProductName(String fid);

    /** Admin side: paged food query */
    PageResult<ResFood> adminPage(int page, int size, String keyword);

    /** Add food */
    ResFood add(ResFood food);

    /** Update food */
    ResFood update(ResFood food);

    /** Delete food (must be off-shelf first) */
    void delete(String fid);

    /** Change on/off shelf status 更新上下架状态*/
    void changeStatus(String fid, Integer status);

    /** Evict food cache */
    void evictFoodCache();

    /** Rebuild ES search index - publish all foods to MQ */
    void syncAllToSearch();
}