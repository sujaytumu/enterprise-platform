package com.enterprise.platform.controller;

import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.repository.CardRepository;
import com.enterprise.platform.repository.TransactionRepository;
import com.enterprise.platform.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Request validation and error-shape tests; repositories/services are mocked. */
@WebMvcTest({AccountController.class, CardController.class, TransactionController.class})
class RequestValidationTest {

    @Autowired MockMvc mvc;

    @MockBean AccountRepository accountRepository;
    @MockBean CardRepository cardRepository;
    @MockBean TransactionRepository transactionRepository;
    @MockBean TransactionService transactionService;

    private void postJson(String path, String body, int expectedStatus, String errorContains) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error", containsString(errorContains)));
    }

    @Test
    void createAccountRejectsBlankHolderName() throws Exception {
        postJson("/api/accounts", "{\"holderName\":\"  \",\"openingBalance\":10}", 400, "holderName");
    }

    @Test
    void createAccountRejectsNonPositiveBalance() throws Exception {
        postJson("/api/accounts", "{\"holderName\":\"Ada\",\"openingBalance\":-5}", 400, "openingBalance");
    }

    @Test
    void createAccountRejectsTooManyDecimalPlaces() throws Exception {
        postJson("/api/accounts", "{\"holderName\":\"Ada\",\"openingBalance\":10.123}", 400, "openingBalance");
    }

    @Test
    void createAccountRejectsBadCurrency() throws Exception {
        postJson("/api/accounts",
                "{\"holderName\":\"Ada\",\"openingBalance\":10,\"currency\":\"dollars\"}", 400, "currency");
    }

    @Test
    void createAccountRejectsMalformedJson() throws Exception {
        postJson("/api/accounts", "{not json", 400, "Malformed");
    }

    @Test
    void authorizeRejectsMissingAccountAndAmount() throws Exception {
        postJson("/api/transactions/authorize", "{}", 400, "accountId");
    }

    @Test
    void authorizeRejectsNonPositiveAmount() throws Exception {
        postJson("/api/transactions/authorize",
                "{\"accountId\":\"6f1b2f0e-7d3a-4a52-9d55-0a1b2c3d4e5f\",\"amount\":0}", 400, "amount");
    }

    @Test
    void authorizeRejectsInvalidUuid() throws Exception {
        postJson("/api/transactions/authorize", "{\"accountId\":\"nope\",\"amount\":5}", 400, "Malformed");
    }

    @Test
    void issueCardRequiresAccountId() throws Exception {
        postJson("/api/cards", "{}", 400, "accountId");
    }

    @Test
    void invalidUuidInPathReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/accounts/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("id")));
    }
}
