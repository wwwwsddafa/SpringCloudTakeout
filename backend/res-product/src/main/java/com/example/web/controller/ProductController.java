package com.example.web.controller;

import com.example.api.FileUploadApi;
import com.example.api.IdGeneratorApi;
import com.example.bean.ResFood;
import com.example.exceptions.IdNotFoundException;
import com.example.exceptions.PicFileUploadException;
import com.example.sevice.ResFoodService;
import com.example.web.vo.PageResult;
import com.example.web.vo.ResfoodVo;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping
@Slf4j
public class ProductController {

    // 注意：完整请求路径为 /product/addProduct

    private final String idGeneratorUrl = "http://idGenerator";
    private final String fileUploadUrl = "http://fileUpload";


    //RestTemplate 常用于微服务之间的通信,是 Spring 框架提供的一个用于发起 HTTP 请求的工具类。
    private final RestTemplate restTemplate;

    @Autowired
    public ProductController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Autowired
    private FileUploadApi fileUploadApi;
    @Autowired
    private IdGeneratorApi idGeneratorApi;
    @Autowired
    private ResFoodService resFoodService;

    //admin表示管理员权限，只有管理员才能添加商品
    //user表示用户权限，用户才能查询商品
    //什么都不加表示所有用户都能访问的接口
    @PostMapping("/admin/addProduct")
    public ResultVo addProduct(ResfoodVo resfoodVo, @RequestParam("photo") MultipartFile[] photo) {
        //ResultVo result = this.restTemplate.getForObject(idGeneratorUrl + "/next/id", ResultVo.class);
        ResultVo result = idGeneratorApi.next();
        String fid = "";
        if (result.getCode() != 200) {
            throw new IdNotFoundException();
        }
        fid = result.getData().toString();
        log.info("新商品id:" + fid);
        resfoodVo.setFid(fid);



        ResultVo uploadResult = this.fileUploadApi.upload(  photo );
        if( uploadResult.getCode()!=200 ){
            throw new PicFileUploadException("图片上传失败");
        }
        List<String> urls = (List<String>) uploadResult.getData();
        // 将urls中每个地址后加一个","作为分隔符，拼接成一个字符串
        String fphoto = urls.stream().map(url -> url + ",").collect(Collectors.joining());
        log.info("新商品的图片为：" + fphoto);
        resfoodVo.setFphoto(fphoto);








//        try {
//
//
//            if (photo != null && photo.length > 0) {
//                // 1. 设置请求头：Content-Type 必须为 multipart/form-data
//                HttpHeaders headers = new HttpHeaders();
//                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//
//                // 2. 构建请求体 MultiValueMap
//                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//                for (MultipartFile file : photo) {
//                    if (!file.isEmpty()) {
//                        // 将 MultipartFile 转为 ByteArrayResource（重写 getFilename 以便服务端正常获取文件名）
//                        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
//                            @Override
//                            public String getFilename() {
//                                return file.getOriginalFilename(); // 必须传文件名，服务端才能识别为文件
//                            }
//                        };
//                        // 注意：这里的 key ("photo") 必须与远程接口接收文件的参数名一致
//                        body.add("uploadFiles", fileResource);
//                    }
//                }
//                // 3. 封装为 HttpEntity
//                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
//                // 4. 发送 POST 请求
//                ResultVo uploadResult = this.restTemplate.postForObject(fileUploadUrl + "/upload", requestEntity, ResultVo.class);
//                if (uploadResult == null || uploadResult.getCode() != 200) {
//                    throw new RuntimeException("图片上传失败");
//                }
//                List<String> urls = (List<String>) uploadResult.getData();
//                // 将urls中每个地址后加一个","作为分隔符，拼接成一个字符串
//                String fphoto = urls.stream().map(url -> url + ",").collect(Collectors.joining());
//                log.info("新商品的图片为：" + fphoto);
//                resfoodVo.setFphoto(fphoto);
//            }
//        } catch (
//                Exception ex) {
//            log.error("图片上传失败:" + ex);
//            throw new PicFileUploadException("图片上传失败:" + ex);
//        }

        //利用spring的对象转换工具类     将resfoodVo转换为ResFood对象
        ResFood resFood = new ResFood();
        BeanUtils.copyProperties(resfoodVo, resFood);
        ResFood food = resFoodService.add(resFood);
        return ResultVo.success(    resfoodVo );
    }

    @GetMapping("/list")
    public ResultVo listOnSale() {
        List<ResfoodVo> list = resFoodService.listOnSale();
        return ResultVo.success(list);
    }

    @GetMapping("/detail/{fid}")
    public ResultVo getDetail(@PathVariable String fid) {
        ResFood food = resFoodService.getOnSaleDetail(fid);
        return ResultVo.success(ResfoodVo.from(food));
    }

    @GetMapping("/admin/name/{fid}")
    public ResultVo getProductName(@PathVariable String fid) {
        ResFood food = resFoodService.getProductName(fid);
        if (food == null) {
            return ResultVo.fail(404, "商品不存在");
        }
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("fid", food.getFid());
        data.put("fname", food.getFname());
        data.put("category", food.getCategory());
        return ResultVo.success(data);
    }

    @GetMapping("/admin/page")
    public ResultVo adminPage(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int size,
                              @RequestParam(required = false) String keyword) {
        PageResult<ResFood> result = resFoodService.adminPage(page, size, keyword);
        return ResultVo.success(result);
    }

    @PostMapping("/admin/updateProduct")
    public ResultVo updateProduct(ResfoodVo resfoodVo,
                                  @RequestParam(value = "photo", required = false) MultipartFile[] photo) {
        // 如果传了新图片，先上传图片
        if (photo != null && photo.length > 0) {
            ResultVo uploadResult = this.fileUploadApi.upload(photo);
            if (uploadResult.getCode() != 200) {
                throw new PicFileUploadException("图片上传失败");
            }
            List<String> urls = (List<String>) uploadResult.getData();
            String fphoto = urls.stream().map(url -> url + ",").collect(Collectors.joining());
            log.info("修改商品的图片为：" + fphoto);
            resfoodVo.setFphoto(fphoto);
        }
        ResFood food = new ResFood();
        BeanUtils.copyProperties(resfoodVo, food);
        ResFood updated = resFoodService.update(food);
        return ResultVo.success(ResfoodVo.from(updated));
    }

    @PostMapping("/admin/changeStatus/{fid}")
    public ResultVo changeStatus(@PathVariable String fid, @RequestParam Integer status) {
        resFoodService.changeStatus(fid, status);
        return ResultVo.success(status == 1 ? "上架成功" : "下架成功");
    }

    @DeleteMapping("/admin/deleteProduct/{fid}")
    public ResultVo deleteProduct(@PathVariable String fid) {
        resFoodService.delete(fid);
        return ResultVo.success("删除成功");
    }

    @PostMapping("/admin/rebuildSearchIndex")
    public ResultVo rebuildSearchIndex() {
        resFoodService.syncAllToSearch();
        return ResultVo.success("ES索引重建已触发，请等待同步完成");
    }
}