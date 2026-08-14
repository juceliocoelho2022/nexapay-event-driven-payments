package br.com.nexapay.account.api;

import br.com.nexapay.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @GetMapping("/{id}")
    public AccountResponse getById(@PathVariable UUID id) {
        return accountService.getById(id);
    }

    @PostMapping("/{id}/credit")
    public AccountResponse credit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return accountService.credit(id, request);
    }

    @PostMapping("/{id}/debit")
    public AccountResponse debit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return accountService.debit(id, request);
    }
}
