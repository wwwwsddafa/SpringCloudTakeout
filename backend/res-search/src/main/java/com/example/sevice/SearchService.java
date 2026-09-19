package com.example.sevice;

import com.example.document.FoodDocument;
import com.example.web.vo.PageResult;

import java.util.List;

public interface SearchService {

    PageResult<FoodDocument> search(String keyword, String category, int page, int size, String sort, String searchMode);

    List<String> suggest(String keyword);

    void saveOrUpdate(FoodDocument food);

    void batchSaveOrUpdate(List<FoodDocument> foods);

    void deleteByFid(String fid);

    void initIndex();
}