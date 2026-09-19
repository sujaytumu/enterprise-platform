package com.enterprise.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the full app against in-memory H2 (profile "test"). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode postJson(String path, String body) throws Exception {
        String res = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    @Test
    void healthEndpointReportsUpWithoutLeakingDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void createAccountIssueCardAndAuthorize() throws Exception {
        JsonNode account = postJson("/api/accounts", "{\"holderName\":\"Ada\",\"openingBalance\":100.00}");
        String accountId = account.get("id").asText();

        JsonNode card = postJson("/api/cards", "{\"accountId\":\"" + accountId + "\"}");
        String cardId = card.get("id").asText();

        JsonNode approved = postJson("/api/transactions/authorize",
                "{\"accountId\":\"" + accountId + "\",\"cardId\":\"" + cardId + "\",\"amount\":40.00,\"merchant\":\"Cafe\"}");
        assertEquals("APPROVED", approved.get("status").asText());

        JsonNode declined = postJson("/api/transactions/authorize",
                "{\"accountId\":\"" + accountId + "\",\"amount\":500.00}");
        assertEquals("DECLINED", declined.get("status").asText());
        assertEquals("Insufficient funds", declined.get("declineReason").asText());

        mvc.perform(get("/api/accounts/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60.0));
    }

    @Test
    void blockedCardIsDeclined() throws Exception {
        JsonNode account = postJson("/api/accounts", "{\"holderName\":\"Grace\",\"openingBalance\":100.00}");
        String accountId = account.get("id").asText();
        String cardId = postJson("/api/cards", "{\"accountId\":\"" + accountId + "\"}").get("id").asText();

        mvc.perform(post("/api/cards/" + cardId + "/block")).andExpect(status().isOk());

        JsonNode tx = postJson("/api/transactions/authorize",
                "{\"accountId\":\"" + accountId + "\",\"cardId\":\"" + cardId + "\",\"amount\":5.00}");
        assertEquals("DECLINED", tx.get("status").asText());
    }

    @Test
    void unknownAccountReturnsNotFound() throws Exception {
        mvc.perform(get("/api/accounts/6f1b2f0e-7d3a-4a52-9d55-0a1b2c3d4e5f"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found"));
    }
}
