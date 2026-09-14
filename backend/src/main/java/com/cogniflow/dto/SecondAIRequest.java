package com.cogniflow.dto;

import com.cogniflow.entity.Domain;
import com.cogniflow.entity.Evolution;
import com.cogniflow.entity.EvolutionKnowledge;
import com.cogniflow.entity.KnowledgeDomain;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.entity.KnowledgeRelation;
import com.cogniflow.entity.RelationKnowledge;
import lombok.Data;

import java.util.List;

@Data
public class SecondAIRequest {

    /**
     * 用户确认后的 Draft。
     *
     * 其中已经包含：
     * 1. 用户确认的新 Knowledge；
     * 2. 用户确认的 Domain；
     * 3. Knowledge 与 Domain 的归属。
     */
    private DraftDTO confirmedDraft;

    /**
     * 用户目前已经存在的全部 Domain。
     *
     * 第二次 AI 只能使用这里的已有 Domain，
     * 或者 confirmedDraft 中经过用户确认的新 Domain。
     */
    private List<Domain> existingDomains;

    /**
     * 可能与本次新认知相关的旧 Knowledge。
     */
    private List<KnowledgeNode> existingKnowledge;

    /**
     * 旧 Knowledge 与 Domain 的关联记录。
     *
     * 第二次 AI 可以通过它知道：
     * 某条旧 Knowledge 原来属于哪些 Domain。
     */
    private List<KnowledgeDomain> existingKnowledgeDomainLinks;

    /**
     * 与旧 Knowledge 相关的旧 Relation。
     */
    private List<KnowledgeRelation> existingRelations;

    /**
     * 与旧 Knowledge 相关的旧 Evolution。
     */
    private List<Evolution> existingEvolutions;

    /**
     * 旧 Relation 与旧 Knowledge 的关联记录。
     */
    private List<RelationKnowledge> existingRelationLinks;

    /**
     * 旧 Evolution 与旧 Knowledge 的关联记录。
     */
    private List<EvolutionKnowledge> existingEvolutionLinks;
}