package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountActivityHoldingValidationServiceTest {

    private final AccountActivityRepository accountActivityRepository = mock(AccountActivityRepository.class);
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private AccountActivityHoldingValidationService service;

    @BeforeEach
    void setUp() {
        service = new AccountActivityHoldingValidationService(accountActivityRepository);
    }

    @Test
    void allowsSellWhenExistingHoldingCoversIt() {
        when(accountActivityRepository.findByBrokerageAccount_Id(brokerageAccountId))
                .thenReturn(List.of(activity("Limit buy", "US67066G1040", "NVDA", "2.0000000000", "2026-07-01T10:00:00Z", 2)));

        assertThatCode(() -> service.validateNoNegativeHoldings(
                brokerageAccountId,
                List.of(activity("Limit sell", "US67066G1040", "NVDA", "1.0000000000", "2026-07-02T10:00:00Z", 2))
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsSellThatWouldMakeHoldingNegative() {
        when(accountActivityRepository.findByBrokerageAccount_Id(brokerageAccountId))
                .thenReturn(List.of(activity("Limit buy", "US67066G1040", "NVDA", "1.0000000000", "2026-07-01T10:00:00Z", 2)));

        assertThatThrownBy(() -> service.validateNoNegativeHoldings(
                brokerageAccountId,
                List.of(activity("Limit sell", "US67066G1040", "NVDA", "2.0000000000", "2026-07-02T10:00:00Z", 2))
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("negative holdings")
                .hasMessageContaining("NVDA")
                .hasMessageContaining("-1");
    }

    @Test
    void ignoresDividendShareCount() {
        when(accountActivityRepository.findByBrokerageAccount_Id(brokerageAccountId)).thenReturn(List.of());

        assertThatCode(() -> service.validateNoNegativeHoldings(
                brokerageAccountId,
                List.of(activity("Dividend (Dividend)", "US67066G1040", "NVDA", "2.0000000000", "2026-07-02T10:00:00Z", 2))
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsTradeRowsWithoutShares() {
        when(accountActivityRepository.findByBrokerageAccount_Id(brokerageAccountId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.validateNoNegativeHoldings(
                brokerageAccountId,
                List.of(activity("Limit buy", "US67066G1040", "NVDA", null, "2026-07-02T10:00:00Z", 2))
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("missing number of shares")
                .hasMessageContaining("row 2");
    }

    private AccountActivity activity(
            String action,
            String isin,
            String ticker,
            String shares,
            String occurredAt,
            int rowNumber
    ) {
        AccountActivity accountActivity = new AccountActivity();
        accountActivity.setAction(action);
        accountActivity.setIsin(isin);
        accountActivity.setTicker(ticker);
        accountActivity.setInstrumentName("NVIDIA");
        accountActivity.setNumberOfShares(shares == null ? null : new BigDecimal(shares));
        accountActivity.setOccurredAt(OffsetDateTime.parse(occurredAt));
        accountActivity.setRowNumber(rowNumber);
        return accountActivity;
    }
}
