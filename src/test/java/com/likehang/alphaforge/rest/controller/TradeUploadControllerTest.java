package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvImportResult;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import com.likehang.alphaforge.service.AccountActivityCsvImportService;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.CsvImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradeUploadControllerTest {

    private final UUID importBatchId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private FakeAccountActivityCsvImportService accountActivityCsvImportService;
    private FakeCurrentUserService currentUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountActivityCsvImportService = new FakeAccountActivityCsvImportService();
        currentUserService = new FakeCurrentUserService(userId);
        TradeUploadController controller = new TradeUploadController(accountActivityCsvImportService, currentUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void uploadsCsvForConfiguredDefaultAccountWhenBrokerageAccountIdIsMissing() throws Exception {
        accountActivityCsvImportService.result = successResult();

        mockMvc.perform(multipart("/api/account-activities/imports").file(csvFile()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importBatchId").value(importBatchId.toString()))
                .andExpect(jsonPath("$.brokerageAccountId").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.successRows").value(2))
                .andExpect(jsonPath("$.failedRows").value(0));

        assertThat(accountActivityCsvImportService.defaultAccountImportCalled).isTrue();
        assertThat(accountActivityCsvImportService.requestedAccountImportCalled).isFalse();
        assertThat(accountActivityCsvImportService.requestedUserId).isEqualTo(userId);
    }

    @Test
    void uploadsCsvForRequestedBrokerageAccount() throws Exception {
        accountActivityCsvImportService.result = successResult();

        mockMvc.perform(multipart("/api/account-activities/imports")
                        .file(csvFile())
                        .param("brokerageAccountId", brokerageAccountId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importBatchId").value(importBatchId.toString()))
                .andExpect(jsonPath("$.brokerageAccountId").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(accountActivityCsvImportService.requestedAccountImportCalled).isTrue();
        assertThat(accountActivityCsvImportService.defaultAccountImportCalled).isFalse();
        assertThat(accountActivityCsvImportService.requestedUserId).isEqualTo(userId);
        assertThat(accountActivityCsvImportService.requestedBrokerageAccountId).isEqualTo(brokerageAccountId);
    }

    @Test
    void returnsBadRequestWhenCsvImportFails() throws Exception {
        accountActivityCsvImportService.exception = new CsvImportException("CSV file is required");

        mockMvc.perform(multipart("/api/account-activities/imports").file(csvFile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("CSV file is required"));
    }

    private MockMultipartFile csvFile() {
        return new MockMultipartFile(
                "file",
                "activity.csv",
                "text/csv",
                "Action,Time (UTC),Total,Currency (Total)\nInterest on cash,2026-07-01 01:05:30+00:00,0.03,EUR\n"
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private AccountActivityCsvImportResult successResult() {
        return new AccountActivityCsvImportResult(
                importBatchId,
                brokerageAccountId,
                "activity.csv",
                "a".repeat(64),
                ImportBatchStatus.COMPLETED,
                2,
                2,
                0,
                List.of()
        );
    }

    private static class FakeAccountActivityCsvImportService extends AccountActivityCsvImportService {

        private AccountActivityCsvImportResult result;
        private CsvImportException exception;
        private boolean defaultAccountImportCalled;
        private boolean requestedAccountImportCalled;
        private UUID requestedUserId;
        private UUID requestedBrokerageAccountId;

        private FakeAccountActivityCsvImportService() {
            super(null, null, null, null, "", "");
        }

        @Override
        public AccountActivityCsvImportResult importCsvForConfiguredDefaultAccount(UUID userId, MultipartFile file) {
            defaultAccountImportCalled = true;
            requestedUserId = userId;
            if (exception != null) {
                throw exception;
            }
            return result;
        }

        @Override
        public AccountActivityCsvImportResult importCsv(UUID userId, UUID brokerageAccountId, MultipartFile file) {
            requestedAccountImportCalled = true;
            requestedUserId = userId;
            requestedBrokerageAccountId = brokerageAccountId;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }

    private static class FakeCurrentUserService extends CurrentUserService {

        private final UUID userId;

        private FakeCurrentUserService(UUID userId) {
            super(null, "");
            this.userId = userId;
        }

        @Override
        public UUID getCurrentUserId() {
            return userId;
        }
    }
}

// controller test = true controller + fake service + fake http request
