package com.foodloop.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.admin.AdminDtos.ReasonRequest;
import com.foodloop.common.web.PageResponse;
import com.foodloop.report.ReportDtos.ReportRow;
import com.foodloop.report.ReportDtos.ReportView;
import com.foodloop.report.ReportEnums.Status;
import com.foodloop.user.CurrentUser;

import jakarta.validation.Valid;

/** The Reports queue (Admin only; the role is checked in AdminReportService). */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService service;

    public AdminReportController(AdminReportService service) {
        this.service = service;
    }

    /** status omitted lists all, open first. */
    @GetMapping
    public PageResponse<ReportRow> list(CurrentUser actor, @RequestParam(required = false) Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.list(actor, status, page, size);
    }

    @GetMapping("/{id}")
    public ReportView view(CurrentUser actor, @PathVariable Long id) {
        return service.view(actor, id);
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(CurrentUser actor, @PathVariable Long id) {
        service.dismiss(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(CurrentUser actor, @PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        service.suspend(actor, id, body.reason());
        return ResponseEntity.noContent().build();
    }
}
