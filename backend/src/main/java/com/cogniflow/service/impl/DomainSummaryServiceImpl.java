package com.cogniflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cogniflow.client.AIClient;
import com.cogniflow.dto.DomainSummaryContextDTO;
import com.cogniflow.dto.DomainSummaryRequest;
import com.cogniflow.dto.DomainSummaryResponse;
import com.cogniflow.dto.DomainSummaryResultDTO;
import com.cogniflow.dto.CognitiveUpdateDTO;
import com.cogniflow.dto.DraftDTO;
import com.cogniflow.dto.DomainPreviewDTO;
import com.cogniflow.dto.KnowledgeResultDTO;
import com.cogniflow.dto.RelationResultDTO;
import com.cogniflow.entity.Domain;
import com.cogniflow.entity.KnowledgeDomain;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.entity.KnowledgeRelation;
import com.cogniflow.entity.RelationKnowledge;
import com.cogniflow.mapper.DomainMapper;
import com.cogniflow.mapper.KnowledgeDomainMapper;
import com.cogniflow.mapper.KnowledgeNodeMapper;
import com.cogniflow.mapper.KnowledgeRelationMapper;
import com.cogniflow.mapper.RelationKnowledgeMapper;
import com.cogniflow.service.DomainSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class DomainSummaryServiceImpl
        implements DomainSummaryService {

    private final AIClient aiClient;

    private final DomainMapper domainMapper;
    private final KnowledgeDomainMapper knowledgeDomainMapper;
    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final RelationKnowledgeMapper relationKnowledgeMapper;

    private final TransactionTemplate transactionTemplate;

    @Override
    public Map<String, String> previewSummaries(
            DraftDTO draft, CognitiveUpdateDTO update, Long userId
    ) {
        Map<String, Domain> domains = new LinkedHashMap<>();
        for (Domain domain : domainMapper.selectList(
                new LambdaQueryWrapper<Domain>().eq(Domain::getUserId, userId))) {
            domains.put("existing:" + domain.getId(), domain);
        }
        Set<String> names = new HashSet<>();
        for (Domain domain : domains.values()) names.add(domain.getName());
        if (draft.getDomainPreview() != null) {
            for (DomainPreviewDTO preview : draft.getDomainPreview()) {
                if (preview == null || preview.getId() != null) continue;
                if (preview.getTempId() == null || preview.getTempId().isBlank()
                        || preview.getName() == null || preview.getName().isBlank()) {
                    throw new IllegalArgumentException("新领域缺少名称或 tempId");
                }
                if (!names.add(preview.getName().trim())) {
                    throw new IllegalArgumentException("新领域与已有领域重名，请选择已有领域");
                }
                Domain virtual = new Domain();
                virtual.setName(preview.getName().trim());
                virtual.setDescription(preview.getDescription());
                domains.put("new:" + preview.getTempId(), virtual);
            }
        }

        Map<Long, KnowledgeNode> nodes = new LinkedHashMap<>();
        for (KnowledgeNode node : knowledgeNodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeNode>().eq(KnowledgeNode::getUserId, userId))) {
            nodes.put(node.getId(), node);
        }
        Map<Long, Set<String>> membership = new HashMap<>();
        if (!nodes.isEmpty()) {
            for (KnowledgeDomain link : knowledgeDomainMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDomain>()
                            .in(KnowledgeDomain::getKnowledgeId, nodes.keySet()))) {
                String ref = "existing:" + link.getDomainId();
                if (!domains.containsKey(ref)) {
                    throw new IllegalArgumentException("Knowledge 关联了其他用户或不存在的领域");
                }
                membership.computeIfAbsent(link.getKnowledgeId(), k -> new LinkedHashSet<>())
                        .add(ref);
            }
        }
        Set<String> affected = new LinkedHashSet<>();
        Map<String, Long> temporaryKnowledgeIds = new HashMap<>();
        long nextVirtualId = -1;
        if (update.getKnowledgeUpdates() != null) {
            for (KnowledgeResultDTO item : update.getKnowledgeUpdates()) {
                if (item == null) continue;
                Long id = item.getId();
                if (id != null && !nodes.containsKey(id)) {
                    throw new IllegalArgumentException("方案引用了其他用户或不存在的 Knowledge");
                }
                if (id == null) {
                    if (item.getTempId() == null || item.getTempId().isBlank()
                            || temporaryKnowledgeIds.containsKey(item.getTempId())) {
                        throw new IllegalArgumentException("新增 Knowledge 的 tempId 无效或重复");
                    }
                    id = nextVirtualId--;
                    temporaryKnowledgeIds.put(item.getTempId(), id);
                }
                affected.addAll(membership.getOrDefault(id, Set.of()));
                Set<String> finalDomains = new LinkedHashSet<>();
                if (item.getDomainIds() != null) {
                    for (Long domainId : item.getDomainIds()) {
                        String ref = "existing:" + domainId;
                        if (!domains.containsKey(ref)) throw new IllegalArgumentException("方案引用了无权使用的领域");
                        finalDomains.add(ref);
                    }
                }
                if (item.getDomainTempIds() != null) {
                    for (String tempId : item.getDomainTempIds()) {
                        String ref = "new:" + tempId;
                        if (!domains.containsKey(ref)) throw new IllegalArgumentException("方案引用了未确认的新领域");
                        finalDomains.add(ref);
                    }
                }
                if (finalDomains.isEmpty()) throw new IllegalArgumentException("Knowledge 至少需要一个领域");
                affected.addAll(finalDomains);
                membership.put(id, finalDomains);
                KnowledgeNode virtual = new KnowledgeNode();
                virtual.setId(id);
                virtual.setUserId(userId);
                virtual.setTitle(item.getTitle());
                virtual.setDescription(item.getDescription());
                nodes.put(id, virtual);
            }
        }
        for (String ref : domains.keySet()) if (ref.startsWith("new:")) affected.add(ref);

        Map<Long, KnowledgeRelation> relations = new LinkedHashMap<>();
        for (KnowledgeRelation relation : knowledgeRelationMapper.selectList(
                new LambdaQueryWrapper<KnowledgeRelation>()
                        .eq(KnowledgeRelation::getUserId, userId))) {
            relations.put(relation.getId(), relation);
        }
        Map<Long, Set<Long>> relationMembers = new HashMap<>();
        if (!relations.isEmpty()) {
            for (RelationKnowledge link : relationKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<RelationKnowledge>()
                            .in(RelationKnowledge::getRelationId, relations.keySet()))) {
                relationMembers.computeIfAbsent(link.getRelationId(), k -> new LinkedHashSet<>())
                        .add(link.getKnowledgeId());
            }
        }
        long nextVirtualRelationId = -1;
        if (update.getRelationUpdates() != null) {
            for (RelationResultDTO item : update.getRelationUpdates()) {
                if (item == null) continue;
                Long id = item.getId();
                if (id != null && !relations.containsKey(id)) {
                    throw new IllegalArgumentException("方案引用了其他用户或不存在的 Relation");
                }
                if (id != null) for (Long k : relationMembers.getOrDefault(id, Set.of()))
                    affected.addAll(membership.getOrDefault(k, Set.of()));
                if (id == null) id = nextVirtualRelationId--;
                Set<Long> members = new LinkedHashSet<>();
                if (item.getKnowledgeIds() != null) members.addAll(item.getKnowledgeIds());
                if (item.getKnowledgeTempIds() != null) {
                    for (String tempId : item.getKnowledgeTempIds()) {
                        Long resolved = temporaryKnowledgeIds.get(tempId);
                        if (resolved == null) throw new IllegalArgumentException("Relation 引用了不存在的新 Knowledge");
                        members.add(resolved);
                    }
                }
                if (members.size() < 2 || !nodes.keySet().containsAll(members)) {
                    throw new IllegalArgumentException("Relation 的 Knowledge 引用无效");
                }
                for (Long k : members) affected.addAll(membership.getOrDefault(k, Set.of()));
                KnowledgeRelation virtual = new KnowledgeRelation();
                virtual.setId(id);
                virtual.setUserId(userId);
                virtual.setDescription(item.getDescription());
                relations.put(id, virtual);
                relationMembers.put(id, members);
            }
        }

        List<Map<String, Object>> contexts = new ArrayList<>();
        for (String ref : affected) {
            Domain domain = domains.get(ref);
            if (domain == null) continue;
            Set<Long> memberIds = new LinkedHashSet<>();
            List<Map<String, Object>> finalKnowledge = new ArrayList<>();
            for (Map.Entry<Long, KnowledgeNode> entry : nodes.entrySet()) {
                if (!membership.getOrDefault(entry.getKey(), Set.of()).contains(ref)) continue;
                memberIds.add(entry.getKey());
                finalKnowledge.add(Map.of("title", java.util.Objects.toString(entry.getValue().getTitle(), ""),
                        "description", java.util.Objects.toString(entry.getValue().getDescription(), "")));
            }
            List<Map<String, Object>> finalRelations = new ArrayList<>();
            for (Map.Entry<Long, KnowledgeRelation> entry : relations.entrySet()) {
                Set<Long> linked = relationMembers.getOrDefault(entry.getKey(), Set.of());
                if (linked.stream().noneMatch(memberIds::contains)) continue;
                List<String> titles = linked.stream().filter(nodes::containsKey)
                        .map(k -> nodes.get(k).getTitle()).toList();
                finalRelations.add(Map.of("description", java.util.Objects.toString(entry.getValue().getDescription(), ""),
                        "knowledgeTitles", titles));
            }
            contexts.add(Map.of("domainRef", ref, "domainName", domain.getName(),
                    "knowledge", finalKnowledge, "relations", finalRelations));
        }
        return contexts.isEmpty() ? Map.of() : aiClient.previewDomainSummaries(contexts);
    }

    @Override
    public void refreshSummaries(
            Set<Long> domainIds,
            Long userId
    ) {
        if (domainIds == null || domainIds.isEmpty()) {
            return;
        }

        /*
         * 为每一个受影响的 Domain
         * 准备当前完整的知识上下文。
         */
        List<DomainSummaryContextDTO> contexts =
                new ArrayList<>();

        for (Long domainId : domainIds) {

            if (domainId == null) {
                continue;
            }

            Domain domain =
                    domainMapper.selectById(domainId);

            if (domain == null) {
                throw new IllegalArgumentException(
                        "需要更新摘要的 Domain 不存在，id="
                                + domainId
                );
            }

            if (!userId.equals(domain.getUserId())) {
                throw new IllegalArgumentException(
                        "无权更新该 Domain 的摘要，id="
                                + domainId
                );
            }

            DomainSummaryContextDTO context =
                    buildDomainContext(
                            domain,
                            userId
                    );

            contexts.add(context);
        }

        if (contexts.isEmpty()) {
            return;
        }

        DomainSummaryRequest request =
                new DomainSummaryRequest();

        request.setDomainContexts(contexts);

        /*
         * AI 调用发生在数据库事务外。
         */
        DomainSummaryResponse response =
                aiClient.summarizeDomains(request);

        /*
         * 在写数据库前校验 AI 返回结果。
         */
        validateResponse(
                response,
                domainIds,
                userId
        );

        /*
         * AI 调用成功后，再开启一个短事务更新摘要。
         */
        transactionTemplate.executeWithoutResult(
                transactionStatus -> {

                    for (DomainSummaryResultDTO result
                            : response.getDomainSummaries()) {

                        Domain domain =
                                domainMapper.selectById(
                                        result.getDomainId()
                                );

                        /*
                         * validateResponse 已经检查过，
                         * 这里再次查询是为了取得待更新实体。
                         */
                        domain.setCognitiveSummary(
                                result.getCognitiveSummary()
                        );

                        domainMapper.updateById(domain);
                    }
                }
        );
    }

    /**
     * 查询一个 Domain 当前完整的
     * Knowledge、Relation 和关联记录。
     */
    private DomainSummaryContextDTO buildDomainContext(
            Domain domain,
            Long userId
    ) {
        DomainSummaryContextDTO context =
                new DomainSummaryContextDTO();

        context.setDomain(domain);

        /*
         * Domain ID
         * → knowledge_domain
         * → Knowledge ID
         */
        List<KnowledgeDomain> knowledgeDomainLinks =
                knowledgeDomainMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDomain>()
                                .eq(
                                        KnowledgeDomain::getDomainId,
                                        domain.getId()
                                )
                );

        List<Long> knowledgeIds =
                knowledgeDomainLinks.stream()
                        .map(KnowledgeDomain::getKnowledgeId)
                        .distinct()
                        .toList();

        if (knowledgeIds.isEmpty()) {
            context.setKnowledge(List.of());
            context.setRelations(List.of());
            context.setRelationLinks(List.of());

            return context;
        }

        /*
         * 查询该 Domain 当前全部 Knowledge。
         */
        List<KnowledgeNode> knowledge =
                knowledgeNodeMapper.selectBatchIds(
                        knowledgeIds
                );

        /*
         * 防止错误关联导致其他用户的 Knowledge
         * 被发送给 AI。
         */
        for (KnowledgeNode knowledgeNode : knowledge) {
            if (!userId.equals(knowledgeNode.getUserId())) {
                throw new IllegalArgumentException(
                        "Domain 关联了其他用户的 Knowledge，id="
                                + knowledgeNode.getId()
                );
            }
        }

        /*
         * Knowledge ID
         * → relation_knowledge
         * → Relation ID
         */
        List<RelationKnowledge> relationLinks =
                relationKnowledgeMapper.selectList(
                        new LambdaQueryWrapper<RelationKnowledge>()
                                .in(
                                        RelationKnowledge::getKnowledgeId,
                                        knowledgeIds
                                )
                );

        List<Long> relationIds =
                relationLinks.stream()
                        .map(RelationKnowledge::getRelationId)
                        .distinct()
                        .toList();

        List<KnowledgeRelation> relations;

        if (relationIds.isEmpty()) {
            relations = List.of();
        } else {
            relations =
                    knowledgeRelationMapper.selectBatchIds(
                            relationIds
                    );

            for (KnowledgeRelation relation : relations) {
                if (!userId.equals(relation.getUserId())) {
                    throw new IllegalArgumentException(
                            "Domain 上下文引用了其他用户的 Relation，id="
                                    + relation.getId()
                    );
                }
            }
        }

        context.setKnowledge(knowledge);
        context.setRelations(relations);
        context.setRelationLinks(relationLinks);

        return context;
    }

    /**
     * 校验领域摘要 AI 的返回结果。
     */
    private void validateResponse(
            DomainSummaryResponse response,
            Set<Long> requestedDomainIds,
            Long userId
    ) {
        if (response == null
                || response.getDomainSummaries() == null) {
            throw new IllegalStateException(
                    "领域摘要 AI 没有返回有效结果"
            );
        }

        Set<Long> returnedDomainIds =
                new HashSet<>();

        for (DomainSummaryResultDTO result
                : response.getDomainSummaries()) {

            if (result == null
                    || result.getDomainId() == null) {
                throw new IllegalStateException(
                        "领域摘要 AI 返回了无效的 Domain ID"
                );
            }

            Long domainId = result.getDomainId();

            /*
             * AI 不能返回本次没有请求更新的 Domain。
             */
            if (!requestedDomainIds.contains(domainId)) {
                throw new IllegalArgumentException(
                        "领域摘要 AI 返回了未请求的 Domain，id="
                                + domainId
                );
            }

            /*
             * 同一个 Domain 不能返回两次。
             */
            if (!returnedDomainIds.add(domainId)) {
                throw new IllegalArgumentException(
                        "领域摘要 AI 重复返回 Domain，id="
                                + domainId
                );
            }

            Domain domain =
                    domainMapper.selectById(domainId);

            if (domain == null) {
                throw new IllegalArgumentException(
                        "领域摘要引用的 Domain 不存在，id="
                                + domainId
                );
            }

            if (!userId.equals(domain.getUserId())) {
                throw new IllegalArgumentException(
                        "领域摘要引用了其他用户的 Domain，id="
                                + domainId
                );
            }

            if (result.getCognitiveSummary() == null) {
                throw new IllegalArgumentException(
                        "领域认知摘要不能为 null，domainId="
                                + domainId
                );
            }
        }

        /*
         * 请求更新的每个 Domain 都必须被返回。
         */
        if (!returnedDomainIds.equals(requestedDomainIds)) {
            throw new IllegalStateException(
                    "领域摘要 AI 没有返回全部 Domain 的摘要"
            );
        }
    }
}
