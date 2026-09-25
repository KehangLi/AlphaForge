package com.likehang.alphaforge.model.dto.query;

import java.util.UUID;

public record BrokerageAccountListResponse(
     UUID id,
     String brokerName,
     String accountName,
     String accountNumberMasked,
     String baseCurrency
) {
}
