package com.cogniflow.service.impl;

import com.cogniflow.config.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cogniflow.client.AIClient;
import com.cogniflow.dto.AnalyzeDraftRequest;
import com.cogniflow.dto.CognitiveUpdateDTO;
import com.cogniflow.dto.CommitDraftResponse;
import com.cogniflow.dto.IntegrationPlanResponse;
import com.cogniflow.dto.PrepareIntegrationPlanRequest;
import com.cogniflow.dto.RelationPreviewDTO;
import com.cogniflow.dto.EvolutionPreviewDTO;
import com.cogniflow.dto.DomainPreviewDTO;
import com.cogniflow.dto.DraftDTO;
import com.cogniflow.dto.EvolutionResultDTO;
import com.cogniflow.dto.KnowledgePreviewDTO;
import com.cogniflow.dto.KnowledgeResultDTO;
import com.cogniflow.dto.RelationResultDTO;
import com.cogniflow.dto.SecondAIRequest;
import com.cogniflow.entity.Conversation;
import com.cogniflow.entity.Domain;
import com.cogniflow.entity.Draft;
import com.cogniflow.entity.Evolution;
import com.cogniflow.entity.EvolutionKnowledge;
import com.cogniflow.entity.KnowledgeDomain;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.entity.KnowledgeRelation;
import com.cogniflow.entity.IntegrationPlan;
import com.cogniflow.entity.RelationKnowledge;
import com.cogniflow.mapper.DraftMapper;
import com.cogniflow.mapper.EvolutionKnowledgeMapper;
import com.cogniflow.mapper.EvolutionMapper;
import com.cogniflow.mapper.KnowledgeDomainMapper;
import com.cogniflow.mapper.KnowledgeNodeMapper;
import com.cogniflow.mapper.KnowledgeRelationMapper;
import com.cogniflow.mapper.RelationKnowledgeMapper;
import com.cogniflow.mapper.IntegrationPlanMapper;
import com.cogniflow.mapper.CognitiveRevisionMapper;
import com.cogniflow.mapper.DraftCommitGuardMapper;
import com.cogniflow.service.AnalysisService;
import com.cogniflow.service.ConversationService;
import com.cogniflow.exception.PlanConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.cogniflow.service.DomainSummaryService;
import com.cogniflow.service.DomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private final AIClient aiClient;

    private final DraftMapper draftMapper;
    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final EvolutionMapper evolutionMapper;
    private final RelationKnowledgeMapper relationKnowledgeMapper;
    private final EvolutionKnowledgeMapper evolutionKnowledgeMapper;
    private final TransactionTemplate transactionTemplate;
    private final DomainService domainService;
    private final KnowledgeDomainMapper knowledgeDomainMapper;
    private final DomainSummaryService domainSummaryService;
    private final ConversationService conversationService;
    private final IntegrationPlanMapper integrationPlanMapper;
    private final CognitiveRevisionMapper cognitiveRevisionMapper;
    private final DraftCommitGuardMapper draftCommitGuardMapper;
    private final ObjectMapper objectMapper;

    /**
     * 第一次 AI 分析：
     * 把原始聊天内容整理成 DraftDTO。
     */
    @Override
    public DraftDTO analyzeDraft(AnalyzeDraftRequest request) {

        // 当前项目还没有接入登录系统，暂时固定使用用户 1。
        Long userId = CurrentUser.id();

        Long conversationId;
        String analysisContent;

        if (request.getConversationId() != null) {
            conversationId = request.getConversationId();
            analysisContent = conversationService.buildAnalysisContent(
                    conversationId,
                    userId
            );
        } else if (request.getChatContent() != null
                && !request.getChatContent().isBlank()) {
            Conversation conversation = conversationService.importText(
                    userId,
                    null,
                    "manual",
                    request.getChatContent()
            );
            conversationId = conversation.getId();
            analysisContent = conversationService.buildAnalysisContent(
                    conversationId,
                    userId
            );
        } else {
            throw new IllegalArgumentException(
                    "chatContent 和 conversationId 至少提供一个"
            );
        }

        /*
         * 第一次 AI 分析前，查询用户已有的 Domain。
         *
         * AI 应优先把新 Knowledge 分到已有 Domain，
         * 只有确实无法合理归类时才能建议新 Domain。
         */
        List<Domain> existingDomains =
                domainService.listByUserId(userId);

        /*
         * 短对话直接分析；长对话先按消息分段，
         * 再逐段合并为一份用户可审核的 Draft。
         */
        List<String> chunks = conversationService.buildAnalysisChunks(
                conversationId,
                userId,
                6000
        );
        DraftDTO draftDTO = null;

        for (String chunk : chunks) {
            DraftDTO partial = aiClient.analyzeDraft(
                    chunk,
                    existingDomains
            );
            draftDTO = draftDTO == null
                    ? partial
                    : aiClient.mergeDrafts(
                            draftDTO,
                            partial,
                            existingDomains
                    );
        }

        /*
         * sourceContent 不是 AI 生成的，
         * 而是后端把用户原始聊天内容补进 Draft。
         */
        draftDTO.setSourceContent(analysisContent);
        draftDTO.setConversationId(conversationId);

        /*
         * requestId 用于防止同一份 Draft 被重复提交。
         */
        draftDTO.setRequestId(
                java.util.UUID.randomUUID().toString()
        );

        return draftDTO;
    }

    /**
     * 用户确认 Draft 后：
     *
     * 1. 保存 Draft。
     * 2. 查找旧 Knowledge 候选。
     * 3. 查找旧 Knowledge 相关的 Relation。
     * 4. 查找旧 Knowledge 相关的 Evolution。
     * 5. 组装第二次 AI 的请求。
     */
    @Override
    public CommitDraftResponse commitDraft(DraftDTO draftDTO) {
        throw new IllegalStateException(
                "旧直写流程已停用，请先生成最终方案并由用户确认"
        );
    }

    @Override
    public IntegrationPlanResponse preparePlan(PrepareIntegrationPlanRequest request) {

        DraftDTO draftDTO = request.getDraft();
        if (draftDTO == null || request.getPlanRequestId() == null
                || request.getPlanRequestId().isBlank()) {
            throw new IllegalArgumentException("Draft 和 planRequestId 不能为空");
        }


        Long userId = CurrentUser.id();

        IntegrationPlan existingPlan = integrationPlanMapper.selectOne(
                new LambdaQueryWrapper<IntegrationPlan>()
                        .eq(IntegrationPlan::getUserId, userId)
                        .eq(IntegrationPlan::getPlanRequestId,
                                request.getPlanRequestId())
                        .last("LIMIT 1")
        );
        if (existingPlan != null) {
            if (!existingPlan.getDraftRequestId().equals(draftDTO.getRequestId())
                    || !existingPlan.getConfirmedDraftJson().equals(toJson(draftDTO))) {
                throw new IllegalArgumentException(
                        "同一 planRequestId 不能提交不同的 Draft"
                );
            }
            return planResponse(existingPlan);
        }

        if (draftCommitGuardMapper.findAppliedPlanId(userId, draftDTO.getRequestId()) != null) {
            throw new PlanConflictException("这份 Draft 已完成沉淀，不能再次生成执行方案");
        }
        Draft legacyCommitted = draftMapper.selectOne(
                new LambdaQueryWrapper<Draft>()
                        .eq(Draft::getUserId, userId)
                        .eq(Draft::getRequestId, draftDTO.getRequestId())
                        .eq(Draft::getStatus, "completed")
                        .last("LIMIT 1"));
        if (legacyCommitted != null) {
            throw new PlanConflictException("这份 Draft 已通过旧流程沉淀过");
        }

        validateDraftReferences(draftDTO);
        if (draftDTO.getConversationId() != null) {
            String ownedContent = conversationService.buildAnalysisContent(
                    draftDTO.getConversationId(), userId);
            if (!ownedContent.equals(draftDTO.getSourceContent())) {
                throw new IllegalArgumentException(
                        "Draft 的对话来源与原始内容不一致，请重新生成 Draft"
                );
            }
        }
        cognitiveRevisionMapper.ensure(userId);
        long baselineRevision = cognitiveRevisionMapper.current(userId);

        /* 先在内存中准备 Draft；第二次 AI 和摘要都成功后再一起保存。 */
        Draft draft = new Draft();

        draft.setUserId(userId);
        draft.setConversationId(draftDTO.getConversationId());
        draft.setRequestId(request.getPlanRequestId());
        draft.setTitle(draftDTO.getTitle());
        draft.setSourceContent(draftDTO.getSourceContent());
        draft.setSummary(draftDTO.getSummary());
        draft.setStatus("planned");

        /*
         * 第二步：粗略查找可能相关的旧 Knowledge。
         *
         * 候选来源：
         * 1. 根据新 Knowledge 标题模糊查询；
         * 2. 根据用户确认的已有 Domain 查询。
         */
        List<KnowledgeNode> existingKnowledge =
                new ArrayList<>();

        /*
         * 第一种候选来源：
         * 根据新 Knowledge 的标题模糊查询。
         */
        if (draftDTO.getKnowledgePreview() != null) {

            for (KnowledgePreviewDTO preview
                    : draftDTO.getKnowledgePreview()) {

                if (preview == null) {
                    continue;
                }

                String title = preview.getTitle();

                if (title == null || title.isBlank()) {
                    continue;
                }

                List<KnowledgeNode> titleCandidates =
                        knowledgeNodeMapper.selectList(
                                new LambdaQueryWrapper<KnowledgeNode>()
                                        .eq(
                                                KnowledgeNode::getUserId,
                                                userId
                                        )
                                        .and(wrapper ->
                                                wrapper.like(
                                                                KnowledgeNode::getTitle,
                                                                title
                                                        )
                                                        .or()
                                                        .like(
                                                                KnowledgeNode::getDescription,
                                                                title
                                                        )
                                        )
                        );

                existingKnowledge.addAll(titleCandidates);
            }
        }

        /*
         * 第二种候选来源：
         * 收集用户在 KnowledgePreview 中确认的已有 Domain ID。
         *
         * 新 Domain 使用 tempId，目前数据库里还没有历史 Knowledge，
         * 所以这里只处理真实 Domain ID。
         */
        Set<Long> confirmedExistingDomainIds =
                new LinkedHashSet<>();

        if (draftDTO.getKnowledgePreview() != null) {

            for (KnowledgePreviewDTO preview
                    : draftDTO.getKnowledgePreview()) {

                if (preview == null
                        || preview.getDomainIds() == null) {
                    continue;
                }

                for (Long domainId : preview.getDomainIds()) {

                    if (domainId == null) {
                        continue;
                    }

                    /*
                     * 防止前端传入不存在的 Domain，
                     * 或者传入其他用户的 Domain。
                     */
                    Domain domain =
                            domainService.getById(domainId);

                    if (domain == null) {
                        throw new IllegalArgumentException(
                                "Draft 引用的 Domain 不存在，id="
                                        + domainId
                        );
                    }

                    if (!userId.equals(domain.getUserId())) {
                        throw new IllegalArgumentException(
                                "Draft 引用了其他用户的 Domain，id="
                                        + domainId
                        );
                    }

                    confirmedExistingDomainIds.add(domainId);
                }
            }
        }

        /*
         * Domain ID
         * → knowledge_domain
         * → Knowledge ID
         * → knowledge_node
         */
        if (!confirmedExistingDomainIds.isEmpty()) {

            List<KnowledgeDomain> domainLinks =
                    knowledgeDomainMapper.selectList(
                            new LambdaQueryWrapper<KnowledgeDomain>()
                                    .in(
                                            KnowledgeDomain::getDomainId,
                                            confirmedExistingDomainIds
                                    )
                    );

            List<Long> domainKnowledgeIds =
                    domainLinks.stream()
                            .map(KnowledgeDomain::getKnowledgeId)
                            .distinct()
                            .toList();

            if (!domainKnowledgeIds.isEmpty()) {

                List<KnowledgeNode> domainCandidates =
                        knowledgeNodeMapper.selectBatchIds(
                                domainKnowledgeIds
                        );

                /*
                 * 关联表本身没有 userId，
                 * 所以从主表查询后还要检查 Knowledge 所属用户。
                 */
                for (KnowledgeNode candidate : domainCandidates) {

                    if (!userId.equals(candidate.getUserId())) {
                        throw new IllegalArgumentException(
                                "Domain 关联了其他用户的 Knowledge，id="
                                        + candidate.getId()
                        );
                    }

                    existingKnowledge.add(candidate);
                }
            }
        }

        /*
         * 标题候选和 Domain 候选可能重复。
         *
         * 使用 Knowledge ID 去重，
         * 比直接调用 distinct() 更明确。
         */
        Map<Long, KnowledgeNode> candidateMap =
                new LinkedHashMap<>();

        for (KnowledgeNode candidate : existingKnowledge) {

            if (candidate == null || candidate.getId() == null) {
                continue;
            }

            candidateMap.putIfAbsent(
                    candidate.getId(),
                    candidate
            );
        }

        existingKnowledge =
                new ArrayList<>(candidateMap.values());

        /*
         * 用于保存旧 Relation、Evolution，
         * 以及它们和 Knowledge 的关联记录。
         */
        List<KnowledgeRelation> existingRelations =
                new ArrayList<>();

        List<RelationKnowledge> existingRelationLinks =
                new ArrayList<>();

        List<Evolution> existingEvolutions =
                new ArrayList<>();

        List<EvolutionKnowledge> existingEvolutionLinks =
                new ArrayList<>();

        /*
         * 旧 Knowledge 与 Domain 的关联记录。
         */
        List<KnowledgeDomain> existingKnowledgeDomainLinks =
                new ArrayList<>();

        /*
         * 只有找到了旧 Knowledge，
         * 才需要继续查询 Relation 和 Evolution。
         */
        if (!existingKnowledge.isEmpty()) {

            List<Long> knowledgeIds = existingKnowledge.stream()
                    .map(KnowledgeNode::getId)
                    .toList();

            /*
             * 查询这些旧 Knowledge 原本所属的 Domain。
             *
             * Knowledge ID
             * → knowledge_domain
             * → Domain ID
             */
            existingKnowledgeDomainLinks =
                    knowledgeDomainMapper.selectList(
                            new LambdaQueryWrapper<KnowledgeDomain>()
                                    .in(
                                            KnowledgeDomain::getKnowledgeId,
                                            knowledgeIds
                                    )
                    );

            /*
             * 第三步：查询 Relation。
             *
             * Knowledge ID
             * → relation_knowledge
             * → Relation ID
             * → knowledge_relation
             */
            existingRelationLinks =
                    relationKnowledgeMapper.selectList(
                            new LambdaQueryWrapper<RelationKnowledge>()
                                    .in(
                                            RelationKnowledge::getKnowledgeId,
                                            knowledgeIds
                                    )
                    );

            List<Long> relationIds =
                    existingRelationLinks.stream()
                            .map(RelationKnowledge::getRelationId)
                            .distinct()
                            .toList();

            if (!relationIds.isEmpty()) {
                existingRelations =
                        knowledgeRelationMapper.selectBatchIds(
                                relationIds
                        );
            }

            /*
             * 第四步：查询 Evolution。
             *
             * Knowledge ID
             * → evolution_knowledge
             * → Evolution ID
             * → evolution
             */
            existingEvolutionLinks =
                    evolutionKnowledgeMapper.selectList(
                            new LambdaQueryWrapper<EvolutionKnowledge>()
                                    .in(
                                            EvolutionKnowledge::getKnowledgeId,
                                            knowledgeIds
                                    )
                    );

            List<Long> evolutionIds =
                    existingEvolutionLinks.stream()
                            .map(EvolutionKnowledge::getEvolutionId)
                            .distinct()
                            .toList();

            if (!evolutionIds.isEmpty()) {
                existingEvolutions =
                        evolutionMapper.selectBatchIds(
                                evolutionIds
                        );
            }
        }

        /*
         * 第二次 AI 可以引用的已有 Domain ID：
         *
         * 1. 用户在本次 Draft 中确认的已有 Domain；
         * 2. 候选旧 Knowledge 原本所属的 Domain。
         */
        Set<Long> allowedExistingDomainIds =
                new LinkedHashSet<>(
                        confirmedExistingDomainIds
                );

        for (KnowledgeDomain link
                : existingKnowledgeDomainLinks) {

            if (link == null || link.getDomainId() == null) {
                continue;
            }

            Domain domain =
                    domainService.getById(link.getDomainId());

            if (domain == null) {
                throw new IllegalArgumentException(
                        "旧 Knowledge 关联的 Domain 不存在，id="
                                + link.getDomainId()
                );
            }

            if (!userId.equals(domain.getUserId())) {
                throw new IllegalArgumentException(
                        "旧 Knowledge 关联了其他用户的 Domain，id="
                                + link.getDomainId()
                );
            }

            allowedExistingDomainIds.add(
                    link.getDomainId()
            );
        }

        /*
         * 收集用户在本次 Draft 中确认的新 Domain tempId。
         *
         * 第二次 AI 不能引用这个集合以外的新 Domain。
         */
        Set<String> allowedNewDomainTempIds =
                new LinkedHashSet<>();

        if (draftDTO.getDomainPreview() != null) {

            for (DomainPreviewDTO preview
                    : draftDTO.getDomainPreview()) {

                if (preview == null) {
                    continue;
                }

                /*
                 * id 为空才表示新 Domain。
                 */
                if (preview.getId() == null) {

                    String tempId = preview.getTempId();

                    if (tempId == null || tempId.isBlank()) {
                        throw new IllegalArgumentException(
                                "用户确认的新 Domain tempId 不能为空"
                        );
                    }

                    if (!allowedNewDomainTempIds.add(tempId)) {
                        throw new IllegalArgumentException(
                                "Draft 中存在重复的 Domain tempId："
                                        + tempId
                        );
                    }
                }
            }
        }

        /*
         * 第五步：组装第二次 AI 的请求。
         */
        List<Domain> existingDomains =
                domainService.listByUserId(userId);

        SecondAIRequest secondAIRequest = new SecondAIRequest();

        /*
         * 用户确认后的 Draft。
         *
         * 其中包含：
         * domainPreview、knowledgePreview、
         * relationPreview、evolutionPreview。
         */
        secondAIRequest.setConfirmedDraft(draftDTO);

        /*
         * 用户数据库中已经存在的 Domain。
         */
        secondAIRequest.setExistingDomains(existingDomains);

        /*
         * 旧 Knowledge 候选。
         */
        secondAIRequest.setExistingKnowledge(existingKnowledge);

        /*
         * 旧 Knowledge 与 Domain 的关联记录。
         */
        secondAIRequest.setExistingKnowledgeDomainLinks(
                existingKnowledgeDomainLinks
        );

        /*
         * 旧 Relation 及其 Knowledge 关联记录。
         */
        secondAIRequest.setExistingRelations(existingRelations);
        secondAIRequest.setExistingRelationLinks(
                existingRelationLinks
        );

        /*
         * 旧 Evolution 及其 Knowledge 关联记录。
         */
        secondAIRequest.setExistingEvolutions(existingEvolutions);
        secondAIRequest.setExistingEvolutionLinks(
                existingEvolutionLinks
        );

        /*
         * 调用第二次 AI。
         *
         * AI 调用期间不启动数据库事务。
         */
        CognitiveUpdateDTO cognitiveUpdate;

        cognitiveUpdate = aiClient.integrateKnowledge(secondAIRequest);

        /*
         * 第二次 AI 不应该返回 null。
         */
        if (cognitiveUpdate == null) {

            throw new IllegalStateException(
                    "第二次 AI 没有返回认知更新结果"
            );
        }

        // AI 有时会把原文原样返回为 UPDATE；完全相同的内容和关联不算变更。
        removeExactNoOpUpdates(cognitiveUpdate, userId);

        if (cognitiveRevisionMapper.current(userId) != baselineRevision) {
            throw new PlanConflictException(
                    "生成方案期间认知库发生变化，请重新生成方案"
            );
        }

        Map<String, Object> changes = buildPlanPreview(
                draftDTO, cognitiveUpdate, userId
        );
        // 领域摘要将在虚拟执行结果上计算，不能读取当前数据库直接当作最终值。
        Map<String, String> plannedSummaries = domainSummaryService.previewSummaries(
                draftDTO, cognitiveUpdate, userId
        );
        changes.put("domainSummaries", buildSummaryPreview(plannedSummaries,
                draftDTO, userId));
        if (cognitiveRevisionMapper.current(userId) != baselineRevision) {
            throw new PlanConflictException("生成摘要期间认知库发生变化，请重新生成方案");
        }

        IntegrationPlan plan = new IntegrationPlan();
        plan.setUserId(userId);
        plan.setDraftRequestId(draftDTO.getRequestId());
        plan.setPlanRequestId(request.getPlanRequestId());
        plan.setStatus("READY");
        plan.setBaselineRevision(baselineRevision);
        plan.setConfirmedDraftJson(toJson(draftDTO));
        plan.setCognitiveUpdateJson(toJson(cognitiveUpdate));
        plan.setPreviewJson(toJson(changes));
        plan.setSummaryJson(toJson(plannedSummaries));
        plan.setExpiresAt(LocalDateTime.now().plusHours(24));
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                draftMapper.insert(draft);
                plan.setDraftId(draft.getId());
                integrationPlanMapper.insert(plan);
            });
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            IntegrationPlan winner = integrationPlanMapper.selectOne(
                    new LambdaQueryWrapper<IntegrationPlan>()
                            .eq(IntegrationPlan::getUserId, userId)
                            .eq(IntegrationPlan::getPlanRequestId, request.getPlanRequestId())
                            .last("LIMIT 1"));
            if (winner != null && winner.getDraftRequestId().equals(draftDTO.getRequestId())
                    && winner.getConfirmedDraftJson().equals(toJson(draftDTO))) {
                return planResponse(winner);
            }
            throw new PlanConflictException("planRequestId 已被其他方案使用");
        }
        return planResponse(plan);
    }

    /**
     * 这里只过滤可确定的精确无变化记录。同义改写是否具有认知价值，
     * 仍由第二次 AI 按提示词判断，不能用字符串算法猜测。
     */
    private void removeExactNoOpUpdates(CognitiveUpdateDTO update, Long userId) {
        if (update.getKnowledgeUpdates() != null) {
            List<KnowledgeResultDTO> effective = new ArrayList<>();
            for (KnowledgeResultDTO item : update.getKnowledgeUpdates()) {
                if (item == null || item.getId() == null) {
                    effective.add(item);
                    continue;
                }
                KnowledgeNode old = knowledgeNodeMapper.selectById(item.getId());
                if (old == null || !userId.equals(old.getUserId())) {
                    throw new IllegalArgumentException("方案更新了其他用户或不存在的 Knowledge");
                }
                Set<Long> oldDomains = new LinkedHashSet<>();
                for (KnowledgeDomain link : knowledgeDomainMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDomain>()
                                .eq(KnowledgeDomain::getKnowledgeId, old.getId()))) {
                    oldDomains.add(link.getDomainId());
                }
                Set<Long> finalDomains = item.getDomainIds() == null
                        ? Set.of() : new LinkedHashSet<>(item.getDomainIds());
                boolean hasNewDomain = item.getDomainTempIds() != null
                        && !item.getDomainTempIds().isEmpty();
                if (java.util.Objects.equals(old.getTitle(), item.getTitle())
                        && java.util.Objects.equals(old.getDescription(), item.getDescription())
                        && oldDomains.equals(finalDomains) && !hasNewDomain) {
                    continue;
                }
                effective.add(item);
            }
            update.setKnowledgeUpdates(effective);
        }
        if (update.getRelationUpdates() != null) {
            List<RelationResultDTO> effective = new ArrayList<>();
            for (RelationResultDTO item : update.getRelationUpdates()) {
                if (item == null || item.getId() == null) {
                    effective.add(item);
                    continue;
                }
                KnowledgeRelation old = knowledgeRelationMapper.selectById(item.getId());
                if (old == null || !userId.equals(old.getUserId())) {
                    throw new IllegalArgumentException("方案更新了其他用户或不存在的 Relation");
                }
                Set<Long> oldKnowledgeIds = new LinkedHashSet<>();
                for (RelationKnowledge link : relationKnowledgeMapper.selectList(
                        new LambdaQueryWrapper<RelationKnowledge>()
                                .eq(RelationKnowledge::getRelationId, old.getId()))) {
                    oldKnowledgeIds.add(link.getKnowledgeId());
                }
                Set<Long> finalKnowledgeIds = item.getKnowledgeIds() == null
                        ? Set.of() : new LinkedHashSet<>(item.getKnowledgeIds());
                boolean hasNewKnowledge = item.getKnowledgeTempIds() != null
                        && !item.getKnowledgeTempIds().isEmpty();
                if (java.util.Objects.equals(old.getDescription(), item.getDescription())
                        && oldKnowledgeIds.equals(finalKnowledgeIds) && !hasNewKnowledge) {
                    continue;
                }
                effective.add(item);
            }
            update.setRelationUpdates(effective);
        }
    }


    /**
     * 保存用户在 Draft 中确认的新 Domain。
     *
     * 已有 Domain 不重复保存，只检查它是否真实存在，
     * 并且是否属于当前用户。
     *
     * 返回：
     * 新 Domain tempId 与数据库真实 ID 的对应关系。
     */
    private Map<String, Long> saveConfirmedDomains(
            DraftDTO draftDTO,
            Long userId
    ) {
        Map<String, Long> tempDomainIdMap =
                new HashMap<>();

        if (draftDTO == null
                || draftDTO.getDomainPreview() == null) {
            return tempDomainIdMap;
        }

        for (DomainPreviewDTO preview
                : draftDTO.getDomainPreview()) {

            if (preview == null) {
                continue;
            }

            /*
             * id 有值，表示用户选择的是已有 Domain。
             *
             * 已有 Domain 不需要再次插入数据库，
             * 但必须验证它是否存在且属于当前用户。
             */
            if (preview.getId() != null) {

                Domain existingDomain =
                        domainService.getById(preview.getId());

                if (existingDomain == null) {
                    throw new IllegalArgumentException(
                            "用户确认的 Domain 不存在，id="
                                    + preview.getId()
                    );
                }

                if (!userId.equals(existingDomain.getUserId())) {
                    throw new IllegalArgumentException(
                            "无权使用该 Domain，id="
                                    + preview.getId()
                    );
                }

                continue;
            }

            /*
             * id 为空，表示这是第一次 AI 建议，
             * 并且已经经过用户确认的新 Domain。
             */
            String tempId = preview.getTempId();

            if (tempId == null || tempId.isBlank()) {
                throw new IllegalArgumentException(
                        "新 Domain 的 tempId 不能为空"
                );
            }

            if (tempDomainIdMap.containsKey(tempId)) {
                throw new IllegalArgumentException(
                        "发现重复的 Domain tempId：" + tempId
                );
            }

            /*
             * 用户主动创建和确认 AI 推荐创建，
             * 最终都复用 DomainService 的创建逻辑。
             *
             * 如果同名 Domain 已存在，
             * createDomain 会直接返回已有 Domain。
             */
            Domain savedDomain = domainService.createDomain(
                    userId,
                    preview.getName(),
                    preview.getDescription()
            );

            tempDomainIdMap.put(
                    tempId,
                    savedDomain.getId()
            );
        }

        return tempDomainIdMap;
    }


    /**
     * 根据第二次 AI 返回的结果，
     * 保存 Knowledge 与 Domain 的关联关系。
     */
    private Set<Long> saveKnowledgeDomainLinks(
            CognitiveUpdateDTO cognitiveUpdate,
            Long userId,
            Map<String, Long> tempKnowledgeIdMap,
            Map<String, Long> tempDomainIdMap,
            Set<Long> allowedExistingDomainIds,
            Set<String> allowedNewDomainTempIds
    ) {
        Set<Long> affectedDomainIds =
                new LinkedHashSet<>();

        if (cognitiveUpdate == null
                || cognitiveUpdate.getKnowledgeUpdates() == null) {
            return affectedDomainIds;
        }

        for (KnowledgeResultDTO result
                : cognitiveUpdate.getKnowledgeUpdates()) {

            if (result == null) {
                continue;
            }

            Long knowledgeId;

            if (result.getId() != null) {
                knowledgeId = result.getId();

                KnowledgeNode existingKnowledge =
                        knowledgeNodeMapper.selectById(knowledgeId);

                if (existingKnowledge == null) {
                    throw new IllegalArgumentException(
                            "Domain 关联引用的 Knowledge 不存在，id="
                                    + knowledgeId
                    );
                }

                if (!userId.equals(existingKnowledge.getUserId())) {
                    throw new IllegalArgumentException(
                            "无权修改该 Knowledge 的 Domain，id="
                                    + knowledgeId
                    );
                }

            } else {
                String knowledgeTempId = result.getTempId();

                knowledgeId =
                        tempKnowledgeIdMap.get(knowledgeTempId);

                if (knowledgeId == null) {
                    throw new IllegalArgumentException(
                            "找不到新增 Knowledge 的真实 ID，tempId="
                                    + knowledgeTempId
                    );
                }
            }

            Set<Long> domainIds = resolveDomainIds(
                    result.getDomainIds(),
                    result.getDomainTempIds(),
                    tempDomainIdMap,
                    allowedExistingDomainIds,
                    allowedNewDomainTempIds,
                    userId
            );

            if (domainIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "每条 Knowledge 至少需要关联一个 Domain"
                );
            }

            // 方案中的归属是最终状态；旧 Knowledge 被移出某领域时也要删除旧关联。
            if (result.getId() != null) {
                List<KnowledgeDomain> oldLinks = knowledgeDomainMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDomain>()
                                .eq(KnowledgeDomain::getKnowledgeId, knowledgeId)
                );
                for (KnowledgeDomain oldLink : oldLinks) {
                    affectedDomainIds.add(oldLink.getDomainId());
                    if (!domainIds.contains(oldLink.getDomainId())) {
                        knowledgeDomainMapper.delete(
                                new LambdaQueryWrapper<KnowledgeDomain>()
                                        .eq(KnowledgeDomain::getKnowledgeId, knowledgeId)
                                        .eq(KnowledgeDomain::getDomainId,
                                                oldLink.getDomainId())
                        );
                    }
                }
            }
            affectedDomainIds.addAll(domainIds);

            for (Long domainId : domainIds) {

                Long linkCount =
                        knowledgeDomainMapper.selectCount(
                                new LambdaQueryWrapper<KnowledgeDomain>()
                                        .eq(
                                                KnowledgeDomain::getKnowledgeId,
                                                knowledgeId
                                        )
                                        .eq(
                                                KnowledgeDomain::getDomainId,
                                                domainId
                                        )
                        );

                if (linkCount > 0) {
                    continue;
                }

                KnowledgeDomain knowledgeDomain =
                        new KnowledgeDomain();

                knowledgeDomain.setKnowledgeId(knowledgeId);
                knowledgeDomain.setDomainId(domainId);

                knowledgeDomainMapper.insert(knowledgeDomain);
            }
        }

        return affectedDomainIds;
    }

    /**
     * 将已有 Domain ID 和新 Domain tempId，
     * 统一转换成真实数据库 Domain ID。
     */
    private Set<Long> resolveDomainIds(
            List<Long> existingDomainIds,
            List<String> newDomainTempIds,
            Map<String, Long> tempDomainIdMap,
            Set<Long> allowedExistingDomainIds,
            Set<String> allowedNewDomainTempIds,
            Long userId
    ) {
        Set<Long> resolvedDomainIds =
                new LinkedHashSet<>();

        /*
         * 处理数据库中已有的 Domain ID。
         */
        if (existingDomainIds != null) {

            for (Long domainId : existingDomainIds) {

                if (domainId == null) {
                    continue;
                }

                if (allowedExistingDomainIds == null
                        || !allowedExistingDomainIds.contains(domainId)) {

                    throw new IllegalArgumentException(
                            "第二次 AI 引用了本次不允许使用的 Domain，id="
                                    + domainId
                    );
                }

                Domain domain =
                        domainService.getById(domainId);

                if (domain == null) {
                    throw new IllegalArgumentException(
                            "第二次 AI 引用的 Domain 不存在，id="
                                    + domainId
                    );
                }

                if (!userId.equals(domain.getUserId())) {
                    throw new IllegalArgumentException(
                            "第二次 AI 引用了其他用户的 Domain，id="
                                    + domainId
                    );
                }

                resolvedDomainIds.add(domainId);
            }
        }

        /*
         * 处理用户确认的新 Domain tempId。
         */
        if (newDomainTempIds != null) {

            for (String tempId : newDomainTempIds) {

                if (tempId == null || tempId.isBlank()) {
                    continue;
                }

                if (allowedNewDomainTempIds == null
                        || !allowedNewDomainTempIds.contains(tempId)) {

                    throw new IllegalArgumentException(
                            "第二次 AI 引用了未经用户确认的新 Domain："
                                    + tempId
                    );
                }

                Long domainId =
                        tempDomainIdMap.get(tempId);

                if (domainId == null) {
                    throw new IllegalArgumentException(
                            "第二次 AI 引用了未经用户确认的 Domain tempId："
                                    + tempId
                    );
                }

                resolvedDomainIds.add(domainId);
            }
        }

        return resolvedDomainIds;
    }


    /**
     * 根据第二次 AI 返回的结果，
     * 新增或更新 Knowledge。
     *
     * 返回：
     * 新 Knowledge 的 tempId 与数据库真实 ID 的对应关系。
     */
    private Map<String, Long> saveKnowledgeUpdates(
            CognitiveUpdateDTO cognitiveUpdate,
            Long userId
    ) {

        //准备临时 ID 对照表
        Map<String, Long> tempKnowledgeIdMap =
                new HashMap<>();

        //检查 AI 是否返回 Knowledge
        if (cognitiveUpdate == null
                || cognitiveUpdate.getKnowledgeUpdates() == null) {
            return tempKnowledgeIdMap;
        }

        //遍历 AI 返回的每个 Knowledge
        for (KnowledgeResultDTO result
                : cognitiveUpdate.getKnowledgeUpdates()) {

            //id == null 时新增
            if (result == null) {
                continue;
            }

            /*
             * id 为空：
             * 第二次 AI 要求新增 Knowledge。
             */
            if (result.getId() == null) {

                String tempId = result.getTempId();

                if (tempId == null || tempId.isBlank()) {
                    throw new IllegalArgumentException(
                            "新增 Knowledge 时 tempId 不能为空"
                    );
                }

                if (tempKnowledgeIdMap.containsKey(tempId)) {
                    throw new IllegalArgumentException(
                            "发现重复的 Knowledge tempId：" + tempId
                    );
                }

                KnowledgeNode knowledgeNode =
                        new KnowledgeNode();

                knowledgeNode.setUserId(userId);
                knowledgeNode.setTitle(result.getTitle());
                knowledgeNode.setDescription(
                        result.getDescription()
                );

                knowledgeNodeMapper.insert(knowledgeNode);

                /*
                 * insert 后，MyBatis-Plus 会把数据库生成的 ID
                 * 放回 knowledgeNode.id。
                 */
                tempKnowledgeIdMap.put(
                        tempId,
                        knowledgeNode.getId()
                );

                continue;
            }

            /*
             * id 有值：
             * 第二次 AI 要求更新已有 Knowledge。
             */
            KnowledgeNode existingKnowledge =
                    knowledgeNodeMapper.selectById(
                            result.getId()
                    );

            if (existingKnowledge == null) {
                throw new IllegalArgumentException(
                        "要更新的 Knowledge 不存在，id="
                                + result.getId()
                );
            }

            /*
             * 防止第二次 AI 返回其他用户的 Knowledge ID。
             */
            if (!userId.equals(existingKnowledge.getUserId())) {
                throw new IllegalArgumentException(
                        "无权更新该 Knowledge，id="
                                + result.getId()
                );
            }

            existingKnowledge.setTitle(result.getTitle());
            existingKnowledge.setDescription(
                    result.getDescription()
            );

            knowledgeNodeMapper.updateById(
                    existingKnowledge
            );
        }

        return tempKnowledgeIdMap;
    }

    /**
     * 新增或更新 Relation，
     * 并保存 Relation 与 Knowledge 的关联关系。
     */
    private void saveRelationUpdates(
            CognitiveUpdateDTO cognitiveUpdate,
            Long userId,
            Long draftId,
            Map<String, Long> tempKnowledgeIdMap
    ) {

        if (cognitiveUpdate == null
                || cognitiveUpdate.getRelationUpdates() == null) {
            return;
        }

        for (RelationResultDTO result
                : cognitiveUpdate.getRelationUpdates()) {

            if (result == null) {
                continue;
            }

            /*
             * 将旧 Knowledge ID 和新 Knowledge tempId
             * 全部转换成真实的数据库 Knowledge ID。
             */
            Set<Long> knowledgeIds = resolveKnowledgeIds(
                    result.getKnowledgeIds(),
                    result.getKnowledgeTempIds(),
                    tempKnowledgeIdMap,
                    userId
            );

            /*
             * 一条 Relation 至少需要关联两个 Knowledge。
             */
            if (knowledgeIds.size() < 2) {
                throw new IllegalArgumentException(
                        "Relation 至少需要关联两个 Knowledge"
                );
            }

            Long relationId;

            /*
             * Relation id 为空：新增关系。
             */
            if (result.getId() == null) {

                KnowledgeRelation relation =
                        new KnowledgeRelation();

                relation.setUserId(userId);
                relation.setSourceDraftId(draftId);
                relation.setDescription(
                        result.getDescription()
                );

                knowledgeRelationMapper.insert(relation);

                relationId = relation.getId();

            } else {

                /*
                 * Relation id 有值：更新旧关系。
                 */
                KnowledgeRelation existingRelation =
                        knowledgeRelationMapper.selectById(
                                result.getId()
                        );

                if (existingRelation == null) {
                    throw new IllegalArgumentException(
                            "要更新的 Relation 不存在，id="
                                    + result.getId()
                    );
                }

                /*
                 * 防止 AI 更新其他用户的 Relation。
                 */
                if (!userId.equals(
                        existingRelation.getUserId()
                )) {
                    throw new IllegalArgumentException(
                            "无权更新该 Relation，id="
                                    + result.getId()
                    );
                }

                existingRelation.setDescription(
                        result.getDescription()
                );

                knowledgeRelationMapper.updateById(
                        existingRelation
                );

                relationId = existingRelation.getId();

                /*
                 * 更新 Relation 时，
                 * 先删除它原来的 Knowledge 关联记录。
                 *
                 * 后面会按照 AI 返回的结果重新建立。
                 */
                relationKnowledgeMapper.delete(
                        new LambdaQueryWrapper<RelationKnowledge>()
                                .eq(
                                        RelationKnowledge::getRelationId,
                                        relationId
                                )
                );
            }

            /*
             * 将 Relation 和相关 Knowledge
             * 写入 relation_knowledge 关联表。
             */
            for (Long knowledgeId : knowledgeIds) {

                RelationKnowledge relationKnowledge =
                        new RelationKnowledge();

                relationKnowledge.setRelationId(relationId);
                relationKnowledge.setKnowledgeId(knowledgeId);

                relationKnowledgeMapper.insert(
                        relationKnowledge
                );
            }
        }
    }

    /**
     * 将 AI 返回的旧 Knowledge ID 和新 Knowledge tempId，
     * 统一转换成数据库真实 Knowledge ID。
     */
    private Set<Long> resolveKnowledgeIds(
            List<Long> existingKnowledgeIds,
            List<String> newKnowledgeTempIds,
            Map<String, Long> tempKnowledgeIdMap,
            Long userId
    ) {

        Set<Long> resolvedKnowledgeIds =
                new LinkedHashSet<>();

        /*
         * 处理 AI 返回的旧 Knowledge ID。
         */
        if (existingKnowledgeIds != null) {

            for (Long knowledgeId : existingKnowledgeIds) {

                if (knowledgeId == null) {
                    continue;
                }

                KnowledgeNode knowledgeNode =
                        knowledgeNodeMapper.selectById(
                                knowledgeId
                        );

                if (knowledgeNode == null) {
                    throw new IllegalArgumentException(
                            "AI 返回结果引用的 Knowledge 不存在，id="
                                    + knowledgeId
                    );
                }

                if (!userId.equals(knowledgeNode.getUserId())) {
                    throw new IllegalArgumentException(
                            "AI 返回结果引用了其他用户的 Knowledge，id="
                                    + knowledgeId
                    );
                }

                resolvedKnowledgeIds.add(knowledgeId);
            }
        }

        /*
         * 处理 AI 返回的新 Knowledge tempId。
         */
        if (newKnowledgeTempIds != null) {

            for (String tempId : newKnowledgeTempIds) {

                if (tempId == null || tempId.isBlank()) {
                    continue;
                }

                Long knowledgeId =
                        tempKnowledgeIdMap.get(tempId);

                if (knowledgeId == null) {
                    throw new IllegalArgumentException(
                            "找不到 tempId 对应的新 Knowledge："
                                    + tempId
                    );
                }

                resolvedKnowledgeIds.add(knowledgeId);
            }
        }

        return resolvedKnowledgeIds;
    }

    /**
     * 新增 Evolution，
     * 并保存 Evolution 与 Knowledge 的关联关系。
     */
    private void saveEvolutionUpdates(
            CognitiveUpdateDTO cognitiveUpdate,
            Long userId,
            Long draftId,
            Map<String, Long> tempKnowledgeIdMap
    ) {

        if (cognitiveUpdate == null
                || cognitiveUpdate.getEvolutionUpdates() == null) {
            return;
        }

        /*
         * 表示同一个 Draft 中 Evolution 的排列顺序。
         */
        int stepOrder = 1;

        for (EvolutionResultDTO result
                : cognitiveUpdate.getEvolutionUpdates()) {

            if (result == null) {
                continue;
            }

            /*
             * 将旧 Knowledge ID 和新增 Knowledge tempId
             * 统一转换为数据库中的真实 Knowledge ID。
             */
            Set<Long> knowledgeIds = resolveKnowledgeIds(
                    result.getKnowledgeIds(),
                    result.getKnowledgeTempIds(),
                    tempKnowledgeIdMap,
                    userId
            );

            /*
             * 一条 Evolution 至少应当涉及一个 Knowledge。
             */
            if (knowledgeIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Evolution 至少需要关联一个 Knowledge"
                );
            }

            /*
             * 检查 AI 返回的演化类型是否合法。
             */
            validateEvolutionEventType(
                    result.getEventType()
            );

            /*
             * Evolution 是历史记录，
             * 因此每次都新增，不更新旧记录。
             */
            Evolution evolution = new Evolution();

            evolution.setUserId(userId);
            evolution.setSourceDraftId(draftId);
            evolution.setStepOrder(stepOrder);
            evolution.setEventType(result.getEventType());
            evolution.setTitle(result.getTitle());
            evolution.setContent(result.getContent());

            evolutionMapper.insert(evolution);

            /*
             * 将 Evolution 和涉及的 Knowledge
             * 写入 evolution_knowledge 关联表。
             */
            for (Long knowledgeId : knowledgeIds) {

                EvolutionKnowledge evolutionKnowledge =
                        new EvolutionKnowledge();

                evolutionKnowledge.setEvolutionId(
                        evolution.getId()
                );

                evolutionKnowledge.setKnowledgeId(
                        knowledgeId
                );

                evolutionKnowledgeMapper.insert(
                        evolutionKnowledge
                );
            }

            stepOrder++;
        }
    }

    /**
     * 检查第二次 AI 返回的 Evolution 类型。
     */
    private void validateEvolutionEventType(
            String eventType
    ) {

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Evolution 的 eventType 不能为空"
            );
        }

        boolean valid = switch (eventType) {
            case "NEW",
                 "EXPANDED",
                 "REVISED",
                 "CORRECTED" -> true;

            default -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "不支持的 Evolution eventType："
                            + eventType
            );
        }
    }

    static void validateDraftReferences(DraftDTO draft) {
        if (draft.getRequestId() == null || draft.getRequestId().isBlank()) {
            throw new IllegalArgumentException("Draft 缺少 request_id");
        }
        Set<String> knowledgeTemps = new LinkedHashSet<>();
        if (draft.getKnowledgePreview() != null) {
            for (KnowledgePreviewDTO item : draft.getKnowledgePreview()) {
                if (item == null || item.getTempId() == null
                        || !knowledgeTemps.add(item.getTempId())) {
                    throw new IllegalArgumentException("Draft Knowledge temp_id 无效或重复");
                }
            }
        }
        Set<String> domainTemps = new LinkedHashSet<>();
        if (draft.getDomainPreview() != null) {
            for (DomainPreviewDTO item : draft.getDomainPreview()) {
                if (item == null) continue;
                if (item.getId() == null && (item.getTempId() == null
                        || !domainTemps.add(item.getTempId()))) {
                    throw new IllegalArgumentException("Draft Domain temp_id 无效或重复");
                }
            }
        }
        if (draft.getKnowledgePreview() != null) {
            for (KnowledgePreviewDTO item : draft.getKnowledgePreview()) {
                if (item.getDomainTempIds() != null
                        && !domainTemps.containsAll(item.getDomainTempIds())) {
                    throw new IllegalArgumentException("Knowledge 引用了已取消的新 Domain");
                }
            }
        }
        if (draft.getRelationPreview() != null) {
            for (RelationPreviewDTO item : draft.getRelationPreview()) {
                if (item == null || item.getKnowledgeTempIds() == null
                        || item.getKnowledgeTempIds().size() < 2
                        || !knowledgeTemps.containsAll(item.getKnowledgeTempIds())) {
                    throw new IllegalArgumentException("Relation 引用了已删除的 Knowledge");
                }
            }
        }
        if (draft.getEvolutionPreview() != null) {
            for (EvolutionPreviewDTO item : draft.getEvolutionPreview()) {
                if (item == null || item.getKnowledgeTempIds() == null
                        || item.getKnowledgeTempIds().isEmpty()
                        || !knowledgeTemps.containsAll(item.getKnowledgeTempIds())) {
                    throw new IllegalArgumentException("Evolution 引用了已删除的 Knowledge");
                }
            }
        }
    }

    private Map<String, Object> buildPlanPreview(
            DraftDTO draft, CognitiveUpdateDTO update, Long userId
    ) {
        Map<String, Object> changes = new LinkedHashMap<>();
        List<Map<String, Object>> knowledge = new ArrayList<>();
        List<Map<String, Object>> relations = new ArrayList<>();
        List<Map<String, Object>> evolutions = new ArrayList<>();
        List<Map<String, Object>> domains = new ArrayList<>();
        Map<String, String> labels = new HashMap<>();
        Map<String, String> domainLabels = new HashMap<>();
        Set<Long> usedExistingDomainIds = new LinkedHashSet<>();
        Set<String> newDomainTemps = new LinkedHashSet<>();
        if (draft.getDomainPreview() != null) {
            for (DomainPreviewDTO item : draft.getDomainPreview()) {
                if (item == null) continue;
                if (item.getId() == null) {
                    newDomainTemps.add(item.getTempId());
                    domainLabels.put("new:" + item.getTempId(), item.getName());
                    Map<String, Object> after = new LinkedHashMap<>();
                    after.put("name", item.getName());
                    after.put("description", item.getDescription());
                    domains.add(change("CREATE", item.getTempId(), null, after));
                }
            }
        }
        if (update.getKnowledgeUpdates() != null) {
            Set<String> newTemps = new LinkedHashSet<>();
            for (KnowledgeResultDTO item : update.getKnowledgeUpdates()) {
                if (item == null) continue;
                requirePlanText(item.getTitle(), "Knowledge 标题");
                requirePlanText(item.getDescription(), "Knowledge 描述");
                KnowledgeNode old = item.getId() == null ? null
                        : knowledgeNodeMapper.selectById(item.getId());
                if (item.getId() != null && (old == null
                        || !userId.equals(old.getUserId()))) {
                    throw new IllegalArgumentException("方案更新了其他用户或不存在的 Knowledge");
                }
                if (item.getId() == null && (item.getTempId() == null
                        || !newTemps.add(item.getTempId()))) {
                    throw new IllegalArgumentException("方案新增 Knowledge temp_id 无效或重复");
                }
                if (item.getDomainTempIds() != null
                        && !newDomainTemps.containsAll(item.getDomainTempIds())) {
                    throw new IllegalArgumentException("方案使用了未确认的新 Domain");
                }
                if ((item.getDomainIds() == null || item.getDomainIds().isEmpty())
                        && (item.getDomainTempIds() == null || item.getDomainTempIds().isEmpty())) {
                    throw new IllegalArgumentException("Knowledge 至少需要一个 Domain");
                }
                if (item.getDomainIds() != null) for (Long domainId : item.getDomainIds()) {
                    Domain domain = domainService.getById(domainId);
                    if (domain == null || !userId.equals(domain.getUserId())) {
                        throw new IllegalArgumentException("方案使用了其他用户或不存在的 Domain");
                    }
                    usedExistingDomainIds.add(domainId);
                    domainLabels.put("existing:" + domainId, domain.getName());
                }
                Map<String, Object> before = null;
                if (old != null) {
                    before = new LinkedHashMap<>();
                    before.put("id", old.getId());
                    before.put("title", old.getTitle());
                    before.put("description", old.getDescription());
                    List<Long> oldDomainIds = knowledgeDomainMapper.selectList(
                            new LambdaQueryWrapper<KnowledgeDomain>()
                                    .eq(KnowledgeDomain::getKnowledgeId, old.getId()))
                            .stream().map(KnowledgeDomain::getDomainId).toList();
                    before.put("domainIds", oldDomainIds);
                    before.put("domainNames", oldDomainIds.stream()
                            .map(domainService::getById).filter(java.util.Objects::nonNull)
                            .map(Domain::getName).toList());
                }
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("title", item.getTitle());
                after.put("description", item.getDescription());
                after.put("domainIds", item.getDomainIds());
                after.put("domainTempIds", item.getDomainTempIds());
                List<String> finalDomainNames = new ArrayList<>();
                if (item.getDomainIds() != null) for (Long id : item.getDomainIds())
                    finalDomainNames.add(domainLabels.get("existing:" + id));
                if (item.getDomainTempIds() != null) for (String id : item.getDomainTempIds())
                    finalDomainNames.add(domainLabels.get("new:" + id));
                after.put("domainNames", finalDomainNames);
                knowledge.add(change(old == null ? "CREATE" : "UPDATE",
                        old == null ? item.getTempId() : "existing:" + old.getId(),
                        before, after));
                labels.put(old == null ? "new:" + item.getTempId()
                        : "existing:" + old.getId(), item.getTitle());
            }
        }
        if (update.getRelationUpdates() != null) {
            for (RelationResultDTO item : update.getRelationUpdates()) {
                if (item == null) continue;
                requirePlanText(item.getDescription(), "Relation 描述");
                KnowledgeRelation old = item.getId() == null ? null
                        : knowledgeRelationMapper.selectById(item.getId());
                if (item.getId() != null && (old == null
                        || !userId.equals(old.getUserId()))) {
                    throw new IllegalArgumentException("方案更新了其他用户或不存在的 Relation");
                }
                List<String> refs = resolvePreviewKnowledgeRefs(item.getKnowledgeIds(),
                        item.getKnowledgeTempIds(), labels, userId);
                Set<String> distinctRefs = new LinkedHashSet<>();
                if (item.getKnowledgeIds() != null) for (Long id : item.getKnowledgeIds())
                    distinctRefs.add("existing:" + id);
                if (item.getKnowledgeTempIds() != null) for (String temp : item.getKnowledgeTempIds())
                    distinctRefs.add("new:" + temp);
                if (distinctRefs.size() < 2)
                    throw new IllegalArgumentException("Relation 至少引用两个不同 Knowledge");
                Map<String, Object> before = null;
                if (old != null) {
                    before = new LinkedHashMap<>();
                    before.put("id", old.getId());
                    before.put("description", old.getDescription());
                    List<Long> oldKnowledgeIds = relationKnowledgeMapper.selectList(
                            new LambdaQueryWrapper<RelationKnowledge>()
                                    .eq(RelationKnowledge::getRelationId, old.getId()))
                            .stream().map(RelationKnowledge::getKnowledgeId).toList();
                    before.put("knowledgeIds", oldKnowledgeIds);
                    before.put("knowledgeTitles", oldKnowledgeIds.stream()
                            .map(knowledgeNodeMapper::selectById)
                            .filter(java.util.Objects::nonNull)
                            .map(KnowledgeNode::getTitle).toList());
                }
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("description", item.getDescription());
                after.put("knowledge", refs);
                after.put("knowledgeIds", item.getKnowledgeIds());
                after.put("knowledgeTempIds", item.getKnowledgeTempIds());
                relations.add(change(old == null ? "CREATE" : "UPDATE",
                        old == null ? null : "existing:" + old.getId(), before, after));
            }
        }
        if (update.getEvolutionUpdates() != null) {
            for (EvolutionResultDTO item : update.getEvolutionUpdates()) {
                if (item == null) continue;
                validateEvolutionEventType(item.getEventType());
                requirePlanText(item.getTitle(), "Evolution 标题");
                requirePlanText(item.getContent(), "Evolution 内容");
                List<String> refs = resolvePreviewKnowledgeRefs(item.getKnowledgeIds(),
                        item.getKnowledgeTempIds(), labels, userId);
                if (refs.isEmpty()) throw new IllegalArgumentException("Evolution 缺少 Knowledge 引用");
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("eventType", item.getEventType());
                after.put("title", item.getTitle());
                after.put("content", item.getContent());
                after.put("knowledge", refs);
                after.put("knowledgeIds", item.getKnowledgeIds());
                after.put("knowledgeTempIds", item.getKnowledgeTempIds());
                evolutions.add(change("CREATE", null, null, after));
            }
        }
        changes.put("knowledge", knowledge);
        changes.put("relations", relations);
        changes.put("evolutions", evolutions);
        for (Long domainId : usedExistingDomainIds) {
            Domain domain = domainService.getById(domainId);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("id", domainId);
            after.put("name", domain.getName());
            after.put("description", domain.getDescription());
            domains.add(change("USE", "existing:" + domainId, null, after));
        }
        changes.put("domains", domains);
        return changes;
    }

    private List<String> resolvePreviewKnowledgeRefs(
            List<Long> ids, List<String> temps, Map<String, String> labels, Long userId
    ) {
        List<String> resolved = new ArrayList<>();
        if (ids != null) for (Long id : ids) {
            KnowledgeNode node = knowledgeNodeMapper.selectById(id);
            if (node == null || !userId.equals(node.getUserId())) {
                throw new IllegalArgumentException("方案引用了其他用户或不存在的 Knowledge");
            }
            resolved.add(labels.getOrDefault("existing:" + id, node.getTitle()));
        }
        if (temps != null) for (String temp : temps) {
            String label = labels.get("new:" + temp);
            if (label == null) throw new IllegalArgumentException("方案引用了未创建的 Knowledge temp_id");
            resolved.add(label);
        }
        return resolved;
    }

    private void requirePlanText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }

    private Map<String, Object> change(String operation, String ref,
                                        Object before, Object after) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("operation", operation);
        value.put("ref", ref);
        value.put("before", before);
        value.put("after", after);
        return value;
    }

    private List<Map<String, Object>> buildSummaryPreview(
            Map<String, String> summaries, DraftDTO draft, Long userId
    ) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : summaries.entrySet()) {
            String ref = entry.getKey();
            String name;
            String before = null;
            if (ref.startsWith("existing:")) {
                Domain domain = domainService.getById(Long.valueOf(ref.substring(9)));
                if (domain == null || !userId.equals(domain.getUserId())) {
                    throw new IllegalArgumentException("领域摘要引用了无权使用的 Domain");
                }
                name = domain.getName();
                before = domain.getCognitiveSummary();
            } else {
                String temp = ref.substring(4);
                DomainPreviewDTO match = draft.getDomainPreview().stream()
                        .filter(d -> d != null && temp.equals(d.getTempId()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("领域摘要引用了未知 Domain"));
                name = match.getName();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("domainRef", ref);
            item.put("domainName", name);
            item.put("before", before);
            item.put("after", entry.getValue());
            list.add(item);
        }
        return list;
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("序列化方案失败", e); }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("读取方案失败", e); }
    }

    private IntegrationPlanResponse planResponse(IntegrationPlan plan) {
        Map<String, Object> changes;
        try {
            changes = objectMapper.readValue(plan.getPreviewJson(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("读取方案预览失败", e);
        }
        String visibleStatus = "READY".equals(plan.getStatus())
                && LocalDateTime.now().isAfter(plan.getExpiresAt())
                ? "EXPIRED" : plan.getStatus();
        if ("READY".equals(visibleStatus)
                && !plan.getBaselineRevision().equals(
                        cognitiveRevisionMapper.current(plan.getUserId()))) {
            visibleStatus = "STALE";
        }
        return new IntegrationPlanResponse(plan.getId(), visibleStatus,
                plan.getExpiresAt(), changes);
    }

    @Override
    public IntegrationPlanResponse getPlan(Long planId) {
        IntegrationPlan plan = integrationPlanMapper.selectById(planId);
        if (plan == null || !CurrentUser.id().equals(plan.getUserId())) {
            throw new IllegalArgumentException("方案不存在或不属于当前用户");
        }
        return planResponse(plan);
    }

    @Override
    public CommitDraftResponse confirmPlan(Long planId) {
        Long userId = CurrentUser.id();
        if (planId == null) throw new IllegalArgumentException("planId 不能为空");
        return transactionTemplate.execute(transactionStatus -> {
            IntegrationPlan plan = integrationPlanMapper.selectOwnedForUpdate(planId, userId);
            if (plan == null) throw new IllegalArgumentException("方案不存在或不属于当前用户");
            if ("APPLIED".equals(plan.getStatus())) {
                return fromJson(plan.getResultJson(), CommitDraftResponse.class);
            }
            if (!"READY".equals(plan.getStatus())) {
                throw new PlanConflictException("方案尚未就绪或已失效，请重新生成");
            }
            if (LocalDateTime.now().isAfter(plan.getExpiresAt())) {
                throw new PlanConflictException("方案已过期，请重新生成");
            }
            cognitiveRevisionMapper.ensure(userId);
            Long currentRevision = cognitiveRevisionMapper.lock(userId);
            if (currentRevision == null || !currentRevision.equals(plan.getBaselineRevision())) {
                throw new PlanConflictException("认知库已变化，方案过期，请重新生成并审核");
            }
            Long alreadyApplied = draftCommitGuardMapper.findAppliedPlanId(
                    userId, plan.getDraftRequestId());
            if (alreadyApplied != null) {
                throw new PlanConflictException("这份 Draft 已由另一份方案完成沉淀");
            }
            try {
                draftCommitGuardMapper.insert(userId, plan.getDraftRequestId(), planId);
            } catch (org.springframework.dao.DuplicateKeyException exception) {
                throw new PlanConflictException("这份 Draft 已由另一份方案完成沉淀");
            }
            Draft draft = draftMapper.selectById(plan.getDraftId());
            if (draft == null || !userId.equals(draft.getUserId())
                    || !"planned".equals(draft.getStatus())) {
                throw new PlanConflictException("方案对应的 Draft 不可执行");
            }
            DraftDTO confirmed = fromJson(plan.getConfirmedDraftJson(), DraftDTO.class);
            CognitiveUpdateDTO update = fromJson(plan.getCognitiveUpdateJson(), CognitiveUpdateDTO.class);
            Map<String, String> summaries;
            try {
                summaries = objectMapper.readValue(plan.getSummaryJson(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("读取已审核的领域摘要失败", e);
            }
            Set<Long> allowedExistingDomainIds = domainService.listByUserId(userId)
                    .stream().map(Domain::getId).collect(java.util.stream.Collectors.toSet());
            Set<String> allowedNewDomainTempIds = new LinkedHashSet<>();
            if (confirmed.getDomainPreview() != null) {
                for (DomainPreviewDTO item : confirmed.getDomainPreview()) {
                    if (item != null && item.getId() == null)
                        allowedNewDomainTempIds.add(item.getTempId());
                }
            }
            Map<String, Long> domainTempIds = saveConfirmedDomains(confirmed, userId);
            Map<String, Long> knowledgeTempIds = saveKnowledgeUpdates(update, userId);
            Set<Long> affected = saveKnowledgeDomainLinks(update, userId,
                    knowledgeTempIds, domainTempIds, allowedExistingDomainIds,
                    allowedNewDomainTempIds);
            saveRelationUpdates(update, userId, draft.getId(), knowledgeTempIds);
            saveEvolutionUpdates(update, userId, draft.getId(), knowledgeTempIds);
            for (Long domainId : affected) {
                String summaryRef = "existing:" + domainId;
                for (Map.Entry<String, Long> created : domainTempIds.entrySet()) {
                    if (domainId.equals(created.getValue())) {
                        summaryRef = "new:" + created.getKey();
                        break;
                    }
                }
                if (!summaries.containsKey(summaryRef)) {
                    throw new IllegalStateException("方案缺少受影响领域的已审核摘要");
                }
            }
            for (Map.Entry<String, String> entry : summaries.entrySet()) {
                Long domainId = entry.getKey().startsWith("existing:")
                        ? Long.valueOf(entry.getKey().substring(9))
                        : domainTempIds.get(entry.getKey().substring(4));
                Domain domain = domainService.getById(domainId);
                if (domain == null || !userId.equals(domain.getUserId())) {
                    throw new IllegalStateException("摘要领域不存在或无权更新");
                }
                domain.setCognitiveSummary(entry.getValue());
                domain.setUpdatedAt(LocalDateTime.now());
                domainService.updateById(domain);
            }
            cognitiveRevisionMapper.bump(userId);
            draft.setStatus("completed");
            draftMapper.updateById(draft);
            CommitDraftResponse response = new CommitDraftResponse(draft.getId(),
                    "completed", update);
            plan.setStatus("APPLIED");
            plan.setResultJson(toJson(response));
            integrationPlanMapper.updateById(plan);
            return response;
        });
    }
}
