package com.cogniflow.client;

import com.cogniflow.config.AIConfig;
import com.cogniflow.dto.ChatMessage;
import com.cogniflow.dto.ChatRequest;
import com.cogniflow.dto.CognitiveUpdateDTO;
import com.cogniflow.dto.DomainSummaryRequest;
import com.cogniflow.dto.DomainSummaryResponse;
import com.cogniflow.dto.DraftDTO;
import com.cogniflow.dto.ResponseFormat;
import com.cogniflow.dto.SecondAIRequest;
import com.cogniflow.entity.Domain;
import com.cogniflow.prompt.AnalyzeDraftPrompt;
import com.cogniflow.prompt.DomainSummaryPrompt;
import com.cogniflow.prompt.IntegrateKnowledgePrompt;
import com.cogniflow.prompt.MergeDraftPrompt;
import com.cogniflow.schema.AnalyzeDraftSchema;
import com.cogniflow.schema.DomainSummarySchema;
import com.cogniflow.schema.IntegrateKnowledgeSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class AIClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AIConfig aiConfig;

    public AIClient(
            ObjectMapper objectMapper,
            AIConfig aiConfig,
            RestClient.Builder restClientBuilder
    ) {
        this.objectMapper = objectMapper;
        this.aiConfig = aiConfig;

        this.restClient = restClientBuilder
                .baseUrl(aiConfig.getBaseUrl())
                .defaultHeader(
                        "Authorization",
                        "Bearer " + aiConfig.getApiKey()
                )
                .build();
    }

    /**
     * 第一阶段：分析聊天生成 Draft
     */
    public DraftDTO analyzeDraft(
            String chatContent,
            List<Domain> existingDomains
    ) {

        try {

            String existingDomainsJson =
                    objectMapper.writeValueAsString(existingDomains);

            String prompt = AnalyzeDraftPrompt.SYSTEM_PROMPT
                    + "\n\n用户已有的认知领域：\n"
                    + existingDomainsJson
                    + "\n\n聊天记录：\n"
                    + chatContent
                    + "\n\nJSON Schema：\n"
                    + AnalyzeDraftSchema.JSON_SCHEMA;

            ResponseFormat responseFormat = new ResponseFormat();
            responseFormat.setType("json_object");

            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setRole("system");
            systemMessage.setContent(
                    "请严格按照指定的 JSON 结构输出。"
            );

            ChatMessage userMessage = new ChatMessage();
            userMessage.setRole("user");
            userMessage.setContent(prompt);

            ChatRequest request = new ChatRequest();
            request.setModel(aiConfig.getModel());
            request.setTemperature(0.2);
            request.setResponseFormat(responseFormat);
            request.setMessages(
                    List.of(systemMessage, userMessage)
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return parseDraftResponse(response);

        } catch (Exception e) {
            throw new RuntimeException(
                    "第一次 AI 分析失败：" + e.getMessage(),
                    e
            );
        }
    }

    /**
     * 合并长对话相邻两段的 Draft 预览。
     */
    public DraftDTO mergeDrafts(
            DraftDTO first,
            DraftDTO second,
            List<Domain> existingDomains
    ) {
        try {
            String prompt = MergeDraftPrompt.SYSTEM_PROMPT
                    + "\n\n用户已有领域：\n"
                    + objectMapper.writeValueAsString(existingDomains)
                    + "\n\n第一段 Draft：\n"
                    + objectMapper.writeValueAsString(first)
                    + "\n\n第二段 Draft：\n"
                    + objectMapper.writeValueAsString(second)
                    + "\n\n必须遵守的 JSON 结构：\n"
                    + AnalyzeDraftSchema.JSON_SCHEMA;

            ResponseFormat responseFormat = new ResponseFormat();
            responseFormat.setType("json_object");

            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setRole("system");
            systemMessage.setContent("请严格输出一个完整的 JSON Draft。");

            ChatMessage userMessage = new ChatMessage();
            userMessage.setRole("user");
            userMessage.setContent(prompt);

            ChatRequest request = new ChatRequest();
            request.setModel(aiConfig.getModel());
            request.setTemperature(0.1);
            request.setResponseFormat(responseFormat);
            request.setMessages(List.of(systemMessage, userMessage));

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return parseDraftResponse(response);
        } catch (Exception exception) {
            throw new RuntimeException(
                    "长对话 Draft 合并失败：" + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * 第二次 AI：
     * 比较本次新认知和数据库中的旧认知，
     * 返回需要执行的知识库更新方案。
     */
    public CognitiveUpdateDTO integrateKnowledge(
            SecondAIRequest secondAIRequest
    ) {

        try {

            JsonNode cognitionNode =
                    objectMapper.valueToTree(secondAIRequest);

            JsonNode confirmedDraftNode =
                    cognitionNode.get("confirmedDraft");

            if (confirmedDraftNode
                    instanceof ObjectNode confirmedDraftObject) {

                confirmedDraftObject.remove("sourceContent");
                confirmedDraftObject.remove("source_content");

                confirmedDraftObject.remove("requestId");
                confirmedDraftObject.remove("request_id");
            }

            String cognitionData =
                    objectMapper.writeValueAsString(cognitionNode);

            String prompt = IntegrateKnowledgePrompt.SYSTEM_PROMPT
                    + "\n\n需要整理的新旧认知数据：\n"
                    + cognitionData
                    + "\n\n必须遵守的 JSON Schema：\n"
                    + IntegrateKnowledgeSchema.JSON_SCHEMA;

            ResponseFormat responseFormat = new ResponseFormat();
            responseFormat.setType("json_object");

            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setRole("system");
            systemMessage.setContent(
                    "你是认知知识库整理助手。"
                            + "请严格按照指定的 JSON 结构输出，"
                            + "不要输出解释或 Markdown。"
            );

            ChatMessage userMessage = new ChatMessage();
            userMessage.setRole("user");
            userMessage.setContent(prompt);

            ChatRequest request = new ChatRequest();
            request.setModel(aiConfig.getModel());
            request.setTemperature(0.1);
            request.setResponseFormat(responseFormat);
            request.setMessages(
                    List.of(systemMessage, userMessage)
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return parseCognitiveUpdateResponse(response);

        } catch (Exception e) {
            throw new RuntimeException(
                    "第二次 AI 分析失败：" + e.getMessage(),
                    e
            );
        }
    }

    /**
     * 领域摘要 AI：
     *
     * 根据受影响 Domain 当前完整的 Knowledge 和 Relation，
     * 重新生成领域整体认知摘要。
     */
    public DomainSummaryResponse summarizeDomains(
            DomainSummaryRequest domainSummaryRequest
    ) {
        try {

            /*
             * 将 Service 查询并整理好的领域上下文
             * 转换成 JSON。
             */
            String domainData =
                    objectMapper.writeValueAsString(
                            domainSummaryRequest
                    );

            /*
             * 拼接：
             *
             * 1. 领域摘要规则；
             * 2. 当前领域完整上下文；
             * 3. 返回结果 Schema。
             */
            String prompt = DomainSummaryPrompt.SYSTEM_PROMPT
                    + "\n\n需要总结的领域上下文：\n"
                    + domainData
                    + "\n\n必须遵守的 JSON Schema：\n"
                    + DomainSummarySchema.JSON_SCHEMA;

            ResponseFormat responseFormat =
                    new ResponseFormat();

            responseFormat.setType("json_object");

            /*
             * system message
             */
            ChatMessage systemMessage =
                    new ChatMessage();

            systemMessage.setRole("system");
            systemMessage.setContent(
                    "你是认知领域总结助手。"
                            + "请严格按照指定的 JSON 结构输出，"
                            + "不要输出解释或 Markdown。"
            );

            /*
             * user message
             */
            ChatMessage userMessage =
                    new ChatMessage();

            userMessage.setRole("user");
            userMessage.setContent(prompt);

            /*
             * 组装 DeepSeek 请求。
             */
            ChatRequest request =
                    new ChatRequest();

            request.setModel(aiConfig.getModel());

            /*
             * 领域摘要应相对稳定，
             * 所以使用较低 temperature。
             */
            request.setTemperature(0.1);

            request.setResponseFormat(responseFormat);
            request.setMessages(
                    List.of(systemMessage, userMessage)
            );

            /*
             * 调用 DeepSeek。
             */
            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return parseDomainSummaryResponse(response);

        } catch (Exception exception) {
            throw new RuntimeException(
                    "领域摘要 AI 调用失败："
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /** 只生成计划中的领域摘要，不写数据库。domainRef 可以是已有 ID 或新领域 tempId。 */
    public Map<String, String> previewDomainSummaries(
            List<Map<String, Object>> virtualContexts
    ) {
        try {
            String prompt = """
                    你是认知领域总结助手。输入是执行一份尚未入库的方案后，
                    每个领域预计拥有的全部 Knowledge 和相关 Relation。
                    只依据输入生成简洁的整体认知摘要；不得补充外部知识，
                    不得包含可识别的私人真实姓名。空领域返回空字符串。
                    每个 domainRef 必须原样返回一次，不得新增或省略。
                    只返回 JSON，格式：
                    {"domain_summaries":[{"domain_ref":"existing:1","cognitive_summary":"..."}]}
                    输入：
                    """ + objectMapper.writeValueAsString(virtualContexts);
            ChatMessage system = new ChatMessage();
            system.setRole("system");
            system.setContent("严格按 JSON 格式输出领域摘要预览。");
            ChatMessage user = new ChatMessage();
            user.setRole("user");
            user.setContent(prompt);
            ResponseFormat format = new ResponseFormat();
            format.setType("json_object");
            ChatRequest request = new ChatRequest();
            request.setModel(aiConfig.getModel());
            request.setTemperature(0.1);
            request.setResponseFormat(format);
            request.setMessages(List.of(system, user));
            String response = restClient.post().uri("/chat/completions")
                    .body(request).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(extractAIContent(response));
            JsonNode items = root.path("domain_summaries");
            if (!items.isArray() || items.size() != virtualContexts.size()) {
                throw new IllegalArgumentException("领域摘要预览返回数量不一致");
            }
            Map<String, String> summaries = new LinkedHashMap<>();
            for (JsonNode item : items) {
                String ref = item.path("domain_ref").asText("");
                if (!item.hasNonNull("cognitive_summary")
                        || summaries.putIfAbsent(ref,
                        item.path("cognitive_summary").asText()) != null) {
                    throw new IllegalArgumentException("领域摘要预览存在重复或缺失");
                }
            }
            for (Map<String, Object> context : virtualContexts) {
                if (!summaries.containsKey(context.get("domainRef"))) {
                    throw new IllegalArgumentException("领域摘要预览缺少目标领域");
                }
            }
            return summaries;
        } catch (Exception exception) {
            throw new IllegalStateException("领域摘要预览失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 解析第一次 AI 的返回结果。
     */
    private DraftDTO parseDraftResponse(
            String response
    ) throws Exception {

        String content = extractAIContent(response);

        return objectMapper.readValue(
                content,
                DraftDTO.class
        );
    }

    /**
     * 解析第二次 AI 的返回结果。
     */
    private CognitiveUpdateDTO parseCognitiveUpdateResponse(
            String response
    ) throws Exception {

        String content = extractAIContent(response);

        return objectMapper.readValue(
                content,
                CognitiveUpdateDTO.class
        );
    }

    /**
     * 解析领域摘要 AI 返回的结果。
     */
    private DomainSummaryResponse parseDomainSummaryResponse(
            String response
    ) throws Exception {

        /*
         * 从 DeepSeek 外层响应中取出：
         * choices[0].message.content
         */
        String content =
                extractAIContent(response);

        /*
         * 把 content 中的 JSON
         * 转换成 DomainSummaryResponse。
         */
        return objectMapper.readValue(
                content,
                DomainSummaryResponse.class
        );
    }

    /**
     * 从 AI 接口的完整响应中，
     * 提取 choices[0].message.content。
     */
    private String extractAIContent(
            String response
    ) throws Exception {

        JsonNode root = objectMapper.readTree(response);
        JsonNode choices = root.get("choices");

        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("AI 返回结果为空");
        }

        JsonNode message = choices.get(0).get("message");

        if (message == null || message.get("content") == null) {
            throw new RuntimeException(
                    "AI 返回结果中缺少 message.content"
            );
        }

        return message.get("content").asText();
    }
}
