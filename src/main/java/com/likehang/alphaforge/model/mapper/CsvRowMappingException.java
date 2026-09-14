package com.likehang.alphaforge.model.mapper;

public class CsvRowMappingException extends IllegalArgumentException {

    private final int rowNumber;
    private final String columnName;

    public CsvRowMappingException(int rowNumber, String columnName, String message) {
        super("CSV row " + rowNumber + ", column '" + columnName + "': " + message);
        this.rowNumber = rowNumber;
        this.columnName = columnName;
    }

    public CsvRowMappingException(int rowNumber, String columnName, String message, Throwable cause) {
        super("CSV row " + rowNumber + ", column '" + columnName + "': " + message, cause);
        this.rowNumber = rowNumber;
        this.columnName = columnName;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }
}
