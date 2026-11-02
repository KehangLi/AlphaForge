package com.likehang.alphaforge.rest.dto.response;

import java.util.List;
import java.util.UUID;

public record HoldingPageResponse(
        UUID brokerageAccountId,
        List<HoldingResponse> holdings,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
