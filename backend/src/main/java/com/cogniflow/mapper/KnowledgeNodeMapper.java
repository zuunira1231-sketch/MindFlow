package com.cogniflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.mapper.KnowledgeNodeMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeNodeMapper extends BaseMapper<KnowledgeNode> {
}
