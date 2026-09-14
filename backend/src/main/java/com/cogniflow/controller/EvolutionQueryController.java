package com.cogniflow.controller;

import com.cogniflow.config.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cogniflow.entity.Domain;
import com.cogniflow.entity.Evolution;
import com.cogniflow.entity.EvolutionKnowledge;
import com.cogniflow.entity.KnowledgeDomain;
import com.cogniflow.entity.KnowledgeNode;
import com.cogniflow.mapper.DomainMapper;
import com.cogniflow.mapper.EvolutionKnowledgeMapper;
import com.cogniflow.mapper.EvolutionMapper;
import com.cogniflow.mapper.KnowledgeDomainMapper;
import com.cogniflow.mapper.KnowledgeNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** v1.0 全局及领域演化记录，只读。 */
@RestController
@RequestMapping("/evolutions")
@RequiredArgsConstructor
public class EvolutionQueryController {

    private final EvolutionMapper evolutionMapper;
    private final EvolutionKnowledgeMapper evolutionKnowledgeMapper;
    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final KnowledgeDomainMapper knowledgeDomainMapper;
    private final DomainMapper domainMapper;

    public record KnowledgeBrief(Long id, String title) {}
    public record DomainBrief(Long id, String name) {}
    public record EvolutionItem(Long id, String title, String content, String eventType,
                                LocalDateTime createdAt, Integer stepOrder,
                                List<KnowledgeBrief> knowledge, List<DomainBrief> domains) {}
    public record EvolutionPage(List<EvolutionItem> items, int page, int size, boolean hasMore) {}

    @GetMapping
    @Transactional(readOnly = true)
    public EvolutionPage list(@RequestParam(required = false) Long domainId,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(defaultValue = "desc") String order) {
        Long userId = CurrentUser.id();
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page 必须大于 0，size 必须在 1 到 100 之间");
        }
        if (!"asc".equals(order) && !"desc".equals(order)) {
            throw new IllegalArgumentException("order 只能是 asc 或 desc");
        }
        if (domainId != null) {
            Domain domain = domainMapper.selectById(domainId);
            if (domain == null || !userId.equals(domain.getUserId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "领域不存在");
            }
        }

        List<Evolution> fetched = evolutionMapper.selectHistoryPage(userId, domainId,
                "asc".equals(order), size + 1, ((long) page - 1) * size);
        boolean hasMore = fetched.size() > size;
        List<Evolution> selected = hasMore ? fetched.subList(0, size) : fetched;
        if (selected.isEmpty()) return new EvolutionPage(List.of(), page, size, false);

        Set<Long> evolutionIds = new LinkedHashSet<>();
        for (Evolution item : selected) evolutionIds.add(item.getId());
        List<EvolutionKnowledge> links = evolutionKnowledgeMapper.selectList(
                new LambdaQueryWrapper<EvolutionKnowledge>()
                        .in(EvolutionKnowledge::getEvolutionId, evolutionIds));
        Set<Long> knowledgeIds = new LinkedHashSet<>();
        for (EvolutionKnowledge link : links) knowledgeIds.add(link.getKnowledgeId());
        Map<Long, KnowledgeNode> ownedKnowledge = new HashMap<>();
        if (!knowledgeIds.isEmpty()) {
            for (KnowledgeNode node : knowledgeNodeMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeNode>()
                            .eq(KnowledgeNode::getUserId, userId)
                            .in(KnowledgeNode::getId, knowledgeIds))) {
                ownedKnowledge.put(node.getId(), node);
            }
        }

        Map<Long, Set<Long>> knowledgeDomains = new HashMap<>();
        Set<Long> domainIds = new LinkedHashSet<>();
        if (!ownedKnowledge.isEmpty()) {
            for (KnowledgeDomain link : knowledgeDomainMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDomain>()
                            .in(KnowledgeDomain::getKnowledgeId, ownedKnowledge.keySet()))) {
                knowledgeDomains.computeIfAbsent(link.getKnowledgeId(), ignored -> new LinkedHashSet<>())
                        .add(link.getDomainId());
                domainIds.add(link.getDomainId());
            }
        }
        Map<Long, Domain> ownedDomains = new HashMap<>();
        if (!domainIds.isEmpty()) {
            for (Domain domain : domainMapper.selectList(new LambdaQueryWrapper<Domain>()
                    .eq(Domain::getUserId, userId).in(Domain::getId, domainIds))) {
                ownedDomains.put(domain.getId(), domain);
            }
        }

        Map<Long, Set<Long>> members = new LinkedHashMap<>();
        for (EvolutionKnowledge link : links) {
            if (ownedKnowledge.containsKey(link.getKnowledgeId())) {
                members.computeIfAbsent(link.getEvolutionId(), ignored -> new LinkedHashSet<>())
                        .add(link.getKnowledgeId());
            }
        }
        List<EvolutionItem> items = new ArrayList<>();
        for (Evolution evolution : selected) {
            List<Long> linkedIds = members.getOrDefault(evolution.getId(), Set.of()).stream()
                    .sorted().toList();
            List<KnowledgeBrief> knowledge = linkedIds.stream()
                    .map(id -> new KnowledgeBrief(id, ownedKnowledge.get(id).getTitle())).toList();
            Set<Long> relatedDomainIds = new LinkedHashSet<>();
            for (Long id : linkedIds) {
                relatedDomainIds.addAll(knowledgeDomains.getOrDefault(id, Set.of()));
            }
            List<DomainBrief> domains = relatedDomainIds.stream()
                    .filter(ownedDomains::containsKey).sorted(Comparator.naturalOrder())
                    .map(id -> new DomainBrief(id, ownedDomains.get(id).getName())).toList();
            items.add(new EvolutionItem(evolution.getId(), evolution.getTitle(),
                    evolution.getContent(), evolution.getEventType(), evolution.getCreatedAt(),
                    evolution.getStepOrder(), knowledge, domains));
        }
        return new EvolutionPage(items, page, size, hasMore);
    }
}
