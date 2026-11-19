package com.likehang.alphaforge.rest.dto.response;

public record CsvImportRowError(
        int rowNumber,
        String columnName,
        String message
) {
}
