package com.cogniflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cogniflow.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper
        extends BaseMapper<ConversationMessage> {
}
