package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.response.InstrumentDetailResponse;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.InstrumentQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts/{brokerageAccountId}/instruments")
public class BrokerageAccountInstrumentController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_PAGE = 1000;

    private final InstrumentQueryService instrumentQueryService;
    private final CurrentUserService currentUserService;

    public BrokerageAccountInstrumentController(InstrumentQueryService instrumentQueryService, CurrentUserService currentUserService) {
        this.instrumentQueryService = instrumentQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{instrumentKey}")
    public ResponseEntity<InstrumentDetailResponse> getInstrument(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @PathVariable("instrumentKey") String instrumentKey,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        PageRequest pageable = PageRequest.of(Math.clamp(page, 0, MAX_PAGE),
                size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE));
        InstrumentDetailResponse result = instrumentQueryService.getInstrumentByBrokerageAccount(
                currentUserService.getCurrentUserId(), brokerageAccountId, instrumentKey, pageable);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
