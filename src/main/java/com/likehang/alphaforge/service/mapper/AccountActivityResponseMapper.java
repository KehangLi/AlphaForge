package com.likehang.alphaforge.service.mapper;

import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.rest.dto.response.AccountActivityResponse;
import org.springframework.stereotype.Component;

@Component
public class AccountActivityResponseMapper {

    public AccountActivityResponse toResponse(AccountActivity activity) {
        return new AccountActivityResponse(
                activity.getId(), activity.getBrokerageAccount().getId(), activity.getImportBatch().getId(),
                activity.getRowNumber(), activity.getExternalTransactionId(), activity.getAction(), activity.getOccurredAt(),
                activity.getIsin(), activity.getTicker(), activity.getInstrumentName(), activity.getNotes(),
                activity.getNumberOfShares(), activity.getPricePerShare(), activity.getPricePerShareCurrency(),
                activity.getExchangeRate(), activity.getResultAmount(), activity.getResultCurrency(),
                activity.getTotalAmount(), activity.getTotalCurrency(), activity.getWithholdingTax(), activity.getWithholdingTaxCurrency(),
                activity.getCurrencyConversionFee(), activity.getCurrencyConversionFeeCurrency(),
                activity.getMerchantName(), activity.getMerchantCategory(),
                activity.getImportBatch().getOriginalFilename(), activity.getJournalNote(), activity.getJournalNoteUpdatedAt()
        );
    }
}
