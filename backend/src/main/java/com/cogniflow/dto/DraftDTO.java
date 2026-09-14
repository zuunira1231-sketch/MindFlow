package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DraftDTO {

        /**
         * 本次 Draft 的标题。
         */
        @NotBlank(message = "Draft 标题不能为空")
        private String title;

        /**
         * 本次聊天内容的总结。
         */
        @NotBlank(message = "Draft 摘要不能为空")
        private String summary;

        /**
         * 用户输入的原始聊天内容。
         */
        @NotBlank(message = "原始聊天内容不能为空")
        private String sourceContent;

        /**
         * 本 Draft 来源的完整对话 ID。
         */
        @JsonProperty("conversation_id")
        private Long conversationId;

        /**
         * 后端为本次 Draft 生成的唯一请求 ID。
         *
         * 用于防止同一个 Draft 被重复提交。
         */
        @NotBlank(message = "Draft 请求 ID 不能为空")
        @JsonProperty("request_id")
        private String requestId;

        /**
         * 第一次 AI 提取的 Knowledge 预览。
         */
        @Valid
        @NotNull(message = "knowledge_preview 不能为空")
        @JsonProperty("knowledge_preview")
        private List<KnowledgePreviewDTO> knowledgePreview;

        /**
         * 第一次 AI 提取的 Relation 预览。
         */
        @Valid
        @NotNull(message = "relation_preview 不能为空")
        @JsonProperty("relation_preview")
        private List<RelationPreviewDTO> relationPreview;

        /**
         * 第一次 AI 提取的 Evolution 预览。
         */
        @Valid
        @NotNull(message = "evolution_preview 不能为空")
        @JsonProperty("evolution_preview")
        private List<EvolutionPreviewDTO> evolutionPreview;

        @Valid
        @JsonProperty("domain_preview")
        private List<DomainPreviewDTO> domainPreview;
}
