package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("knowledge_domain")
public class KnowledgeDomain {

    private Long knowledgeId;

    private Long domainId;
}
