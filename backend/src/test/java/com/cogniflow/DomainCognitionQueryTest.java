package com.cogniflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:domain_cognition_query_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
@AutoConfigureMockMvc(addFilters = false)
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class DomainCognitionQueryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void data() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));
        jdbc.execute("CREATE TABLE IF NOT EXISTS domain (id BIGINT PRIMARY KEY, user_id BIGINT, name VARCHAR(255), description VARCHAR(1000), cognitive_summary CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_node (id BIGINT PRIMARY KEY, user_id BIGINT, title VARCHAR(255), description CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_relation (id BIGINT PRIMARY KEY, user_id BIGINT, source_draft_id BIGINT, description CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_domain (knowledge_id BIGINT, domain_id BIGINT, PRIMARY KEY(knowledge_id,domain_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS relation_knowledge (relation_id BIGINT, knowledge_id BIGINT, PRIMARY KEY(relation_id,knowledge_id))");
        for (String table : new String[]{"relation_knowledge", "knowledge_domain",
                "knowledge_relation", "knowledge_node", "domain"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO domain(id,user_id,name,description,cognitive_summary) VALUES(1,1,'Physics','Physical concepts','Current summary')");
        jdbc.update("INSERT INTO domain(id,user_id,name,description,cognitive_summary) VALUES(2,1,'Testing','Software tests',NULL)");
        jdbc.update("INSERT INTO domain(id,user_id,name,description,cognitive_summary) VALUES(3,1,'Empty','No knowledge',NULL)");
        jdbc.update("INSERT INTO domain(id,user_id,name,description,cognitive_summary) VALUES(99,2,'Private','Other user',NULL)");
        for (int id = 1; id <= 6; id++) {
            jdbc.update("INSERT INTO knowledge_node(id,user_id,title,description,updated_at) VALUES(?,?,?,?,?)",
                    id, 1, "Knowledge " + id, "Description " + id,
                    java.sql.Timestamp.valueOf("2026-09-14 0" + id + ":00:00"));
        }
        jdbc.update("INSERT INTO knowledge_node(id,user_id,title,description,updated_at) VALUES(90,2,'Private knowledge','Secret',CURRENT_TIMESTAMP)");
        for (int id = 1; id <= 5; id++) {
            jdbc.update("INSERT INTO knowledge_domain(knowledge_id,domain_id) VALUES(?,1)", id);
        }
        jdbc.update("INSERT INTO knowledge_domain(knowledge_id,domain_id) VALUES(5,2)");
        jdbc.update("INSERT INTO knowledge_domain(knowledge_id,domain_id) VALUES(6,2)");
        // 即使关联表中出现跨用户脏数据，列表与详情也不得展示它。
        jdbc.update("INSERT INTO knowledge_domain(knowledge_id,domain_id) VALUES(90,1)");
        jdbc.update("INSERT INTO knowledge_relation(id,user_id,description) VALUES(8,1,'Cross-domain relation')");
        jdbc.update("INSERT INTO relation_knowledge(relation_id,knowledge_id) VALUES(8,5),(8,6)");
        jdbc.update("INSERT INTO knowledge_relation(id,user_id,description) VALUES(9,2,'Private relation')");
        jdbc.update("INSERT INTO relation_knowledge(relation_id,knowledge_id) VALUES(9,5),(9,90)");
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listKeepsOriginalFieldsAndReturnsFourRecentOwnedKnowledge() throws Exception {
        mvc.perform(get("/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].cognitiveSummary").value("Current summary"))
                .andExpect(jsonPath("$[0].recentKnowledge.length()").value(4))
                .andExpect(jsonPath("$[0].recentKnowledge[0].id").value(5))
                .andExpect(jsonPath("$[0].recentKnowledge[3].id").value(2))
                .andExpect(jsonPath("$[2].recentKnowledge.length()").value(0))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void detailIncludesCrossDomainRelationButNotOtherUsersData() throws Exception {
        mvc.perform(get("/domains/1/cognition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain.id").value(1))
                .andExpect(jsonPath("$.knowledge.length()").value(5))
                .andExpect(jsonPath("$.knowledge[0].id").value(5))
                .andExpect(jsonPath("$.knowledge[0].domainIds[0]").value(1))
                .andExpect(jsonPath("$.knowledge[0].domainIds[1]").value(2))
                .andExpect(jsonPath("$.relations.length()").value(1))
                .andExpect(jsonPath("$.relations[0].id").value(8))
                .andExpect(jsonPath("$.relations[0].knowledge[0].id").value(5))
                .andExpect(jsonPath("$.relations[0].knowledge[0].inCurrentDomain").value(true))
                .andExpect(jsonPath("$.relations[0].knowledge[1].id").value(6))
                .andExpect(jsonPath("$.relations[0].knowledge[1].inCurrentDomain").value(false));
    }

    @Test
    void emptyDomainAndMissingOrForeignDomain() throws Exception {
        mvc.perform(get("/domains/3/cognition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledge.length()").value(0))
                .andExpect(jsonPath("$.relations.length()").value(0));
        mvc.perform(get("/domains/404/cognition"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("领域不存在"));
        mvc.perform(get("/domains/99/cognition"))
                .andExpect(status().isNotFound());
    }
}
