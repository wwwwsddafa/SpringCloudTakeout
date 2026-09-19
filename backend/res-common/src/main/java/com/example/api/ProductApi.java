package com.example.api;

import com.example.web.vo.ResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product")
public interface ProductApi {

    @PostMapping("/admin/rebuildSearchIndex")
    ResultVo rebuildSearchIndex();

    @GetMapping("/detail/{fid}")
    ResultVo getProductDetail(@PathVariable String fid);

    @GetMapping("/admin/name/{fid}")
    ResultVo getProductName(@PathVariable String fid);

    @GetMapping("/admin/page")
    ResultVo searchProducts(@RequestParam("page") int page,
                            @RequestParam("size") int size,
                            @RequestParam("keyword") String keyword);

    @PostMapping("/review/stats/batch")
    ResultVo getProductsRatingStats(@RequestBody List<String> fids,
                                    @RequestParam("date") String date);
}