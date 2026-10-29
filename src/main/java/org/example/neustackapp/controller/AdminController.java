package org.example.neustackapp.controller;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.AdminSummaryResponse;
import org.example.neustackapp.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/summary")
    public ResponseEntity<AdminSummaryResponse> getAdminSummary() {
        AdminSummaryResponse summary = adminService.getAdminSummary();
        return ResponseEntity.ok(summary);
    }
}
