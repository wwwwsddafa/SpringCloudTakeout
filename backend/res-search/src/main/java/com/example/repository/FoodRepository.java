package com.example.repository;

import com.example.document.FoodDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FoodRepository extends ElasticsearchRepository<FoodDocument, String> {

}

/*
* FoodRepository 是 Elasticsearch 的数据访问接口（DAO）。
如果说 FoodDocument 是“数据长什么样”，那么 FoodRepository 就是"怎么操作这些数据"的工具。
*
*
* extends ElasticsearchRepository：
* 只要继承了这个接口，Spring Data 就自动送你一套 CRUD（增删改查） 方法，一行代码都不用写
*
*/