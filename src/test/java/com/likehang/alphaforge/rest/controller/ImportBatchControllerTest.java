package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.query.ImportBatchPageResponse;
import com.likehang.alphaforge.model.dto.query.ImportBatchResponse;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.ImportBatchService;
import com.likehang.alphaforge.service.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportBatchControllerTest {

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final UUID importBatchId = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private FakeImportBatchService importBatchService;
    private FakeCurrentUserService currentUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        importBatchService = new FakeImportBatchService();
        currentUserService = new FakeCurrentUserService(userId);
        ImportBatchController controller = new ImportBatchController(
                importBatchService,
                currentUserService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void listsImportBatchesForBrokerageAccount() throws Exception {
        importBatchService.result = result();

        mockMvc.perform(get("/api/brokerage-accounts/{brokerageAccountId}/import-batches", brokerageAccountId)
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerageAccountId").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$.importBatches[0].id").value(importBatchId.toString()))
                .andExpect(jsonPath("$.importBatches[0].brokerageAccountId").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$.importBatches[0].originalFilename").value("activity.csv"))
                .andExpect(jsonPath("$.importBatches[0].fileHash").value("a".repeat(64)))
                .andExpect(jsonPath("$.importBatches[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.importBatches[0].totalRows").value(10))
                .andExpect(jsonPath("$.importBatches[0].successRows").value(9))
                .andExpect(jsonPath("$.importBatches[0].failedRows").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        assertThat(importBatchService.requestedListUserId).isEqualTo(userId);
        assertThat(importBatchService.requestedListBrokerageAccountId).isEqualTo(brokerageAccountId);
        assertThat(importBatchService.requestedPageable.getPageNumber()).isZero();
        assertThat(importBatchService.requestedPageable.getPageSize()).isEqualTo(25);
    }

    @Test
    void normalizesPagingInput() throws Exception {
        importBatchService.result = result();

        mockMvc.perform(get("/api/brokerage-accounts/{brokerageAccountId}/import-batches", brokerageAccountId)
                        .param("page", "-1")
                        .param("size", "500"))
                .andExpect(status().isOk());

        assertThat(importBatchService.requestedPageable.getPageNumber()).isZero();
        assertThat(importBatchService.requestedPageable.getPageSize()).isEqualTo(200);
    }

    @Test
    void deletesImportBatch() throws Exception {
        mockMvc.perform(delete(
                        "/api/brokerage-accounts/{brokerageAccountId}/import-batches/{importBatchId}",
                        brokerageAccountId,
                        importBatchId
                ))
                .andExpect(status().isNoContent());

        assertThat(importBatchService.deleteCalled).isTrue();
        assertThat(importBatchService.requestedDeleteUserId).isEqualTo(userId);
        assertThat(importBatchService.requestedDeleteBrokerageAccountId).isEqualTo(brokerageAccountId);
        assertThat(importBatchService.requestedDeleteImportBatchId).isEqualTo(importBatchId);
    }

    @Test
    void returnsNotFoundWhenDeletingMissingImportBatch() throws Exception {
        importBatchService.deleteException = new ResourceNotFoundException(
                "Import batch not found: " + importBatchId
        );

        mockMvc.perform(delete(
                        "/api/brokerage-accounts/{brokerageAccountId}/import-batches/{importBatchId}",
                        brokerageAccountId,
                        importBatchId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Import batch not found: " + importBatchId));
    }

    private ImportBatchPageResponse result() {
        return new ImportBatchPageResponse(
                brokerageAccountId,
                List.of(new ImportBatchResponse(
                        importBatchId,
                        brokerageAccountId,
                        "activity.csv",
                        "a".repeat(64),
                        ImportBatchStatus.COMPLETED,
                        10,
                        9,
                        1,
                        Instant.parse("2026-07-01T10:15:30Z"),
                        Instant.parse("2026-07-01T10:15:40Z"),
                        Instant.parse("2026-07-01T10:15:30Z"),
                        Instant.parse("2026-07-01T10:15:40Z")
                )),
                0,
                25,
                1,
                1,
                true,
                true
        );
    }

    private static class FakeImportBatchService extends ImportBatchService {

        private ImportBatchPageResponse result;
        private ResourceNotFoundException deleteException;
        private UUID requestedListUserId;
        private UUID requestedListBrokerageAccountId;
        private Pageable requestedPageable;
        private boolean deleteCalled;
        private UUID requestedDeleteUserId;
        private UUID requestedDeleteBrokerageAccountId;
        private UUID requestedDeleteImportBatchId;

        private FakeImportBatchService() {
            super(null, null, null);
        }

        @Override
        public ImportBatchPageResponse getImportBatchesByBrokerageAccount(
                UUID userId,
                UUID brokerageAccountId,
                Pageable pageable
        ) {
            requestedListUserId = userId;
            requestedListBrokerageAccountId = brokerageAccountId;
            requestedPageable = pageable;
            return result;
        }

        @Override
        public void deleteImportBatch(
                UUID userId,
                UUID brokerageAccountId,
                UUID importBatchId
        ) {
            deleteCalled = true;
            requestedDeleteUserId = userId;
            requestedDeleteBrokerageAccountId = brokerageAccountId;
            requestedDeleteImportBatchId = importBatchId;

            if (deleteException != null) {
                throw deleteException;
            }
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
