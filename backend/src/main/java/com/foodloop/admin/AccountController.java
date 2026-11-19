package com.foodloop.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.admin.AdminDtos.AccountRow;
import com.foodloop.admin.AdminDtos.AccountStatus;
import com.foodloop.admin.AdminDtos.ReasonRequest;
import com.foodloop.common.web.PageResponse;
import com.foodloop.user.CurrentUser;
import com.foodloop.user.Role;

import jakarta.validation.Valid;

/** The Accounts screen (Admin only; the role is checked in AccountService). */
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AccountRow> list(CurrentUser actor, @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role, @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.list(actor, q, role, status, page, size);
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(CurrentUser actor, @PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        service.suspend(actor, id, body.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reinstate")
    public ResponseEntity<Void> reinstate(CurrentUser actor, @PathVariable Long id) {
        service.reinstate(actor, id);
        return ResponseEntity.noContent().build();
    }
}
