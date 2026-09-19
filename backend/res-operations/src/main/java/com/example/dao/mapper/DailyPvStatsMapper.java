package com.example.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bean.ResDailyPvStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DailyPvStatsMapper extends BaseMapper<ResDailyPvStats> {
}