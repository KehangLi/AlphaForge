package com.likehang.alphaforge.model.dto.query;

import java.util.List;
import java.util.UUID;

public record AccountActivityPageResponse(
        UUID brokerageAccountId,
        List<AccountActivityResponse> activities,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
