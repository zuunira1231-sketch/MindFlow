package com.cogniflow.service;

import com.cogniflow.dto.DraftDTO;
import com.cogniflow.dto.CognitiveUpdateDTO;
import java.util.Map;
import java.util.Set;

public interface DomainSummaryService {

    /**
     * 重新生成并更新指定 Domain 的整体认知摘要。
     */
    void refreshSummaries(
            Set<Long> domainIds,
            Long userId
    );

    Map<String, String> previewSummaries(
            DraftDTO draft, CognitiveUpdateDTO update, Long userId
    );
}
