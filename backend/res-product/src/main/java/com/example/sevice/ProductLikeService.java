package com.example.sevice;

import com.example.web.vo.ResultVo;

public interface ProductLikeService {

    ResultVo like(String userId, String fid, Integer likeType);

    ResultVo getLikeStatus(String userId, String fid);

    ResultVo getLikeCount(String fid);
}