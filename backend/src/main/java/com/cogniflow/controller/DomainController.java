package com.cogniflow.controller;

import com.cogniflow.config.CurrentUser;
import com.cogniflow.dto.CreateDomainRequest;
import com.cogniflow.dto.DomainCognitionResponse;
import com.cogniflow.dto.DomainListItemResponse;
import com.cogniflow.dto.UpdateDomainRequest;
import com.cogniflow.entity.Domain;
import com.cogniflow.service.DomainCognitionQueryService;
import com.cogniflow.service.DomainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;
    private final DomainCognitionQueryService domainCognitionQueryService;

    /**
     * 查询当前用户已有的全部领域。
     */
    @GetMapping
    public List<DomainListItemResponse> listDomains() {
        Long userId = CurrentUser.id();

        return domainCognitionQueryService.listDomains(userId);
    }

    /** 领域完整认知：领域内知识及与之相连的跨领域 Relation。 */
    @GetMapping("/{domainId}/cognition")
    public DomainCognitionResponse getCognition(@PathVariable Long domainId) {
        return domainCognitionQueryService.getCognition(domainId, CurrentUser.id());
    }

    /**
     * 用户主动创建领域。
     */
    @PostMapping
    public Domain createDomain(
            @Valid @RequestBody CreateDomainRequest request
    ) {
        Long userId = CurrentUser.id();

        return domainService.createDomain(
                userId,
                request.getName(),
                request.getDescription()
        );
    }

    /**
     * 修改当前用户的领域名称和说明。
     */
    @PutMapping("/{domainId}")
    public Domain updateDomain(
            @PathVariable Long domainId,
            @Valid @RequestBody UpdateDomainRequest request
    ) {
        Long userId = CurrentUser.id();

        return domainService.updateDomain(
                domainId,
                userId,
                request.getName(),
                request.getDescription()
        );
    }

    /**
     * 删除尚未关联 Knowledge 的空领域。
     */
    @DeleteMapping("/{domainId}")
    public void deleteDomain(
            @PathVariable Long domainId
    ) {
        Long userId = CurrentUser.id();

        domainService.deleteDomain(domainId, userId);
    }
}
