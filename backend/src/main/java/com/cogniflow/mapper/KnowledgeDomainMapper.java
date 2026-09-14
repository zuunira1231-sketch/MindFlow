package com.cogniflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cogniflow.entity.KnowledgeDomain;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDomainMapper
        extends BaseMapper<KnowledgeDomain> {
}
