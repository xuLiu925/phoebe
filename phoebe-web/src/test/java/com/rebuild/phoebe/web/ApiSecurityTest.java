package com.rebuild.phoebe.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String token(String user, String pwd) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pwd + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode n = om.readTree(body);
        return "Bearer " + n.path("data").path("token").asText();
    }

    @Test
    void loginAndPermissions() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());

        String demo = token("demo", "demo123");
        mvc.perform(get("/api/plans").header("Authorization", demo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].plan.name", hasItem("眼霜知识推广")));
        mvc.perform(get("/api/plans/3").header("Authorization", demo)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit/tasks").header("Authorization", demo)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/accounts").header("Authorization", demo)).andExpect(status().isForbidden());
    }

    @Test
    void auditorApprovesPendingQa() throws Exception {
        String auditor = token("auditor", "auditor123");
        mvc.perform(post("/api/audit/tasks/1/approve").header("Authorization", auditor)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        String demo = token("demo", "demo123");
        mvc.perform(get("/api/qas/3").header("Authorization", demo))
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"));
        // 重复审核 → 409
        mvc.perform(post("/api/audit/tasks/1/approve").header("Authorization", auditor)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
    }

    @Test
    void adminImpersonation() throws Exception {
        String admin = token("admin", "admin123");
        mvc.perform(get("/api/plans").header("Authorization", admin).header("X-Account-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].plan.name").value("眼部护理问答"));
    }

    @Test
    void industryRestrictionOnAccountOpening() throws Exception {
        String admin = token("admin", "admin123");
        mvc.perform(post("/api/admin/accounts").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"companyName\":\"某医院\",\"industry\":\"医疗服务\",\"username\":\"h1\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeholderIsPublic() throws Exception {
        mvc.perform(get("/api/images/placeholder?w=100&h=50&text=A")).andExpect(status().isOk());
    }
}
