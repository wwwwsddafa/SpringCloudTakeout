package com.example.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bean.ResDailyOrderStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DailyOrderStatsMapper extends BaseMapper<ResDailyOrderStats> {
}