package com.cogniflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_flow_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "demo.auth.username=test",
        // Test-only BCrypt hash; no production credential is stored in the repository.
        "demo.auth.password-hash=$2y$10$clxzvkJbRpwKuFXNh2ef1ub9/Don/y4M4Wiozivqfqy5.CxwXHCjS",
        "server.servlet.session.cookie.secure=false",
        "ai.api-key=test-key",
        "ai.base-url=http://127.0.0.1:1",
        "ai.model=test-model",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
@AutoConfigureMockMvc
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void schema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS domain (id BIGINT PRIMARY KEY, user_id BIGINT, name VARCHAR(255), description VARCHAR(1000), cognitive_summary CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.update("DELETE FROM domain");
    }

    @Test
    void unauthenticatedReadAndWriteAreDenied() throws Exception {
        mvc.perform(get("/domains"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/evolutions"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/knowledge/5"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/analysis/draft/plans/1/confirm"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/conversation-imports/pdf"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/domains").contentType("application/json")
                        .content("{\"name\":\"Should not be saved\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(options("/domains")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void loginPersistsSessionAndLogoutInvalidatesIt() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        JsonNode csrf = mapper.readTree(csrfResult.getResponse().getContentAsString());
        String token = csrf.get("token").asText();

        mvc.perform(post("/auth/login").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"username\":\"test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
        mvc.perform(post("/auth/login").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"username\":\"test\",\"password\":\"testpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(1));
        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(get("/domains").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/domains").session(session)
                        .contentType("application/json").content("{\"name\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").session(session)
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
        mvc.perform(get("/domains"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }
}
