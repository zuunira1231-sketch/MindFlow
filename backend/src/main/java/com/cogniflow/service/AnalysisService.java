package com.cogniflow.service;

import com.cogniflow.dto.AnalyzeDraftRequest;
import com.cogniflow.dto.DraftDTO;
import com.cogniflow.dto.CommitDraftResponse;
import com.cogniflow.dto.IntegrationPlanResponse;
import com.cogniflow.dto.PrepareIntegrationPlanRequest;

public interface AnalysisService {

    /**
     * 第一阶段：分析聊天生成 Draft
     */
    DraftDTO analyzeDraft(AnalyzeDraftRequest request);

    CommitDraftResponse commitDraft(DraftDTO draftDTO);

    IntegrationPlanResponse preparePlan(PrepareIntegrationPlanRequest request);

    IntegrationPlanResponse getPlan(Long planId);

    CommitDraftResponse confirmPlan(Long planId);

}
