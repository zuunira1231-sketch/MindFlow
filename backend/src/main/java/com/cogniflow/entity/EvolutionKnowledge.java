package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("evolution_knowledge")
public class EvolutionKnowledge {

    private Long evolutionId;

    private Long knowledgeId;
}
