package com.likehang.alphaforge.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "account_activity",
        indexes = {
                @Index(name = "idx_account_activity_import_batch_id", columnList = "import_batch_id"),
                @Index(name = "idx_account_activity_brokerage_account_id", columnList = "brokerage_account_id"),
                @Index(name = "idx_account_activity_external_transaction_id", columnList = "external_transaction_id"),
                @Index(name = "idx_account_activity_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_account_activity_ticker", columnList = "ticker"),
                @Index(name = "idx_account_activity_action", columnList = "action")
        }
)
public class AccountActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brokerage_account_id", nullable = false)
    private BrokerageAccount brokerageAccount;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "external_transaction_id", length = 120)
    private String externalTransactionId;

    @Column(name = "action", nullable = false, length = 120)
    private String action;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "isin", length = 12)
    private String isin;

    @Column(name = "ticker", length = 40)
    private String ticker;

    @Column(name = "instrument_name", length = 255)
    private String instrumentName;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "number_of_shares", precision = 28, scale = 10)
    private BigDecimal numberOfShares;

    @Column(name = "price_per_share", precision = 28, scale = 10)
    private BigDecimal pricePerShare;

    @Column(name = "price_per_share_currency", length = 3)
    private String pricePerShareCurrency;

    @Column(name = "exchange_rate", precision = 24, scale = 10)
    private BigDecimal exchangeRate;

    @Column(name = "result_amount", precision = 19, scale = 4)
    private BigDecimal resultAmount;

    @Column(name = "result_currency", length = 3)
    private String resultCurrency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "total_currency", nullable = false, length = 3)
    private String totalCurrency;

    @Column(name = "withholding_tax", precision = 19, scale = 4)
    private BigDecimal withholdingTax;

    @Column(name = "withholding_tax_currency", length = 3)
    private String withholdingTaxCurrency;

    @Column(name = "currency_conversion_fee", precision = 19, scale = 4)
    private BigDecimal currencyConversionFee;

    @Column(name = "currency_conversion_fee_currency", length = 3)
    private String currencyConversionFeeCurrency;

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "merchant_category", length = 120)
    private String merchantCategory;

    @Column(name = "raw_row_hash", length = 64)
    private String rawRowHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AccountActivity() {
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public void setImportBatch(ImportBatch importBatch) {
        this.importBatch = importBatch;
    }

    public BrokerageAccount getBrokerageAccount() {
        return brokerageAccount;
    }

    public void setBrokerageAccount(BrokerageAccount brokerageAccount) {
        this.brokerageAccount = brokerageAccount;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getNumberOfShares() {
        return numberOfShares;
    }

    public void setNumberOfShares(BigDecimal numberOfShares) {
        this.numberOfShares = numberOfShares;
    }

    public BigDecimal getPricePerShare() {
        return pricePerShare;
    }

    public void setPricePerShare(BigDecimal pricePerShare) {
        this.pricePerShare = pricePerShare;
    }

    public String getPricePerShareCurrency() {
        return pricePerShareCurrency;
    }

    public void setPricePerShareCurrency(String pricePerShareCurrency) {
        this.pricePerShareCurrency = pricePerShareCurrency;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public BigDecimal getResultAmount() {
        return resultAmount;
    }

    public void setResultAmount(BigDecimal resultAmount) {
        this.resultAmount = resultAmount;
    }

    public String getResultCurrency() {
        return resultCurrency;
    }

    public void setResultCurrency(String resultCurrency) {
        this.resultCurrency = resultCurrency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getTotalCurrency() {
        return totalCurrency;
    }

    public void setTotalCurrency(String totalCurrency) {
        this.totalCurrency = totalCurrency;
    }

    public BigDecimal getWithholdingTax() {
        return withholdingTax;
    }

    public void setWithholdingTax(BigDecimal withholdingTax) {
        this.withholdingTax = withholdingTax;
    }

    public String getWithholdingTaxCurrency() {
        return withholdingTaxCurrency;
    }

    public void setWithholdingTaxCurrency(String withholdingTaxCurrency) {
        this.withholdingTaxCurrency = withholdingTaxCurrency;
    }

    public BigDecimal getCurrencyConversionFee() {
        return currencyConversionFee;
    }

    public void setCurrencyConversionFee(BigDecimal currencyConversionFee) {
        this.currencyConversionFee = currencyConversionFee;
    }

    public String getCurrencyConversionFeeCurrency() {
        return currencyConversionFeeCurrency;
    }

    public void setCurrencyConversionFeeCurrency(String currencyConversionFeeCurrency) {
        this.currencyConversionFeeCurrency = currencyConversionFeeCurrency;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public String getRawRowHash() {
        return rawRowHash;
    }

    public void setRawRowHash(String rawRowHash) {
        this.rawRowHash = rawRowHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
