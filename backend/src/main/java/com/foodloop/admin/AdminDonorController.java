package com.foodloop.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.admin.AdminDtos.DonorCounts;
import com.foodloop.admin.AdminDtos.ReasonRequest;
import com.foodloop.common.web.PageResponse;
import com.foodloop.donor.VerificationStatus;
import com.foodloop.user.CurrentUser;

import jakarta.validation.Valid;

/** Verify donors (Admin only; the role is checked in AdminDonorService). */
@RestController
@RequestMapping("/api/admin/donors")
public class AdminDonorController {

    private final AdminDonorService service;

    public AdminDonorController(AdminDonorService service) {
        this.service = service;
    }

    /** status = WAITING (default), VERIFIED or NOT_VERIFIED. */
    @GetMapping
    public PageResponse<DonorCard> list(CurrentUser actor, @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.list(actor, status, page, size);
    }

    /** The numbers on the tabs. */
    @GetMapping("/counts")
    public DonorCounts counts(CurrentUser actor) {
        return service.counts(actor);
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<Void> verify(CurrentUser actor, @PathVariable Long id) {
        service.verify(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(CurrentUser actor, @PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        service.reject(actor, id, body.reason());
        return ResponseEntity.noContent().build();
    }
}
