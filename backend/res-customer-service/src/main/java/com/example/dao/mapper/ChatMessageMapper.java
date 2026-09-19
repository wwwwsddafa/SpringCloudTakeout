package com.example.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bean.ChatMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM res_chat_message WHERE (sender_id = #{userId} OR receiver_id = #{userId}) AND create_time >= #{since} AND msg_type != 'SYSTEM' ORDER BY create_time ASC LIMIT 100")
    List<ChatMessage> findRecentByUserId(@Param("userId") String userId, @Param("since") LocalDateTime since);
}