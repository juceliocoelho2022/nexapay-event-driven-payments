package br.com.nexapay.ledger.api;

import br.com.nexapay.ledger.service.LedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts/{accountId}")
    public Page<LedgerEntryResponse> getAccountEntries(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ledgerService.getEntriesByAccountId(accountId, pageable);
    }
}