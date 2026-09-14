package com.cogniflow.dto;

import com.cogniflow.entity.Domain;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.entity.KnowledgeRelation;
import com.cogniflow.entity.RelationKnowledge;
import lombok.Data;

import java.util.List;

@Data
public class DomainSummaryContextDTO {

    /**
     * 当前需要更新摘要的 Domain。
     */
    private Domain domain;

    /**
     * 当前 Domain 下的全部 Knowledge。
     */
    private List<KnowledgeNode> knowledge;

    /**
     * 与当前 Domain 的 Knowledge 相关的 Relation。
     */
    private List<KnowledgeRelation> relations;

    /**
     * 上述 Relation 与 Knowledge 的关联记录。
     *
     * AI 通过它判断每条 Relation
     * 具体连接了哪些 Knowledge。
     */
    private List<RelationKnowledge> relationLinks;
}
