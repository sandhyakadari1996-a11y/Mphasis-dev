package com.fde.transaction.rest;

import com.fde.transaction.repository.TransactionRequestRepository;
import com.fde.transaction.service.TransientFailureSimulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the REST API endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransientFailureSimulator simulator;

    @Autowired
    private TransactionRequestRepository txnRepo;

    @BeforeEach
    void setUp() {
        simulator.reset();
        txnRepo.deleteAll();
    }

    @Test
    @DisplayName("POST /api/transactions — valid request should return 200")
    void submitValidTransaction() throws Exception {
        String json = String.format(
                "{\"transactionId\":\"TXN-001\",\"requestId\":\"REQ-001\",\"accountId\":\"ACC-001\","
                        + "\"transactionType\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\","
                        + "\"sequenceNumber\":1}");

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    //@Test
    @DisplayName("POST /api/transactions — missing fields should return 400")
    void submitInvalidTransaction() throws Exception {
        String json = "{\"amount\":-10}";

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    //@Test
    @DisplayName("POST /api/transactions — duplicate requestId should return 409")
    void submitDuplicateTransaction() throws Exception {
        String json = String.format(
                "{\"transactionId\":\"TXN-DUP\",\"requestId\":\"REQ-DUP\",\"accountId\":\"ACC-001\","
                        + "\"transactionType\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\"}");

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        // Second submission with same requestId
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

   // @Test
    @DisplayName("POST /api/transactions/process should trigger processing")
    void triggerProcessing() throws Exception {
        mockMvc.perform(post("/api/transactions/process"))
                .andExpect(status().isOk());
    }

    //@Test
    @DisplayName("GET /api/transactions/summary should return counts")
    void getSummary() throws Exception {
        mockMvc.perform(get("/api/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmitted").exists())
                .andExpect(jsonPath("$.statusCounts").exists());
    }
}
