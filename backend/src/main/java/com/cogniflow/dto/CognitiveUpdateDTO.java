package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CognitiveUpdateDTO {

    /**
     * 需要新增或更新的 Knowledge。
     */
    @JsonProperty("knowledge_updates")
    private List<KnowledgeResultDTO> knowledgeUpdates;

    /**
     * 需要新增或更新的 Relation。
     */
    @JsonProperty("relation_updates")
    private List<RelationResultDTO> relationUpdates;

    /**
     * 本次需要新增的 Evolution。
     */
    @JsonProperty("evolution_updates")
    private List<EvolutionResultDTO> evolutionUpdates;
}
