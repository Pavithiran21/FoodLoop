package com.foodloop.claim;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.claim.ClaimDtos.ActiveCount;
import com.foodloop.claim.ClaimDtos.ClaimResponse;
import com.foodloop.claim.ClaimDtos.ClaimRow;
import com.foodloop.common.web.PageResponse;
import com.foodloop.report.ReportDtos.ReportRequest;
import com.foodloop.report.ReportService;
import com.foodloop.user.CurrentUser;

import jakarta.validation.Valid;

/** Claims, for both sides: the receiver's list, the tracker, and the moves each side may make. */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;
    private final ReportService reportService;

    public ClaimController(ClaimService claimService, ReportService reportService) {
        this.claimService = claimService;
        this.reportService = reportService;
    }

    /** My claims (receiver). tab = ACTIVE (default) or PAST. */
    @GetMapping
    public PageResponse<ClaimRow> mine(CurrentUser actor, @RequestParam(defaultValue = "ACTIVE") String tab,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return claimService.myClaims(actor, tab, page, size);
    }

    /** The number on the "My claims" badge. */
    @GetMapping("/active-count")
    public ActiveCount activeCount(CurrentUser actor) {
        return new ActiveCount(claimService.activeCount(actor));
    }

    @GetMapping("/{id}")
    public ClaimResponse get(CurrentUser actor, @PathVariable Long id) {
        return claimService.get(actor, id);
    }

    /** Donor: claimed to ready ("Mark as packed"). */
    @PostMapping("/{id}/ready")
    public ClaimResponse ready(CurrentUser actor, @PathVariable Long id) {
        return claimService.markReady(actor, id);
    }

    /** Donor: ready to picked up ("Confirm handover"). */
    @PostMapping("/{id}/picked-up")
    public ClaimResponse pickedUp(CurrentUser actor, @PathVariable Long id) {
        return claimService.confirmHandover(actor, id);
    }

    /** Receiver: picked up to completed ("I got it"). */
    @PostMapping("/{id}/complete")
    public ClaimResponse complete(CurrentUser actor, @PathVariable Long id) {
        return claimService.confirmReceipt(actor, id);
    }

    /** Either side, until the food is picked up. */
    @PostMapping("/{id}/cancel")
    public ClaimResponse cancel(CurrentUser actor, @PathVariable Long id) {
        return claimService.cancel(actor, id);
    }

    /** Donor: report the receiver of this claim. */
    @PostMapping("/{id}/report")
    public ResponseEntity<Void> report(CurrentUser actor, @PathVariable Long id, @Valid @RequestBody ReportRequest body) {
        reportService.reportReceiver(actor, id, body);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
