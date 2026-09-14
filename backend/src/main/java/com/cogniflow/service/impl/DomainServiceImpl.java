package com.cogniflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cogniflow.entity.Domain;
import com.cogniflow.entity.KnowledgeDomain;
import com.cogniflow.mapper.DomainMapper;
import com.cogniflow.mapper.KnowledgeDomainMapper;
import com.cogniflow.mapper.CognitiveRevisionMapper;
import com.cogniflow.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DomainServiceImpl
        extends ServiceImpl<DomainMapper, Domain>
        implements DomainService {

    private final KnowledgeDomainMapper knowledgeDomainMapper;
    private final CognitiveRevisionMapper cognitiveRevisionMapper;

    @Override
    public List<Domain> listByUserId(Long userId) {
        return list(
                new LambdaQueryWrapper<Domain>()
                        .eq(Domain::getUserId, userId)
                        .orderByAsc(Domain::getId)
        );
    }

    @Override
    @Transactional
    public Domain createDomain(
            Long userId,
            String name,
            String description
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("领域名称不能为空");
        }

        cognitiveRevisionMapper.ensure(userId);
        cognitiveRevisionMapper.lock(userId);

        String normalizedName = name.trim();

        Domain existingDomain = getOne(
                new LambdaQueryWrapper<Domain>()
                        .eq(Domain::getUserId, userId)
                        .eq(Domain::getName, normalizedName)
                        .last("LIMIT 1")
        );

        if (existingDomain != null) {
            return existingDomain;
        }

        LocalDateTime now = LocalDateTime.now();

        Domain domain = new Domain();
        domain.setUserId(userId);
        domain.setName(normalizedName);
        domain.setDescription(
                StringUtils.hasText(description)
                        ? description.trim()
                        : null
        );
        domain.setCognitiveSummary(null);
        domain.setCreatedAt(now);
        domain.setUpdatedAt(now);

        save(domain);
        cognitiveRevisionMapper.bump(userId);

        return domain;
    }

    @Override
    @Transactional
    public Domain updateDomain(
            Long domainId,
            Long userId,
            String name,
            String description
    ) {
        cognitiveRevisionMapper.ensure(userId);
        cognitiveRevisionMapper.lock(userId);
        Domain domain = requireOwnedDomain(domainId, userId);

        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("领域名称不能为空");
        }

        String normalizedName = name.trim();

        Long duplicateCount = count(
                new LambdaQueryWrapper<Domain>()
                        .eq(Domain::getUserId, userId)
                        .eq(Domain::getName, normalizedName)
                        .ne(Domain::getId, domainId)
        );

        if (duplicateCount > 0) {
            throw new IllegalArgumentException("已经存在同名领域");
        }

        domain.setName(normalizedName);
        domain.setDescription(
                StringUtils.hasText(description)
                        ? description.trim()
                        : null
        );
        domain.setUpdatedAt(LocalDateTime.now());

        updateById(domain);
        cognitiveRevisionMapper.bump(userId);
        return domain;
    }

    @Override
    @Transactional
    public void deleteDomain(Long domainId, Long userId) {
        cognitiveRevisionMapper.ensure(userId);
        cognitiveRevisionMapper.lock(userId);
        requireOwnedDomain(domainId, userId);

        Long linkCount = knowledgeDomainMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDomain>()
                        .eq(KnowledgeDomain::getDomainId, domainId)
        );

        if (linkCount > 0) {
            throw new IllegalArgumentException(
                    "该领域仍关联 Knowledge，不能直接删除"
            );
        }

        removeById(domainId);
        cognitiveRevisionMapper.bump(userId);
    }

    private Domain requireOwnedDomain(Long domainId, Long userId) {
        if (domainId == null) {
            throw new IllegalArgumentException("domainId 不能为空");
        }

        Domain domain = getById(domainId);

        if (domain == null) {
            throw new IllegalArgumentException(
                    "Domain 不存在，id=" + domainId
            );
        }

        if (!userId.equals(domain.getUserId())) {
            throw new IllegalArgumentException(
                    "无权操作该 Domain，id=" + domainId
            );
        }

        return domain;
    }
}
