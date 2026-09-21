package com.likehang.alphaforge.model.mapper;

public class CsvRowMappingException extends IllegalArgumentException {

    private final int rowNumber;
    private final String columnName;
    private final String reason;

    public CsvRowMappingException(int rowNumber, String columnName, String reason) {
        super("CSV row " + rowNumber + ", column '" + columnName + "': " + reason); //子类继承父类的constructor
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.reason = reason;
    }

    public CsvRowMappingException(int rowNumber, String columnName, String reason, Throwable cause) {
        super("CSV row " + rowNumber + ", column '" + columnName + "': " + reason, cause);
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.reason = reason;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getReason() {
        return reason;
    }
}
