package com.likehang.alphaforge.csv;

import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.rest.dto.response.CsvImportRowError;

import java.util.List;

public record AccountActivityCsvParseResult(
        int totalRows,
        List<AccountActivity> activities,
        int failedRows,
        List<CsvImportRowError> errors
) {
}
