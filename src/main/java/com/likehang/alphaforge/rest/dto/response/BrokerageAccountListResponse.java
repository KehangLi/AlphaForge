package com.likehang.alphaforge.rest.dto.response;

import java.util.UUID;

public record BrokerageAccountListResponse(
     UUID id,
     String brokerName,
     String accountName,
     String accountNumberMasked,
     String baseCurrency
) {
}
