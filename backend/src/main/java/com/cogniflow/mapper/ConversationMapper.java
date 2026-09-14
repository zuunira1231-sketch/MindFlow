package com.cogniflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cogniflow.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
