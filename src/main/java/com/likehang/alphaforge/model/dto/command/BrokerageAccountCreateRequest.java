package com.likehang.alphaforge.model.dto.command;

public record BrokerageAccountCreateRequest(
        String brokerName,
        String accountName,
        String accountNumberMasked,
        String baseCurrency
) {
}
