package com.likehang.alphaforge.rest.dto.request;

public record BrokerageAccountCreateRequest(
        String brokerName,
        String accountName,
        String accountNumberMasked,
        String baseCurrency
) {
}
