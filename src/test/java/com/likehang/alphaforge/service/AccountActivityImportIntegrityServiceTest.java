package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountActivityImportIntegrityServiceTest {

    private final UUID accountId = UUID.randomUUID();
    private final AccountActivityRepository activities = mock(AccountActivityRepository.class);
    private final AccountActivityImportIntegrityService service = new AccountActivityImportIntegrityService(activities);

    @Test
    void rejectsDuplicateIdDespiteDifferentDecimalFormattingTimezoneAndDisplayMetadata() {
        AccountActivity existing = activity("EOF-1", 2);
        existing.setNumberOfShares(new BigDecimal("2.0000000000"));
        when(activities.findByBrokerageAccount_Id(accountId)).thenReturn(List.of(existing));
        AccountActivity incoming = activity(" EOF-1 ", 5);
        incoming.setOccurredAt(OffsetDateTime.parse("2026-10-01T12:00:00+02:00"));
        incoming.setInstrumentName("Renamed display name");
        incoming.setTicker("NEW-TICKER");
        incoming.setNotes("Different export notes");

        assertThatThrownBy(() -> service.validate(accountId, List.of(incoming)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Duplicate transaction ID")
                .hasMessageContaining("CSV row 5").hasMessageContaining(existing.getImportBatch().getId().toString());
    }

    @Test
    void rejectsConflictingContentForTheSameId() {
        when(activities.findByBrokerageAccount_Id(accountId)).thenReturn(List.of(activity("EOF-1", 2)));
        AccountActivity changed = activity("EOF-1", 3);
        changed.setTotalAmount(new BigDecimal("301"));
        assertThatThrownBy(() -> service.validate(accountId, List.of(changed)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Conflicting transaction ID");
    }

    @Test
    void rejectsDuplicateIdsInsideOneFile() {
        AccountActivity first = activity("EOF-1", 2);
        AccountActivity second = activity("EOF-1", 3);
        second.setImportBatch(first.getImportBatch());
        assertThatThrownBy(() -> service.validate(accountId, List.of(first, second)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("same CSV file");
    }

    @Test
    void allowsIdenticalContentWithDifferentNonblankIds() {
        when(activities.findByBrokerageAccount_Id(accountId)).thenReturn(List.of(activity("EOF-1", 2)));
        assertThatCode(() -> service.validate(accountId, List.of(activity("EOF-2", 3))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNoIdDividendOverlap() {
        AccountActivity dividend = activity(null, 2);
        dividend.setAction("Dividend (Dividend)");
        when(activities.findByBrokerageAccount_Id(accountId)).thenReturn(List.of(dividend));
        AccountActivity repeated = activity(" ", 9);
        repeated.setAction("Dividend (Dividend)");
        assertThatThrownBy(() -> service.validate(accountId, List.of(repeated)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("without a transaction ID");
    }

    @Test
    void detectsIdAddedOrRemovedBetweenExports() {
        when(activities.findByBrokerageAccount_Id(accountId)).thenReturn(List.of(activity(null, 2)));
        assertThatThrownBy(() -> service.validate(accountId, List.of(activity("EOF-1", 3))))
                .isInstanceOf(ConflictException.class);
        when(activities.findByBrokerageAccount_Id(accountId)).thenReturn(List.of(activity("EOF-1", 2)));
        assertThatThrownBy(() -> service.validate(accountId, List.of(activity(null, 3))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void permitsDifferentNoIdEventsAtTheSameTime() {
        AccountActivity changed = activity(null, 3);
        changed.setTotalAmount(new BigDecimal("301"));
        assertThatCode(() -> service.validate(accountId, List.of(activity(null, 2), changed)))
                .doesNotThrowAnyException();
    }

    @Test
    void comparesPersistedPrecisionAndCurrencyForNoIdEvents() {
        AccountActivity stored = activity(null, 2);
        stored.setTotalAmount(new BigDecimal("300.0000"));
        when(activities.findByBrokerageAccount_Id(accountId)).thenReturn(List.of(stored));
        AccountActivity rounded = activity(null, 3);
        rounded.setTotalAmount(new BigDecimal("300.00001"));
        assertThatThrownBy(() -> service.validate(accountId, List.of(rounded)))
                .isInstanceOf(ConflictException.class);
        rounded.setTotalCurrency("USD");
        assertThatCode(() -> service.validate(accountId, List.of(rounded))).doesNotThrowAnyException();
    }

    private AccountActivity activity(String externalId, int row) {
        ImportBatch batch = new ImportBatch();
        batch.setId(UUID.randomUUID());
        AccountActivity activity = new AccountActivity();
        activity.setImportBatch(batch);
        activity.setExternalTransactionId(externalId);
        activity.setRowNumber(row);
        activity.setAction("Limit buy");
        activity.setOccurredAt(OffsetDateTime.parse("2026-10-01T10:00:00Z"));
        activity.setIsin("US67066G1040");
        activity.setTicker("NVDA");
        activity.setNumberOfShares(new BigDecimal("2"));
        activity.setPricePerShare(new BigDecimal("150"));
        activity.setPricePerShareCurrency("EUR");
        activity.setTotalAmount(new BigDecimal("300"));
        activity.setTotalCurrency("EUR");
        return activity;
    }
}
