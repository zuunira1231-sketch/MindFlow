package com.cogniflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cogniflow.dto.DomainCognitionResponse;
import com.cogniflow.dto.DomainKnowledgeBrief;
import com.cogniflow.dto.DomainKnowledgeItem;
import com.cogniflow.dto.DomainListItemResponse;
import com.cogniflow.dto.DomainRelationItem;
import com.cogniflow.dto.DomainRelationKnowledgeItem;
import com.cogniflow.entity.Domain;
import com.cogniflow.entity.KnowledgeDomain;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.entity.KnowledgeRelation;
import com.cogniflow.entity.RelationKnowledge;
import com.cogniflow.mapper.KnowledgeDomainMapper;
import com.cogniflow.mapper.KnowledgeNodeMapper;
import com.cogniflow.mapper.KnowledgeRelationMapper;
import com.cogniflow.mapper.RelationKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 领域页面的只读聚合查询；当前数据量较小，详情暂不分页。 */
@Service
@RequiredArgsConstructor
public class DomainCognitionQueryService {

    private final DomainService domainService;
    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final KnowledgeDomainMapper knowledgeDomainMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final RelationKnowledgeMapper relationKnowledgeMapper;

    private static final Comparator<KnowledgeNode> RECENT_FIRST =
            Comparator.comparing(DomainCognitionQueryService::activityTime,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(KnowledgeNode::getId, Comparator.reverseOrder());

    private static LocalDateTime activityTime(KnowledgeNode node) {
        return node.getUpdatedAt() != null ? node.getUpdatedAt() : node.getCreatedAt();
    }

    @Transactional(readOnly = true)
    public List<DomainListItemResponse> listDomains(Long userId) {
        List<Domain> domains = domainService.listByUserId(userId);
        if (domains.isEmpty()) return List.of();

        Set<Long> domainIds = new LinkedHashSet<>();
        for (Domain domain : domains) domainIds.add(domain.getId());
        List<KnowledgeDomain> links = knowledgeDomainMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDomain>()
                        .in(KnowledgeDomain::getDomainId, domainIds));
        Set<Long> linkedKnowledgeIds = new LinkedHashSet<>();
        for (KnowledgeDomain link : links) linkedKnowledgeIds.add(link.getKnowledgeId());

        Map<Long, KnowledgeNode> ownedKnowledge = loadOwnedKnowledge(linkedKnowledgeIds, userId);
        Map<Long, List<KnowledgeNode>> byDomain = new HashMap<>();
        for (KnowledgeDomain link : links) {
            KnowledgeNode node = ownedKnowledge.get(link.getKnowledgeId());
            if (node != null) byDomain.computeIfAbsent(link.getDomainId(), ignored -> new ArrayList<>())
                    .add(node);
        }

        List<DomainListItemResponse> response = new ArrayList<>();
        for (Domain domain : domains) {
            List<DomainKnowledgeBrief> recent = byDomain.getOrDefault(domain.getId(), List.of())
                    .stream().distinct().sorted(RECENT_FIRST).limit(4)
                    .map(node -> new DomainKnowledgeBrief(node.getId(), node.getTitle()))
                    .toList();
            response.add(new DomainListItemResponse(domain.getId(), domain.getUserId(),
                    domain.getName(), domain.getDescription(), domain.getCognitiveSummary(),
                    domain.getCreatedAt(), domain.getUpdatedAt(), recent));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public DomainCognitionResponse getCognition(Long domainId, Long userId) {
        Domain domain = domainService.getById(domainId);
        if (domain == null || !userId.equals(domain.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "领域不存在");
        }

        List<KnowledgeDomain> currentLinks = knowledgeDomainMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDomain>()
                        .eq(KnowledgeDomain::getDomainId, domainId));
        Set<Long> candidateIds = new LinkedHashSet<>();
        for (KnowledgeDomain link : currentLinks) candidateIds.add(link.getKnowledgeId());
        Map<Long, KnowledgeNode> currentNodes = loadOwnedKnowledge(candidateIds, userId);
        if (currentNodes.isEmpty()) return new DomainCognitionResponse(domain, List.of(), List.of());
        Set<Long> currentIds = currentNodes.keySet();

        Set<Long> ownedDomainIds = new HashSet<>();
        for (Domain owned : domainService.listByUserId(userId)) ownedDomainIds.add(owned.getId());
        Map<Long, Set<Long>> allDomainIds = new HashMap<>();
        for (KnowledgeDomain link : knowledgeDomainMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDomain>()
                        .in(KnowledgeDomain::getKnowledgeId, currentIds))) {
            if (ownedDomainIds.contains(link.getDomainId())) {
                allDomainIds.computeIfAbsent(link.getKnowledgeId(), ignored -> new LinkedHashSet<>())
                        .add(link.getDomainId());
            }
        }
        List<DomainKnowledgeItem> knowledge = currentNodes.values().stream()
                .sorted(RECENT_FIRST)
                .map(node -> new DomainKnowledgeItem(node.getId(), node.getTitle(),
                        node.getDescription(),
                        allDomainIds.getOrDefault(node.getId(), Set.of()).stream().sorted().toList(),
                        node.getUpdatedAt()))
                .toList();

        // 只要 Relation 至少关联当前领域的一条 Knowledge，就纳入详情。
        List<RelationKnowledge> touchingLinks = relationKnowledgeMapper.selectList(
                new LambdaQueryWrapper<RelationKnowledge>()
                        .in(RelationKnowledge::getKnowledgeId, currentIds));
        Set<Long> candidateRelationIds = new LinkedHashSet<>();
        for (RelationKnowledge link : touchingLinks) candidateRelationIds.add(link.getRelationId());
        if (candidateRelationIds.isEmpty()) {
            return new DomainCognitionResponse(domain, knowledge, List.of());
        }
        List<KnowledgeRelation> ownedRelations = knowledgeRelationMapper.selectList(
                new LambdaQueryWrapper<KnowledgeRelation>()
                        .eq(KnowledgeRelation::getUserId, userId)
                        .in(KnowledgeRelation::getId, candidateRelationIds)
                        .orderByAsc(KnowledgeRelation::getId));
        if (ownedRelations.isEmpty()) {
            return new DomainCognitionResponse(domain, knowledge, List.of());
        }
        Set<Long> relationIds = new LinkedHashSet<>();
        for (KnowledgeRelation relation : ownedRelations) relationIds.add(relation.getId());
        List<RelationKnowledge> allRelationLinks = relationKnowledgeMapper.selectList(
                new LambdaQueryWrapper<RelationKnowledge>()
                        .in(RelationKnowledge::getRelationId, relationIds));
        Set<Long> allLinkedKnowledgeIds = new LinkedHashSet<>();
        for (RelationKnowledge link : allRelationLinks) {
            allLinkedKnowledgeIds.add(link.getKnowledgeId());
        }
        Map<Long, KnowledgeNode> visibleNodes = loadOwnedKnowledge(allLinkedKnowledgeIds, userId);
        Map<Long, List<DomainRelationKnowledgeItem>> relationKnowledge = new HashMap<>();
        for (RelationKnowledge link : allRelationLinks) {
            KnowledgeNode node = visibleNodes.get(link.getKnowledgeId());
            if (node == null) continue;
            relationKnowledge.computeIfAbsent(link.getRelationId(), ignored -> new ArrayList<>())
                    .add(new DomainRelationKnowledgeItem(node.getId(), node.getTitle(),
                            currentIds.contains(node.getId())));
        }
        List<DomainRelationItem> relations = new ArrayList<>();
        for (KnowledgeRelation relation : ownedRelations) {
            List<DomainRelationKnowledgeItem> members = relationKnowledge
                    .getOrDefault(relation.getId(), List.of()).stream()
                    .distinct().sorted(Comparator.comparing(DomainRelationKnowledgeItem::id))
                    .toList();
            if (members.stream().anyMatch(DomainRelationKnowledgeItem::inCurrentDomain)) {
                relations.add(new DomainRelationItem(relation.getId(), relation.getDescription(), members));
            }
        }
        return new DomainCognitionResponse(domain, knowledge, relations);
    }

    private Map<Long, KnowledgeNode> loadOwnedKnowledge(Set<Long> ids, Long userId) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, KnowledgeNode> nodes = new LinkedHashMap<>();
        for (KnowledgeNode node : knowledgeNodeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeNode>()
                        .eq(KnowledgeNode::getUserId, userId)
                        .in(KnowledgeNode::getId, ids))) {
            nodes.put(node.getId(), node);
        }
        return nodes;
    }
}
