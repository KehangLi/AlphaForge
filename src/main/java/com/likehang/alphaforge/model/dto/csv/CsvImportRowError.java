package com.likehang.alphaforge.model.dto.csv;

public record CsvImportRowError(
        int rowNumber,
        String columnName,
        String message
) {
}

// 不可变 只装数据 字段固定 主要用于传输