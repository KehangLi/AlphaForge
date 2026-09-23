package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvImportResult;
import com.likehang.alphaforge.service.AccountActivityCsvImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/account-activities")
public class TradeUploadController {

    private final AccountActivityCsvImportService accountActivityCsvImportService;

    public TradeUploadController(AccountActivityCsvImportService accountActivityCsvImportService) {
        this.accountActivityCsvImportService = accountActivityCsvImportService;
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AccountActivityCsvImportResult> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "brokerageAccountId", required = false) UUID brokerageAccountId
    ) {
        AccountActivityCsvImportResult result = brokerageAccountId == null
                ? accountActivityCsvImportService.importCsvForConfiguredDefaultAccount(file)
                : accountActivityCsvImportService.importCsv(brokerageAccountId, file);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
