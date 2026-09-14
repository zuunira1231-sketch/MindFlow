package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("relation_knowledge")
public class RelationKnowledge {

    private Long relationId;

    private Long knowledgeId;
}
