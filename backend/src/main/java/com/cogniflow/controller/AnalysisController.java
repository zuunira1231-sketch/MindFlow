package com.cogniflow.controller;

import com.cogniflow.dto.AnalyzeDraftRequest;
import com.cogniflow.dto.DraftDTO;
import com.cogniflow.service.AnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cogniflow.dto.CommitDraftResponse;
import com.cogniflow.dto.IntegrationPlanResponse;
import com.cogniflow.dto.PrepareIntegrationPlanRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    // 构造器注入（推荐）
    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/draft")
    public DraftDTO analyzeDraft(
            @Valid @RequestBody AnalyzeDraftRequest request
    ) {
        return analysisService.analyzeDraft(request);
    }

    @PostMapping("/draft/commit")
    public CommitDraftResponse commitDraft(
            @Valid @RequestBody DraftDTO draftDTO
    ) {
        throw new ResponseStatusException(HttpStatus.GONE,
                "旧接口已停用：请先生成最终方案，再由用户确认执行");
    }

    @PostMapping("/draft/plans")
    public IntegrationPlanResponse preparePlan(
            @Valid @RequestBody PrepareIntegrationPlanRequest request
    ) {
        return analysisService.preparePlan(request);
    }

    @GetMapping("/draft/plans/{planId}")
    public IntegrationPlanResponse getPlan(@PathVariable Long planId) {
        return analysisService.getPlan(planId);
    }

    @PostMapping("/draft/plans/{planId}/confirm")
    public CommitDraftResponse confirmPlan(@PathVariable Long planId) {
        return analysisService.confirmPlan(planId);
    }
}
