package com.cogniflow;

import com.cogniflow.client.AIClient;
import com.cogniflow.config.AIConfig;
import com.cogniflow.dto.*;
import com.cogniflow.exception.PlanConflictException;
import com.cogniflow.service.AnalysisService;
import com.cogniflow.service.DomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:integration_plan_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ai.api-key=test-key",
        "ai.base-url=http://127.0.0.1:1",
        "ai.model=test-model",
        "demo.auth.username=test",
        "demo.auth.password-hash=$2y$10$clxzvkJbRpwKuFXNh2ef1ub9/Don/y4M4Wiozivqfqy5.CxwXHCjS",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class IntegrationPlanFlowTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisService analysisService;
    @Autowired DomainService domainService;

    @TestConfiguration
    static class FakeAIConfiguration {
        @Bean @Primary
        AIClient fakeAI(ObjectMapper mapper, AIConfig config,
                        RestClient.Builder builder) {
            return new AIClient(mapper, config, builder) {
                @Override
                public CognitiveUpdateDTO integrateKnowledge(SecondAIRequest request) {
                    if ("NOOP_TEST".equals(request.getConfirmedDraft().getSummary())) {
                        KnowledgeResultDTO unchanged = new KnowledgeResultDTO();
                        unchanged.setId(request.getExistingKnowledge().get(0).getId());
                        unchanged.setTempId("");
                        unchanged.setTitle(request.getExistingKnowledge().get(0).getTitle());
                        unchanged.setDescription(request.getExistingKnowledge().get(0).getDescription());
                        unchanged.setDomainIds(List.of(request.getExistingDomains().get(0).getId()));
                        unchanged.setDomainTempIds(List.of());
                        CognitiveUpdateDTO result = new CognitiveUpdateDTO();
                        result.setKnowledgeUpdates(List.of(unchanged));
                        result.setRelationUpdates(List.of());
                        result.setEvolutionUpdates(List.of());
                        return result;
                    }
                    KnowledgeResultDTO knowledge = new KnowledgeResultDTO();
                    knowledge.setTempId("k1");
                    knowledge.setTitle("The sky is blue");
                    knowledge.setDescription("Blue light is scattered.");
                    knowledge.setDomainIds(List.of());
                    knowledge.setDomainTempIds(List.of("d1"));
                    KnowledgeResultDTO clouds = new KnowledgeResultDTO();
                    clouds.setTempId("k2");
                    clouds.setTitle("Clouds can look white");
                    clouds.setDescription("Cloud droplets scatter many colors.");
                    clouds.setDomainIds(List.of());
                    clouds.setDomainTempIds(List.of("d1"));
                    RelationResultDTO relation = new RelationResultDTO();
                    relation.setDescription("Both involve scattering.");
                    relation.setKnowledgeIds(List.of());
                    relation.setKnowledgeTempIds(List.of("k1", "k2"));
                    EvolutionResultDTO evolution = new EvolutionResultDTO();
                    evolution.setTitle("Learned about scattering");
                    evolution.setContent("Connected two color phenomena.");
                    evolution.setEventType("NEW");
                    evolution.setKnowledgeIds(List.of());
                    evolution.setKnowledgeTempIds(List.of("k1", "k2"));
                    CognitiveUpdateDTO result = new CognitiveUpdateDTO();
                    result.setKnowledgeUpdates(List.of(knowledge, clouds));
                    result.setRelationUpdates(List.of(relation));
                    result.setEvolutionUpdates(List.of(evolution));
                    return result;
                }
                @Override
                public Map<String, String> previewDomainSummaries(
                        List<Map<String, Object>> contexts) {
                    Map<String, String> result = new LinkedHashMap<>();
                    for (Map<String, Object> context : contexts) {
                        result.put((String) context.get("domainRef"),
                                "Reviewed domain summary");
                    }
                    return result;
                }
            };
        }
    }

    @BeforeEach
    void schema() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));
        List<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE IF NOT EXISTS draft (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, conversation_id BIGINT, request_id VARCHAR(100), title VARCHAR(255), source_content CLOB, summary VARCHAR(1000), status VARCHAR(30), created_at TIMESTAMP, updated_at TIMESTAMP)");
        statements.add("CREATE TABLE IF NOT EXISTS domain (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, name VARCHAR(255), description VARCHAR(1000), cognitive_summary CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        statements.add("CREATE TABLE IF NOT EXISTS knowledge_node (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, title VARCHAR(255), description CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        statements.add("CREATE TABLE IF NOT EXISTS knowledge_relation (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, source_draft_id BIGINT, description CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        statements.add("CREATE TABLE IF NOT EXISTS evolution (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, source_draft_id BIGINT, step_order INT, event_type VARCHAR(30), title VARCHAR(255), content CLOB, created_at TIMESTAMP)");
        statements.add("CREATE TABLE IF NOT EXISTS knowledge_domain (knowledge_id BIGINT, domain_id BIGINT, PRIMARY KEY(knowledge_id,domain_id))");
        statements.add("CREATE TABLE IF NOT EXISTS relation_knowledge (relation_id BIGINT, knowledge_id BIGINT, PRIMARY KEY(relation_id,knowledge_id))");
        statements.add("CREATE TABLE IF NOT EXISTS evolution_knowledge (evolution_id BIGINT, knowledge_id BIGINT, PRIMARY KEY(evolution_id,knowledge_id))");
        statements.add("CREATE TABLE IF NOT EXISTS cognitive_revision (user_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0)");
        statements.add("CREATE TABLE IF NOT EXISTS integration_plan (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, draft_id BIGINT, draft_request_id VARCHAR(100), plan_request_id VARCHAR(100), status VARCHAR(24), baseline_revision BIGINT, confirmed_draft_json CLOB, cognitive_update_json CLOB, preview_json CLOB, summary_json CLOB, result_json CLOB, expires_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP, CONSTRAINT uk_plan_req UNIQUE(user_id,plan_request_id))");
        statements.add("CREATE TABLE IF NOT EXISTS draft_commit_guard (user_id BIGINT, draft_request_id VARCHAR(100), applied_plan_id BIGINT, created_at TIMESTAMP, PRIMARY KEY(user_id,draft_request_id))");
        statements.forEach(jdbc::execute);
        for (String table : List.of("evolution_knowledge", "relation_knowledge",
                "knowledge_domain", "draft_commit_guard", "integration_plan",
                "evolution", "knowledge_relation", "knowledge_node", "domain",
                "draft", "cognitive_revision")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewDoesNotWriteAndConfirmationIsIdempotentAndStaleSafe() {
        DraftDTO draft = draft();
        PrepareIntegrationPlanRequest request = new PrepareIntegrationPlanRequest();
        request.setDraft(draft);
        request.setPlanRequestId(UUID.randomUUID().toString());
        IntegrationPlanResponse plan = analysisService.preparePlan(request);
        assertEquals("READY", plan.getStatus());
        assertEquals(0, count("knowledge_node"));
        assertEquals(0, count("domain"));
        assertEquals(0, count("knowledge_domain"));

        String approvedSummary = jdbc.queryForObject(
                "SELECT summary_json FROM integration_plan WHERE id=?",
                String.class, plan.getPlanId());
        jdbc.update("UPDATE integration_plan SET summary_json='{}' WHERE id=?",
                plan.getPlanId());
        assertThrows(IllegalStateException.class,
                () -> analysisService.confirmPlan(plan.getPlanId()));
        assertEquals(0, count("knowledge_node"));
        assertEquals(0, count("domain"));
        assertEquals(0, count("draft_commit_guard"));
        jdbc.update("UPDATE integration_plan SET summary_json=? WHERE id=?",
                approvedSummary, plan.getPlanId());

        CommitDraftResponse first = analysisService.confirmPlan(plan.getPlanId());
        CommitDraftResponse repeated = analysisService.confirmPlan(plan.getPlanId());
        assertEquals(first.getDraftId(), repeated.getDraftId());
        assertEquals(2, count("knowledge_node"));
        assertEquals(1, count("domain"));
        assertEquals(2, count("knowledge_domain"));
        assertEquals(1, count("knowledge_relation"));
        assertEquals(2, count("relation_knowledge"));
        assertEquals(1, count("evolution"));
        assertEquals(2, count("evolution_knowledge"));
        assertEquals("Reviewed domain summary", jdbc.queryForObject(
                "SELECT cognitive_summary FROM domain LIMIT 1", String.class));
        PrepareIntegrationPlanRequest replay = new PrepareIntegrationPlanRequest();
        replay.setDraft(draft);
        replay.setPlanRequestId(UUID.randomUUID().toString());
        assertThrows(PlanConflictException.class,
                () -> analysisService.preparePlan(replay));

        DraftDTO secondDraft = draft();
        PrepareIntegrationPlanRequest secondRequest = new PrepareIntegrationPlanRequest();
        secondRequest.setDraft(secondDraft);
        secondRequest.setPlanRequestId(UUID.randomUUID().toString());
        IntegrationPlanResponse stalePlan = analysisService.preparePlan(secondRequest);
        domainService.createDomain(1L, "Another domain", "manual change");
        assertThrows(PlanConflictException.class,
                () -> analysisService.confirmPlan(stalePlan.getPlanId()));
        assertEquals(2, count("knowledge_node"));
    }

    @Test
    void exactSameKnowledgeAndMembershipAreNotShownAsUpdate() {
        jdbc.update("INSERT INTO domain(user_id,name,description,cognitive_summary) VALUES(1,'Optics','Physics','Existing summary')");
        Long domainId = jdbc.queryForObject("SELECT id FROM domain WHERE name='Optics'", Long.class);
        jdbc.update("INSERT INTO knowledge_node(user_id,title,description) VALUES(1,'The sky is blue','Blue light is scattered.')");
        Long knowledgeId = jdbc.queryForObject("SELECT id FROM knowledge_node WHERE title='The sky is blue'", Long.class);
        jdbc.update("INSERT INTO knowledge_domain(knowledge_id,domain_id) VALUES(?,?)", knowledgeId, domainId);

        DraftDTO draft = new DraftDTO();
        draft.setRequestId(UUID.randomUUID().toString());
        draft.setTitle("Same knowledge");
        draft.setSummary("NOOP_TEST");
        draft.setSourceContent("[USER] Why is the sky blue?");
        KnowledgePreviewDTO preview = new KnowledgePreviewDTO();
        preview.setTempId("k1");
        preview.setTitle("The sky is blue");
        preview.setDescription("Blue light is scattered.");
        preview.setDomainIds(List.of(domainId));
        preview.setDomainTempIds(List.of());
        draft.setKnowledgePreview(List.of(preview));
        draft.setRelationPreview(List.of());
        draft.setEvolutionPreview(List.of());
        draft.setDomainPreview(List.of());

        PrepareIntegrationPlanRequest request = new PrepareIntegrationPlanRequest();
        request.setDraft(draft);
        request.setPlanRequestId(UUID.randomUUID().toString());
        IntegrationPlanResponse plan = analysisService.preparePlan(request);
        assertEquals(List.of(), plan.getChanges().get("knowledge"));
        assertEquals(List.of(), plan.getChanges().get("domainSummaries"));
        assertEquals("Blue light is scattered.", jdbc.queryForObject(
                "SELECT description FROM knowledge_node WHERE id=?", String.class, knowledgeId));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private DraftDTO draft() {
        DraftDTO draft = new DraftDTO();
        draft.setRequestId(UUID.randomUUID().toString());
        draft.setTitle("Sky colors");
        draft.setSummary("A conversation about colors");
        draft.setSourceContent("[USER] Why is the sky blue?");
        KnowledgePreviewDTO knowledge = new KnowledgePreviewDTO();
        knowledge.setTempId("k1");
        knowledge.setTitle("The sky is blue");
        knowledge.setDescription("Blue light is scattered.");
        knowledge.setDomainIds(List.of());
        knowledge.setDomainTempIds(List.of("d1"));
        KnowledgePreviewDTO clouds = new KnowledgePreviewDTO();
        clouds.setTempId("k2");
        clouds.setTitle("Clouds can look white");
        clouds.setDescription("Cloud droplets scatter many colors.");
        clouds.setDomainIds(List.of());
        clouds.setDomainTempIds(List.of("d1"));
        draft.setKnowledgePreview(List.of(knowledge, clouds));
        RelationPreviewDTO relation = new RelationPreviewDTO();
        relation.setDescription("Both involve scattering.");
        relation.setKnowledgeTempIds(List.of("k1", "k2"));
        draft.setRelationPreview(List.of(relation));
        EvolutionPreviewDTO evolution = new EvolutionPreviewDTO();
        evolution.setTitle("Learned about scattering");
        evolution.setDescription("Connected two color phenomena.");
        evolution.setKnowledgeTempIds(List.of("k1", "k2"));
        draft.setEvolutionPreview(List.of(evolution));
        DomainPreviewDTO domain = new DomainPreviewDTO();
        domain.setTempId("d1");
        domain.setName("Physics " + UUID.randomUUID());
        domain.setDescription("Physical phenomena");
        draft.setDomainPreview(List.of(domain));
        return draft;
    }
}
