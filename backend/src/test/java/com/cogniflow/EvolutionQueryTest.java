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
        "spring.datasource.url=jdbc:h2:mem:evolution_query_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class EvolutionQueryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void data() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));
        jdbc.execute("CREATE TABLE IF NOT EXISTS domain (id BIGINT PRIMARY KEY, user_id BIGINT, name VARCHAR(255), description VARCHAR(1000), cognitive_summary CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_node (id BIGINT PRIMARY KEY, user_id BIGINT, title VARCHAR(255), description CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS evolution (id BIGINT PRIMARY KEY, user_id BIGINT, source_draft_id BIGINT, step_order INT, event_type VARCHAR(30), title VARCHAR(255), content CLOB, created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS knowledge_domain (knowledge_id BIGINT, domain_id BIGINT, PRIMARY KEY(knowledge_id,domain_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS evolution_knowledge (evolution_id BIGINT, knowledge_id BIGINT, PRIMARY KEY(evolution_id,knowledge_id))");
        for (String table : new String[]{"evolution_knowledge", "knowledge_domain",
                "evolution", "knowledge_node", "domain"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO domain(id,user_id,name) VALUES(1,1,'Physics'),(2,1,'Software'),(3,1,'Empty'),(99,2,'Private')");
        jdbc.update("INSERT INTO knowledge_node(id,user_id,title) VALUES(5,1,'Sky'),(6,1,'Clouds'),(90,2,'Secret')");
        jdbc.update("INSERT INTO knowledge_domain(knowledge_id,domain_id) VALUES(5,1),(5,2),(6,2),(90,1),(90,99)");
        jdbc.update("INSERT INTO evolution(id,user_id,step_order,event_type,title,content,created_at) VALUES(10,1,1,'NEW','First','Original text',TIMESTAMP '2026-09-14 11:30:00')");
        jdbc.update("INSERT INTO evolution(id,user_id,step_order,event_type,title,content,created_at) VALUES(11,1,2,'REVISED','Second','Revised text',TIMESTAMP '2026-09-14 11:30:00')");
        jdbc.update("INSERT INTO evolution(id,user_id,step_order,event_type,title,content,created_at) VALUES(13,1,2,'CORRECTED','Third','Correction text',TIMESTAMP '2026-09-14 11:30:00')");
        jdbc.update("INSERT INTO evolution(id,user_id,step_order,event_type,title,content,created_at) VALUES(99,2,1,'NEW','Private','Secret',TIMESTAMP '2026-09-14 12:00:00')");
        jdbc.update("INSERT INTO evolution_knowledge(evolution_id,knowledge_id) VALUES(10,5),(10,6),(11,6),(99,5)");
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void globalPaginationAndStableOrdering() throws Exception {
        mvc.perform(get("/evolutions?page=1&size=2&order=desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(13))
                .andExpect(jsonPath("$.items[1].id").value(11))
                .andExpect(jsonPath("$.items[1].content").value("Revised text"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/evolutions?page=2&size=2&order=desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/evolutions?order=asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[1].id").value(11))
                .andExpect(jsonPath("$.items[2].id").value(13));
    }

    @Test
    void domainFilterCrossDomainAndUserIsolation() throws Exception {
        String sample = mvc.perform(get("/evolutions?domainId=1&order=asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].knowledge[0].id").value(5))
                .andExpect(jsonPath("$.items[0].knowledge[1].id").value(6))
                .andExpect(jsonPath("$.items[0].domains[0].id").value(1))
                .andExpect(jsonPath("$.items[0].domains[1].id").value(2))
                .andExpect(jsonPath("$.items[0].domains.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        System.out.println("EVOLUTION_QUERY_SAMPLE=" + sample);
        mvc.perform(get("/evolutions?domainId=2&order=asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[1].id").value(11));
        mvc.perform(get("/evolutions?domainId=99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("领域不存在"));
    }

    @Test
    void emptyResultAndInvalidParameters() throws Exception {
        mvc.perform(get("/evolutions?domainId=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/evolutions?page=100&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/evolutions?domainId=404"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/evolutions?order=random"))
                .andExpect(status().isBadRequest());
    }
}
