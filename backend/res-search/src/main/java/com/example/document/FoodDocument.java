package com.example.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "resfood")
public class FoodDocument {

    @Id
    private String fid;

    @Field(type = FieldType.Text, analyzer = "ik_smart", searchAnalyzer = "ik_smart")
    private String fname;

    @Field(type = FieldType.Text, analyzer = "ik_smart", searchAnalyzer = "ik_smart")
    private String detail;

    @Field(type = FieldType.Double)
    private BigDecimal normprice;

    @Field(type = FieldType.Double)
    private BigDecimal realprice;

    @Field(type = FieldType.Keyword)
    private String fphoto;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Integer)
    private Integer likeCount;

    @Field(type = FieldType.Integer)
    private Integer dislikeCount;

    @JsonIgnore
    @Field(type = FieldType.Dense_Vector)
    private List<Float> fnameVector;
}

/*这个类有3个作用：
* 定义索引结构	告诉 ES 索引里存哪些字段
* 配置分词规则	决定哪些字段需要中文分词
* 数据载体	在 Java 和 ES 之间传递数据
*
* 如果把 ES 索引比作一张数据库表，那么 FoodDocument 就是这张表的实体类。相当于原来bean类
*
* */